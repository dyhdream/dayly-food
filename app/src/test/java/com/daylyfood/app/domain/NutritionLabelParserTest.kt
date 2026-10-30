package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelParserTest {

    @Test
    fun `parses a per-100g label in kilojoules`() {
        val label = NutritionLabelParser.parse(
            """
            营养成分表
            项目        每100克    NRV%
            能量        1823 kJ    22%
            蛋白质      8.5 g      14%
            脂肪        12.0 g     20%
            碳水化合物  62.5 g     21%
            钠          480 mg     24%
            """.trimIndent(),
        )

        assertEquals(1823.0, label.energyKj!!, 0.001)
        assertEquals(435.7, label.kcal!!, 0.5)
        assertEquals(8.5, label.protein!!, 0.001)
        assertEquals(12.0, label.fat!!, 0.001)
        assertEquals(62.5, label.carb!!, 0.001)
        assertEquals(480.0, label.sodiumMg!!, 0.001)
        assertTrue(label.energyDrift() != null)
    }

    @Test
    fun `saturated and trans fat lines do not shadow total fat`() {
        val label = NutritionLabelParser.parse(
            """
            能量 900 kJ
            蛋白质 5 g
            脂肪 10 g
            - 饱和脂肪 4 g
            - 反式脂肪 0 g
            碳水化合物 20 g
            """.trimIndent(),
        )
        assertEquals(10.0, label.fat!!, 0.001)
    }

    @Test
    fun `per serving label is rescaled to 100g`() {
        val label = NutritionLabelParser.parse(
            """
            每份 30 克
            能量 540 kJ
            蛋白质 3.0 g
            脂肪 6.0 g
            碳水化合物 15.0 g
            钠 90 mg
            """.trimIndent(),
        )
        assertEquals("PER_SERVING", label.basis)
        assertEquals(30.0, label.servingGrams!!, 0.001)

        val per100 = label.per100()
        assertEquals("PER_100", per100.basis)
        assertEquals(1800.0, per100.energyKj!!, 0.001)
        assertEquals(10.0, per100.protein!!, 0.001)
        assertEquals(300.0, per100.sodiumMg!!, 0.001)
    }

    @Test
    fun `kcal labels are understood directly`() {
        val label = NutritionLabelParser.parse("能量 250 kcal  蛋白质 10g 脂肪 8g 碳水化合物 30g")
        assertEquals(250.0, label.kcal!!, 0.001)
        assertNull(label.energyKj)
    }

    @Test
    fun `full width digits and commas are normalised`() {
        val label = NutritionLabelParser.parse("能量 １，２３４ kJ 脂肪 ３．５ g 钠 ２２０ mg")
        assertEquals(1234.0, label.energyKj!!, 0.001)
        assertEquals(3.5, label.fat!!, 0.001)
        assertEquals(220.0, label.sodiumMg!!, 0.001)
    }

    @Test
    fun `missing table yields empty result instead of garbage`() {
        val label = NutritionLabelParser.parse("这是一张菜品的照片，没有营养成分表")
        assertTrue(label.isEmpty)
        assertNull(label.kcal)
    }

    @Test
    fun `suspicious label is flagged when macros and energy disagree`() {
        // 20g protein + 5g fat + 60g carb implies ~325 kcal, but the label says 120 kcal
        val label = NutritionLabelParser.parse("能量 120 kcal 蛋白质 20g 脂肪 5g 碳水化合物 60g")
        assertTrue(label.isSuspicious())
    }

    @Test
    fun `consistent label is not flagged`() {
        // 4*10 + 9*8 + 4*30 = 232 kcal vs declared 240 -> within tolerance
        val label = NutritionLabelParser.parse("能量 240 kcal 蛋白质 10g 脂肪 8g 碳水化合物 30g")
        assertTrue(!label.isSuspicious())
    }

    @Test
    fun `kilojoule conversion factor`() {
        assertEquals(100.0, NutritionLabelParser.kcalFrom(418.4), 0.0001)
    }
}
