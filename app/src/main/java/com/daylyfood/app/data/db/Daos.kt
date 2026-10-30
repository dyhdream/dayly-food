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
        ORDER BY CASE kind WHEN 'DISH' THEN 0 WHEN 'INGREDIENT' THEN 1 ELSE 2 END, name
        LIMIT 40
        """
    )
    suspend fun search(q: String): List<FoodEntity>

    @Query("SELECT * FROM food_item ORDER BY CASE kind WHEN 'DISH' THEN 0 WHEN 'INGREDIENT' THEN 1 ELSE 2 END, name LIMIT 20")
    suspend fun recent(): List<FoodEntity>

    @Query("SELECT * FROM food_item WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

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
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ProfileEntity)
}
