package com.daylyfood.app.domain

/** Cooking methods supply only a default oil amount; the user can override it. */
data class CookingMethod(
    val id: String,
    val label: String,
    val oilPer100gRaw: Double,
)

object CookingMethods {
    val ALL = listOf(
        CookingMethod("C01", "清蒸", 0.0),
        CookingMethod("C02", "水煮", 0.0),
        CookingMethod("C03", "凉拌", 3.0),
        CookingMethod("C04", "快炒", 5.0),
        CookingMethod("C05", "红烧", 8.0),
        CookingMethod("C06", "干锅", 8.0),
        CookingMethod("C07", "油炸", 15.0),
        CookingMethod("C08", "煎", 6.0),
    )

    fun byId(id: String): CookingMethod = ALL.firstOrNull { it.id == id } ?: ALL[3]

    fun defaultOilGrams(methodId: String, rawGrams: Double): Double =
        byId(methodId).oilPer100gRaw * rawGrams / 100.0
}

/** Guess the meal slot from the clock so adding never asks a question it can answer itself. */
fun guessMeal(hour: Int): String = when (hour) {
    in 5..10 -> "breakfast"
    in 11..14 -> "lunch"
    in 17..21 -> "dinner"
    else -> "snack"
}

val MEAL_LABELS = listOf(
    "breakfast" to "早餐",
    "lunch" to "午餐",
    "dinner" to "晚餐",
    "snack" to "加餐",
)
