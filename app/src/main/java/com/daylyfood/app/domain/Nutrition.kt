package com.daylyfood.app.domain

/** Pure calculation, no Android dependencies, unit-testable. */
object Nutrition {

    /** Mifflin-St Jeor. sex: "M" or "F". */
    fun bmr(sex: String, age: Int, heightCm: Double, weightKg: Double): Double =
        if (sex == "M") {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        }

    fun tdee(bmr: Double, activity: Double): Double = bmr * activity

    /**
     * Goal adjustment with a safety floor: losing weight must never suggest an
     * intake below 1500 kcal (male) / 1200 kcal (female).
     */
    fun targetKcal(tdee: Double, goal: String, sex: String): Double {
        val factor = when (goal) {
            "LOSE" -> 0.8
            "GAIN" -> 1.1
            else -> 1.0
        }
        val floor = if (sex == "M") 1500.0 else 1200.0
        return maxOf(tdee * factor, floor)
    }

    data class Macros(val proteinG: Double, val fatG: Double, val carbG: Double)

    /** Protein 1.6 g/kg when cutting, 1.4 otherwise; fat 25% of energy; carbs take the rest. */
    fun macros(targetKcal: Double, weightKg: Double, goal: String): Macros {
        val proteinPerKg = if (goal == "LOSE") 1.6 else 1.4
        val proteinG = proteinPerKg * weightKg
        val fatG = targetKcal * 0.25 / 9.0
        val carbG = maxOf(0.0, (targetKcal - proteinG * 4.0 - fatG * 9.0)) / 4.0
        return Macros(proteinG, fatG, carbG)
    }
}
