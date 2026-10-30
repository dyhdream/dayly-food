package com.daylyfood.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daylyfood.app.data.db.MetaDao
import com.daylyfood.app.data.db.MetaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** M0 smoke test: proves Hilt -> Room -> Compose is wired end to end. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val metaDao: MetaDao,
) : ViewModel() {

    private val _launchCount = MutableStateFlow(0)
    val launchCount: StateFlow<Int> = _launchCount.asStateFlow()

    init {
        viewModelScope.launch {
            val next = (metaDao.get(KEY)?.toIntOrNull() ?: 0) + 1
            metaDao.put(MetaEntity(KEY, next.toString()))
            _launchCount.value = next
        }
    }

    private companion object {
        const val KEY = "launch_count"
    }
}
