package com.daylyfood.app.data.ocr

import android.graphics.Bitmap
import com.daylyfood.app.domain.NutritionLabelParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads a 营养成分表 from a photo entirely on device: no API key, no cost, works offline.
 * The recognizer is created and closed per call because labels are read rarely; keeping a
 * client alive for the process would just hold native buffers.
 */
@Singleton
class LabelReader @Inject constructor() {

    suspend fun read(bitmap: Bitmap): Result<NutritionLabelParser.Label> =
        withContext(Dispatchers.Default) {
            runCatching {
                val text = recognize(bitmap)
                val label = NutritionLabelParser.parse(text).per100()
                if (label.isEmpty) {
                    error("没认出营养成分表。试着让表格占满画面、对正、避开反光。")
                }
                label
            }
        }

    /** Raw OCR text, kept for the "识别得不对" fallback where we show what was read. */
    suspend fun readText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching { recognize(bitmap) }
    }

    private suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
        } finally {
            recognizer.close()
        }
    }
}
