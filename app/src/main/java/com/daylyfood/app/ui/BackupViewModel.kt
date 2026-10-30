package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.BuildConfig
import com.daylyfood.app.data.Backup
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.ProfileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Export/restore of everything the user owns. File IO stays in the screen via SAF. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val foodDao: FoodDao,
    private val diaryDao: DiaryDao,
    private val profileDao: ProfileDao,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data class Done(val message: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun suggestedFileName(): String =
        "dayly-food-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".json"

    /** Builds the JSON and hands it to [write]; [write] returns false if the file could not be saved. */
    fun export(write: (String) -> Boolean) = viewModelScope.launch {
        _state.value = State.Working
        try {
            val payload = Backup.Payload(
                profile = profileDao.observe().first(),
                customFoods = foodDao.customFoods(),
                servings = foodDao.customServings(),
                diary = diaryDao.all(),
            )
            val json = Backup.encode(
                payload = payload,
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
            if (!write(json)) {
                _state.value = State.Failed("写入文件失败")
                return@launch
            }
            _state.value = State.Done(
                "已导出 ${payload.diary.size} 条记录、${payload.customFoods.size} 个自建食物"
            )
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "导出失败")
        }
    }

    /** Replaces local data with the backup; the caller must confirm first. */
    fun restore(json: String) = viewModelScope.launch {
        _state.value = State.Working
        try {
            val payload = Backup.decode(json)
            diaryDao.clearAll()
            foodDao.deleteCustomFoods()
            foodDao.deleteOrphanServings()
            payload.profile?.let { profileDao.save(it) }
            if (payload.customFoods.isNotEmpty()) foodDao.insertFoods(payload.customFoods)
            if (payload.servings.isNotEmpty()) foodDao.insertServings(payload.servings)
            if (payload.diary.isNotEmpty()) diaryDao.insertAll(payload.diary)
            _state.value = State.Done(
                "已恢复 ${payload.diary.size} 条记录、${payload.customFoods.size} 个自建食物"
            )
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "恢复失败，文件可能不是有效备份")
        }
    }

    fun reset() {
        _state.value = State.Idle
    }
}
