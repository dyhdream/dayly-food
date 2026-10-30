package com.daylyfood.app.data.ai

import com.daylyfood.app.domain.FoodReplyParser
import com.daylyfood.app.domain.LabelReplyParser
import com.daylyfood.app.domain.NutritionLabelParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognises what is in a photo with the vision model.
 *
 * The model is asked one question and must answer in JSON; every field is treated as a
 * proposal that gets range-checked and, when possible, replaced with a library value.
 */
@Singleton
class FoodRecognizer @Inject constructor(
    private val client: AiClient,
) {

    suspend fun recognize(jpegBase64: String): Result<FoodReplyParser.Outcome> =
        client.completeVision(SYSTEM_PROMPT, USER_PROMPT, jpegBase64)
            .mapCatching { FoodReplyParser.parse(it) }

    /**
     * Second shot used only when on-device OCR could not read the label: ask the model for the
     * numbers themselves. Keeps the free path free, and still delivers when ML Kit misses.
     */
    suspend fun extractLabel(jpegBase64: String): Result<NutritionLabelParser.Label?> =
        client.completeVision(LABEL_SYSTEM_PROMPT, LABEL_PROMPT, jpegBase64)
            .mapCatching { LabelReplyParser.parse(it) }

    private companion object {
        const val SYSTEM_PROMPT =
            "你是食物识别助手。你只输出 JSON，不解释、不使用 markdown 代码块。"

        const val LABEL_SYSTEM_PROMPT =
            "你是营养成分表识别助手。你只输出 JSON，不解释、不使用 markdown 代码块。"

        val LABEL_PROMPT = """
            读出这张营养成分表上的数字，只输出 JSON：
            {"energy_value":1823,"energy_unit":"kj","protein":8.5,"fat":12,"carb":62.5,"sodium":480,"fiber":3,"basis":"per_100","serving_grams":null}

            要求：
            - energy_unit 只能是 "kj" 或 "kcal"，按表上印的单位填（国内多为 kJ）
            - basis 填 "per_100" 或 "per_serving"；如果是每份，serving_grams 填一份多少克
            - protein/fat/carb 单位克，sodium 单位毫克，fiber 单位克
            - 表上没有的项目填 null
            - 只填表上真实印着的数字，不要估算
        """.trimIndent()

        val USER_PROMPT = """
            判断这张照片的内容，只输出 JSON：
            - 如果是包装上的营养成分表 → {"kind":"label","items":[]}
            - 如果是食物、菜品或饮料 → 列出画面里每一样可以吃的东西
            - 其他（人、宠物、风景、没有食物）→ {"kind":"other","items":[]}

            食物格式（数值都是「每 100 克」）：
            {"kind":"dish","items":[{"name":"香干炒肉","grams":200,"kcal_per_100g":188,"protein":18,"fat":18,"carb":6}]}

            要求：
            - name 用中国人常说的叫法，如「香干炒肉」，不要写「炒肉片配豆腐」
            - grams 是照片里这一份大概多少克，宁可给个整数
            - 一盘里有几样菜就列几项，不要合并
            - 只要是你认为最可能的值，不要留空
        """.trimIndent()
    }
}
