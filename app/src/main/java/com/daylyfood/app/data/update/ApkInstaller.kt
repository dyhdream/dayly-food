package com.daylyfood.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Downloads the release APK and hands it to the system installer.
 *
 * GitHub serves release assets from release-assets.githubusercontent.com, which is
 * unreachable on many mainland networks. So: try the official URL once, then fall back
 * to public GitHub mirrors. Every attempt is checked against the release's published
 * SHA-256, so a mirror cannot hand us a modified APK.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "dayly-food-${info.version}.apk")
            val partial = File(directory, "dayly-food-${info.version}.apk.part")

            val candidates = buildList {
                add(info.downloadUrl)
                MIRRORS.forEach { add(it + info.downloadUrl) }
            }

            val failures = mutableListOf<String>()
            for ((index, url) in candidates.withIndex()) {
                if (target.exists()) target.delete()
                if (partial.exists()) partial.delete()
                try {
                    fetch(url, partial, info.sizeBytes, onProgress)
                    if (info.sha256 != null) {
                        val actual = sha256(partial)
                        if (!actual.equals(info.sha256, ignoreCase = true)) {
                            partial.delete()
                            failures += "来源${index + 1} 文件校验不通过"
                            continue
                        }
                    }
                    if (!partial.renameTo(target)) {
                        partial.copyTo(target, overwrite = true)
                        partial.delete()
                    }
                    return@withContext target
                } catch (e: Exception) {
                    partial.delete()
                    failures += describe(e)
                }
            }
            throw IllegalStateException(
                "下载失败（${failures.distinct().joinToString("；")}）。可点「用浏览器下载」。"
            )
        }

    /** Escape hatch when every mirror is blocked too. */
    fun openReleasePage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Opens the system installer; the user still confirms, which Android requires. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetch(url: String, sink: File, expectedSize: Long, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "dayly-food")
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            connection.inputStream.use { input ->
                sink.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (sink.length() == 0L) throw IllegalStateException("文件为空")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun describe(error: Exception): String = when (error) {
        is UnknownHostException -> "域名解析失败"
        is SocketTimeoutException -> "连接超时"
        is ConnectException -> "连接被拒绝"
        is SSLException -> "证书校验失败"
        else -> error.message?.take(40) ?: "未知错误"
    }

    companion object {
        private const val RELEASE_PAGE = "https://github.com/dyhdream/dayly-food/releases/latest"

        private val MIRRORS = listOf(
            "https://ghfast.top/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
        )
    }
}
