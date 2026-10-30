package com.daylyfood.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything this app drops into cacheDir and never needs again.
 *
 * Two things used to grow without bound: the downloaded release APK (21 MB per version) and
 * the camera capture (only deleted when the next shot started). Both live here now, and the
 * user gets a number plus a button instead of an invisible pile.
 */
@Singleton
class CacheCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val photosDir: File get() = File(context.cacheDir, "photos")
    private val updatesDir: File get() = File(context.cacheDir, "updates")

    /**
     * Deletes capture photos, `.part` leftovers, and update APKs that are either stale (>24h)
     * or from another version. A just-downloaded APK for the running version is kept, so the
     * user can still come back and hit install.
     */
    suspend fun sweep(): Long = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - KEEP_UPDATE_MS
        var freed = 0L

        photosDir.listFiles()?.forEach { file ->
            freed += sizeOf(file)
            file.deleteRecursively()
        }

        updatesDir.listFiles()?.forEach { file ->
            // A half-downloaded package is never resumable here: always drop it.
            val partial = file.name.endsWith(".part")
            val stale = file.lastModified() < cutoff
            val wrongVersion = !file.name.contains(CURRENT_VERSION)
            if (partial || stale || wrongVersion) {
                freed += sizeOf(file)
                file.deleteRecursively()
            }
        }
        freed
    }

    /** Bytes currently held by photo captures and update packages. */
    suspend fun usageBytes(): Long = withContext(Dispatchers.IO) {
        listOf(photosDir, updatesDir).sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        else file.length()

    private companion object {
        const val KEEP_UPDATE_MS = 24 * 60 * 60 * 1000L
        val CURRENT_VERSION = com.daylyfood.app.BuildConfig.VERSION_NAME
    }
}
