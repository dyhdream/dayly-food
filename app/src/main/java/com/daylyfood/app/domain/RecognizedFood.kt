package com.daylyfood.app.domain

import com.daylyfood.app.data.db.FoodEntity

/**
 * One food the vision model saw, already resolved against the local library.
 *
 * Level 1 (library hit) wins over the model's own numbers: the library value is reviewable
 * and reproducible, the model's is a guess. [source] records which one was used so the
 * confirm screen can say so instead of pretending both are equal.
 */
data class RecognizedFood(
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carb100: Double,
    val source: String,
    val foodId: String? = null,
) {
    val isFromLibrary: Boolean get() = source == FROM_LIBRARY

    val kcal: Double get() = kcal100 * grams / 100.0

    companion object {
        const val FROM_LIBRARY = "library"
        const val FROM_AI = "ai"
    }
}

object RecognizedFoodResolver {

    /** Nuts, oil and seasonings are plausible at 20 g but absurd at 2 kg: clamp instead of trusting. */
    private const val MAX_GRAMS = 3000.0
    private const val MAX_KCAL_PER_100 = 900.0

    fun resolve(
        items: List<RawItem>,
        lookup: (String) -> FoodEntity?,
    ): List<RecognizedFood> = items.mapNotNull { item ->
        val grams = item.grams?.takeIf { it > 0 }?.coerceAtMost(MAX_GRAMS) ?: return@mapNotNull null
        val name = item.name.trim().ifBlank { return@mapNotNull null }
        val library = lookup(name)

        if (library != null) {
            RecognizedFood(
                name = library.name,
                grams = grams,
                kcal100 = library.kcal100,
                protein100 = library.protein100,
                fat100 = library.fat100,
                carb100 = library.carb100,
                source = RecognizedFood.FROM_LIBRARY,
                foodId = library.id,
            )
        } else {
            val kcal = item.kcalPer100?.coerceIn(0.0, MAX_KCAL_PER_100) ?: return@mapNotNull null
            RecognizedFood(
                name = name,
                grams = grams,
                kcal100 = kcal,
                protein100 = item.protein?.coerceIn(0.0, 100.0) ?: 0.0,
                fat100 = item.fat?.coerceIn(0.0, 100.0) ?: 0.0,
                carb100 = item.carb?.coerceIn(0.0, 100.0) ?: 0.0,
                source = RecognizedFood.FROM_AI,
            )
        }
    }

    /** Raw model output before library resolution. */
    data class RawItem(
        val name: String,
        val grams: Double?,
        val kcalPer100: Double?,
        val protein: Double?,
        val fat: Double?,
        val carb: Double?,
    )
}
