package com.daylyfood.app.data

import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ProfileEntity
import com.daylyfood.app.data.db.ServingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupTest {

    private val profile = ProfileEntity(
        sex = "F", age = 28, heightCm = 165.0, weightKg = 55.5, activity = 1.55, goal = "LOSE",
    )

    private val customFood = FoodEntity(
        id = "D123",
        name = "香干炒肉",
        kind = "CUSTOM",
        basis = "COOKED",
        category = "组合菜品",
        brand = null,
        baseUnit = "g",
        kcal100 = 188.0,
        protein100 = 17.9,
        fat100 = 11.2,
        carb100 = 4.1,
        fiber100 = null,
        sodium100 = null,
        source = "local",
        sourceUrl = null,
        confidence = "low",
        verified = false,
        notes = "快炒：香干 50g + 猪里脊 100g + 油 8g",
    )

    private val serving = ServingEntity("SD123", "D123", "整盘", 158.0, true, 1)

    private val diary = DiaryEntryEntity(
        id = 7,
        epochDay = 20321,
        meal = "lunch",
        foodId = "D123",
        nameSnapshot = "香干炒肉",
        grams = 158.0,
        kcal = 297.0,
        protein = 28.3,
        fat = 17.7,
        carb = 6.45,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun `round trip preserves everything`() {
        val payload = Backup.Payload(profile, listOf(customFood), listOf(serving), listOf(diary))
        val json = Backup.encode(payload, appVersion = "0.6.0", exportedAt = "2026-10-30T14:00:00")
        val decoded = Backup.decode(json)

        assertEquals("F", decoded.profile?.sex)
        assertEquals(55.5, decoded.profile!!.weightKg, 0.001)
        assertEquals(1, decoded.customFoods.size)
        assertEquals("香干炒肉", decoded.customFoods[0].name)
        assertEquals("快炒：香干 50g + 猪里脊 100g + 油 8g", decoded.customFoods[0].notes)
        assertNull(decoded.customFoods[0].fiber100)
        assertEquals(1, decoded.servings.size)
        assertEquals(158.0, decoded.servings[0].grams, 0.001)
        assertEquals(1, decoded.diary.size)
        assertEquals(20321L, decoded.diary[0].epochDay)
        assertEquals(297.0, decoded.diary[0].kcal, 0.001)
    }

    @Test
    fun `restored diary ids are reset so they never collide`() {
        val payload = Backup.Payload(profile, emptyList(), emptyList(), listOf(diary))
        val decoded = Backup.decode(Backup.encode(payload, "0.6.0", "now"))
        assertEquals(0L, decoded.diary[0].id)
    }

    @Test
    fun `empty backup still decodes`() {
        val decoded = Backup.decode(Backup.encode(Backup.Payload(null, emptyList(), emptyList(), emptyList()), "0.6.0", "now"))
        assertNull(decoded.profile)
        assertTrue(decoded.customFoods.isEmpty())
        assertTrue(decoded.diary.isEmpty())
        assertTrue(decoded.isEmpty)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `newer schema is rejected instead of silently truncated`() {
        Backup.decode("""{"schema":99,"customFoods":[],"servings":[],"diary":[]}""")
    }
}
