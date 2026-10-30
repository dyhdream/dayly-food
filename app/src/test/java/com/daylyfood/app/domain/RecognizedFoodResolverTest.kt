package com.daylyfood.app.domain

import com.daylyfood.app.data.db.FoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedFoodResolverTest {

    private val libraryFood = FoodEntity(
        id = "D01",
        name = "香干炒肉",
        kind = "DISH",
        basis = "COOKED",
        category = "家常菜",
        brand = null,
        baseUnit = "g",
        kcal100 = 188.0,
        protein100 = 18.0,
        fat100 = 18.0,
        carb100 = 4.0,
        fiber100 = null,
        sodium100 = null,
        source = "derived",
        sourceUrl = null,
        confidence = "high",
        verified = true,
        notes = null,
    )

    @Test
    fun `library hit overrides the model numbers`() {
        val resolved = RecognizedFoodResolver.resolve(
            listOf(
                RecognizedFoodResolver.RawItem("香干炒肉", 200.0, kcalPer100 = 240.0, 15.0, 15.0, 5.0),
            ),
        ) { name -> if (name == "香干炒肉") libraryFood else null }

        assertEquals(1, resolved.size)
        assertTrue(resolved[0].isFromLibrary)
        assertEquals("D01", resolved[0].foodId)
        assertEquals(188.0, resolved[0].kcal100, 0.001)
        assertEquals(376.0, resolved[0].kcal, 0.5)
    }

    @Test
    fun `unknown dish keeps model numbers and is marked ai`() {
        val resolved = RecognizedFoodResolver.resolve(
            listOf(RecognizedFoodResolver.RawItem("外婆菜炒蛋", 150.0, 150.0, 8.0, 11.0, 4.0)),
        ) { null }

        assertEquals(RecognizedFood.FROM_AI, resolved[0].source)
        assertEquals(225.0, resolved[0].kcal, 0.5)
    }

    @Test
    fun `absurd grams are clamped instead of trusted`() {
        val resolved = RecognizedFoodResolver.resolve(
            listOf(RecognizedFoodResolver.RawItem("米饭", 50_000.0, 116.0, 2.6, 0.3, 25.9)),
        ) { null }
        assertEquals(3000.0, resolved[0].grams, 0.001)
    }

    @Test
    fun `absurd energy is clamped to something physically possible`() {
        val resolved = RecognizedFoodResolver.resolve(
            listOf(RecognizedFoodResolver.RawItem("未知", 100.0, kcalPer100 = 9999.0, null, null, null)),
        ) { null }
        assertEquals(900.0, resolved[0].kcal100, 0.001)
    }

    @Test
    fun `item without usable numbers is dropped rather than stored as zero calories`() {
        val resolved = RecognizedFoodResolver.resolve(
            listOf(
                RecognizedFoodResolver.RawItem("说不清的东西", null, null, null, null, null),
                RecognizedFoodResolver.RawItem("", 100.0, 100.0, null, null, null),
            ),
        ) { null }
        assertTrue(resolved.isEmpty())
    }
}
