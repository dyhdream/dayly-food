package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.ai.AiClient
import com.daylyfood.app.data.ai.AiConfig
import com.daylyfood.app.data.ai.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: AiSettings,
    private val client: AiClient,
) : ViewModel() {

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data class Ok(val reply: String) : TestState
        data class Failed(val message: String) : TestState
    }

    val config: StateFlow<AiConfig> = settings.config

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    fun maskedKey(): String? = settings.maskedKey()

    fun saveConfig(url: String, model: String, key: String) {
        settings.setEndpoint(url)
        settings.setModel(model)
        if (key.isNotBlank()) settings.saveKey(key)
        _test.value = TestState.Idle
    }

    fun clearKey() {
        settings.saveKey("")
        _test.value = TestState.Idle
    }

    fun setVision(enabled: Boolean) = settings.setVisionEnabled(enabled)

    fun setSummary(enabled: Boolean) = settings.setSummaryEnabled(enabled)

    fun setWeekly(enabled: Boolean) = settings.setWeeklyEnabled(enabled)

    fun setDailyLimit(limit: Int) = settings.setDailyLimit(limit)

    fun setPrices(perMillionIn: Double, perMillionOut: Double) =
        settings.setPrices(perMillionIn, perMillionOut)

    /** Saves first so a test never runs against stale fields. */
    fun saveAndTest(url: String, model: String, key: String) {
        saveConfig(url, model, key)
        testConnection()
    }

    fun testConnection() = viewModelScope.launch {
        _test.value = TestState.Testing
        _test.value = client.testConnection().fold(
            onSuccess = { TestState.Ok(it.trim().take(80)) },
            onFailure = { TestState.Failed(it.message ?: "测试失败") },
        )
    }
}
