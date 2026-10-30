package com.daylyfood.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes a picked photo down to a size OCR handles well. Labels carry small print, so the
 * limit is generous compared with a photo meant as an avatar.
 */
@Singleton
class PhotoLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 2000 px on the long edge: small print on a label needs the pixels, and an over-large
     * bitmap is what made OCR drop lines in the first place.
     */
    suspend fun load(uri: Uri, maxPx: Int = 2000): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null on purpose here: it only fills `bounds`.
            val probe = open(uri) ?: error("读不到这张图片")
            probe.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("这张不是有效图片")

            val scale = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxPx)
            val options = BitmapFactory.Options().apply { inSampleSize = scale }
            val stream = open(uri) ?: error("读不到这张图片")
            val bitmap = stream.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("图片解码失败")

            val rotated = rotateByExif(uri, bitmap)
            // Models and OCR both work better on a bounded long edge.
            if (maxOf(rotated.width, rotated.height) > maxPx) {
                val factor = maxPx.toFloat() / maxOf(rotated.width, rotated.height)
                Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * factor).toInt().coerceAtLeast(1),
                    (rotated.height * factor).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                rotated
            }
        }
    }

    /** A missing or revoked uri throws rather than returning null; keep the message readable. */
    private fun open(uri: Uri): java.io.InputStream? = runCatching {
        context.contentResolver.openInputStream(uri)
    }.getOrNull()

    private fun rotateByExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // The unrotated copy is dead the moment we return; don't wait for the GC to free 20 MB.
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}

/** Where the camera writes its full-size capture before we read it back. */
@Singleton
class TempPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun newFile(): File {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        // One file reused per shot: a stale capture is worthless and we do not want them piling up.
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "capture.jpg")
    }

    /** Drops the capture as soon as the scan is over instead of waiting for the next shot. */
    fun clear() {
        File(context.cacheDir, "photos").listFiles()?.forEach { it.delete() }
    }
}
