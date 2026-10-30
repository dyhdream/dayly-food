package com.daylyfood.app.domain

import org.json.JSONObject

/**
 * Second-shot parser: when on-device OCR fails but the model says the photo *is* a nutrition
 * label, we ask the model for the numbers directly instead of giving up.
 */
object LabelReplyParser {

    fun parse(reply: String): NutritionLabelParser.Label? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { JSONObject(reply.substring(start, end + 1)) }.getOrNull() ?: return null

        val energyValue = root.doubleOrNull("energy_value") ?: root.doubleOrNull("energy")
        val unit = root.optString("energy_unit").lowercase()
        val isKj = unit.contains("kj") || unit.contains("千焦") || (unit.isEmpty() && (energyValue ?: 0.0) > 200)

        val label = NutritionLabelParser.Label(
            basis = if (root.optString("basis").contains("serv", ignoreCase = true)) "PER_SERVING" else "PER_100",
            servingGrams = root.doubleOrNull("serving_grams"),
            energyKj = energyValue.takeIf { isKj },
            energyKcal = energyValue.takeIf { energyValue != null && !isKj },
            protein = root.doubleOrNull("protein"),
            fat = root.doubleOrNull("fat"),
            carb = root.doubleOrNull("carb"),
            fiber = root.doubleOrNull("fiber"),
            sodiumMg = root.doubleOrNull("sodium"),
        )
        return label.takeIf { !it.isEmpty }
    }

    private fun JSONObject.doubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key)
        return if (value.isNaN() || value < 0) null else value
    }
}
