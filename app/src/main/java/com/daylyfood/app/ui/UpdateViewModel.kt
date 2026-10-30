package com.daylyfood.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.update.ApkInstaller
import com.daylyfood.app.data.update.UpdateChecker
import com.daylyfood.app.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
    @ApplicationContext context: Context,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val percent: Int, val version: String) : State
        data class Failed(val message: String, val version: String? = null) : State
    }

    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun check() = viewModelScope.launch {
        _state.value = State.Checking
        _state.value = try {
            val info = checker.check()
            if (info == null) State.UpToDate else State.Available(info)
        } catch (e: Exception) {
            State.Failed(e.message ?: "网络不可用")
        }
    }

    /** "下次再说": remember the version so the silent check stops asking about it. */
    fun postpone(version: String) {
        prefs.edit().putString(KEY_POSTPONED, version).apply()
        _state.value = State.Idle
    }

    fun downloadAndInstall(info: UpdateInfo) = viewModelScope.launch {
        _state.value = State.Downloading(0, info.version)
        try {
            val apk = installer.download(info) { percent ->
                _state.value = State.Downloading(percent, info.version)
            }
            installer.install(apk)
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "下载失败", info.version)
        }
    }

    private var lastAutoCheckAt = 0L

    /**
     * Auto check on start / resume. Postponed optional versions stay quiet, but an important
     * update is never hidden — that is the whole point of marking it important.
     */
    fun checkSilently() = viewModelScope.launch {
        if (_state.value is State.Checking || _state.value is State.Downloading) return@launch
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckAt < AUTO_CHECK_INTERVAL_MS) return@launch
        lastAutoCheckAt = now
        val info = runCatching { checker.check() }.getOrNull() ?: return@launch
        val postponed = prefs.getString(KEY_POSTPONED, null)
        _state.value = when {
            info.mandatory -> State.Available(info)
            info.version == postponed -> State.Idle
            else -> State.Available(info)
        }
    }

    fun reset() {
        _state.value = State.Idle
    }

    fun openReleasePage() = installer.openReleasePage()

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 10 * 60 * 1000L
        private const val KEY_POSTPONED = "postponedVersion"
    }
}
