package com.example.data.reader

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class OcrPageResult(
    val pageIndex: Int,
    val text: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Offline OCR powered by bundled Google ML Kit Latin Text Recognition.
 * Note: Offline bundled recognizer supports Latin-script text only and may make recognition mistakes on noisy scans.
 */
object OfflineOcrHelper {

    suspend fun recognizeBitmap(bitmap: Bitmap, pageIndex: Int): OcrPageResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val recognizer = try {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            } catch (e: Throwable) {
                cont.resume(
                    OcrPageResult(
                        pageIndex = pageIndex,
                        text = "",
                        isSuccess = false,
                        errorMessage = e.localizedMessage ?: "OCR recognizer unavailable"
                    )
                )
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                runCatching { recognizer.close() }
            }

            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        runCatching { recognizer.close() }
                        if (cont.isActive) {
                            cont.resume(
                                OcrPageResult(
                                    pageIndex = pageIndex,
                                    text = visionText.text.trim(),
                                    isSuccess = true
                                )
                            )
                        }
                    }
                    .addOnFailureListener { error ->
                        runCatching { recognizer.close() }
                        if (cont.isActive) {
                            cont.resume(
                                OcrPageResult(
                                    pageIndex = pageIndex,
                                    text = "",
                                    isSuccess = false,
                                    errorMessage = error.localizedMessage ?: "OCR failed"
                                )
                            )
                        }
                    }
            } catch (t: Throwable) {
                runCatching { recognizer.close() }
                if (cont.isActive) {
                    cont.resume(
                        OcrPageResult(
                            pageIndex = pageIndex,
                            text = "",
                            isSuccess = false,
                            errorMessage = t.localizedMessage ?: "OCR processing error"
                        )
                    )
                }
            }
        }
    }
}
