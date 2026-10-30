package com.daylyfood.app.domain

import kotlin.math.abs

/**
 * Parses a Chinese nutrition label (营养成分表) into per-100 values.
 *
 * Labels vary in three ways we must handle:
 *  - basis: 「每100克」 vs 「每份 30 克」 -> everything is normalised to per 100 g/ml
 *  - energy unit: kJ (usual in China) vs kcal -> 1 kcal = 4.184 kJ
 *  - noise: NRV% columns, full-width digits, 饱和脂肪/反式脂肪 lines above 脂肪
 */
object NutritionLabelParser {

    private const val KJ_PER_KCAL = 4.184

    data class Label(
        val basis: String = BASIS_PER_100,
        val servingGrams: Double? = null,
        val energyKj: Double? = null,
        val energyKcal: Double? = null,
        val protein: Double? = null,
        val fat: Double? = null,
        val carb: Double? = null,
        val fiber: Double? = null,
        val sodiumMg: Double? = null,
    ) {
        /** Energy in kcal, from whichever unit the label used. */
        val kcal: Double?
            get() = energyKcal ?: energyKj?.let { it / KJ_PER_KCAL }

        val isEmpty: Boolean
            get() = kcal == null && protein == null && fat == null && carb == null && sodiumMg == null

        /** Rescales to a 100 g/ml basis so it can be stored like any other food. */
        fun per100(): Label {
            if (basis == BASIS_PER_100) return this
            val grams = servingGrams?.takeIf { it > 0 } ?: return this
            val factor = 100.0 / grams
            return Label(
                basis = BASIS_PER_100,
                servingGrams = null,
                energyKj = energyKj?.times(factor),
                energyKcal = energyKcal?.times(factor),
                protein = protein?.times(factor),
                fat = fat?.times(factor),
                carb = carb?.times(factor),
                fiber = fiber?.times(factor),
                sodiumMg = sodiumMg?.times(factor),
            )
        }

        /**
         * Label energy vs energy implied by macros. A large gap means an OCR mistake or a
         * per-serving/per-100 mix-up, so the confirm screen can warn instead of trusting it.
         */
        fun energyDrift(): Double? {
            val declared = kcal ?: return null
            if (declared <= 0) return null
            val p = protein ?: return null
            val f = fat ?: return null
            val c = carb ?: return null
            val implied = 4 * p + 9 * f + 4 * c
            return abs(implied - declared) / declared
        }

        fun isSuspicious(): Boolean = (energyDrift() ?: 0.0) > 0.30
    }

    /** Parses free text (model reply or OCR output). Tolerant: any missing field stays null. */
    fun parse(text: String): Label {
        val normalized = normalize(text)
        val basis = when {
            PER_100_PATTERN.containsMatchIn(normalized) -> BASIS_PER_100
            PER_SERVING_PATTERN.containsMatchIn(normalized) -> BASIS_PER_SERVING
            else -> BASIS_PER_100
        }
        val servingGrams = PER_SERVING_PATTERN.find(normalized)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        val energyMatch = ENERGY_PATTERN.find(normalized)
        val energyValue = energyMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val energyUnit = energyMatch?.groupValues?.getOrNull(2)?.lowercase()
        val isKj = energyUnit != null && (energyUnit.contains("kj") || energyUnit.contains("千焦"))

        return Label(
            basis = basis,
            servingGrams = servingGrams,
            energyKj = energyValue.takeIf { isKj },
            energyKcal = energyValue.takeIf { energyValue != null && !isKj },
            protein = grab(normalized, PROTEIN_PATTERN),
            fat = grab(normalized, FAT_PATTERN),
            carb = grab(normalized, CARB_PATTERN),
            fiber = grab(normalized, FIBER_PATTERN),
            sodiumMg = grab(normalized, SODIUM_PATTERN),
        )
    }

    fun kcalFrom(kj: Double): Double = kj / KJ_PER_KCAL

    private fun grab(text: String, pattern: Regex): Double? =
        pattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

    /** Full-width digits/punctuation and commas would otherwise break every pattern. */
    private fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            val mapped = when (char) {
                in '０'..'９' -> '0' + (char - '０')
                '．' -> '.'
                // Chinese labels use 「，」 as a thousands separator, not a decimal point.
                '，' -> ','
                '％' -> '%'
                'Ｋ', 'ｋ' -> 'k'
                'Ｊ', 'ｊ' -> 'j'
                'Ｍ', 'ｍ' -> 'm'
                'Ｇ', 'ｇ' -> 'g'
                '　' -> ' '
                else -> char
            }
            builder.append(mapped)
        }
        return builder.toString().replace(",", "")
    }

    private const val BASIS_PER_100 = "PER_100"
    private const val BASIS_PER_SERVING = "PER_SERVING"

    private val PER_100_PATTERN = Regex("每\\s*100\\s*(?:克|g|毫升|ml)", RegexOption.IGNORE_CASE)
    private val PER_SERVING_PATTERN = Regex(
        "每\\s*份[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*(?:克|g|毫升|ml)",
        RegexOption.IGNORE_CASE,
    )
    private val ENERGY_PATTERN = Regex(
        "(?:能量|热量|energy)[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*(kj|千焦|kcal|千卡|大卡)",
        RegexOption.IGNORE_CASE,
    )
    private val PROTEIN_PATTERN = Regex("蛋白质[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*g", RegexOption.IGNORE_CASE)
    // 「饱和脂肪」「反式脂肪」 sit above 脂肪 in the table and must not win the match.
    private val FAT_PATTERN = Regex("(?<!饱和)(?<!反式)脂肪[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*g", RegexOption.IGNORE_CASE)
    private val CARB_PATTERN = Regex("碳水化合物[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*g", RegexOption.IGNORE_CASE)
    private val FIBER_PATTERN = Regex("膳食纤维[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*g", RegexOption.IGNORE_CASE)
    private val SODIUM_PATTERN = Regex("钠[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*mg", RegexOption.IGNORE_CASE)
}
