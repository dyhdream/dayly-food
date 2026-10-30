package com.daylyfood.app.data

import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ProfileEntity
import com.daylyfood.app.data.db.ServingEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON backup of everything the user owns: profile, self-made foods and the diary.
 * Built-in foods are shipped in the APK, so they are never exported.
 */
object Backup {

    const val SCHEMA = 1

    data class Payload(
        val profile: ProfileEntity?,
        val customFoods: List<FoodEntity>,
        val servings: List<ServingEntity>,
        val diary: List<DiaryEntryEntity>,
    ) {
        val isEmpty: Boolean get() = customFoods.isEmpty() && diary.isEmpty() && profile == null
    }

    fun encode(payload: Payload, appVersion: String, exportedAt: String): String {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("app", "dayly-food")
        root.put("version", appVersion)
        root.put("exportedAt", exportedAt)

        payload.profile?.let { profile ->
            root.put(
                "profile",
                JSONObject()
                    .put("sex", profile.sex)
                    .put("age", profile.age)
                    .put("heightCm", profile.heightCm)
                    .put("weightKg", profile.weightKg)
                    .put("activity", profile.activity)
                    .put("goal", profile.goal),
            )
        }

        root.put("customFoods", JSONArray().apply {
            payload.customFoods.forEach { food ->
                put(
                    JSONObject()
                        .put("id", food.id)
                        .put("name", food.name)
                        .put("kind", food.kind)
                        .put("basis", food.basis)
                        .put("category", food.category)
                        .put("brand", food.brand)
                        .put("baseUnit", food.baseUnit)
                        .put("kcal100", food.kcal100)
                        .put("protein100", food.protein100)
                        .put("fat100", food.fat100)
                        .put("carb100", food.carb100)
                        .put("fiber100", food.fiber100)
                        .put("sodium100", food.sodium100)
                        .put("source", food.source)
                        .put("sourceUrl", food.sourceUrl)
                        .put("confidence", food.confidence)
                        .put("verified", food.verified)
                        .put("notes", food.notes),
                )
            }
        })

        root.put("servings", JSONArray().apply {
            payload.servings.forEach { serving ->
                put(
                    JSONObject()
                        .put("id", serving.id)
                        .put("foodId", serving.foodId)
                        .put("label", serving.label)
                        .put("grams", serving.grams)
                        .put("isDefault", serving.isDefault)
                        .put("sort", serving.sort),
                )
            }
        })

        root.put("diary", JSONArray().apply {
            payload.diary.forEach { entry ->
                put(
                    JSONObject()
                        .put("epochDay", entry.epochDay)
                        .put("meal", entry.meal)
                        .put("foodId", entry.foodId)
                        .put("nameSnapshot", entry.nameSnapshot)
                        .put("grams", entry.grams)
                        .put("kcal", entry.kcal)
                        .put("protein", entry.protein)
                        .put("fat", entry.fat)
                        .put("carb", entry.carb)
                        .put("createdAt", entry.createdAt),
                )
            }
        })

        return root.toString(2)
    }

    /** Throws on a newer schema so a future backup is never silently truncated. */
    fun decode(text: String): Payload {
        val root = JSONObject(text)
        val schema = root.optInt("schema", 0)
        require(schema in 1..SCHEMA) { "备份格式版本 $schema 不受支持（当前 $SCHEMA）" }

        val profile = root.optJSONObject("profile")?.let {
            ProfileEntity(
                sex = it.optString("sex", "M"),
                age = it.optInt("age", 30),
                heightCm = it.optDouble("heightCm", 175.0),
                weightKg = it.optDouble("weightKg", 70.0),
                activity = it.optDouble("activity", 1.375),
                goal = it.optString("goal", "MAINTAIN"),
            )
        }

        val customFoods = root.optJSONArray("customFoods").mapObjects {
            FoodEntity(
                id = it.getString("id"),
                name = it.getString("name"),
                kind = it.optString("kind", "CUSTOM"),
                basis = it.optString("basis", "AS_SOLD"),
                category = it.optString("category", "自定义"),
                brand = it.optStringOrNull("brand"),
                baseUnit = it.optString("baseUnit", "g"),
                kcal100 = it.optDouble("kcal100", 0.0),
                protein100 = it.optDouble("protein100", 0.0),
                fat100 = it.optDouble("fat100", 0.0),
                carb100 = it.optDouble("carb100", 0.0),
                fiber100 = it.optDoubleOrNull("fiber100"),
                sodium100 = it.optDoubleOrNull("sodium100"),
                source = it.optString("source", "local"),
                sourceUrl = it.optStringOrNull("sourceUrl"),
                confidence = it.optString("confidence", "low"),
                verified = it.optBoolean("verified", false),
                notes = it.optStringOrNull("notes"),
            )
        }

        val servings = root.optJSONArray("servings").mapObjects {
            ServingEntity(
                id = it.getString("id"),
                foodId = it.getString("foodId"),
                label = it.optString("label", "一份"),
                grams = it.optDouble("grams", 100.0),
                isDefault = it.optBoolean("isDefault", true),
                sort = it.optInt("sort", 1),
            )
        }

        val diary = root.optJSONArray("diary").mapObjects {
            DiaryEntryEntity(
                // ids are regenerated on import so a restore never collides with existing rows
                id = 0,
                epochDay = it.getLong("epochDay"),
                meal = it.getString("meal"),
                foodId = it.optStringOrNull("foodId"),
                nameSnapshot = it.getString("nameSnapshot"),
                grams = it.optDouble("grams", 0.0),
                kcal = it.optDouble("kcal", 0.0),
                protein = it.optDouble("protein", 0.0),
                fat = it.optDouble("fat", 0.0),
                carb = it.optDouble("carb", 0.0),
                createdAt = it.optLong("createdAt", 0L),
            )
        }

        return Payload(profile, customFoods, servings, diary)
    }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { transform(getJSONObject(it)) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key)
}
