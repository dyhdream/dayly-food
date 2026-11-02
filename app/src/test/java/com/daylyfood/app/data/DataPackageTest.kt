package com.daylyfood.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The data package is remote input: it is parsed before it is trusted. Every check here is a
 * boundary that keeps a bad package from corrupting the food library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataPackageTest {

    private fun food(
        id: String = "F001",
        name: String = "米饭",
        kind: String = "INGREDIENT",
        kcal: Double = 116.0,
    ) = """
        {"id":"$id","name":"$name","kind":"$kind","basis":"RAW","category":"主食",
         "baseUnit":"g","kcal100":$kcal,"protein100":2.6,"fat100":0.3,"carb100":25.9,
         "source":"cn_fct6_ref","confidence":"high","verified":true}
    """.trimIndent()

    private fun packageJson(
        foods: String = food(),
        servings: String = """{"id":"S001","foodId":"F001","label":"一碗","grams":150,"isDefault":true,"sort":1}""",
        aliases: String = """{"foodId":"F001","alias":"白饭","kind":"COMMON"}""",
        version: Int = 3,
    ) = """{"dataVersion":$version,"foods":[$foods],"servings":[$servings],"aliases":[$aliases]}"""

    @Test
    fun `valid package parses`() {
        val pkg = DataPackage.parse(packageJson()).getOrThrow()

        assertEquals(3, pkg.dataVersion)
        assertEquals(1, pkg.foods.size)
        assertEquals("米饭", pkg.foods.single().name)
        assertEquals(150.0, pkg.servings.single().grams, 0.001)
        assertEquals("白饭", pkg.aliases.single().alias)
    }

    @Test
    fun `package without version is rejected`() {
        val result = DataPackage.parse("""{"foods":[${food()}]}""")

        assertTrue(result.isFailure)
    }

    @Test
    fun `impossible calories are rejected`() {
        val result = DataPackage.parse(packageJson(foods = food(kcal = 1200.0)))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("热量"))
    }

    @Test
    fun `unknown kind is rejected`() {
        val result = DataPackage.parse(packageJson(foods = food(kind = "MAGIC")))

        assertTrue(result.isFailure)
    }

    @Test
    fun `servings pointing at a food outside the package are dropped, not failed`() {
        val orphan = """{"id":"S002","foodId":"F999","label":"一份","grams":100,"isDefault":false,"sort":2}"""

        val pkg = DataPackage.parse(packageJson(servings = orphan)).getOrThrow()

        assertTrue(pkg.servings.isEmpty())
        assertTrue(pkg.foods.isNotEmpty())
    }

    @Test
    fun `empty food list is rejected`() {
        val result = DataPackage.parse("""{"dataVersion":2,"foods":[],"servings":[],"aliases":[]}""")

        assertTrue(result.isFailure)
    }

    @Test
    fun `malformed json is rejected instead of crashing`() {
        assertTrue(DataPackage.parse("{not json").isFailure)
    }
}
