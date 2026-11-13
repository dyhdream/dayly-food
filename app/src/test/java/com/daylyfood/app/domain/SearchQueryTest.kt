package com.daylyfood.app.domain

import com.daylyfood.app.data.db.FoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    private fun food(name: String, kind: String = "INGREDIENT") = FoodEntity(
        id = "$kind:$name",
        name = name,
        kind = kind,
        basis = "RAW",
        category = "test",
        brand = null,
        baseUnit = "g",
        kcal100 = 100.0,
        protein100 = 1.0,
        fat100 = 1.0,
        carb100 = 1.0,
        fiber100 = null,
        sodium100 = null,
        source = "test",
        sourceUrl = null,
        confidence = "high",
        verified = true,
        notes = null,
    )

    @Test
    fun `synonym lets 西红柿 find 番茄`() {
        val terms = SearchQuery.terms("西红柿")
        assertTrue("西红柿", terms.contains("番茄"))
        assertTrue(terms.contains("西红柿"))
    }

    @Test
    fun `multi word query is split`() {
        val terms = SearchQuery.terms("番茄 炒蛋")
        assertTrue(terms.contains("番茄"))
        assertTrue(terms.contains("炒蛋"))
    }

    @Test
    fun `noise words are dropped`() {
        val terms = SearchQuery.terms("我想吃一碗米饭")
        assertEquals(listOf("米饭"), terms.filter { it == "米饭" }.take(1))
        assertTrue(terms.none { it == "我想" })
    }

    @Test
    fun `exact name ranks above longer name`() {
        val ranked = SearchQuery.rank(listOf(food("番茄"), food("番茄牛腩")), "番茄")
        assertEquals("番茄", ranked.first().name)
    }

    @Test
    fun `dish beats ingredient on equal name match`() {
        val ranked = SearchQuery.rank(
            listOf(food("番茄", "INGREDIENT"), food("番茄", "DISH")),
            "番茄",
        )
        assertEquals("DISH", ranked.first().kind)
    }

    @Test
    fun `blank query produces nothing`() {
        assertEquals(emptyList<String>(), SearchQuery.terms("   "))
    }
}
