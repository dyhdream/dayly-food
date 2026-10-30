package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OCR output does not look like a tidy table: columns collapse, lines merge and order
 * drifts. These cases pin the parser against that messy shape.
 */
class NutritionLabelOcrTest {

    @Test
    fun `collapsed columns still parse`() {
        // OCR often glues the NRV% column onto the value.
        val label = NutritionLabelParser.parse(
            "能量 1823kJ 22% 蛋白质 8.5g 14% 脂肪 12.0g 20% 碳水化合物 62.5g 21% 钠 480mg 24%",
        )
        assertEquals(1823.0, label.energyKj!!, 0.001)
        assertEquals(8.5, label.protein!!, 0.001)
        assertEquals(12.0, label.fat!!, 0.001)
        assertEquals(62.5, label.carb!!, 0.001)
        assertEquals(480.0, label.sodiumMg!!, 0.001)
    }

    @Test
    fun `unit glued to the number parses`() {
        val label = NutritionLabelParser.parse("能量 900千焦 蛋白质 6g 脂肪 3g 碳水化合物 40g")
        assertEquals(900.0, label.energyKj!!, 0.001)
        assertEquals(40.0, label.carb!!, 0.001)
    }

    @Test
    fun `missing column header falls back to per 100`() {
        val label = NutritionLabelParser.parse("能量 1500kJ 蛋白质 10g 脂肪 5g 碳水化合物 50g 钠 300mg")
        assertEquals("PER_100", label.basis)
        assertEquals(300.0, label.sodiumMg!!, 0.001)
    }

    @Test
    fun `fiber is picked up when printed`() {
        val label = NutritionLabelParser.parse(
            "能量 1200kJ 蛋白质 4g 脂肪 2g 碳水化合物 55g 膳食纤维 6.5g 钠 120mg",
        )
        assertEquals(6.5, label.fiber!!, 0.001)
    }

    @Test
    fun `noise lines around the table do not break it`() {
        val label = NutritionLabelParser.parse(
            """
            产品标准号 GB/T 20981
            保质期 6 个月
            营养成分表
            能量 1600kJ 蛋白质 7g
            脂肪 9g 碳水化合物 58g
            钠 350mg
            贮存条件 阴凉干燥处
            """.trimIndent(),
        )
        assertEquals(1600.0, label.energyKj!!, 0.001)
        assertEquals(58.0, label.carb!!, 0.001)
        assertNull(label.fiber)
    }

    @Test
    fun `energy in kcal is not converted twice`() {
        val label = NutritionLabelParser.parse("能量 300大卡 蛋白质 10g 脂肪 10g 碳水化合物 40g")
        assertEquals(300.0, label.kcal!!, 0.001)
        assertNull(label.energyKj)
        assertTrue(!label.isSuspicious())
    }
}
