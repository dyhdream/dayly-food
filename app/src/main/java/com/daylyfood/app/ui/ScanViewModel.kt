package com.daylyfood.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.ai.AiSettings
import com.daylyfood.app.data.ai.FoodRecognizer
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity
import com.daylyfood.app.data.ocr.LabelReader
import com.daylyfood.app.data.ocr.PhotoLoader
import com.daylyfood.app.domain.FoodReplyParser
import com.daylyfood.app.domain.LabelFoodFactory
import com.daylyfood.app.domain.NutritionLabelParser
import com.daylyfood.app.domain.RecognizedFood
import com.daylyfood.app.domain.RecognizedFoodResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val labelReader: LabelReader,
    private val photoLoader: PhotoLoader,
    private val foodRecognizer: FoodRecognizer,
    private val aiSettings: AiSettings,
    private val foodDao: FoodDao,
    private val diaryDao: DiaryDao,
) : ViewModel() {

    sealed interface State {
        data object Reading : State
        data class LabelReady(val label: NutritionLabelParser.Label) : State
        data class DishesReady(val items: List<RecognizedFood>) : State
        data class Failed(val message: String, val offerAiSetup: Boolean = false) : State
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /**
     * Label first, model second. A nutrition label is printed text that on-device OCR reads
     * for free, so we never spend a model call on one.
     */
    fun start(uri: Uri) = viewModelScope.launch {
        _state.value = State.Reading
        val bitmap = photoLoader.load(uri).getOrElse {
            _state.value = State.Failed(it.message ?: "图片读取失败")
            return@launch
        }

        val label = labelReader.read(bitmap).getOrNull()
        if (label != null) {
            _state.value = State.LabelReady(label)
            return@launch
        }

        val config = aiSettings.config.value
        if (!config.visionEnabled) {
            // Only the dish path needs AI; a label was already handled above for free.
            _state.value = State.Failed(
                "这张里没有营养成分表。\n想拍照认菜，需要打开「识图记餐」并填好模型接口。",
                offerAiSetup = true,
            )
            return@launch
        }
        if (!config.isConfigured) {
            _state.value = State.Failed(
                "这张里没有营养成分表。\n拍照认菜要在「AI 设置」里填接口地址、模型名和 Key。",
                offerAiSetup = true,
            )
            return@launch
        }

        val outcome = foodRecognizer.recognize(bitmap.toJpegBase64()).getOrElse { error ->
            _state.value = State.Failed(error.message ?: "识别失败")
            return@launch
        }

        when (outcome) {
            is FoodReplyParser.Outcome.NotFood ->
                _state.value = State.Failed("${outcome.reason}。\n换个角度重拍，或手动添加。")

            is FoodReplyParser.Outcome.Dishes -> {
                // Resolve on the coroutine, then hand a plain map to the pure resolver.
                val library = outcome.items.associate { it.name to lookup(it.name) }
                val resolved = RecognizedFoodResolver.resolve(outcome.items) { library[it] }
                _state.value = if (resolved.isEmpty()) {
                    State.Failed("认出了食物，但没拿到可用数值。\n手动添加一次，它就会进库里。")
                } else {
                    State.DishesReady(resolved)
                }
            }
        }
    }

    /** Library match wins over the model's guess; exact name first, then containment. */
    private suspend fun lookup(name: String): FoodEntity? {
        val candidates = foodDao.search(name)
        return candidates.firstOrNull { it.name == name }
            ?: candidates.firstOrNull { name.contains(it.name) || it.name.contains(name) }
    }

    fun reset() {
        _state.value = null
    }

    fun saveLabel(
        name: String,
        label: NutritionLabelParser.Label,
        grams: Double,
        meal: String,
        day: Long,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        val stamp = System.currentTimeMillis()
        val foodId = LabelFoodFactory.newId(stamp)
        val food = LabelFoodFactory.toFood(foodId, name, label)
        foodDao.insertFoods(listOf(food))
        foodDao.insertServings(listOf(LabelFoodFactory.defaultServing(foodId, label)))
        diaryDao.insert(food.toEntry(day, meal, grams, stamp))
        _state.value = null
        onDone()
    }

    fun saveDishes(
        items: List<RecognizedFood>,
        meal: String,
        day: Long,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        val stamp = System.currentTimeMillis()
        items.forEachIndexed { index, item ->
            val foodId = item.foodId ?: createFood(item, stamp + index)
            diaryDao.insert(
                DiaryEntryEntity(
                    epochDay = day,
                    meal = meal,
                    foodId = foodId,
                    nameSnapshot = item.name,
                    grams = item.grams,
                    kcal = item.kcal,
                    protein = item.protein100 * item.grams / 100.0,
                    fat = item.fat100 * item.grams / 100.0,
                    carb = item.carb100 * item.grams / 100.0,
                    createdAt = stamp + index,
                ),
            )
        }
        _state.value = null
        onDone()
    }

    /** Saves an AI-only dish so the next photo can hit the library instead of paying again. */
    private suspend fun createFood(item: RecognizedFood, stamp: Long): String {
        val id = "C" + stamp.toString().takeLast(11)
        foodDao.insertFoods(
            listOf(
                FoodEntity(
                    id = id,
                    name = item.name,
                    kind = "CUSTOM",
                    basis = "COOKED",
                    category = "AI识别",
                    brand = null,
                    baseUnit = "g",
                    kcal100 = item.kcal100,
                    protein100 = item.protein100,
                    fat100 = item.fat100,
                    carb100 = item.carb100,
                    fiber100 = null,
                    sodium100 = null,
                    source = "ai",
                    sourceUrl = null,
                    confidence = "low",
                    verified = false,
                    notes = "AI 识图估算，未核对",
                ),
            ),
        )
        foodDao.insertServings(
            listOf(ServingEntity("S$id", id, "一份", item.grams, isDefault = true, sort = 1)),
        )
        return id
    }

    private fun FoodEntity.toEntry(day: Long, meal: String, grams: Double, stamp: Long) =
        DiaryEntryEntity(
            epochDay = day,
            meal = meal,
            foodId = id,
            nameSnapshot = name,
            grams = grams,
            kcal = kcal100 * grams / 100.0,
            protein = protein100 * grams / 100.0,
            fat = fat100 * grams / 100.0,
            carb = carb100 * grams / 100.0,
            createdAt = stamp,
        )

    private fun Bitmap.toJpegBase64(quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
