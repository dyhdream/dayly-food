package com.daylyfood.app.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user types the endpoint and model name themselves: provider model ids change faster
 * than any preset list we could ship, and a wrong guess would only get in their way.
 */
data class AiConfig(
    val endpointUrl: String = "",
    val modelName: String = "",
    val visionEnabled: Boolean = false,
    /** Daily review agent: off by default, it costs money and not everyone wants it. */
    val summaryEnabled: Boolean = false,
    /** Weekly report: off by default, same reason with more force. */
    val weeklyEnabled: Boolean = false,
    val dailyCallLimit: Int = 20,
    val hasKey: Boolean = false,
    val usedToday: Int = 0,
) {
    val isConfigured: Boolean
        get() = endpointUrl.isNotBlank() && modelName.isNotBlank() && hasKey

    val remainingToday: Int get() = (dailyCallLimit - usedToday).coerceAtLeast(0)

    val canCall: Boolean get() = isConfigured && remainingToday > 0
}

/**
 * Pure guard for the daily call budget; the UI shows it and the client enforces it.
 * Kept free of Android types so it can be unit tested.
 */
object CallBudget {
    fun canCall(used: Int, limit: Int): Boolean = limit > 0 && used < limit

    fun remaining(used: Int, limit: Int): Int = (limit - used).coerceAtLeast(0)
}

@Singleton
class AiSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val keyStore: AiKeyStore,
) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    private fun readConfig(): AiConfig {
        val today = todayEpochDay()
        val used = if (prefs.getLong(KEY_USAGE_DAY, 0L) == today) prefs.getInt(KEY_USAGE_COUNT, 0) else 0
        return AiConfig(
            endpointUrl = prefs.getString(KEY_URL, "").orEmpty(),
            modelName = prefs.getString(KEY_MODEL, "").orEmpty(),
            visionEnabled = prefs.getBoolean(KEY_VISION, false),
            summaryEnabled = prefs.getBoolean(KEY_SUMMARY, false),
            weeklyEnabled = prefs.getBoolean(KEY_WEEKLY, false),
            dailyCallLimit = prefs.getInt(KEY_LIMIT, 20),
            hasKey = keyStore.has(KEY_SLOT),
            usedToday = used,
        )
    }

    fun refresh() {
        _config.value = readConfig()
    }

    fun setEndpoint(url: String) {
        prefs.edit().putString(KEY_URL, url.trim()).apply()
        refresh()
    }

    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
        refresh()
    }

    fun saveKey(apiKey: String) {
        if (apiKey.isBlank()) keyStore.clear(KEY_SLOT) else keyStore.save(KEY_SLOT, apiKey)
        refresh()
    }

    fun maskedKey(): String? = keyStore.masked(KEY_SLOT)

    fun setVisionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VISION, enabled).apply()
        refresh()
    }

    fun setSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUMMARY, enabled).apply()
        refresh()
    }

    fun setWeeklyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY, enabled).apply()
        refresh()
    }

    fun setDailyLimit(limit: Int) {
        prefs.edit().putInt(KEY_LIMIT, limit.coerceIn(1, 200)).apply()
        refresh()
    }

    fun readKey(): String? = keyStore.read(KEY_SLOT)

    /** Records one call against today's budget. */
    fun recordCall() {
        val today = todayEpochDay()
        val used = if (prefs.getLong(KEY_USAGE_DAY, 0L) == today) prefs.getInt(KEY_USAGE_COUNT, 0) else 0
        prefs.edit()
            .putLong(KEY_USAGE_DAY, today)
            .putInt(KEY_USAGE_COUNT, used + 1)
            .apply()
        refresh()
    }

    private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private companion object {
        const val KEY_SLOT = "custom"
        const val KEY_URL = "endpointUrl"
        const val KEY_MODEL = "modelName"
        const val KEY_VISION = "vision"
        const val KEY_SUMMARY = "summary"
        const val KEY_WEEKLY = "weekly"
        const val KEY_LIMIT = "dailyLimit"
        const val KEY_USAGE_DAY = "usageDay"
        const val KEY_USAGE_COUNT = "usageCount"
    }
}
