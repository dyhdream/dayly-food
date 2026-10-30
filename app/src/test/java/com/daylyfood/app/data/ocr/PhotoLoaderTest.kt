package com.daylyfood.app.data.ocr

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

/**
 * Regression for the bug where every photo reported "读不到这张图片": the bounds probe decodes
 * with inJustDecodeBounds=true, which legitimately returns null, and the null was treated as a
 * read failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoLoaderTest {

    // 1x1 JPEG
    private val onePixelJpeg =
        "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4n" +
            "ICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/E" +
            "ABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=="

    @Test
    fun `decodes a real jpeg instead of failing the bounds probe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "shot.jpg").apply {
            writeBytes(Base64.getDecoder().decode(onePixelJpeg))
        }
        val loader = PhotoLoader(context)

        val result = runBlocking { loader.load(Uri.fromFile(file)) }

        assertTrue("expected success, got ${result.exceptionOrNull()?.message}", result.isSuccess)
    }

    @Test
    fun `missing file reports a readable error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = PhotoLoader(context)

        val result = runBlocking { loader.load(Uri.fromFile(File(context.cacheDir, "nope.jpg"))) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("读不到") == true)
    }
}
