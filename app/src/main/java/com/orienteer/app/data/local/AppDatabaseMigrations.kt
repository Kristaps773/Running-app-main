package com.orienteer.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations. Schema v3 is exported under app/schemas.
 *
 * [com.orienteer.app.di.DatabaseModule] no longer uses
 * fallbackToDestructiveMigration — bump [AppDatabase] version only with a new
 * Migration here so upgrades do not wipe run history.
 */
object AppDatabaseMigrations {

    /**
     * v1 → v2: walking-path distance on routes (defaults to 0 when missing).
     * Safe if column already exists? No — Room only runs this when user DB version is 1.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "routes", "actualPathDistanceM")) {
                db.execSQL(
                    "ALTER TABLE routes ADD COLUMN actualPathDistanceM REAL NOT NULL DEFAULT 0.0"
                )
            }
        }
    }

    /**
     * v2 → v3: GPS breadcrumbs and checkpoint timing JSON on run sessions.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "run_sessions", "trackedPointsJson")) {
                db.execSQL(
                    "ALTER TABLE run_sessions ADD COLUMN trackedPointsJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
            if (!columnExists(db, "run_sessions", "trackedPointTimestampsJson")) {
                db.execSQL(
                    "ALTER TABLE run_sessions ADD COLUMN trackedPointTimestampsJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
            if (!columnExists(db, "run_sessions", "checkpointReachedAtJson")) {
                db.execSQL(
                    "ALTER TABLE run_sessions ADD COLUMN checkpointReachedAtJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }
    }

    /** Direct path for installs that never hit intermediate v2. */
    val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2.migrate(db)
            MIGRATION_2_3.migrate(db)
        }
    }

    /**
     * v3 → v4: skipped (unreachable) checkpoint ids on run sessions.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "run_sessions", "skippedCheckpointIdsJson")) {
                db.execSQL(
                    "ALTER TABLE run_sessions ADD COLUMN skippedCheckpointIdsJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }
    }

    val MIGRATION_2_4 = object : Migration(2, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3.migrate(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    val MIGRATION_1_4 = object : Migration(1, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_3.migrate(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_1_3,
        MIGRATION_3_4,
        MIGRATION_2_4,
        MIGRATION_1_4
    )

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex < 0) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }
}
