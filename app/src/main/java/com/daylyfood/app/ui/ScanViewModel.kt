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
import kotlinx.coroutines.withTimeoutOrNull
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
        data class Failed(
            val message: String,
            val offerAiSetup: Boolean = false,
            /** What on-device OCR actually saw, shown so a parser gap can be reported. */
            val rawText: String? = null,
        ) : State
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /**
     * Label first (free on-device OCR), model second. If the model then says "this is a label",
     * ask it for the numbers instead of reporting failure — OCR missing must not dead-end.
     */
    fun start(uri: Uri) = viewModelScope.launch {
        _state.value = State.Reading
        val bitmap = photoLoader.load(uri).getOrElse {
            _state.value = State.Failed(it.message ?: "图片读取失败")
            return@launch
        }

        val ocrText = withTimeoutOrNull(OCR_TIMEOUT_MS) { labelReader.readText(bitmap) }
            ?.getOrNull().orEmpty()
        val parsed = NutritionLabelParser.parse(ocrText).takeIf { !it.isEmpty }?.per100()
        if (parsed != null) {
            _state.value = State.LabelReady(parsed)
            return@launch
        }
        val rawText = ocrText.takeIf { it.isNotBlank() }?.take(400)

        val config = aiSettings.config.value
        if (!config.visionEnabled || !config.isConfigured) {
            val hint = if (!config.visionEnabled) {
                "想拍照认菜，需要打开「识图记餐」并填好模型接口。"
            } else {
                "拍照认菜要在「AI 设置」里填接口地址、模型名和 Key。"
            }
            _state.value = State.Failed("没读出营养成分表。\n$hint", offerAiSetup = true, rawText = rawText)
            return@launch
        }

        val jpeg = bitmap.toJpegBase64()
        val outcome = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { foodRecognizer.recognize(jpeg) }
            ?.getOrElse { error ->
                _state.value = State.Failed(error.message ?: "识别失败", rawText = rawText)
                return@launch
            } ?: run {
                // Never leave the user staring at "正在识别…".
                _state.value = State.Failed("识别超时。检查网络后重拍，或手动添加。", rawText = rawText)
                return@launch
            }

        when (outcome) {
            is FoodReplyParser.Outcome.NotFood -> {
                val labelFromModel = if (outcome.reason.contains("营养成分表")) {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) { foodRecognizer.extractLabel(jpeg) }?.getOrNull()
                } else {
                    null
                }
                _state.value = if (labelFromModel != null) {
                    State.LabelReady(labelFromModel.per100())
                } else {
                    State.Failed("${outcome.reason}。\n换个角度重拍，或手动添加。", rawText = rawText)
                }
            }

            is FoodReplyParser.Outcome.Dishes -> {
                // Resolve on the coroutine, then hand a plain map to the pure resolver.
                val library = outcome.items.associate { it.name to lookup(it.name) }
                val resolved = RecognizedFoodResolver.resolve(outcome.items) { library[it] }
                _state.value = if (resolved.isEmpty()) {
                    State.Failed("认出了食物，但没拿到可用数值。\n手动添加一次，它就会进库里。", rawText = rawText)
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

    private companion object {
        const val OCR_TIMEOUT_MS = 25_000L
        const val NETWORK_TIMEOUT_MS = 75_000L
    }
}
