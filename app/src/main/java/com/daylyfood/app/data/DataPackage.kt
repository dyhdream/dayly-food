package com.daylyfood.app.data

import com.daylyfood.app.data.db.AliasEntity
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * A downloadable data package: the same three seed tables as JSON, so the food library can be
 * extended without shipping a new APK.
 *
 * Everything is validated before it touches the database — a package that fails any check is
 * dropped whole, because a half-applied package leaves the library inconsistent.
 */
data class DataPackage(
    val dataVersion: Int,
    val foods: List<FoodEntity>,
    val servings: List<ServingEntity>,
    val aliases: List<AliasEntity>,
) {
    val isEmpty: Boolean get() = foods.isEmpty()

    companion object {
        const val MAX_FOODS = 20_000
        const val MAX_SERVINGS = 40_000
        const val MAX_ALIASES = 40_000
        private val ID_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]{0,19}$")
        private val KINDS = setOf("INGREDIENT", "DISH", "PACKAGED", "CUSTOM")
        private val BASES = setOf("RAW", "COOKED", "AS_SOLD")
        private val UNITS = setOf("g", "ml")

        /** Parses and validates; returns null with a reason when the payload is unusable. */
        fun parse(json: String): Result<DataPackage> = runCatching {
            val root = JSONObject(json)
            val version = root.optInt("dataVersion", -1)
            require(version > 0) { "数据包缺少版本号" }

            val foods = readFoods(root.optJSONArray("foods"))
            val servings = readServings(root.optJSONArray("servings"), foods.map { it.id }.toSet())
            val aliases = readAliases(root.optJSONArray("aliases"), foods.map { it.id }.toSet())

            require(foods.isNotEmpty()) { "数据包里没有食物" }
            DataPackage(version, foods, servings, aliases)
        }

        private fun readFoods(array: JSONArray?): List<FoodEntity> {
            if (array == null) return emptyList()
            require(array.length() <= MAX_FOODS) { "食物条数异常：${array.length()}" }
            return (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                val id = o.getString("id")
                require(ID_PATTERN.matches(id)) { "食物 id 不合法：$id" }
                val name = o.getString("name").trim()
                require(name.isNotEmpty() && name.length <= 40) { "食物名不合法：$id" }
                val kind = o.getString("kind")
                require(kind in KINDS) { "$name 的类型不认识：$kind" }
                val basis = o.getString("basis")
                require(basis in BASES) { "$name 的基准不认识：$basis" }
                val unit = o.optString("baseUnit", "g")
                require(unit in UNITS) { "$name 的单位不认识：$unit" }

                val kcal = o.getDouble("kcal100")
                require(kcal in 0.0..900.0) { "$name 的热量不合理：$kcal" }
                val protein = o.getDouble("protein100")
                val fat = o.getDouble("fat100")
                val carb = o.getDouble("carb100")
                require(protein in 0.0..100.0) { "$name 的蛋白不合理：$protein" }
                require(fat in 0.0..100.0) { "$name 的脂肪不合理：$fat" }
                require(carb in 0.0..100.0) { "$name 的碳水不合理：$carb" }

                FoodEntity(
                    id = id,
                    name = name,
                    kind = kind,
                    basis = basis,
                    category = o.optString("category").ifBlank { "其他" },
                    brand = o.optString("brand").ifBlank { null },
                    baseUnit = unit,
                    kcal100 = kcal,
                    protein100 = protein,
                    fat100 = fat,
                    carb100 = carb,
                    fiber100 = o.optDouble("fiber100", Double.NaN).takeIf { !it.isNaN() },
                    sodium100 = o.optDouble("sodium100", Double.NaN).takeIf { !it.isNaN() },
                    source = o.optString("source").ifBlank { "estimate" },
                    sourceUrl = o.optString("sourceUrl").ifBlank { null },
                    confidence = o.optString("confidence").ifBlank { "medium" },
                    verified = o.optBoolean("verified", false),
                    notes = o.optString("notes").ifBlank { null },
                )
            }
        }

        private fun readServings(array: JSONArray?, foodIds: Set<String>): List<ServingEntity> {
            if (array == null) return emptyList()
            require(array.length() <= MAX_SERVINGS) { "份量条数异常：${array.length()}" }
            return (0 until array.length()).mapNotNull { index ->
                val o = array.getJSONObject(index)
                val foodId = o.getString("foodId")
                // A serving for a food we are not shipping would be dead weight; skip it.
                if (foodId !in foodIds) return@mapNotNull null
                ServingEntity(
                    id = o.getString("id"),
                    foodId = foodId,
                    label = o.getString("label"),
                    grams = o.getDouble("grams").also {
                        require(it > 0.0 && it <= 5_000.0) { "份量克数不合理：$foodId $it" }
                    },
                    isDefault = o.optBoolean("isDefault", false),
                    sort = o.optInt("sort", 0),
                )
            }
        }

        private fun readAliases(array: JSONArray?, foodIds: Set<String>): List<AliasEntity> {
            if (array == null) return emptyList()
            require(array.length() <= MAX_ALIASES) { "别名条数异常：${array.length()}" }
            return (0 until array.length()).mapNotNull { index ->
                val o = array.getJSONObject(index)
                val foodId = o.getString("foodId")
                if (foodId !in foodIds) return@mapNotNull null
                val alias = o.getString("alias").trim()
                if (alias.isEmpty()) return@mapNotNull null
                AliasEntity(foodId = foodId, alias = alias, kind = o.optString("kind").ifBlank { "COMMON" })
            }
        }
    }
}
