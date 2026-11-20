package com.example.data.reader

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class ParsedTextDocument(
    val title: String,
    val sections: List<DocumentSection>
)

data class DocumentSection(
    val title: String,
    val blocks: List<TextBlock>
)

object SafeHtmlTextParser {
    private val scriptStyleRegex = Regex(
        pattern = "<(script|style|noscript|iframe|object|embed)[^>]*>.*?</\\1>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val commentRegex = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
    private val headingRegex = Regex("<h([1-6])[^>]*>(.*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val blockTagRegex = Regex("</?(p|div|li|blockquote|pre|br|tr|section|article)[^>]*>", RegexOption.IGNORE_CASE)
    private val anyTagRegex = Regex("<[^>]+>")

    fun parseToBlocks(rawHtml: String): List<TextBlock> {
        val cleaned = rawHtml
            .replace(commentRegex, "")
            .replace(scriptStyleRegex, "")

        val blocks = mutableListOf<TextBlock>()
        // Extract headings in document order by splitting around heading tags and paragraph breaks
        val linesWithMarkers = cleaned
            .replace(headingRegex) { match ->
                val level = match.groupValues[1].toIntOrNull() ?: 2
                val inner = decodeEntities(match.groupValues[2].replace(anyTagRegex, " ")).trim()
                if (inner.isNotBlank()) "\n@@H$level@@$inner\n" else "\n"
            }
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n@@LI@@")
            .replace(Regex("<blockquote[^>]*>", RegexOption.IGNORE_CASE), "\n@@QT@@")
            .replace(Regex("<pre[^>]*>", RegexOption.IGNORE_CASE), "\n@@CODE@@")
            .replace(blockTagRegex, "\n")
            .replace(anyTagRegex, "")

        for (rawLine in linesWithMarkers.split('\n')) {
            val line = decodeEntities(rawLine).replace(Regex("[ \\t\\r]+"), " ").trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("@@H") && line.length > 6 && line[3].isDigit() && line.substring(4, 6) == "@@" -> {
                    val lvl = line[3].digitToIntOrNull() ?: 2
                    val text = line.substring(6).trim()
                    if (text.isNotEmpty()) blocks += TextBlock.Heading(lvl, text)
                }
                line.startsWith("@@LI@@") -> {
                    val text = line.removePrefix("@@LI@@").trim()
                    if (text.isNotEmpty()) blocks += TextBlock.BulletItem(text)
                }
                line.startsWith("@@QT@@") -> {
                    val text = line.removePrefix("@@QT@@").trim()
                    if (text.isNotEmpty()) blocks += TextBlock.Quote(text)
                }
                line.startsWith("@@CODE@@") -> {
                    val text = line.removePrefix("@@CODE@@").trim()
                    if (text.isNotEmpty()) blocks += TextBlock.Code(text)
                }
                else -> blocks += TextBlock.Paragraph(line)
            }
        }
        return blocks
    }

    private fun decodeEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.let { code ->
                    if (code in 32..0x10FFFF) String(Character.toChars(code)) else ""
                } ?: ""
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { code ->
                    if (code in 32..0x10FFFF) String(Character.toChars(code)) else ""
                } ?: ""
            }
    }
}

object ExtendedDocumentParser {

    private const val MAX_TEXT_BYTES = 16 * 1024 * 1024 // 16 MB safety cap

    fun parseTextDocument(
        contentResolver: ContentResolver,
        uri: Uri,
        format: DocumentFormat,
        fallbackTitle: String
    ): Result<ParsedTextDocument> {
        return when (format) {
            DocumentFormat.TXT -> parsePlainText(contentResolver, uri, fallbackTitle)
            DocumentFormat.HTML -> parseHtml(contentResolver, uri, fallbackTitle)
            DocumentFormat.MD -> parseMarkdown(contentResolver, uri, fallbackTitle)
            DocumentFormat.DOCX -> parseDocx(contentResolver, uri, fallbackTitle)
            DocumentFormat.ODT -> parseOdt(contentResolver, uri, fallbackTitle)
            DocumentFormat.FB2 -> parseFb2(contentResolver, uri, fallbackTitle)
            DocumentFormat.RTF -> parseRtf(contentResolver, uri, fallbackTitle)
            DocumentFormat.MOBI, DocumentFormat.AZW3 -> parseMobiOrAzw3(contentResolver, uri, fallbackTitle)
            DocumentFormat.XPS -> parseXps(contentResolver, uri, fallbackTitle)
            DocumentFormat.DOC -> Result.failure(
                UnsupportedOperationException(
                    "Legacy binary .doc (Word 97–2003) files require Microsoft Word or conversion to .docx / .pdf. Please convert to DOCX or PDF for full fidelity."
                )
            )
            DocumentFormat.CBR -> Result.failure(
                UnsupportedOperationException(
                    "CBR (RAR) archives use proprietary RAR compression not supported offline. Please convert to CBZ (ZIP) or PDF."
                )
            )
            else -> Result.failure(IllegalArgumentException("Format $format does not use text parser"))
        }
    }

    private fun readTextStream(contentResolver: ContentResolver, uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                val take = minOf(read, MAX_TEXT_BYTES - total)
                if (take <= 0) break
                out.write(buf, 0, take)
                total += take
            }
            out.toByteArray()
        } ?: throw IllegalStateException("Cannot open document stream")

        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> {
                val utf8 = String(bytes, Charsets.UTF_8)
                // If excessive replacement characters appear, fall back to Windows-1252 / ISO-8859-1
                val badCount = utf8.take(2048).count { it == '\uFFFD' }
                if (badCount > 8) String(bytes, Charset.forName("windows-1252")) else utf8
            }
        }
    }

    private fun chunkParagraphsIntoSections(
        title: String,
        blocks: List<TextBlock>,
        blocksPerSection: Int = 80
    ): ParsedTextDocument {
        if (blocks.isEmpty()) {
            return ParsedTextDocument(title, listOf(DocumentSection(title, listOf(TextBlock.Paragraph("")))))
        }
        val sections = blocks.chunked(blocksPerSection).mapIndexed { idx, chunk ->
            val heading = chunk.filterIsInstance<TextBlock.Heading>().firstOrNull()?.text
                ?: if (blocks.size <= blocksPerSection) title else "${title} (${idx + 1})"
            DocumentSection(heading, chunk)
        }
        return ParsedTextDocument(title, sections)
    }

    private fun parsePlainText(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            val text = readTextStream(resolver, uri)
            val blocks = text.replace("\r\n", "\n")
                .split(Regex("\n{2,}"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { TextBlock.Paragraph(it) }
            Result.success(chunkParagraphsIntoSections(title, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHtml(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            val html = readTextStream(resolver, uri)
            val docTitle = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: title
            val blocks = SafeHtmlTextParser.parseToBlocks(html)
            Result.success(chunkParagraphsIntoSections(docTitle, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMarkdown(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            val md = readTextStream(resolver, uri).replace("\r\n", "\n")
            val blocks = mutableListOf<TextBlock>()
            var inCode = false
            val codeBuf = StringBuilder()
            val paraBuf = StringBuilder()

            fun flushPara() {
                val p = paraBuf.toString().trim()
                if (p.isNotEmpty()) blocks += TextBlock.Paragraph(stripInlineMarkdown(p))
                paraBuf.clear()
            }

            for (rawLine in md.lines()) {
                val line = rawLine.trimEnd()
                if (line.trimStart().startsWith("```")) {
                    flushPara()
                    if (inCode) {
                        blocks += TextBlock.Code(codeBuf.toString().trimEnd())
                        codeBuf.clear()
                        inCode = false
                    } else {
                        inCode = true
                    }
                    continue
                }
                if (inCode) {
                    codeBuf.appendLine(rawLine)
                    continue
                }
                when {
                    line.isBlank() -> flushPara()
                    line.startsWith("#") -> {
                        flushPara()
                        val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                        val text = stripInlineMarkdown(line.drop(level).trim())
                        if (text.isNotEmpty()) blocks += TextBlock.Heading(level, text)
                    }
                    line.trimStart().startsWith(">") -> {
                        flushPara()
                        val q = stripInlineMarkdown(line.trimStart().removePrefix(">").trim())
                        if (q.isNotEmpty()) blocks += TextBlock.Quote(q)
                    }
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                        flushPara()
                        val item = stripInlineMarkdown(line.trimStart().drop(2).trim())
                        if (item.isNotEmpty()) blocks += TextBlock.BulletItem(item)
                    }
                    else -> {
                        if (paraBuf.isNotEmpty()) paraBuf.append(' ')
                        paraBuf.append(line.trim())
                    }
                }
            }
            flushPara()
            if (inCode && codeBuf.isNotEmpty()) {
                blocks += TextBlock.Code(codeBuf.toString().trimEnd())
            }
            Result.success(chunkParagraphsIntoSections(title, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stripInlineMarkdown(text: String): String {
        return text
            .replace(Regex("!\\[([^\\]]*)]\\([^)]+\\)"), "$1")
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
            .replace(Regex("(\\*|_)(.*?)\\1"), "$2")
            .replace("`", "")
    }

    private fun parseDocx(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            var docXml: ByteArray? = null
            resolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val name = sanitizeZipEntryPath(entry.name)
                        if (name == "word/document.xml") {
                            docXml = zis.readBytesBounded(12 * 1024 * 1024)
                            break
                        }
                        zis.closeEntry()
                    }
                }
            }
            val xmlBytes = docXml ?: return Result.failure(IllegalArgumentException("word/document.xml missing in DOCX"))
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            val blocks = mutableListOf<TextBlock>()
            val paraBuilder = StringBuilder()
            var isHeading = false
            var inText = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "w:p" -> {
                                paraBuilder.clear()
                                isHeading = false
                            }
                            "w:pStyle" -> {
                                val styleVal = parser.getAttributeValue(null, "w:val").orEmpty()
                                if (styleVal.contains("Heading", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                    isHeading = true
                                }
                            }
                            "w:t" -> inText = true
                            "w:tab" -> paraBuilder.append("  ")
                            "w:br" -> paraBuilder.append('\n')
                        }
                    }
                    XmlPullParser.TEXT -> if (inText) paraBuilder.append(parser.text.orEmpty())
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "w:t" -> inText = false
                            "w:p" -> {
                                val p = paraBuilder.toString().trim()
                                if (p.isNotEmpty()) {
                                    blocks += if (isHeading) TextBlock.Heading(2, p) else TextBlock.Paragraph(p)
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
            Result.success(chunkParagraphsIntoSections(title, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOdt(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            var contentXml: ByteArray? = null
            resolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val name = sanitizeZipEntryPath(entry.name)
                        if (name == "content.xml") {
                            contentXml = zis.readBytesBounded(12 * 1024 * 1024)
                            break
                        }
                        zis.closeEntry()
                    }
                }
            }
            val xmlBytes = contentXml ?: return Result.failure(IllegalArgumentException("content.xml missing in ODT"))
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            val blocks = mutableListOf<TextBlock>()
            val buf = StringBuilder()
            var capture = false
            var heading = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "text:h" -> { buf.clear(); capture = true; heading = true }
                            "text:p" -> { buf.clear(); capture = true; heading = false }
                            "text:s" -> if (capture) buf.append(' ')
                            "text:tab" -> if (capture) buf.append("  ")
                            "text:line-break" -> if (capture) buf.append('\n')
                        }
                    }
                    XmlPullParser.TEXT -> if (capture) buf.append(parser.text.orEmpty())
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "text:h" || parser.name == "text:p") {
                            val text = buf.toString().trim()
                            if (text.isNotEmpty()) {
                                blocks += if (heading) TextBlock.Heading(2, text) else TextBlock.Paragraph(text)
                            }
                            capture = false
                        }
                    }
                }
                event = parser.next()
            }
            Result.success(chunkParagraphsIntoSections(title, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFb2(resolver: ContentResolver, uri: Uri, fallbackTitle: String): Result<ParsedTextDocument> {
        return try {
            val raw = readTextStream(resolver, uri)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)), "UTF-8")

            var bookTitle = fallbackTitle
            val sections = mutableListOf<DocumentSection>()
            var currentSectionTitle = fallbackTitle
            var currentBlocks = mutableListOf<TextBlock>()
            var inBody = false
            var inTitle = false
            var inBookTitle = false
            var inParagraph = false
            var inCite = false
            val textBuf = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "book-title" -> { inBookTitle = true; textBuf.clear() }
                            "body" -> inBody = true
                            "section" -> {
                                if (currentBlocks.isNotEmpty()) {
                                    sections += DocumentSection(currentSectionTitle, currentBlocks)
                                    currentBlocks = mutableListOf()
                                }
                                currentSectionTitle = "Section ${sections.size + 1}"
                            }
                            "title" -> if (inBody) { inTitle = true; textBuf.clear() }
                            "p", "v" -> if (inBody) { inParagraph = true; textBuf.clear() }
                            "cite", "epigraph" -> if (inBody) inCite = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inBookTitle || inTitle || inParagraph) {
                            textBuf.append(parser.text.orEmpty())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.lowercase()) {
                            "book-title" -> {
                                val t = textBuf.toString().trim()
                                if (t.isNotEmpty()) bookTitle = t
                                inBookTitle = false
                            }
                            "body" -> inBody = false
                            "title" -> {
                                if (inTitle) {
                                    val t = textBuf.toString().replace(Regex("\\s+"), " ").trim()
                                    if (t.isNotEmpty()) {
                                        currentSectionTitle = t
                                        currentBlocks += TextBlock.Heading(2, t)
                                    }
                                    inTitle = false
                                }
                            }
                            "p", "v" -> {
                                if (inParagraph) {
                                    val t = textBuf.toString().replace(Regex("\\s+"), " ").trim()
                                    if (t.isNotEmpty()) {
                                        currentBlocks += if (inCite) TextBlock.Quote(t) else TextBlock.Paragraph(t)
                                    }
                                    inParagraph = false
                                }
                            }
                            "cite", "epigraph" -> inCite = false
                        }
                    }
                }
                event = parser.next()
            }
            if (currentBlocks.isNotEmpty()) {
                sections += DocumentSection(currentSectionTitle, currentBlocks)
            }
            if (sections.isEmpty()) {
                return Result.failure(IllegalArgumentException("No readable content found in FB2"))
            }
            Result.success(ParsedTextDocument(bookTitle, sections))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRtf(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            val rtf = readTextStream(resolver, uri)
            val out = StringBuilder()
            val skipGroups = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer")
            val stack = ArrayDeque<Boolean>() // true = skip group
            var skipCurrent = false
            var unicodeSkipCount = 1
            var i = 0

            while (i < rtf.length) {
                val ch = rtf[i]
                when (ch) {
                    '{' -> {
                        stack.addLast(skipCurrent)
                        i++
                    }
                    '}' -> {
                        skipCurrent = if (stack.isNotEmpty()) stack.removeLast() else false
                        i++
                    }
                    '\\' -> {
                        if (i + 1 >= rtf.length) break
                        val next = rtf[i + 1]
                        when {
                            next == '\\' || next == '{' || next == '}' -> {
                                if (!skipCurrent) out.append(next)
                                i += 2
                            }
                            next == '\'' && i + 3 < rtf.length -> {
                                val hex = rtf.substring(i + 2, i + 4).toIntOrNull(16)
                                if (!skipCurrent && hex != null) {
                                    out.append(hex.toChar())
                                }
                                i += 4
                            }
                            next == '*' -> {
                                skipCurrent = true
                                i += 2
                            }
                            else -> {
                                var j = i + 1
                                while (j < rtf.length && rtf[j].isLetter()) j++
                                val word = rtf.substring(i + 1, j)
                                val numStart = j
                                if (j < rtf.length && (rtf[j] == '-' || rtf[j].isDigit())) {
                                    j++
                                    while (j < rtf.length && rtf[j].isDigit()) j++
                                }
                                val param = rtf.substring(numStart, j).toIntOrNull()
                                if (j < rtf.length && rtf[j] == ' ') j++

                                if (word in skipGroups) {
                                    skipCurrent = true
                                } else if (!skipCurrent) {
                                    when (word) {
                                        "par", "line" -> out.append('\n')
                                        "tab" -> out.append("  ")
                                        "uc" -> if (param != null) unicodeSkipCount = param.coerceIn(0, 8)
                                        "u" -> {
                                            if (param != null) {
                                                val code = if (param < 0) param + 65536 else param
                                                out.append(code.toChar())
                                                // Skip replacement ANSI chars following \uN
                                                var skipped = 0
                                                while (skipped < unicodeSkipCount && j < rtf.length && rtf[j] != '\\' && rtf[j] != '{' && rtf[j] != '}') {
                                                    j++
                                                    skipped++
                                                }
                                            }
                                        }
                                    }
                                }
                                i = j
                            }
                        }
                    }
                    '\r', '\n' -> i++
                    else -> {
                        if (!skipCurrent) out.append(ch)
                        i++
                    }
                }
            }

            val blocks = out.toString()
                .split(Regex("\n+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { TextBlock.Paragraph(it) }
            Result.success(chunkParagraphsIntoSections(title, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMobiOrAzw3(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Cannot open MOBI/AZW3 file"))
            if (bytes.size < 80) return Result.failure(IllegalArgumentException("Invalid MOBI/AZW3 header"))

            fun u16(off: Int): Int = ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)
            fun u32(off: Int): Long =
                ((bytes[off].toLong() and 0xFF) shl 24) or
                    ((bytes[off + 1].toLong() and 0xFF) shl 16) or
                    ((bytes[off + 2].toLong() and 0xFF) shl 8) or
                    (bytes[off + 3].toLong() and 0xFF)

            val numRecords = u16(76)
            if (numRecords < 2 || bytes.size < 78 + numRecords * 8) {
                return Result.failure(IllegalArgumentException("Malformed PalmDB record table"))
            }

            val recordOffsets = IntArray(numRecords) { idx ->
                u32(78 + idx * 8).toInt()
            }

            val rec0 = recordOffsets[0]
            if (rec0 + 16 >= bytes.size) return Result.failure(IllegalArgumentException("Invalid PalmDOC record 0"))

            val compression = u16(rec0)
            val textRecordCount = u16(rec0 + 8)
            val encryptionType = u16(rec0 + 12)

            if (encryptionType != 0) {
                return Result.failure(IllegalStateException("DRM-protected or encrypted MOBI/AZW3 files cannot be opened."))
            }
            if (compression != 1 && compression != 2) {
                return Result.failure(
                    UnsupportedOperationException("HUFF/CDIC compressed MOBI/AZW3 variant (type $compression) is not supported.")
                )
            }

            val textOut = java.io.ByteArrayOutputStream()
            val maxRecord = minOf(textRecordCount, numRecords - 1)
            for (r in 1..maxRecord) {
                val start = recordOffsets[r]
                val end = if (r + 1 < numRecords) recordOffsets[r + 1] else bytes.size
                if (start < 0 || end <= start || end > bytes.size) continue
                if (compression == 1) {
                    textOut.write(bytes, start, end - start)
                } else {
                    decompressPalmDocLz77(bytes, start, end, textOut)
                }
            }

            val decoded = String(textOut.toByteArray(), Charsets.UTF_8)
            val blocks = SafeHtmlTextParser.parseToBlocks(decoded)
            Result.success(chunkParagraphsIntoSections(title, blocks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decompressPalmDocLz77(src: ByteArray, start: Int, end: Int, out: java.io.ByteArrayOutputStream) {
        val buf = mutableListOf<Byte>()
        var i = start
        while (i < end) {
            val c = src[i++].toInt() and 0xFF
            when {
                c == 0 -> buf.add(0)
                c in 1..8 -> {
                    val count = minOf(c, end - i)
                    repeat(count) { buf.add(src[i++]) }
                }
                c <= 0x7F -> buf.add(c.toByte())
                c >= 0xC0 -> {
                    buf.add(' '.code.toByte())
                    buf.add((c xor 0x80).toByte())
                }
                else -> {
                    if (i >= end) break
                    val next = src[i++].toInt() and 0xFF
                    val pair = (c shl 8) or next
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    if (distance in 1..buf.size) {
                        repeat(length) {
                            buf.add(buf[buf.size - distance])
                        }
                    }
                }
            }
        }
        out.write(buf.toByteArray())
    }

    private fun parseXps(resolver: ContentResolver, uri: Uri, title: String): Result<ParsedTextDocument> {
        return try {
            val pageTexts = mutableListOf<Pair<String, String>>()
            resolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val name = sanitizeZipEntryPath(entry.name)
                        if (name != null && name.endsWith(".fpage", ignoreCase = true)) {
                            val bytes = zis.readBytesBounded(4 * 1024 * 1024)
                            val extracted = extractXpsPageText(bytes)
                            if (extracted.isNotBlank()) {
                                pageTexts += name to extracted
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
            if (pageTexts.isEmpty()) {
                return Result.failure(IllegalArgumentException("No readable text pages found in XPS document"))
            }
            val sorted = pageTexts.sortedWith(compareBy { it.first })
            val sections = sorted.mapIndexed { idx, (_, text) ->
                val blocks = text.split('\n').filter { it.isNotBlank() }.map { TextBlock.Paragraph(it) }
                DocumentSection("Page ${idx + 1}", blocks)
            }
            Result.success(ParsedTextDocument(title, sections))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractXpsPageText(bytes: ByteArray): String {
        val sb = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("Glyphs", ignoreCase = true)) {
                val u = parser.getAttributeValue(null, "UnicodeString")
                if (!u.isNullOrBlank()) {
                    sb.appendLine(u)
                }
            }
            event = parser.next()
        }
        return sb.toString()
    }
}
