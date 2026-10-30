package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionTest {

    @Test
    fun `male bmr uses Mifflin-St Jeor`() {
        // 10*70 + 6.25*175 - 5*28 + 5 = 1658.75
        assertEquals(1658.75, Nutrition.bmr("M", 28, 175.0, 70.0), 0.01)
    }

    @Test
    fun `female bmr is 161 lower than male baseline`() {
        assertEquals(1492.75, Nutrition.bmr("F", 28, 175.0, 70.0), 0.01)
    }

    @Test
    fun `target kcal never drops below the safety floor`() {
        // Female, very low tdee, cutting: 1200 floor wins over 800.
        assertEquals(1200.0, Nutrition.targetKcal(1000.0, "LOSE", "F"), 0.01)
        // Male floor is 1500.
        assertEquals(1500.0, Nutrition.targetKcal(1000.0, "LOSE", "M"), 0.01)
    }

    @Test
    fun `cutting applies 20 percent deficit above the floor`() {
        assertEquals(2400.0, Nutrition.targetKcal(3000.0, "LOSE", "M"), 0.01)
        assertEquals(3300.0, Nutrition.targetKcal(3000.0, "GAIN", "M"), 0.01)
        assertEquals(3000.0, Nutrition.targetKcal(3000.0, "MAINTAIN", "M"), 0.01)
    }

    @Test
    fun `macros follow 1_6g protein cutting and 25 percent fat`() {
        val m = Nutrition.macros(targetKcal = 1600.0, weightKg = 60.0, goal = "LOSE")
        assertEquals(96.0, m.proteinG, 0.01)
        assertEquals(44.44, m.fatG, 0.01)
        // (1600 - 96*4 - 44.44*9) / 4 = (1600 - 384 - 400) / 4 = 204
        assertEquals(204.0, m.carbG, 0.5)
    }
}
