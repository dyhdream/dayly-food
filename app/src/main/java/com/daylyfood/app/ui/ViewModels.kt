package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.SeedImporter
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ProfileDao
import com.daylyfood.app.data.db.ProfileEntity
import com.daylyfood.app.data.db.RecentFood
import com.daylyfood.app.data.db.ServingEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val importer: SeedImporter,
    private val profileDao: ProfileDao,
) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val profile: StateFlow<ProfileEntity?> = profileDao.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            importer.ensureSeeded()
            _ready.value = true
        }
    }

    fun saveProfile(profile: ProfileEntity) = viewModelScope.launch {
        profileDao.save(profile)
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val diaryDao: DiaryDao,
    private val profileDao: ProfileDao,
) : ViewModel() {

    private val _day = MutableStateFlow(LocalDate.now().toEpochDay())
    val day: StateFlow<Long> = _day.asStateFlow()

    val profile: StateFlow<ProfileEntity?> = profileDao.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<DiaryEntryEntity>> = _day
        .flatMapLatest { diaryDao.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recent: StateFlow<List<RecentFood>> = diaryDao.recent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun shiftDay(delta: Long) {
        val today = LocalDate.now().toEpochDay()
        _day.value = (_day.value + delta).coerceAtMost(today)
    }

    fun delete(id: Long) = viewModelScope.launch { diaryDao.delete(id) }

    /** One tap re-records a recent food at its last portion, no search needed. */
    fun quickAdd(item: RecentFood, meal: String) = viewModelScope.launch {
        val factor = item.grams / 100.0
        diaryDao.insert(
            DiaryEntryEntity(
                epochDay = _day.value,
                meal = meal,
                foodId = item.foodId,
                nameSnapshot = item.name,
                grams = item.grams,
                kcal = item.kcal100 * factor,
                protein = item.protein100 * factor,
                fat = item.fat100 * factor,
                carb = item.carb100 * factor,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    fun copyPreviousDay() = viewModelScope.launch {
        val source = diaryDao.list(_day.value - 1)
        if (source.isEmpty()) return@launch
        val now = System.currentTimeMillis()
        diaryDao.insertAll(
            source.map {
                it.copy(id = 0, epochDay = _day.value, createdAt = now)
            }
        )
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val foodDao: FoodDao,
    private val diaryDao: DiaryDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<FoodEntity>>(emptyList())
    val results: StateFlow<List<FoodEntity>> = _results.asStateFlow()

    private val _selected = MutableStateFlow<FoodEntity?>(null)
    val selected: StateFlow<FoodEntity?> = _selected.asStateFlow()

    private val _servings = MutableStateFlow<List<ServingEntity>>(emptyList())
    val servings: StateFlow<List<ServingEntity>> = _servings.asStateFlow()

    init {
        viewModelScope.launch {
            _query.collectLatest { q ->
                _results.value = if (q.isBlank()) foodDao.recent() else foodDao.search(q.trim())
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun select(food: FoodEntity) = viewModelScope.launch {
        _selected.value = food
        _servings.value = foodDao.servings(food.id)
    }

    fun dismiss() {
        _selected.value = null
        _servings.value = emptyList()
    }

    fun record(meal: String, grams: Double, onDone: () -> Unit) = viewModelScope.launch {
        val food = _selected.value ?: return@launch
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
            )
        )
        dismiss()
        onDone()
    }
}
