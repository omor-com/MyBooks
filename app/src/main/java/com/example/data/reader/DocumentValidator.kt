package com.example.data.reader

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class ValidatedDocumentInfo(
    val format: DocumentFormat,
    val contentHash: String,
    val fileSizeBytes: Long,
    val detectedTitle: String? = null,
    val detectedAuthor: String? = null,
    val detectedPublisher: String? = null,
    val detectedLanguage: String? = null,
    val detectedDescription: String? = null,
    val errorReason: String? = null
)

object DocumentValidator {

    private const val MAX_HASH_BYTES = 2 * 1024 * 1024 // Stream hash up to 2MB for fast duplicate fingerprinting

    fun validateAndInspect(
        contentResolver: ContentResolver,
        uri: Uri,
        displayName: String,
        mimeType: String?
    ): Result<ValidatedDocumentInfo> {
        return try {
            val header = ByteArray(128)
            var headerRead = 0
            var totalSize = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            contentResolver.openInputStream(uri)?.use { raw ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = raw.read(buf)
                    if (read <= 0) break
                    if (headerRead < header.size) {
                        val toCopy = minOf(read, header.size - headerRead)
                        System.arraycopy(buf, 0, header, headerRead, toCopy)
                        headerRead += toCopy
                    }
                    if (totalSize < MAX_HASH_BYTES) {
                        val hashLen = minOf(read.toLong(), MAX_HASH_BYTES - totalSize).toInt()
                        digest.update(buf, 0, hashLen)
                    }
                    totalSize += read
                }
            } ?: return Result.failure(IllegalStateException("Cannot open document stream"))

            if (totalSize == 0L) {
                return Result.failure(IllegalArgumentException("Empty file (0 bytes)"))
            }

            // Include totalSize in digest so files sharing prefix but differing in tail don't collide
            digest.update(totalSize.toString().toByteArray())
            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }

            val hintFormat = DocumentFormat.fromExtensionOrMime(displayName, mimeType)
            val detectedFormat = detectByMagicBytes(
                contentResolver = contentResolver,
                uri = uri,
                header = header.copyOf(headerRead),
                hint = hintFormat
            ) ?: return Result.failure(
                IllegalArgumentException("Unsupported or unrecognized document format: $displayName")
            )

            // Refuse raw ZIP archives that are not EPUB, DOCX, ODT, CBZ, or XPS
            if (isZipHeader(header, headerRead) &&
                detectedFormat !in setOf(
                    DocumentFormat.EPUB,
                    DocumentFormat.DOCX,
                    DocumentFormat.ODT,
                    DocumentFormat.CBZ,
                    DocumentFormat.XPS
                )
            ) {
                return Result.failure(IllegalArgumentException("Generic ZIP archives are not book documents"))
            }

            Result.success(
                ValidatedDocumentInfo(
                    format = detectedFormat,
                    contentHash = hashHex,
                    fileSizeBytes = totalSize
                )
            )
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isZipHeader(header: ByteArray, len: Int): Boolean {
        return len >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte())
    }

    private fun detectByMagicBytes(
        contentResolver: ContentResolver,
        uri: Uri,
        header: ByteArray,
        hint: DocumentFormat?
    ): DocumentFormat? {
        if (header.size >= 5 &&
            header[0] == '%'.code.toByte() &&
            header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[4] == '-'.code.toByte()
        ) {
            return DocumentFormat.PDF
        }

        // Images: JPEG, PNG, WEBP, TIFF
        if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
            return DocumentFormat.IMAGE
        }
        if (header.size >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
        ) {
            return DocumentFormat.IMAGE
        }
        if (header.size >= 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) {
            return DocumentFormat.IMAGE
        }
        if (header.size >= 4 && (
                (header[0] == 0x49.toByte() && header[1] == 0x49.toByte() && header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) ||
                (header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() && header[2] == 0x00.toByte() && header[3] == 0x2A.toByte())
            )
        ) {
            return DocumentFormat.IMAGE
        }

        // RAR (CBR)
        if (header.size >= 6 && header[0] == 0x52.toByte() && header[1] == 0x61.toByte() &&
            header[2] == 0x72.toByte() && header[3] == 0x21.toByte()
        ) {
            return DocumentFormat.CBR
        }

        // RTF
        val asciiPrefix = String(header, 0, minOf(header.size, 64), Charsets.US_ASCII)
        if (asciiPrefix.trimStart().startsWith("{\\rtf")) {
            return DocumentFormat.RTF
        }

        // MOBI / AZW3 PalmDB signature at offset 60 ("BOOKMOBI")
        if (header.size >= 68) {
            val mobiSig = String(header, 60, 8, Charsets.US_ASCII)
            if (mobiSig == "BOOKMOBI" || mobiSig == "TEXtREAd") {
                return if (hint == DocumentFormat.AZW3) DocumentFormat.AZW3 else DocumentFormat.MOBI
            }
        }

        // Legacy OLE2 (.doc)
        if (header.size >= 8 &&
            header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
        ) {
            return DocumentFormat.DOC
        }

        // ZIP containers: inspect entries to distinguish EPUB, DOCX, ODT, XPS, CBZ
        if (isZipHeader(header, header.size)) {
            return inspectZipContainer(contentResolver, uri, hint)
        }

        // Text / XML / HTML / Markdown
        val textPreview = decodePreviewText(header).trimStart()
        if (textPreview.contains("<FictionBook", ignoreCase = true)) {
            return DocumentFormat.FB2
        }
        if (textPreview.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            textPreview.startsWith("<html", ignoreCase = true) ||
            (hint == DocumentFormat.HTML && isLikelyText(header))
        ) {
            return DocumentFormat.HTML
        }
        if (hint == DocumentFormat.MD && isLikelyText(header)) {
            return DocumentFormat.MD
        }
        if (hint == DocumentFormat.FB2 && textPreview.startsWith("<")) {
            return DocumentFormat.FB2
        }
        if ((hint == DocumentFormat.TXT || hint == null) && isLikelyText(header)) {
            return hint ?: DocumentFormat.TXT
        }

        return null
    }

    private fun inspectZipContainer(
        contentResolver: ContentResolver,
        uri: Uri,
        hint: DocumentFormat?
    ): DocumentFormat? {
        var hasEpubContainer = false
        var hasDocx = false
        var hasOdtMimetype = false
        var hasXps = false
        var hasImages = false

        contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zis ->
                var entriesScanned = 0
                while (entriesScanned < 60) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name.lowercase()
                    entriesScanned++
                    when {
                        name == "meta-inf/container.xml" -> hasEpubContainer = true
                        name == "word/document.xml" -> hasDocx = true
                        name == "fixeddocumentsequence.fdseq" || name.endsWith(".fpage") -> hasXps = true
                        name == "mimetype" -> {
                            val bytes = zis.readBytesBounded(128)
                            val mime = String(bytes, Charsets.UTF_8).trim()
                            if (mime == "application/epub+zip") return DocumentFormat.EPUB
                            if (mime == "application/vnd.oasis.opendocument.text") hasOdtMimetype = true
                        }
                        name == "content.xml" && hint == DocumentFormat.ODT -> hasOdtMimetype = true
                        name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                            name.endsWith(".png") || name.endsWith(".webp") -> hasImages = true
                    }
                    zis.closeEntry()
                }
            }
        }

        return when {
            hasEpubContainer -> DocumentFormat.EPUB
            hasDocx -> DocumentFormat.DOCX
            hasOdtMimetype -> DocumentFormat.ODT
            hasXps -> DocumentFormat.XPS
            hint == DocumentFormat.CBZ && hasImages -> DocumentFormat.CBZ
            hasImages && hint == null -> DocumentFormat.CBZ
            else -> null
        }
    }

    private fun decodePreviewText(bytes: ByteArray): String {
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }
    }

    private fun isLikelyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.size >= 2 && (
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            )
        ) {
            return true
        }
        var controlBytes = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0) return false
            if (v < 0x09 || (v in 0x0E..0x1F && v != 0x1B)) {
                controlBytes++
            }
        }
        return controlBytes <= bytes.size / 10
    }
}

internal fun ZipInputStream.readBytesBounded(maxBytes: Int = 8 * 1024 * 1024): ByteArray {
    val buffer = ByteArray(8192)
    val out = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        if (total + read > maxBytes) {
            throw SecurityException("Archive entry exceeds safe extraction limit ($maxBytes bytes)")
        }
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}

internal fun sanitizeZipEntryPath(entryName: String): String? {
    val normalized = entryName.replace('\\', '/').trimStart('/')
    if (normalized.isEmpty() || normalized.contains("..")) return null
    return normalized
}
