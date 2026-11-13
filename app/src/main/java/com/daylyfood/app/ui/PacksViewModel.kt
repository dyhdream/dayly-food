package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.DataUpdater
import com.daylyfood.app.data.PackRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Regional packs: browse what exists, mount what you need, remove what you don't. */
@HiltViewModel
class PacksViewModel @Inject constructor(
    private val updater: DataUpdater,
) : ViewModel() {

    data class Row(
        val ref: PackRef,
        val installedVersion: Int,
        val working: Boolean = false,
        val error: String? = null,
    )

    data class State(
        val loading: Boolean = true,
        val rows: List<Row> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        updater.packCatalog().fold(
            onSuccess = { refs ->
                _state.value = State(
                    loading = false,
                    rows = refs.map { Row(it, updater.installedPackVersion(it.id)) },
                )
            },
            onFailure = {
                _state.value = State(loading = false, error = "菜品包清单拉不到：${it.message ?: "网络不可用"}")
            },
        )
    }

    fun install(id: String) = viewModelScope.launch {
        mark(id) { it.copy(working = true, error = null) }
        updater.installPack(id).fold(
            onSuccess = { count ->
                _state.value = _state.value.copy(
                    rows = _state.value.rows.map {
                        if (it.ref.id == id) it.copy(working = false, installedVersion = it.ref.version) else it
                    },
                )
                lastMessage = "已装上 $count 道菜"
            },
            onFailure = { e ->
                mark(id) { it.copy(working = false, error = e.message ?: "下载失败") }
            },
        )
    }

    fun remove(id: String) = viewModelScope.launch {
        mark(id) { it.copy(working = true, error = null) }
        runCatching { updater.removePack(id) }
            .onSuccess {
                mark(id) { it.copy(working = false, installedVersion = 0) }
                lastMessage = "已移除这个包"
            }
            .onFailure { e -> mark(id) { it.copy(working = false, error = e.message ?: "移除失败") } }
    }

    /** One-line result the screen shows once, then forgets. */
    var lastMessage: String? = null
        private set

    private fun mark(id: String, change: (Row) -> Row) {
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { if (it.ref.id == id) change(it) else it },
        )
    }
}
