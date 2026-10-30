package com.daylyfood.app.domain

/**
 * Sums a user-composed dish from raw ingredients plus cooking oil.
 *
 * The dish's total energy is exact (it is just the ingredients). Its per-100g values
 * depend on the cooked weight, which we approximate as ingredients + oil: enough for
 * portion math, and the user records a whole plate anyway.
 */
object DishComposer {

    data class Ingredient(
        val name: String,
        val grams: Double,
        val kcal100: Double,
        val protein100: Double,
        val fat100: Double,
        val carb100: Double,
    )

    data class Totals(
        val grams: Double,
        val kcal: Double,
        val protein: Double,
        val fat: Double,
        val carb: Double,
    )

    fun compose(ingredients: List<Ingredient>, oilGrams: Double): Totals {
        var grams = 0.0
        var kcal = 0.0
        var protein = 0.0
        var fat = 0.0
        var carb = 0.0
        ingredients.forEach { item ->
            val factor = item.grams / 100.0
            grams += item.grams
            kcal += item.kcal100 * factor
            protein += item.protein100 * factor
            fat += item.fat100 * factor
            carb += item.carb100 * factor
        }
        // Cooking oil is pure fat: 9 kcal per gram.
        grams += oilGrams
        kcal += oilGrams * 9.0
        fat += oilGrams
        return Totals(grams, kcal, protein, fat, carb)
    }

    /** Rescales to a 100 g basis so the dish can live in food_item like any other food. */
    fun per100(totals: Totals): Totals {
        if (totals.grams <= 0.0) return Totals(0.0, 0.0, 0.0, 0.0, 0.0)
        val factor = 100.0 / totals.grams
        return Totals(
            grams = 100.0,
            kcal = totals.kcal * factor,
            protein = totals.protein * factor,
            fat = totals.fat * factor,
            carb = totals.carb * factor,
        )
    }

    /** Human-readable recipe line stored in notes, e.g. "香干 50g + 猪里脊 100g + 油 8g". */
    fun describe(ingredients: List<Ingredient>, oilGrams: Double): String {
        val parts = ingredients.map { "${it.name} ${it.grams.roundToIntOrZero()}g" }.toMutableList()
        if (oilGrams > 0) parts += "油 ${oilGrams.roundToIntOrZero()}g"
        return parts.joinToString(" + ")
    }

    private fun Double.roundToIntOrZero(): Int = if (isFinite()) kotlin.math.round(this).toInt() else 0
}
