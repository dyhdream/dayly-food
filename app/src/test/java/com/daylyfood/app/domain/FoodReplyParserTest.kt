package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoodReplyParserTest {

    @Test
    fun `plain json is parsed`() {
        val outcome = FoodReplyParser.parse(
            """{"kind":"dish","items":[{"name":"香干炒肉","grams":200,"kcal_per_100g":188,"protein":18,"fat":18,"carb":6}]}""",
        )
        val dishes = outcome as FoodReplyParser.Outcome.Dishes
        assertEquals(1, dishes.items.size)
        assertEquals("香干炒肉", dishes.items[0].name)
        assertEquals(200.0, dishes.items[0].grams!!, 0.001)
        assertEquals(188.0, dishes.items[0].kcalPer100!!, 0.001)
    }

    @Test
    fun `markdown fenced json is parsed`() {
        val outcome = FoodReplyParser.parse(
            """
            好的，识别结果如下：
            ```json
            {"kind":"dish","items":[{"name":"米饭","grams":150,"kcal_per_100g":116}]}
            ```
            希望有帮助
            """.trimIndent(),
        )
        val dishes = outcome as FoodReplyParser.Outcome.Dishes
        assertEquals("米饭", dishes.items[0].name)
    }

    @Test
    fun `multiple dishes stay separate`() {
        val outcome = FoodReplyParser.parse(
            """{"kind":"dish","items":[
                {"name":"米饭","grams":150,"kcal_per_100g":116},
                {"name":"番茄炒蛋","grams":200,"kcal_per_100g":99},
                {"name":"可乐","grams":330,"kcal_per_100g":43}
            ]}""",
        )
        val dishes = outcome as FoodReplyParser.Outcome.Dishes
        assertEquals(3, dishes.items.size)
        assertEquals("可乐", dishes.items[2].name)
    }

    @Test
    fun `label photo is not treated as a dish`() {
        val outcome = FoodReplyParser.parse("""{"kind":"label","items":[]}""")
        assertTrue(outcome is FoodReplyParser.Outcome.NotFood)
    }

    @Test
    fun `non food photo reports a readable reason`() {
        val outcome = FoodReplyParser.parse("""{"kind":"other","items":[]}""")
        assertEquals("照片里没看出食物", (outcome as FoodReplyParser.Outcome.NotFood).reason)
    }

    @Test
    fun `garbage reply is rejected instead of crashing`() {
        val outcome = FoodReplyParser.parse("抱歉，我无法识别这张图片")
        assertTrue(outcome is FoodReplyParser.Outcome.NotFood)
    }

    @Test
    fun `empty items is rejected`() {
        val outcome = FoodReplyParser.parse("""{"kind":"dish","items":[]}""")
        assertTrue(outcome is FoodReplyParser.Outcome.NotFood)
    }

    @Test
    fun `kcal100 spelling is accepted`() {
        val outcome = FoodReplyParser.parse(
            """{"kind":"dish","items":[{"name":"包子","grams":100,"kcal100":220}]}""",
        )
        val dishes = outcome as FoodReplyParser.Outcome.Dishes
        assertEquals(220.0, dishes.items[0].kcalPer100!!, 0.001)
    }
}
