package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.update.ApkInstaller
import com.daylyfood.app.data.update.UpdateChecker
import com.daylyfood.app.data.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val percent: Int) : State
        data class Failed(val message: String) : State
    }

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

    fun downloadAndInstall(info: UpdateInfo) = viewModelScope.launch {
        _state.value = State.Downloading(0)
        try {
            val apk = installer.download(info) { percent -> _state.value = State.Downloading(percent) }
            installer.install(apk)
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "下载失败")
        }
    }

    private var lastAutoCheckAt = 0L

    /**
     * Re-check when the app comes back to the foreground. Throttled so switching apps
     * does not spam the API; the manual button always bypasses it.
     */
    fun checkSilently() = viewModelScope.launch {
        if (_state.value is State.Checking || _state.value is State.Downloading) return@launch
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckAt < AUTO_CHECK_INTERVAL_MS) return@launch
        lastAutoCheckAt = now
        check()
    }

    fun reset() {
        _state.value = State.Idle
    }

    fun openReleasePage() = installer.openReleasePage()

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 10 * 60 * 1000L
    }
}
