package com.daylyfood.app.domain

import com.daylyfood.app.data.db.FoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelFoodFactoryTest {

    private val label = NutritionLabelParser.parse(
        "能量 1823kJ 蛋白质 8.5g 脂肪 12g 碳水化合物 62.5g 膳食纤维 3g 钠 480mg",
    ).per100()

    @Test
    fun `label becomes a packaged food with sodium and fiber`() {
        val food = LabelFoodFactory.toFood("L1", "某品牌 饼干", label)
        assertEquals("PACKAGED", food.kind)
        assertEquals("AS_SOLD", food.basis)
        assertEquals("label", food.source)
        assertEquals("扫标签", food.category)
        assertEquals(480.0, food.sodium100!!, 0.001)
        assertEquals(3.0, food.fiber100!!, 0.001)
        assertEquals(8.5, food.protein100, 0.001)
        assertEquals(1823 / 4.184, food.kcal100, 0.01)
    }

    @Test
    fun `blank name gets a readable fallback instead of an empty row`() {
        val food = LabelFoodFactory.toFood("L2", "   ", label)
        assertEquals("扫的包装食品", food.name)
    }

    @Test
    fun `drink labels are stored in millilitres`() {
        val drink = NutritionLabelParser.parse("营养成分表 每100毫升 能量 180千焦 碳水化合物 10克").per100()
        assertEquals("ml", drink.unit)

        val food = LabelFoodFactory.toFood("L3", "某品牌 果汁", drink)
        assertEquals("ml", food.baseUnit)
        assertEquals("100 ml", LabelFoodFactory.defaultServing("L3", drink).label)
    }

    @Test
    fun `default serving is 100g when the label has no per-serving basis`() {
        val serving = LabelFoodFactory.defaultServing("L1", label)
        assertEquals(100.0, serving.grams, 0.001)
        assertEquals("L1", serving.foodId)
    }

    @Test
    fun `per-serving labels keep their serving size`() {
        val perServing = NutritionLabelParser.parse(
            "每份 30 克 能量 540kJ 蛋白质 3g 脂肪 6g 碳水化合物 15g 钠 90mg",
        )
        val food = LabelFoodFactory.toFood("L3", "小包零食", perServing)
        // scaled to 100 g for storage
        assertEquals(300.0, food.sodium100!!, 0.001)

        val serving = LabelFoodFactory.defaultServing("L3", perServing)
        assertEquals(30.0, serving.grams, 0.001)
        assertEquals("每份（30g）", serving.label)
    }

    @Test
    fun `missing fiber stays null rather than zero`() {
        val withoutFiber = NutritionLabelParser.parse("能量 900kJ 蛋白质 5g 脂肪 3g 碳水化合物 40g")
        val food = LabelFoodFactory.toFood("L4", "无纤维标注", withoutFiber)
        assertNull(food.fiber100)
        assertNull(food.sodium100)
    }
}
