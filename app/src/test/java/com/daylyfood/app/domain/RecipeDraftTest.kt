package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Remote model output is untrusted: only ingredients we know, only sane amounts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecipeDraftTest {

    private val catalog = listOf(
        RecipeDraftParser.CatalogItem("F001", "青椒"),
        RecipeDraftParser.CatalogItem("F002", "猪里脊"),
        RecipeDraftParser.CatalogItem("F003", "食用油(大豆油)"),
    )
    private val methods = CookingMethods.ALL.map { it.id }.toSet()

    private fun reply(
        name: String = "辣椒炒肉",
        ingredients: String = """[{"id":"F001","grams":150},{"id":"F002","grams":120}]""",
        method: String = "C04",
        oil: String = ""","oilGrams":8""",
    ) = """{"name":"$name","method":"$method","ingredients":$ingredients$oil}"""

    @Test
    fun `valid draft parses`() {
        val draft = RecipeDraftParser.parse(reply(), catalog, methods).getOrThrow()

        assertEquals("辣椒炒肉", draft.name)
        assertEquals(2, draft.lines.size)
        assertEquals(8.0, draft.oilGrams, 0.001)
    }

    @Test
    fun `missing oil falls back to the method default`() {
        val draft = RecipeDraftParser.parse(
            """{"name":"炒里脊","method":"C01","ingredients":[{"id":"F002","grams":200}]}""",
            catalog,
            methods,
        ).getOrThrow()

        assertEquals(0.0, draft.oilGrams, 0.001)
    }

    @Test
    fun `ingredient outside the catalog is rejected`() {
        val result = RecipeDraftParser.parse(
            reply(ingredients = """[{"id":"F999","grams":100}]"""),
            catalog,
            methods,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("不在库里"))
    }

    @Test
    fun `absurd gram amounts are rejected`() {
        val result = RecipeDraftParser.parse(
            reply(ingredients = """[{"id":"F001","grams":900}]"""),
            catalog,
            methods,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `too total heavy dish is rejected`() {
        val heavy = """[{"id":"F001","grams":400},{"id":"F002","grams":400},{"id":"F003","grams":400},{"id":"F001","grams":100}]"""
        assertTrue(RecipeDraftParser.parse(reply(ingredients = heavy), catalog, methods).isFailure)
    }

    @Test
    fun `unknown cooking method is rejected`() {
        assertTrue(RecipeDraftParser.parse(reply(method = "C99"), catalog, methods).isFailure)
    }

    @Test
    fun `duplicate ingredient is rejected`() {
        val dup = """[{"id":"F001","grams":100},{"id":"F001","grams":50}]"""
        assertTrue(RecipeDraftParser.parse(reply(ingredients = dup), catalog, methods).isFailure)
    }

    @Test
    fun `json wrapped in a code fence still parses`() {
        val fenced = "```json\n" + reply() + "\n```"

        assertEquals("辣椒炒肉", RecipeDraftParser.parse(fenced, catalog, methods).getOrThrow().name)
    }

    @Test
    fun `composition mostly missing from the library is rejected`() {
        val thin = """{"name":"辣椒炒肉","method":"C04","ingredients":[{"id":"F001","grams":150}],"missing":["木耳","笋","豆豉"]}"""

        assertTrue(RecipeDraftParser.parse(thin, catalog, methods).isFailure)
    }

    @Test
    fun `dish whose name shares nothing with its ingredients is rejected`() {
        val wrong = """{"name":"蛋炒饭","method":"C04","ingredients":[{"id":"F002","grams":150}]}"""

        assertTrue(RecipeDraftParser.parse(wrong, catalog, methods).isFailure)
    }

    @Test
    fun `excess oil is rejected`() {
        assertTrue(RecipeDraftParser.parse(reply(oil = ""","oilGrams":90"""), catalog, methods).isFailure)
    }
}
