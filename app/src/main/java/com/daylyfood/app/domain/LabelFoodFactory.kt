package com.daylyfood.app.domain

import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity

/**
 * Turns a parsed nutrition label into a storeable food. Label values are per 100 g/ml after
 * [NutritionLabelParser.Label.per100], which matches our PACKAGED/AS_SOLD convention.
 */
object LabelFoodFactory {

    /** Long, stable id from the millisecond clock; label foods are never rewritten. */
    fun newId(stamp: Long): String = "L" + stamp.toString().takeLast(11)

    fun toFood(
        id: String,
        name: String,
        label: NutritionLabelParser.Label,
    ): FoodEntity {
        val per100 = label.per100()
        return FoodEntity(
            id = id,
            name = name.trim().ifBlank { "扫的包装食品" },
            kind = "PACKAGED",
            basis = "AS_SOLD",
            category = "扫标签",
            brand = null,
            baseUnit = per100.unit,
            kcal100 = per100.kcal ?: 0.0,
            protein100 = per100.protein ?: 0.0,
            fat100 = per100.fat ?: 0.0,
            carb100 = per100.carb ?: 0.0,
            fiber100 = per100.fiber,
            sodium100 = per100.sodiumMg,
            source = "label",
            sourceUrl = null,
            confidence = "medium",
            verified = false,
            notes = "拍照扫营养成分表录入" + (per100.energyKj?.let { "；标签能量 ${it.toInt()} kJ" } ?: ""),
        )
    }

    fun defaultServing(foodId: String, label: NutritionLabelParser.Label): ServingEntity {
        val unit = label.unit
        val amount = label.servingGrams ?: 100.0
        return ServingEntity(
            id = "S$foodId",
            foodId = foodId,
            label = if (label.servingGrams != null) "每份（${amount.toInt()}$unit）" else "100 $unit",
            grams = amount,
            isDefault = true,
            sort = 1,
        )
    }
}
