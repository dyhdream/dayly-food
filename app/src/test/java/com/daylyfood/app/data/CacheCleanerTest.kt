package com.daylyfood.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.daylyfood.app.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Cache growth was the reported bug: a 21 MB update package per version and a capture photo per
 * shot, none of them ever deleted. These pin the retention rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CacheCleanerTest {

    private lateinit var context: Context
    private lateinit var cleaner: CacheCleaner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.deleteRecursively()
        cleaner = CacheCleaner(context)
    }

    private fun file(dir: String, name: String, bytes: Int = 1024): File {
        val folder = File(context.cacheDir, dir).apply { mkdirs() }
        return File(folder, name).apply { writeBytes(ByteArray(bytes)) }
    }

    @Test
    fun `capture photos are always removed`() {
        val photo = file("photos", "capture.jpg", 64 * 1024)

        runBlocking { cleaner.sweep() }

        assertFalse(photo.exists())
    }

    @Test
    fun `update apk of another version is removed`() {
        val old = file("updates", "dayly-food-0.8.0.apk")

        runBlocking { cleaner.sweep() }

        assertFalse(old.exists())
    }

    @Test
    fun `current version apk downloaded just now is kept so the user can still install`() {
        val current = file("updates", "dayly-food-${BuildConfig.VERSION_NAME}.apk")

        runBlocking { cleaner.sweep() }

        assertTrue(current.exists())
    }

    @Test
    fun `current version apk older than a day is removed`() {
        val current = file("updates", "dayly-food-${BuildConfig.VERSION_NAME}.apk")
        current.setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)

        runBlocking { cleaner.sweep() }

        assertFalse(current.exists())
    }

    @Test
    fun `partial download is removed even for the current version`() {
        val partial = file("updates", "dayly-food-${BuildConfig.VERSION_NAME}.apk.part")

        runBlocking { cleaner.sweep() }

        assertFalse(partial.exists())
    }

    @Test
    fun `usage counts what is still on disk`() {
        file("photos", "capture.jpg", 4096)
        file("updates", "dayly-food-0.8.0.apk", 8192)

        val bytes = runBlocking { cleaner.usageBytes() }

        assertEquals(12_288L, bytes)
    }

    @Test
    fun `sweep reports freed bytes`() {
        file("photos", "capture.jpg", 4096)
        file("updates", "dayly-food-0.8.0.apk", 8192)

        val freed = runBlocking { cleaner.sweep() }

        assertEquals(12_288L, freed)
        assertEquals(0L, runBlocking { cleaner.usageBytes() })
    }
}
