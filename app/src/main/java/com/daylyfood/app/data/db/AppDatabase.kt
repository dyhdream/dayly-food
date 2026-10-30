package com.daylyfood.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntity::class,
        ServingEntity::class,
        AliasEntity::class,
        DiaryEntryEntity::class,
        ProfileEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        /** v2 adds the unit so drinks can be shown in ml; existing rows were all grams. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entry ADD COLUMN unit TEXT NOT NULL DEFAULT 'g'")
            }
        }

        /** v3 fixes rows recorded before drinks knew about ml: the food says ml, the row said g. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(BACKFILL_ML_UNIT_SQL)
            }
        }

        /** Shared with the test so the SQL that ships is the SQL that gets checked. */
        val BACKFILL_ML_UNIT_SQL = """
            UPDATE diary_entry SET unit = 'ml'
            WHERE foodId IN (SELECT id FROM food_item WHERE baseUnit = 'ml')
        """.trimIndent()
    }
}
