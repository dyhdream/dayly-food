package com.daylyfood.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daylyfood.app.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Guards the first-launch path: seed import must succeed and search must hit Chinese names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeedImportTest {

    private lateinit var database: AppDatabase
    private lateinit var importer: SeedImporter

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        importer = SeedImporter(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `imports the full seed dataset`() = runTest {
        importer.ensureSeeded()
        assertEquals(31, database.foodDao().count())
    }

    @Test
    fun `import is idempotent`() = runTest {
        importer.ensureSeeded()
        importer.ensureSeeded()
        assertEquals(31, database.foodDao().count())
    }

    @Test
    fun `search finds dish by exact chinese name`() = runTest {
        importer.ensureSeeded()
        val hits = database.foodDao().search("米饭")
        assertTrue("expected a hit for 米饭, got $hits", hits.isNotEmpty())
        assertTrue(hits.any { it.name == "米饭(熟)" })
    }

    @Test
    fun `search resolves alias to canonical food`() = runTest {
        importer.ensureSeeded()
        val hits = database.foodDao().search("西红柿")
        assertTrue("alias 西红柿 should resolve to 番茄, got $hits", hits.any { it.name == "番茄" })
    }

    @Test
    fun `servings are attached to a food`() = runTest {
        importer.ensureSeeded()
        val servings = database.foodDao().servings("F101")
        assertTrue("米饭 should carry servings", servings.isNotEmpty())
        assertEquals(1, servings.count { it.isDefault })
    }
}
