package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.ai.AiClient
import com.daylyfood.app.data.ai.AiSettings
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.ProfileDao
import com.daylyfood.app.domain.Nutrition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Last seven days of eating, summarised. Pure arithmetic; AI commentary is optional. */
@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    private val diaryDao: DiaryDao,
    private val profileDao: ProfileDao,
    private val aiClient: AiClient,
    private val aiSettings: AiSettings,
) : ViewModel() {

    data class Row(val label: String, val value: String)

    data class State(
        val loading: Boolean = true,
        val daysLogged: Int = 0,
        val avgKcal: Int = 0,
        val target: Int? = null,
        val rows: List<Row> = emptyList(),
        val topFoods: List<Pair<String, Int>> = emptyList(),
        val aiText: String? = null,
        val aiLoading: Boolean = false,
        val aiError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var lastPrompt: String = ""

    fun load() = viewModelScope.launch {
        val today = LocalDate.now().toEpochDay()
        val from = today - 6
        val entries = diaryDao.between(from, today)
        val profile = profileDao.observe().first()
        val target = profile?.let {
            Nutrition.targetKcal(
                Nutrition.tdee(Nutrition.bmr(it.sex, it.age, it.heightCm, it.weightKg), it.activity),
                it.goal,
                it.sex,
            ).toInt()
        }

        if (entries.isEmpty()) {
            _state.value = State(loading = false, target = target)
            return@launch
        }

        val days = entries.groupBy { it.epochDay }
        val loggedDays = days.size
        val totalKcal = entries.sumOf { it.kcal }
        val avgKcal = (totalKcal / loggedDays).toInt()
        val protein = entries.sumOf { it.protein } / loggedDays
        val fat = entries.sumOf { it.fat } / loggedDays
        val carb = entries.sumOf { it.carb } / loggedDays
        val top = entries.groupingBy { it.nameSnapshot }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        lastPrompt = buildString {
            append("最近7天有记录的天数：$loggedDays\n")
            if (target != null) append("每日目标：$target kcal\n")
            append("平均每天摄入：$avgKcal kcal，蛋白 ${protein.toInt()}g，脂肪 ${fat.toInt()}g，碳水 ${carb.toInt()}g\n")
            append("最常吃：" + top.joinToString("、") { "${it.first}×${it.second}" })
        }

        _state.value = State(
            loading = false,
            daysLogged = loggedDays,
            avgKcal = avgKcal,
            target = target,
            rows = listOf(
                Row("平均蛋白", "${protein.toInt()} g/天"),
                Row("平均脂肪", "${fat.toInt()} g/天"),
                Row("平均碳水", "${carb.toInt()} g/天"),
            ),
            topFoods = top,
        )
    }

    /** Only runs when the user turned 「每周报告」 on; otherwise it says why, instead of silently doing nothing. */
    fun askAi() = viewModelScope.launch {
        if (!aiSettings.weeklyEnabled()) {
            _state.value = _state.value.copy(aiError = "AI 周报在「AI 设置」里是关的，打开后再试")
            return@launch
        }
        _state.value = _state.value.copy(aiLoading = true, aiError = null)
        val result = aiClient.completeText(
            system = "你是一名中文营养师。只根据给出的数据说 2-3 句可执行的话，不要编造没给的数据，不要列清单。",
            user = lastPrompt,
        )
        _state.value = result.fold(
            onSuccess = { _state.value.copy(aiLoading = false, aiText = it.trim()) },
            onFailure = { _state.value.copy(aiLoading = false, aiError = it.message ?: "AI 调用失败") },
        )
    }
}
