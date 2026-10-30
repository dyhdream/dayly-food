package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity
import com.daylyfood.app.domain.CookingMethods
import com.daylyfood.app.domain.DishComposer
import com.daylyfood.app.domain.guessMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class DishViewModel @Inject constructor(
    private val foodDao: FoodDao,
    private val diaryDao: DiaryDao,
) : ViewModel() {

    val myDishes: StateFlow<List<FoodEntity>> = foodDao.observeCustom()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val builtInDishes: StateFlow<List<FoodEntity>> = foodDao.observeDishes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Everything searchable: dishes, ingredients, packaged goods and self-made entries. */
    val allFoods: StateFlow<List<FoodEntity>> = foodDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class DraftItem(
        val foodId: String,
        val name: String,
        val grams: Double,
        val kcal100: Double,
        val protein100: Double,
        val fat100: Double,
        val carb100: Double,
    )

    data class ComposeState(
        val name: String = "",
        val methodId: String = "C04",
        val oilGrams: Double = 0.0,
        val oilTouched: Boolean = false,
        val items: List<DraftItem> = emptyList(),
        val query: String = "",
        val results: List<FoodEntity> = emptyList(),
    ) {
        val rawGrams: Double get() = items.sumOf { it.grams }

        val totals: DishComposer.Totals
            get() = DishComposer.compose(
                items.map {
                    DishComposer.Ingredient(it.name, it.grams, it.kcal100, it.protein100, it.fat100, it.carb100)
                },
                oilGrams,
            )
    }

    private val _compose = MutableStateFlow(ComposeState())
    val compose: StateFlow<ComposeState> = _compose.asStateFlow()

    fun startCompose() {
        _compose.value = ComposeState()
    }

    fun cancelCompose() {
        _compose.value = ComposeState()
    }

    fun setName(value: String) {
        _compose.value = _compose.value.copy(name = value)
    }

    fun setMethod(methodId: String) {
        val state = _compose.value
        val oil = if (state.oilTouched) state.oilGrams
        else CookingMethods.defaultOilGrams(methodId, state.rawGrams)
        _compose.value = state.copy(methodId = methodId, oilGrams = oil)
    }

    fun setOil(value: String) {
        val grams = value.toDoubleOrNull() ?: 0.0
        _compose.value = _compose.value.copy(oilGrams = grams.coerceAtLeast(0.0), oilTouched = true)
    }

    fun setQuery(value: String) {
        _compose.value = _compose.value.copy(query = value)
        viewModelScope.launch {
            val results = if (value.isBlank()) emptyList() else foodDao.search(value.trim()).take(12)
            _compose.value = _compose.value.copy(results = results)
        }
    }

    fun addIngredient(food: FoodEntity) {
        val state = _compose.value
        if (state.items.any { it.foodId == food.id }) return
        val item = DraftItem(food.id, food.name, 100.0, food.kcal100, food.protein100, food.fat100, food.carb100)
        val items = state.items + item
        val oil = if (state.oilTouched) state.oilGrams
        else CookingMethods.defaultOilGrams(state.methodId, items.sumOf { it.grams })
        _compose.value = state.copy(items = items, oilGrams = oil, query = "", results = emptyList())
    }

    fun setGrams(foodId: String, value: String) {
        val grams = value.toDoubleOrNull() ?: return
        val state = _compose.value
        val items = state.items.map { if (it.foodId == foodId) it.copy(grams = grams.coerceAtLeast(0.0)) else it }
        val oil = if (state.oilTouched) state.oilGrams
        else CookingMethods.defaultOilGrams(state.methodId, items.sumOf { it.grams })
        _compose.value = state.copy(items = items, oilGrams = oil)
    }

    fun removeIngredient(foodId: String) {
        val state = _compose.value
        val items = state.items.filterNot { it.foodId == foodId }
        val oil = if (state.oilTouched) state.oilGrams
        else CookingMethods.defaultOilGrams(state.methodId, items.sumOf { it.grams })
        _compose.value = state.copy(items = items, oilGrams = oil)
    }

    /** Saves the composed dish as a food the user can find and reuse, then logs it. */
    fun saveDish(meal: String, epochDay: Long, onDone: () -> Unit) = viewModelScope.launch {
        val state = _compose.value
        if (state.items.isEmpty()) return@launch
        val totals = state.totals
        val per100 = DishComposer.per100(totals)
        val stamp = System.currentTimeMillis()
        val foodId = "D${stamp.toString().takeLast(11)}"
        val method = CookingMethods.byId(state.methodId)
        val name = state.name.trim().ifBlank { "${state.items.joinToString("") { it.name }}" }

        foodDao.insertFoods(
            listOf(
                FoodEntity(
                    id = foodId,
                    name = name,
                    kind = "CUSTOM",
                    basis = "COOKED",
                    category = "组合菜品",
                    brand = null,
                    baseUnit = "g",
                    kcal100 = per100.kcal,
                    protein100 = per100.protein,
                    fat100 = per100.fat,
                    carb100 = per100.carb,
                    fiber100 = null,
                    sodium100 = null,
                    source = "local",
                    sourceUrl = null,
                    confidence = "low",
                    verified = false,
                    notes = "${method.label}：" + DishComposer.describe(
                        state.items.map {
                            DishComposer.Ingredient(it.name, it.grams, it.kcal100, it.protein100, it.fat100, it.carb100)
                        },
                        state.oilGrams,
                    ),
                )
            )
        )
        foodDao.insertServings(
            listOf(
                ServingEntity(
                    id = "S$foodId",
                    foodId = foodId,
                    label = "整盘",
                    grams = totals.grams,
                    isDefault = true,
                    sort = 1,
                )
            )
        )
        diaryDao.insert(
            DiaryEntryEntity(
                epochDay = epochDay,
                meal = meal,
                foodId = foodId,
                nameSnapshot = name,
                grams = totals.grams,
                kcal = totals.kcal,
                protein = totals.protein,
                fat = totals.fat,
                carb = totals.carb,
                createdAt = stamp,
                    unit = "g",
            )
        )
        _compose.value = ComposeState()
        onDone()
    }

    /** Logs an existing dish (built-in or mine) into a meal using its default serving. */
    fun logDish(foodId: String, meal: String, onDone: () -> Unit) = viewModelScope.launch {
        val food = foodDao.byId(foodId) ?: return@launch
        val grams = foodDao.servings(foodId).firstOrNull { it.isDefault }?.grams
            ?: foodDao.servings(foodId).firstOrNull()?.grams
            ?: 100.0
        val factor = grams / 100.0
        diaryDao.insert(
            DiaryEntryEntity(
                epochDay = LocalDate.now().toEpochDay(),
                meal = meal,
                foodId = food.id,
                nameSnapshot = food.name,
                grams = grams,
                kcal = food.kcal100 * factor,
                protein = food.protein100 * factor,
                fat = food.fat100 * factor,
                carb = food.carb100 * factor,
                createdAt = System.currentTimeMillis(),
                    unit = food.baseUnit,
            )
        )
        onDone()
    }

    fun suggestedMeal(): String = guessMeal(LocalTime.now().hour)

    /** Rounded kcal for display, kept here so the screen stays declarative. */
    fun kcalOf(totals: DishComposer.Totals): Int = totals.kcal.roundToInt()
}
