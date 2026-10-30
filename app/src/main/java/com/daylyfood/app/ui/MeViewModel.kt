package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import com.daylyfood.app.data.ai.AiConfig
import com.daylyfood.app.data.ai.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MeViewModel @Inject constructor(
    aiSettings: AiSettings,
) : ViewModel() {
    val aiConfig: StateFlow<AiConfig> = aiSettings.config
}
