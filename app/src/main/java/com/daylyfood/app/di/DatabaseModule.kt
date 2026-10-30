package com.daylyfood.app.di

import android.content.Context
import androidx.room.Room
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.DiaryDao
import com.daylyfood.app.data.db.FoodDao
import com.daylyfood.app.data.db.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "daylyfood_v1.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideFoodDao(database: AppDatabase): FoodDao = database.foodDao()

    @Provides
    fun provideDiaryDao(database: AppDatabase): DiaryDao = database.diaryDao()

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()
}
