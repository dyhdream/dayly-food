package com.daylyfood.app.data.db

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The v3 migration rewrites units on existing diary rows. A typo here silently corrupts old
 * records, and no device session can prove it, so the shipped SQL is executed against a
 * throwaway database instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationBackfillTest {

    private fun freshDb(): SQLiteDatabase {
        val db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE food_item (id TEXT PRIMARY KEY, baseUnit TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE diary_entry (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, foodId TEXT, unit TEXT NOT NULL DEFAULT 'g')",
        )
        db.execSQL("INSERT INTO food_item VALUES ('F1', 'ml'), ('F2', 'g')")
        return db
    }

    private fun units(db: SQLiteDatabase): Map<String, String> {
        val result = mutableMapOf<String, String>()
        db.rawQuery("SELECT foodId, unit FROM diary_entry ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
        }
        return result
    }

    @Test
    fun `drink rows become ml and solid rows stay grams`() {
        val db = freshDb()
        db.execSQL("INSERT INTO diary_entry (foodId) VALUES ('F1'), ('F2')")

        db.execSQL(AppDatabase.BACKFILL_ML_UNIT_SQL)

        val units = units(db)
        assertEquals("ml", units["F1"])
        assertEquals("g", units["F2"])
    }

    @Test
    fun `rows without a food id are left alone`() {
        val db = freshDb()
        db.execSQL("INSERT INTO diary_entry (foodId) VALUES (NULL)")

        db.execSQL(AppDatabase.BACKFILL_ML_UNIT_SQL)

        assertEquals("g", units(db).values.single())
    }
}
