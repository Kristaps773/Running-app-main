package com.orienteer.app.di

import android.content.Context
import androidx.room.Room
import com.orienteer.app.data.local.AppDatabase
import com.orienteer.app.data.local.dao.RouteDao
import com.orienteer.app.data.local.dao.RunSessionDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "orienteer_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideRunSessionDao(db: AppDatabase): RunSessionDao = db.runSessionDao()
}
