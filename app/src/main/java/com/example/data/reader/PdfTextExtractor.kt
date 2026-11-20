package com.example.data.reader

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

data class PdfExtractedPage(
    val pageIndex: Int,
    val text: String
)

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String
)

/**
 * Lightweight offline PDF text-stream extractor for standard embedded text PDFs
 * (supporting uncompressed and FlateDecode content streams). Scanned/image-only PDFs
 * yield empty text and are handled via [OfflineOcrHelper].
 */
object PdfTextExtractor {
    private const val MAX_PDF_BYTES_FOR_TEXT_SCAN = 24 * 1024 * 1024 // 24 MB safety guard

    fun extractAllTextChunks(
        contentResolver: ContentResolver,
        uri: Uri,
        maxPagesEstimate: Int = 500
    ): List<PdfExtractedPage> {
        val pdfBytes = readBoundedBytes(contentResolver, uri) ?: return emptyList()
        if (pdfBytes.size < 16) return emptyList()
        val rawLatin = String(pdfBytes, Charsets.ISO_8859_1)
        val streams = extractContentStreams(pdfBytes, rawLatin, maxStreams = maxPagesEstimate * 3)
        if (streams.isEmpty()) return emptyList()

        val pages = mutableListOf<PdfExtractedPage>()
        var pageIdx = 0
        for (streamText in streams) {
            if (!streamText.contains("BT") && !streamText.contains("Tj") && !streamText.contains("TJ")) {
                continue
            }
            val extracted = extractTextOperators(streamText).trim()
            if (extracted.isNotEmpty() && !isMostlyGarbage(extracted)) {
                pages += PdfExtractedPage(pageIndex = pageIdx, text = extracted)
                pageIdx++
                if (pages.size >= maxPagesEstimate) break
            }
        }
        return pages
    }

    fun extractTextFromBytes(pdfBytes: ByteArray): String {
        if (pdfBytes.size < 16) return ""
        val rawLatin = String(pdfBytes, Charsets.ISO_8859_1)
        val streams = extractContentStreams(pdfBytes, rawLatin, maxStreams = 250)
        return streams.asSequence()
            .filter { it.contains("Tj") || it.contains("TJ") }
            .map { extractTextOperators(it).trim() }
            .filter { it.isNotEmpty() && !isMostlyGarbage(it) }
            .joinToString("\n\n")
    }

    fun searchPdfText(
        contentResolver: ContentResolver,
        uri: Uri,
        query: String,
        ocrFallbackCache: Map<Int, String> = emptyMap(),
        maxHits: Int = 50
    ): List<PdfSearchHit> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val hits = mutableListOf<PdfSearchHit>()
        val extracted = extractAllTextChunks(contentResolver, uri)
        val combinedByPage = linkedMapOf<Int, String>()
        extracted.forEach { combinedByPage[it.pageIndex] = it.text }
        ocrFallbackCache.forEach { (page, ocrText) ->
            val existing = combinedByPage[page].orEmpty()
            if (ocrText.isNotBlank()) {
                combinedByPage[page] = if (existing.isBlank()) ocrText else "$existing\n$ocrText"
            }
        }
        for ((pageIdx, text) in combinedByPage) {
            val matchIdx = text.indexOf(clean, ignoreCase = true)
            if (matchIdx >= 0) {
                val start = (matchIdx - 40).coerceAtLeast(0)
                val end = (matchIdx + clean.length + 70).coerceAtMost(text.length)
                val prefix = if (start > 0) "…" else ""
                val suffix = if (end < text.length) "…" else ""
                val snippet = (prefix + text.substring(start, end) + suffix)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                hits += PdfSearchHit(pageIndex = pageIdx, snippet = snippet)
                if (hits.size >= maxHits) break
            }
        }
        return hits
    }

    private fun readBoundedBytes(contentResolver: ContentResolver, uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    val take = minOf(read, MAX_PDF_BYTES_FOR_TEXT_SCAN - total)
                    if (take <= 0) break
                    out.write(buf, 0, take)
                    total += take
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractContentStreams(
        pdfBytes: ByteArray,
        rawLatin: String,
        maxStreams: Int
    ): List<String> {
        val results = mutableListOf<String>()
        var searchFrom = 0
        while (results.size < maxStreams) {
            val streamKwIdx = rawLatin.indexOf("stream", startIndex = searchFrom)
            if (streamKwIdx < 0) break
            val headerStart = (streamKwIdx - 256).coerceAtLeast(0)
            val dictWindow = rawLatin.substring(headerStart, streamKwIdx)
            val afterKw = streamKwIdx + 6
            val dataStart = when {
                afterKw + 1 < pdfBytes.size && rawLatin[afterKw] == '\r' && rawLatin[afterKw + 1] == '\n' -> afterKw + 2
                afterKw < pdfBytes.size && (rawLatin[afterKw] == '\n' || rawLatin[afterKw] == '\r') -> afterKw + 1
                else -> {
                    searchFrom = afterKw
                    continue
                }
            }
            val endStreamIdx = rawLatin.indexOf("endstream", startIndex = dataStart)
            if (endStreamIdx < 0) break

            var dataEnd = endStreamIdx
            while (dataEnd > dataStart && (rawLatin[dataEnd - 1] == '\n' || rawLatin[dataEnd - 1] == '\r')) {
                dataEnd--
            }
            val sliceLen = (dataEnd - dataStart).coerceIn(0, 4 * 1024 * 1024)
            if (sliceLen > 0) {
                val slice = pdfBytes.copyOfRange(dataStart, dataStart + sliceLen)
                val isFlate = dictWindow.contains("/FlateDecode", ignoreCase = true) ||
                    dictWindow.contains("/Fl ", ignoreCase = false)
                val decodedText = if (isFlate) {
                    inflateBounded(slice)?.let { String(it, Charsets.ISO_8859_1) }
                } else if (!dictWindow.contains("/DCTDecode") && !dictWindow.contains("/JPXDecode")) {
                    String(slice, Charsets.ISO_8859_1)
                } else {
                    null
                }
                if (!decodedText.isNullOrBlank()) {
                    results += decodedText
                }
            }
            searchFrom = endStreamIdx + 9
        }
        return results
    }

    private fun inflateBounded(compressed: ByteArray, maxOutputBytes: Int = 2 * 1024 * 1024): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (!inflater.finished() && total < maxOutputBytes) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, count)
                    total += count
                }
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun extractTextOperators(stream: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = stream.length
        while (i < n) {
            when (val ch = stream[i]) {
                '(' -> {
                    val parsed = StringBuilder()
                    var depth = 1
                    i++
                    while (i < n && depth > 0) {
                        val c = stream[i]
                        when (c) {
                            '\\' -> {
                                i++
                                if (i < n) {
                                    when (val esc = stream[i]) {
                                        'n' -> parsed.append('\n')
                                        'r' -> parsed.append(' ')
                                        't' -> parsed.append('\t')
                                        'b', 'f' -> Unit
                                        '(', ')', '\\' -> parsed.append(esc)
                                        in '0'..'7' -> {
                                            val octEnd = minOf(n, i + 3)
                                            var oct = ""
                                            while (i < octEnd && stream[i] in '0'..'7') {
                                                oct += stream[i]
                                                i++
                                            }
                                            i--
                                            val code = oct.toIntOrNull(8)
                                            if (code != null && code >= 32) parsed.append(code.toChar())
                                        }
                                        else -> parsed.append(esc)
                                    }
                                }
                            }
                            '(' -> {
                                depth++
                                parsed.append('(')
                            }
                            ')' -> {
                                depth--
                                if (depth > 0) parsed.append(')')
                            }
                            else -> if (c.code >= 32 || c == '\n' || c == '\t') parsed.append(c)
                        }
                        i++
                    }
                    // Look ahead briefly to confirm it belongs to Tj / TJ / ' / "
                    val tail = stream.substring(i, minOf(n, i + 40))
                    if (tail.contains("Tj") || tail.contains("]") || tail.contains("'") || tail.contains("\"")) {
                        if (parsed.isNotEmpty()) {
                            sb.append(parsed)
                        }
                    }
                }
                'T' -> {
                    if (i + 1 < n && (stream[i + 1] == '*' || stream[i + 1] == 'D' || stream[i + 1] == 'd')) {
                        if (sb.isNotEmpty() && sb.last() != '\n' && sb.last() != ' ') sb.append(' ')
                    }
                    i++
                }
                'E' -> {
                    if (i + 1 < n && stream[i + 1] == 'T') {
                        if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                    }
                    i++
                }
                else -> i++
            }
        }
        return sb.toString()
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" ?\n ?"), "\n")
            .trim()
    }

    private fun isMostlyGarbage(text: String): Boolean {
        if (text.isBlank()) return true
        val sample = text.take(400)
        val printable = sample.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?'-()\"/" }
        return printable.toFloat() / sample.length.toFloat() < 0.55f
    }
}
