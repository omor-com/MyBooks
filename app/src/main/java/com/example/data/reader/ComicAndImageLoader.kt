package com.example.data.reader

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

object ComicAndImageLoader {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")

    fun listCbzEntries(contentResolver: ContentResolver, uri: Uri): Result<List<String>> {
        return try {
            val names = mutableListOf<String>()
            contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val safe = sanitizeZipEntryPath(entry.name)
                        if (!entry.isDirectory && safe != null) {
                            val ext = safe.substringAfterLast('.', "").lowercase()
                            if (ext in imageExtensions) {
                                names += safe
                            }
                        }
                        zis.closeEntry()
                    }
                }
            } ?: return Result.failure(IllegalStateException("Cannot open CBZ archive"))

            if (names.isEmpty()) {
                return Result.failure(IllegalArgumentException("No supported images found inside CBZ archive"))
            }
            Result.success(names.sortedWith(NaturalOrderComparator))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadCbzPageBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
        entryName: String,
        maxDimensionPx: Int = 2048
    ): Bitmap? {
        return try {
            var bytes: ByteArray? = null
            contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val safe = sanitizeZipEntryPath(entry.name)
                        if (safe == entryName) {
                            bytes = zis.readBytesBounded(16 * 1024 * 1024)
                            break
                        }
                        zis.closeEntry()
                    }
                }
            }
            bytes?.let { decodeSampledBitmap(it, maxDimensionPx) }
        } catch (_: Exception) {
            null
        }
    }

    fun loadSingleImageBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimensionPx: Int = 2400
    ): Bitmap? {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                val limit = 24 * 1024 * 1024
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    if (total + r > limit) break
                    out.write(buf, 0, r)
                    total += r
                }
                out.toByteArray()
            } ?: return null
            decodeSampledBitmap(bytes, maxDimensionPx)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private object NaturalOrderComparator : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val partsA = splitTokens(a)
            val partsB = splitTokens(b)
            for (i in 0 until minOf(partsA.size, partsB.size)) {
                val pA = partsA[i]
                val pB = partsB[i]
                val nA = pA.toLongOrNull()
                val nB = pB.toLongOrNull()
                val cmp = if (nA != null && nB != null) nA.compareTo(nB) else pA.compareTo(pB, ignoreCase = true)
                if (cmp != 0) return cmp
            }
            return partsA.size.compareTo(partsB.size)
        }

        private fun splitTokens(s: String): List<String> =
            Regex("(\\d+|\\D+)").findAll(s).map { it.value }.toList()
    }
}
