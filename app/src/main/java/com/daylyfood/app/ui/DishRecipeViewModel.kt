package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.room.withTransaction
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.ai.AiClient
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity
import com.daylyfood.app.domain.CookingMethods
import com.daylyfood.app.domain.DishComposer
import com.daylyfood.app.domain.RecipeDraft
import com.daylyfood.app.domain.RecipeDraftParser
import com.daylyfood.app.domain.RecipePrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Builds a dish the library does not have yet, from ingredients it does — then stores it.
 *
 * This is how the app handles the long tail without shipping a dish catalogue: the model proposes
 * a composition, the numbers come from our own ingredient table, and the result becomes a normal
 * row in the local library so recording goes through the same path as everything else.
 */
@HiltViewModel
class DishRecipeViewModel @Inject constructor(
    private val foodDao: FoodDao,
    private val aiClient: AiClient,
    private val database: AppDatabase,
) : ViewModel() {

    data class Preview(
        val dish: FoodEntity,
        val totalGrams: Double,
        val recipeText: String,
        /** Ingredients the model wanted but we do not have; shown so nothing vanishes silently. */
        val missing: List<String>,
        /** How many real recipe pages grounded this composition; 0 means it was recalled. */
        val sources: Int,
    )

    sealed interface State {
        data object Idle : State
        data class Working(val dish: String) : State
        data class Ready(val dish: String, val preview: Preview) : State
        data class Failed(val dish: String, val message: String) : State
        data class Saved(val name: String, val recorded: Boolean) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var pending: Pair<RecipeDraft, Double>? = null

    fun create(dishName: String) = viewModelScope.launch {
        val name = dishName.trim()
        if (name.isEmpty()) return@launch
        _state.value = State.Working(name)

        val ingredients = foodDao.observeAll().first().filter { it.kind == "INGREDIENT" }
        if (ingredients.isEmpty()) {
            _state.value = State.Failed(name, "食材库是空的")
            return@launch
        }
        val catalog = ingredients.map { RecipeDraftParser.CatalogItem(it.id, it.name) }
        val byId = ingredients.associateBy { it.id }

        // Ground first, generate second: a real recipe page turns "recall" into "extract".
        // Search is opt-in and failures are silent — the whitelist below is what keeps it honest.
        val references = aiClient.searchWeb("$name 做法 食材 用量")
            .getOrDefault(emptyList())
            .map { "${it.title}(${it.url})：${it.text.take(1200)}" }

        val reply = aiClient.completeText(
            system = RecipePrompt.system(),
            user = RecipePrompt.user(name, catalog, references),
        ).getOrElse {
            _state.value = State.Failed(name, it.message ?: "AI 不可用")
            return@launch
        }

        val draft = RecipeDraftParser.parse(reply, catalog, CookingMethods.ALL.map { it.id }.toSet())
            .getOrElse {
                _state.value = State.Failed(name, it.message ?: "配方不可用")
                return@launch
            }

        val lines = draft.lines.mapNotNull { line ->
            val food = byId[line.ingredientId] ?: return@mapNotNull null
            food to line.grams
        }
        if (lines.size != draft.lines.size) {
            _state.value = State.Failed(name, "配方里的食材对不上库")
            return@launch
        }

        val composerLines = lines.map { (food, grams) ->
            DishComposer.Ingredient(
                name = food.name,
                grams = grams,
                kcal100 = food.kcal100,
                protein100 = food.protein100,
                fat100 = food.fat100,
                carb100 = food.carb100,
            )
        }
        val per100 = DishComposer.per100(DishComposer.compose(composerLines, draft.oilGrams))
        val total = per100.let { composerLines.sumOf { line -> line.grams } + draft.oilGrams }

        val nextId = "F" + ((foodDao.maxFoodNumber() ?: 0) + 1).toString().padStart(3, '0')
        val dish = FoodEntity(
            id = nextId,
            name = draft.name,
            kind = "DISH",
            basis = "COOKED",
            category = "家常菜",
            brand = null,
            baseUnit = "g",
            kcal100 = per100.kcal,
            protein100 = per100.protein,
            fat100 = per100.fat,
            carb100 = per100.carb,
            fiber100 = null,
            sodium100 = null,
            source = "ai_recipe",
            sourceUrl = null,
            confidence = "low",
            verified = false,
            notes = "AI 配方：" + DishComposer.describe(composerLines, draft.oilGrams),
        )
        pending = draft to total
        _state.value = State.Ready(
            dish = name,
            preview = Preview(dish, total, dish.notes.orEmpty(), draft.missing, references.size),
        )
    }

    /** Inserts the dish (and the diary row when the user wants it logged right away). */
    fun commit(day: Long, meal: String, record: Boolean) = viewModelScope.launch {
        val ready = _state.value as? State.Ready ?: return@launch
        val (_, total) = pending ?: return@launch
        val dish = ready.preview.dish
        val servingId = "S" + ((foodDao.maxServingNumber() ?: 0) + 1).toString().padStart(3, '0')
        val grams = total.roundToInt().toDouble()

        database.withTransaction {
            foodDao.insertFoods(listOf(dish))
            foodDao.insertServings(
                listOf(
                    ServingEntity(
                        id = servingId,
                        foodId = dish.id,
                        label = "一份",
                        grams = grams,
                        isDefault = true,
                        sort = 1,
                    ),
                ),
            )
            if (record) {
                val factor = grams / 100.0
                database.diaryDao().insert(
                    DiaryEntryEntity(
                        epochDay = day,
                        meal = meal,
                        foodId = dish.id,
                        nameSnapshot = dish.name,
                        grams = grams,
                        kcal = dish.kcal100 * factor,
                        protein = dish.protein100 * factor,
                        fat = dish.fat100 * factor,
                        carb = dish.carb100 * factor,
                        createdAt = System.currentTimeMillis(),
                        unit = "g",
                    ),
                )
            }
        }
        pending = null
        _state.value = State.Saved(dish.name, record)
    }

    fun dismiss() {
        _state.value = State.Idle
        pending = null
    }
}
