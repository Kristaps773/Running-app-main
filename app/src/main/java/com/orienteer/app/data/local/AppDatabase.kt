package com.orienteer.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.orienteer.app.data.local.dao.RouteDao
import com.orienteer.app.data.local.dao.RunSessionDao
import com.orienteer.app.data.local.entity.RouteConverters
import com.orienteer.app.data.local.entity.RouteEntity
import com.orienteer.app.data.local.entity.RunSessionEntity

@Database(
    entities = [RouteEntity::class, RunSessionEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(RouteConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun runSessionDao(): RunSessionDao
}
