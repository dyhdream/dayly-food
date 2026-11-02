package com.daylyfood.app.data.db

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v4 only adds the seed_meta table; a broken statement would fail every upgraded install. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationV4Test {

    @Test
    fun `seed meta table is created and stores one row`() {
        val db = SQLiteDatabase.create(null)

        db.execSQL(AppDatabase.CREATE_SEED_META_SQL)
        // Running it twice must not throw: migrations can be retried after a crash.
        db.execSQL(AppDatabase.CREATE_SEED_META_SQL)
        db.execSQL("INSERT OR REPLACE INTO seed_meta (id, dataVersion, appliedAt) VALUES (0, 7, 123)")
        db.execSQL("INSERT OR REPLACE INTO seed_meta (id, dataVersion, appliedAt) VALUES (0, 8, 456)")

        db.rawQuery("SELECT dataVersion, appliedAt FROM seed_meta WHERE id = 0", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(8, cursor.getInt(0))
            assertEquals(456L, cursor.getLong(1))
        }
    }
}
