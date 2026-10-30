package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DishComposerTest {

    private val xiangGan = DishComposer.Ingredient("香干", 50.0, 140.0, 16.2, 3.6, 11.5)
    private val liJi = DishComposer.Ingredient("猪里脊", 100.0, 155.0, 20.2, 7.9, 0.7)

    @Test
    fun `sums ingredients plus oil`() {
        val totals = DishComposer.compose(listOf(xiangGan, liJi), oilGrams = 8.0)

        // 香干 50g: 70 kcal ; 里脊 100g: 155 kcal ; 油 8g: 72 kcal
        assertEquals(297.0, totals.kcal, 0.001)
        assertEquals(158.0, totals.grams, 0.001)
        assertEquals(28.3, totals.protein, 0.001) // 8.1 + 20.2
        assertEquals(17.7, totals.fat, 0.001)     // 1.8 + 7.9 + 8
        assertEquals(6.45, totals.carb, 0.001)    // 5.75 + 0.7
    }

    @Test
    fun `per 100g rescales without changing totals`() {
        val totals = DishComposer.compose(listOf(xiangGan, liJi), oilGrams = 8.0)
        val scaled = DishComposer.per100(totals)

        assertEquals(100.0, scaled.grams, 0.001)
        assertEquals(297.0 / 158.0 * 100.0, scaled.kcal, 0.001)
        assertEquals(totals.kcal / scaled.kcal, totals.grams / 100.0, 0.001)
    }

    @Test
    fun `oil adds pure fat and nine kcal per gram`() {
        val totals = DishComposer.compose(emptyList(), oilGrams = 10.0)
        assertEquals(90.0, totals.kcal, 0.001)
        assertEquals(10.0, totals.fat, 0.001)
        assertEquals(0.0, totals.protein, 0.001)
        assertEquals(0.0, totals.carb, 0.001)
    }

    @Test
    fun `empty dish does not divide by zero`() {
        val scaled = DishComposer.per100(DishComposer.compose(emptyList(), 0.0))
        assertEquals(0.0, scaled.kcal, 0.001)
        assertEquals(0.0, scaled.grams, 0.001)
    }

    @Test
    fun `description lists parts`() {
        assertEquals(
            "香干 50g + 猪里脊 100g + 油 8g",
            DishComposer.describe(listOf(xiangGan, liJi), 8.0),
        )
    }
}
