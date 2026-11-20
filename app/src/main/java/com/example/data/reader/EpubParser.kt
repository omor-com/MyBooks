package com.example.data.reader

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val paragraphs: List<TextBlock>
)

data class EpubTocItem(
    val title: String,
    val chapterIndex: Int
)

data class EpubBookContent(
    val title: String,
    val author: String,
    val publisher: String,
    val language: String,
    val description: String,
    val coverBytes: ByteArray?,
    val chapters: List<EpubChapter>,
    val toc: List<EpubTocItem>
)

sealed interface TextBlock {
    data class Heading(val level: Int, val text: String) : TextBlock
    data class Paragraph(val text: String) : TextBlock
    data class Quote(val text: String) : TextBlock
    data class Code(val text: String) : TextBlock
    data class BulletItem(val text: String) : TextBlock
}

object EpubParser {

    fun parse(contentResolver: ContentResolver, uri: Uri): Result<EpubBookContent> {
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            var totalBytes = 0L
            val maxTotalBytes = 64L * 1024L * 1024L

            contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val safeName = sanitizeZipEntryPath(entry.name)
                        if (!entry.isDirectory && safeName != null) {
                            // Skip huge fonts/videos inside epub while keeping xml/html/images
                            val ext = safeName.substringAfterLast('.', "").lowercase()
                            if (ext in setOf("xml", "opf", "ncx", "xhtml", "html", "htm", "txt", "jpg", "jpeg", "png", "webp", "svg")) {
                                val data = zis.readBytesBounded(6 * 1024 * 1024)
                                totalBytes += data.size
                                if (totalBytes > maxTotalBytes) {
                                    throw SecurityException("EPUB archive exceeds memory safety limit")
                                }
                                entries[safeName] = data
                            }
                        }
                        zis.closeEntry()
                    }
                }
            } ?: return Result.failure(IllegalStateException("Unable to read EPUB file"))

            if (entries.isEmpty()) {
                return Result.failure(IllegalArgumentException("EPUB archive is empty or corrupted"))
            }

            // Check DRM encryption.xml
            if (entries.keys.any { it.equals("META-INF/encryption.xml", ignoreCase = true) || it.equals("META-INF/rights.xml", ignoreCase = true) }) {
                val encXml = entries.entries.firstOrNull { it.key.equals("META-INF/encryption.xml", ignoreCase = true) }?.value
                if (encXml != null && String(encXml, Charsets.UTF_8).contains("EncryptedData", ignoreCase = true)) {
                    // Font obfuscation uses IDPF/Adobe font mangling; only fail if content XHTML is encrypted
                    val encText = String(encXml, Charsets.UTF_8)
                    if (!encText.contains("font", ignoreCase = true) && encText.contains(".html", ignoreCase = true)) {
                        return Result.failure(IllegalStateException("This EPUB is DRM-encrypted and cannot be opened"))
                    }
                }
            }

            val containerBytes = entries["META-INF/container.xml"]
                ?: entries.entries.firstOrNull { it.key.endsWith("container.xml", ignoreCase = true) }?.value
            val opfPath = containerBytes?.let { parseContainerOpfPath(it) }
                ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
                ?: return Result.failure(IllegalArgumentException("EPUB OPF package manifest missing"))

            val opfBytes = entries[opfPath]
                ?: entries.entries.firstOrNull { it.key.equals(opfPath, ignoreCase = true) }?.value
                ?: return Result.failure(IllegalArgumentException("EPUB OPF file missing: $opfPath"))

            val opfDir = opfPath.substringBeforeLast('/', "")
            val parsedOpf = parseOpf(opfBytes, opfDir)

            val coverBytes = parsedOpf.coverHref?.let { href ->
                val resolved = resolvePath(opfDir, href)
                entries[resolved] ?: entries.entries.firstOrNull { it.key.equals(resolved, ignoreCase = true) }?.value
            }

            val ncxTitles = parsedOpf.ncxHref?.let { href ->
                val resolved = resolvePath(opfDir, href)
                entries[resolved]?.let { parseNcxTitles(it, opfDir) }
            } ?: emptyMap()

            val chapters = mutableListOf<EpubChapter>()
            parsedOpf.spineHrefs.forEachIndexed { index, rawHref ->
                val cleanHref = rawHref.substringBefore('#')
                val resolved = resolvePath(opfDir, cleanHref)
                val chapterBytes = entries[resolved]
                    ?: entries.entries.firstOrNull { it.key.equals(resolved, ignoreCase = true) }?.value
                if (chapterBytes != null) {
                    val html = String(chapterBytes, Charsets.UTF_8)
                    val blocks = SafeHtmlTextParser.parseToBlocks(html)
                    if (blocks.isNotEmpty()) {
                        val title = ncxTitles[resolved]
                            ?: ncxTitles[cleanHref]
                            ?: blocks.filterIsInstance<TextBlock.Heading>().firstOrNull()?.text
                            ?: "Chapter ${chapters.size + 1}"
                        chapters += EpubChapter(
                            id = "ch_${index}",
                            title = title.take(120),
                            href = resolved,
                            paragraphs = blocks
                        )
                    }
                }
            }

            if (chapters.isEmpty()) {
                return Result.failure(IllegalArgumentException("No readable chapters found in EPUB"))
            }

            val toc = chapters.mapIndexed { idx, ch -> EpubTocItem(ch.title, idx) }

            Result.success(
                EpubBookContent(
                    title = parsedOpf.title.ifBlank { "Untitled EPUB" },
                    author = parsedOpf.author,
                    publisher = parsedOpf.publisher,
                    language = parsedOpf.language,
                    description = parsedOpf.description,
                    coverBytes = coverBytes,
                    chapters = chapters,
                    toc = toc
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolvePath(baseDir: String, relative: String): String {
        val decoded = Uri.decode(relative).substringBefore('#').trimStart('/')
        if (baseDir.isEmpty()) return decoded
        val combined = "$baseDir/$decoded"
        val stack = ArrayDeque<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun parseContainerOpfPath(bytes: ByteArray): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val path = parser.getAttributeValue(null, "full-path")
                if (!path.isNullOrBlank()) return path
            }
            event = parser.next()
        }
        return null
    }

    private data class OpfData(
        val title: String,
        val author: String,
        val publisher: String,
        val language: String,
        val description: String,
        val coverHref: String?,
        val ncxHref: String?,
        val spineHrefs: List<String>
    )

    private fun parseOpf(bytes: ByteArray, opfDir: String): OpfData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var title = ""
        var author = ""
        var publisher = ""
        var language = ""
        var description = ""
        var coverId: String? = null
        var coverHref: String? = null
        var ncxId: String? = null
        val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, mediaType)
        val spineIds = mutableListOf<String>()

        var currentTag: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':').lowercase()
                    currentTag = tag
                    when (tag) {
                        "meta" -> {
                            val name = parser.getAttributeValue(null, "name")
                            val content = parser.getAttributeValue(null, "content")
                            if (name.equals("cover", ignoreCase = true) && !content.isNullOrBlank()) {
                                coverId = content
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id").orEmpty()
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                            val props = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id.isNotBlank() && href.isNotBlank()) {
                                manifest[id] = href to mediaType
                                if (props.contains("cover-image", ignoreCase = true)) {
                                    coverHref = href
                                }
                                if (mediaType == "application/x-dtbncx+xml") {
                                    ncxId = id
                                }
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) spineIds += idref
                        }
                        "spine" -> {
                            val tocAttr = parser.getAttributeValue(null, "toc")
                            if (!tocAttr.isNullOrBlank()) ncxId = tocAttr
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "title" -> if (title.isEmpty()) title = text
                            "creator" -> if (author.isEmpty()) author = text
                            "publisher" -> if (publisher.isEmpty()) publisher = text
                            "language" -> if (language.isEmpty()) language = text
                            "description" -> if (description.isEmpty()) description = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> currentTag = null
            }
            event = parser.next()
        }

        if (coverHref == null && coverId != null) {
            coverHref = manifest[coverId]?.first
        }
        if (coverHref == null) {
            coverHref = manifest.values.firstOrNull { (href, mime) ->
                mime.startsWith("image/") && href.contains("cover", ignoreCase = true)
            }?.first
        }

        val spineHrefs = if (spineIds.isNotEmpty()) {
            spineIds.mapNotNull { manifest[it]?.first }
        } else {
            manifest.values.filter { (_, mime) ->
                mime.contains("html", ignoreCase = true)
            }.map { it.first }
        }

        val ncxHref = ncxId?.let { manifest[it]?.first }
        return OpfData(title, author, publisher, language, description, coverHref, ncxHref, spineHrefs)
    }

    private fun parseNcxTitles(bytes: ByteArray, opfDir: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var inNavLabel = false
            var inText = false
            var pendingLabel = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.substringAfter(':').lowercase()
                        when (tag) {
                            "navlabel" -> {
                                inNavLabel = true
                                pendingLabel = ""
                            }
                            "text" -> if (inNavLabel) inText = true
                            "content" -> {
                                val src = parser.getAttributeValue(null, "src")?.substringBefore('#')
                                if (!src.isNullOrBlank() && pendingLabel.isNotBlank()) {
                                    result[resolvePath(opfDir, src)] = pendingLabel
                                    result[src] = pendingLabel
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inNavLabel && inText) {
                            pendingLabel += parser.text.orEmpty()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.substringAfter(':').lowercase()
                        if (tag == "text") inText = false
                        if (tag == "navlabel") {
                            inNavLabel = false
                            pendingLabel = pendingLabel.trim()
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return result
    }
}
