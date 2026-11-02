package com.orienteer.app.data.backup

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.google.gson.Gson
import com.orienteer.app.R
import com.orienteer.app.data.local.AppDatabase
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.local.dao.RouteDao
import com.orienteer.app.data.local.dao.RunSessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class DataBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val routeDao: RouteDao,
    private val runSessionDao: RunSessionDao,
    private val userPreferencesStore: UserPreferencesStore,
    private val gson: Gson
) {

    suspend fun buildBackup(): OrienteerBackupFile = withContext(Dispatchers.IO) {
        OrienteerBackupFile(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            routes = routeDao.getAllRoutes(),
            runSessions = runSessionDao.getAllSessions(),
            settings = userPreferencesStore.backupSnapshot()
        )
    }

    suspend fun writeBackupFile(): File = withContext(Dispatchers.IO) {
        val backup = buildBackup()
        val json = gson.toJson(backup)
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val file = File(docsDir, "OrienteerRun_backup_$dateStr.json")
        file.writeText(json, Charsets.UTF_8)
        file
    }

    fun shareBackupFile(file: File): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val subject = "OrienteerRun backup"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newUri(context.contentResolver, subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.backup_share_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    suspend fun parseBackupJson(json: String): OrienteerBackupFile = withContext(Dispatchers.Default) {
        val backup = runCatching {
            gson.fromJson(json, OrienteerBackupFile::class.java)
        }.getOrElse { throw BackupException("Could not read backup file", it) }
            ?: throw BackupException("Backup file is empty")
        if (backup.schemaVersion < 1 || backup.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw BackupException(
                "Unsupported backup version ${backup.schemaVersion} (app supports up to $BACKUP_SCHEMA_VERSION)"
            )
        }
        backup
    }

    suspend fun importFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw BackupException("Could not open backup file")
        replaceAllWithBackup(parseBackupJson(json))
    }

    /**
     * Replace-all restore: clears local runs/routes, inserts backup rows, applies settings.
     */
    suspend fun replaceAllWithBackup(backup: OrienteerBackupFile) = withContext(Dispatchers.IO) {
        database.withTransaction {
            runSessionDao.deleteAllSessions()
            routeDao.deleteAllRoutes()
            if (backup.routes.isNotEmpty()) {
                routeDao.insertRoutes(backup.routes)
            }
            if (backup.runSessions.isNotEmpty()) {
                runSessionDao.insertSessions(backup.runSessions)
            }
        }
        userPreferencesStore.restoreFromBackup(backup.settings)
    }
}
