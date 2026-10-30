package com.daylyfood.app.di

import android.content.Context
import androidx.room.Room
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.MetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "daylyfood.db").build()

    @Provides
    fun provideMetaDao(database: AppDatabase): MetaDao = database.metaDao()
}
