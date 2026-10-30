package com.daylyfood.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_item")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val basis: String,
    val category: String,
    val brand: String?,
    val baseUnit: String,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carb100: Double,
    val fiber100: Double?,
    val sodium100: Double?,
    val source: String,
    val sourceUrl: String?,
    val confidence: String,
    val verified: Boolean,
    val notes: String?,
)

@Entity(tableName = "food_serving")
data class ServingEntity(
    @PrimaryKey val id: String,
    val foodId: String,
    val label: String,
    val grams: Double,
    val isDefault: Boolean,
    val sort: Int,
)

@Entity(tableName = "food_alias")
data class AliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: String,
    val alias: String,
    val kind: String,
)

@Entity(tableName = "diary_entry")
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: String,
    val foodId: String?,
    val nameSnapshot: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val createdAt: Long,
    /** "g" or "ml": drinks are measured in millilitres, and the diary has to say so. */
    // defaultValue must match MIGRATION_1_2's ALTER, or Room rejects the migrated schema.
    @ColumnInfo(defaultValue = "g") val unit: String = "g",
)

@Entity(tableName = "user_profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val sex: String,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: Double,
    val goal: String,
)
