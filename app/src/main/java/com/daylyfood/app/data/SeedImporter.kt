package com.daylyfood.app.data

import android.content.Context
import com.daylyfood.app.data.db.AliasEntity
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports the seed CSVs from assets on first launch.
 *
 * The CSVs come from the validated seed dataset, so parsing stays simple: no field
 * contains a comma or a quote. A malformed row is skipped instead of crashing first launch.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {

    suspend fun ensureSeeded() {
        val dao = database.foodDao()
        if (dao.count() > 0) return

        dao.insertFoods(readRows("seed/foods.csv", ::food))
        dao.insertServings(readRows("seed/servings.csv", ::serving))
        dao.insertAliases(readRows("seed/aliases.csv", ::alias))
    }

    private fun <T> readRows(path: String, mapper: (List<String>) -> T?): List<T> {
        val rows = mutableListOf<T>()
        context.assets.open(path).bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                mapper(line.split(','))?.let { rows.add(it) }
            }
        }
        return rows
    }

    private fun food(c: List<String>): FoodEntity? {
        if (c.size < 18) return null
        return FoodEntity(
            id = c[0],
            name = c[1],
            kind = c[2],
            basis = c[3],
            category = c[4],
            brand = c[5].ifBlank { null },
            baseUnit = c[6],
            kcal100 = c[7].toDoubleOrNull() ?: return null,
            protein100 = c[8].toDoubleOrNull() ?: return null,
            fat100 = c[9].toDoubleOrNull() ?: return null,
            carb100 = c[10].toDoubleOrNull() ?: return null,
            fiber100 = c[11].toDoubleOrNull(),
            sodium100 = c[12].toDoubleOrNull(),
            source = c[13],
            sourceUrl = c[14].ifBlank { null },
            confidence = c[15],
            verified = c[16] == "1",
            notes = c[17].ifBlank { null },
        )
    }

    private fun serving(c: List<String>): ServingEntity? {
        if (c.size < 6) return null
        return ServingEntity(
            id = c[0],
            foodId = c[1],
            label = c[2],
            grams = c[3].toDoubleOrNull() ?: return null,
            isDefault = c[4] == "1",
            sort = c[5].toIntOrNull() ?: 0,
        )
    }

    private fun alias(c: List<String>): AliasEntity? {
        if (c.size < 3) return null
        return AliasEntity(foodId = c[0], alias = c[1], kind = c[2])
    }
}
