package com.daylyfood.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.ocr.LabelReader
import com.daylyfood.app.data.ocr.PhotoLoader
import com.daylyfood.app.domain.LabelFoodFactory
import com.daylyfood.app.domain.NutritionLabelParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val labelReader: LabelReader,
    private val photoLoader: PhotoLoader,
    private val foodDao: FoodDao,
    private val diaryDao: DiaryDao,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Reading : State
        data class Ready(val label: NutritionLabelParser.Label) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun scan(uri: Uri) = viewModelScope.launch {
        _state.value = State.Reading
        _state.value = photoLoader.load(uri).fold(
            onSuccess = { bitmap ->
                // The bitmap is only needed during recognition; OCR copies what it needs.
                labelReader.read(bitmap).fold(
                    onSuccess = { State.Ready(it) },
                    onFailure = { State.Failed(it.message ?: "识别失败") },
                )
            },
            onFailure = { State.Failed(it.message ?: "图片读取失败") },
        )
    }

    fun reset() {
        _state.value = State.Idle
    }

    fun save(
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

        val factor = grams / 100.0
        diaryDao.insert(
            DiaryEntryEntity(
                epochDay = day,
                meal = meal,
                foodId = foodId,
                nameSnapshot = food.name,
                grams = grams,
                kcal = food.kcal100 * factor,
                protein = food.protein100 * factor,
                fat = food.fat100 * factor,
                carb = food.carb100 * factor,
                createdAt = stamp,
            ),
        )
        _state.value = State.Idle
        onDone()
    }
}
