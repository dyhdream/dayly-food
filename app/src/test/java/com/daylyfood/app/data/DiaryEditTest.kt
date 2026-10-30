package com.daylyfood.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.DiaryEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rescaling a logged portion must keep the per-100g ratios intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiaryEditTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rescaling grams rescales nutrients proportionally`() = runTest {
        val dao = database.diaryDao()
        val id = dao.insert(
            DiaryEntryEntity(
                epochDay = 1L,
                meal = "lunch",
                foodId = "F101",
                nameSnapshot = "米饭(熟)",
                grams = 100.0,
                kcal = 138.4,
                protein = 2.96,
                fat = 0.32,
                carb = 31.16,
                createdAt = 0L,
            )
        )

        dao.updateGrams(id, 250.0)

        val entry = dao.list(1L).single()
        assertEquals(250.0, entry.grams, 0.001)
        assertEquals(346.0, entry.kcal, 0.01)
        assertEquals(7.4, entry.protein, 0.01)
        assertEquals(0.8, entry.fat, 0.01)
        assertEquals(77.9, entry.carb, 0.01)
    }

    @Test
    fun `non positive grams is rejected`() = runTest {
        val dao = database.diaryDao()
        val id = dao.insert(
            DiaryEntryEntity(
                epochDay = 1L, meal = "lunch", foodId = null, nameSnapshot = "测试",
                grams = 100.0, kcal = 100.0, protein = 1.0, fat = 1.0, carb = 1.0, createdAt = 0L,
            )
        )

        dao.updateGrams(id, 0.0)

        assertEquals(100.0, dao.list(1L).single().grams, 0.001)
    }
}
