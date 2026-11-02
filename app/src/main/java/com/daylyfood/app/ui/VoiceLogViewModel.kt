package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.domain.VoiceMealParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turns a spoken sentence into diary rows.
 *
 * Only foods already in the library are matched; quantities default to each food's own serving
 * size, so the number the user sees comes from data we can explain, not from parsing words.
 */
@HiltViewModel
class VoiceLogViewModel @Inject constructor(
    private val foodDao: FoodDao,
    private val diaryDao: DiaryDao,
) : ViewModel() {

    data class Item(val food: FoodEntity, val grams: Double, val label: String)

    sealed interface State {
        data object Idle : State
        data object Resolving : State
        data class Ready(val text: String, val items: List<Item>) : State
        data class Empty(val text: String) : State
        data class Saved(val count: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun resolve(text: String) = viewModelScope.launch {
        _state.value = State.Resolving
        val foods = foodDao.observeAll().first()
        val aliases = foodDao.allAliases().groupBy { it.foodId }
        val candidates = foods.map { food ->
            VoiceMealParser.Candidate(
                foodId = food.id,
                name = food.name,
                aliases = aliases[food.id]?.map { it.alias }.orEmpty(),
            )
        }
        val matches = VoiceMealParser.parse(text, candidates)
        if (matches.isEmpty()) {
            _state.value = State.Empty(text)
            return@launch
        }
        val byId = foods.associateBy { it.id }
        val items = matches.mapNotNull { match ->
            val food = byId[match.foodId] ?: return@mapNotNull null
            val serving = foodDao.servings(food.id).firstOrNull { it.isDefault }
                ?: foodDao.servings(food.id).firstOrNull()
            Item(
                food = food,
                grams = serving?.grams ?: 100.0,
                label = serving?.label ?: "100 ${food.baseUnit}",
            )
        }
        _state.value = State.Ready(text, items)
    }

    fun remove(foodId: String) {
        val current = _state.value as? State.Ready ?: return
        val rest = current.items.filterNot { it.food.id == foodId }
        _state.value = if (rest.isEmpty()) State.Empty(current.text) else current.copy(items = rest)
    }

    fun save(day: Long, meal: String) = viewModelScope.launch {
        val current = _state.value as? State.Ready ?: return@launch
        val now = System.currentTimeMillis()
        current.items.forEach { item ->
            val factor = item.grams / 100.0
            diaryDao.insert(
                DiaryEntryEntity(
                    epochDay = day,
                    meal = meal,
                    foodId = item.food.id,
                    nameSnapshot = item.food.name,
                    grams = item.grams,
                    kcal = item.food.kcal100 * factor,
                    protein = item.food.protein100 * factor,
                    fat = item.food.fat100 * factor,
                    carb = item.food.carb100 * factor,
                    createdAt = now,
                    unit = item.food.baseUnit,
                ),
            )
        }
        _state.value = State.Saved(current.items.size)
    }

    fun reset() {
        _state.value = State.Idle
    }
}
