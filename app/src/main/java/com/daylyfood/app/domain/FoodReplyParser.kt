package com.daylyfood.app.domain

import org.json.JSONObject

/**
 * Parses the vision model's reply. Models like to wrap JSON in prose or markdown fences,
 * so the parser digs the object out and range-checks nothing here — that happens in
 * [RecognizedFoodResolver], where clamping has a meaning.
 */
object FoodReplyParser {

    sealed interface Outcome {
        data class Dishes(val items: List<RecognizedFoodResolver.RawItem>) : Outcome
        data class NotFood(val reason: String) : Outcome
    }

    fun parse(reply: String): Outcome {
        val json = extractJson(reply) ?: return Outcome.NotFood("模型没有返回可解析的结果")
        val root = runCatching { JSONObject(json) }
            .getOrElse { return Outcome.NotFood("模型返回的格式不对") }

        when (root.optString("kind").lowercase()) {
            "label" -> return Outcome.NotFood("照片里是营养成分表")
            "other", "" -> return Outcome.NotFood("照片里没看出食物")
        }

        val array = root.optJSONArray("items") ?: return Outcome.NotFood("没识别到食物")
        val items = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            RecognizedFoodResolver.RawItem(
                name = name,
                grams = item.optDoubleOrNull("grams"),
                kcalPer100 = item.optDoubleOrNull("kcal_per_100g") ?: item.optDoubleOrNull("kcal100"),
                protein = item.optDoubleOrNull("protein"),
                fat = item.optDoubleOrNull("fat"),
                carb = item.optDoubleOrNull("carb"),
            )
        }
        return if (items.isEmpty()) Outcome.NotFood("没识别到食物") else Outcome.Dishes(items)
    }

    private fun extractJson(reply: String): String? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return reply.substring(start, end + 1)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeUnless { it.isNaN() }
    }
}
