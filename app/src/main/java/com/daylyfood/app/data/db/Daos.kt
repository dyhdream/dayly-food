package com.daylyfood.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    // 31 rows today; LIKE is plenty. Switch to FTS4 n-gram when the seed grows past ~2k.
    @Query(
        """
        SELECT * FROM food_item
        WHERE name LIKE '%' || :q || '%'
           OR id IN (SELECT foodId FROM food_alias WHERE alias LIKE '%' || :q || '%')
        ORDER BY CASE kind WHEN 'CUSTOM' THEN 0 WHEN 'DISH' THEN 1 WHEN 'INGREDIENT' THEN 2 ELSE 3 END, name
        LIMIT 40
        """
    )
    suspend fun search(q: String): List<FoodEntity>

    @Query("SELECT * FROM food_item ORDER BY CASE kind WHEN 'CUSTOM' THEN 0 WHEN 'DISH' THEN 1 WHEN 'INGREDIENT' THEN 2 ELSE 3 END, name LIMIT 20")
    suspend fun recent(): List<FoodEntity>

    @Query("SELECT * FROM food_item WHERE kind = 'CUSTOM' ORDER BY name")
    fun observeCustom(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food_item WHERE kind = 'DISH' ORDER BY name")
    fun observeDishes(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food_item WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    @Query("SELECT * FROM food_item WHERE kind = 'CUSTOM' ORDER BY name")
    suspend fun customFoods(): List<FoodEntity>

    @Query("SELECT * FROM food_serving WHERE foodId IN (SELECT id FROM food_item WHERE kind = 'CUSTOM')")
    suspend fun customServings(): List<ServingEntity>

    @Query("DELETE FROM food_item WHERE kind = 'CUSTOM'")
    suspend fun deleteCustomFoods()

    @Query("DELETE FROM food_serving WHERE foodId NOT IN (SELECT id FROM food_item)")
    suspend fun deleteOrphanServings()

    @Query("SELECT * FROM food_serving WHERE foodId = :foodId ORDER BY sort")
    suspend fun servings(foodId: String): List<ServingEntity>

    @Query("SELECT COUNT(*) FROM food_item")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(items: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServings(items: List<ServingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(items: List<AliasEntity>)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entry WHERE epochDay = :day ORDER BY id")
    fun observe(day: Long): Flow<List<DiaryEntryEntity>>

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun delete(id: Long)

    /** Rescales an entry in place; every RHS reads the old row, so the ratios stay intact. */
    @Query(
        """
        UPDATE diary_entry SET
            kcal = kcal * :newGrams / grams,
            protein = protein * :newGrams / grams,
            fat = fat * :newGrams / grams,
            carb = carb * :newGrams / grams,
            grams = :newGrams
        WHERE id = :id AND grams > 0 AND :newGrams > 0
        """
    )
    suspend fun updateGrams(id: Long, newGrams: Double)

    @Query("SELECT * FROM diary_entry WHERE epochDay = :day ORDER BY id")
    suspend fun list(day: Long): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entry ORDER BY epochDay, id")
    suspend fun all(): List<DiaryEntryEntity>

    @Query("DELETE FROM diary_entry")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(entries: List<DiaryEntryEntity>)

    /** Per-100g values are rebuilt from the stored snapshots, so quick-add needs no food lookup. */
    @Query(
        """
        SELECT nameSnapshot AS name,
               MAX(grams) AS grams,
               MAX(kcal * 100.0 / grams) AS kcal100,
               MAX(protein * 100.0 / grams) AS protein100,
               MAX(fat * 100.0 / grams) AS fat100,
               MAX(carb * 100.0 / grams) AS carb100,
               MAX(foodId) AS foodId,
               MAX(createdAt) AS createdAt
        FROM diary_entry
        WHERE grams > 0
        GROUP BY nameSnapshot
        ORDER BY createdAt DESC
        LIMIT 8
        """
    )
    fun recent(): Flow<List<RecentFood>>
}

data class RecentFood(
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carb100: Double,
    val foodId: String?,
    val createdAt: Long,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ProfileEntity)
}
