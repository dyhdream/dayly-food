package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.CacheCleaner
import com.daylyfood.app.data.DataUpdater
import com.daylyfood.app.data.ai.AiConfig
import com.daylyfood.app.data.ai.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeViewModel @Inject constructor(
    aiSettings: AiSettings,
    private val cacheCleaner: CacheCleaner,
    private val dataUpdater: DataUpdater,
) : ViewModel() {

    val aiConfig: StateFlow<AiConfig> = aiSettings.config

    private val _cacheBytes = MutableStateFlow<Long?>(null)
    val cacheBytes: StateFlow<Long?> = _cacheBytes.asStateFlow()

    val dataState: StateFlow<DataUpdater.State> = dataUpdater.state
    val dataVersion: StateFlow<Int> = dataUpdater.appliedVersion

    init {
        refreshCache()
        viewModelScope.launch { dataUpdater.refreshVersion() }
    }

    fun checkData() = viewModelScope.launch { dataUpdater.checkNow() }

    fun refreshCache() = viewModelScope.launch {
        _cacheBytes.value = cacheCleaner.usageBytes()
    }

    fun clearCache() = viewModelScope.launch {
        cacheCleaner.sweep()
        _cacheBytes.value = cacheCleaner.usageBytes()
    }
}
