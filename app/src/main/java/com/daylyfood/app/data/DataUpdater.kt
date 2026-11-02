package com.daylyfood.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.daylyfood.app.BuildConfig
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.SeedMetaEntity
import com.daylyfood.app.data.update.UpdateChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silent food-library updates, decoupled from the APK.
 *
 * The library is data, not code: a JSON snapshot is fetched from a static host, validated, and
 * written in one transaction. Rows are keyed by id and written with REPLACE, so re-applying the
 * same package is harmless and a corrected value reaches existing installs.
 */
@Singleton
class DataUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: Int) : State
        data class Applied(val version: Int, val foods: Int) : State
        data class Failed(val message: String) : State
    }

    private val prefs = context.getSharedPreferences("data_update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _appliedVersion = MutableStateFlow(0)
    val appliedVersion: StateFlow<Int> = _appliedVersion.asStateFlow()

    suspend fun refreshVersion() {
        _appliedVersion.value = withContext(Dispatchers.IO) {
            database.seedMetaDao().get()?.dataVersion ?: 0
        }
    }

    /** Called on app start: at most once a day, and a failure never surfaces. */
    suspend fun maybeAutoCheck() {
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        runCatching { check() }
    }

    suspend fun checkNow() {
        runCatching { check() }
    }

    private suspend fun check() {
        _state.value = State.Checking
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

        val current = withContext(Dispatchers.IO) {
            database.seedMetaDao().get()?.dataVersion ?: 0
        }
        _appliedVersion.value = current

        _state.value = try {
            val index = fetchIndex()
            when {
                // A package built for a newer app could carry fields this build cannot read.
                UpdateChecker.isNewer(index.minAppVersion, BuildConfig.VERSION_NAME) ->
                    State.UpToDate(current)
                index.dataVersion <= current -> State.UpToDate(current)
                else -> {
                    val bytes = fetchBytes(index.url)
                    val actual = sha256(bytes)
                    if (!actual.equals(index.sha256, ignoreCase = true)) {
                        State.Failed("数据包校验不通过，已忽略")
                    } else {
                        val pkg = DataPackage.parse(bytes.decodeToString()).getOrThrow()
                        require(pkg.dataVersion == index.dataVersion) { "数据包版本与清单不一致" }
                        apply(pkg)
                        State.Applied(pkg.dataVersion, pkg.foods.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "数据更新失败", e)
            State.Failed(describe(e))
        }
    }

    private suspend fun apply(pkg: DataPackage) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.foodDao()
            dao.insertFoods(pkg.foods)
            dao.insertServings(pkg.servings)
            dao.insertAliases(pkg.aliases)
            database.seedMetaDao().save(
                SeedMetaEntity(dataVersion = pkg.dataVersion, appliedAt = System.currentTimeMillis()),
            )
        }
        _appliedVersion.value = pkg.dataVersion
    }

    private suspend fun fetchIndex(): Index = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in HOSTS) {
            try {
                val json = JSONObject(fetchBytes(base + "index.json").decodeToString())
                return@withContext Index(
                    dataVersion = json.getInt("dataVersion"),
                    file = json.getString("file"),
                    sha256 = json.getString("sha256"),
                    minAppVersion = json.optString("minAppVersion").ifBlank { "0.0.0" },
                    base = base,
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("数据源不可用")
    }

    private suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "dayly-food/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun describe(error: Exception): String = when (error) {
        is java.net.UnknownHostException -> "网络不可用"
        is java.net.SocketTimeoutException -> "连接超时"
        else -> error.message?.take(60)?.ifBlank { null } ?: error.javaClass.simpleName
    }

    private data class Index(
        val dataVersion: Int,
        val file: String,
        val sha256: String,
        val minAppVersion: String,
        val base: String,
    ) {
        val url: String get() = base + file
    }

    private companion object {
        const val TAG = "DataUpdater"
        const val KEY_LAST_CHECK = "lastCheckAt"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** Static hosting only — no backend. jsDelivr first because it is reachable in China. */
        val HOSTS = listOf(
            "https://cdn.jsdelivr.net/gh/dyhdream/dayly-food@main/data/",
            "https://raw.githubusercontent.com/dyhdream/dayly-food/main/data/",
            "https://ghfast.top/https://raw.githubusercontent.com/dyhdream/dayly-food/main/data/",
        )
    }
}
