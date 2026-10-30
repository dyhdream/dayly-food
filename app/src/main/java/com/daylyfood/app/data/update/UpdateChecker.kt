package com.daylyfood.app.data.update

import com.daylyfood.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** "sha256:..." as published by GitHub; used to validate mirror downloads. */
    val sha256: String?,
)

/**
 * Update check against GitHub Releases, which is our update server: the app has no backend,
 * and a public repo's latest-release API needs no credentials.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    /** null = already newest. Throws on network/parse failure so the caller can tell the two apart. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            // GitHub rejects requests without a User-Agent.
            setRequestProperty("User-Agent", "dayly-food/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode != 200) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val latest = json.optString("tag_name").trim().trimStart('v', 'V')
            val asset = json.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            } ?: return@withContext null
            val url = asset.optString("browser_download_url")
            if (url.isBlank() || !isNewer(latest, BuildConfig.VERSION_NAME)) return@withContext null
            UpdateInfo(
                version = latest,
                notes = json.optString("body").ifBlank { "新版本已发布" },
                downloadUrl = url,
                sizeBytes = asset.optLong("size"),
                sha256 = asset.optString("digest")
                    .removePrefix("sha256:")
                    .takeIf { it.length == 64 },
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API = "https://api.github.com/repos/dyhdream/dayly-food/releases/latest"

        fun isNewer(candidate: String, current: String): Boolean {
            val a = parts(candidate)
            val b = parts(current)
            for (index in 0 until maxOf(a.size, b.size)) {
                val left = a.getOrElse(index) { 0 }
                val right = b.getOrElse(index) { 0 }
                if (left != right) return left > right
            }
            return false
        }

        private fun parts(version: String): List<Int> =
            version.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
    }
}
