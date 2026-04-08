package com.melonapp.android_nsw_parking_overlay.data.database

import android.content.Context
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryBackupManager(
    private val context: Context,
    private val historyDao: CarParkHistoryDao,
    private val gson: Gson = Gson()
) {

    suspend fun exportToTree(contentResolver: ContentResolver, treeUri: Uri): String {
        contentResolver.takePersistableReadWritePermission(treeUri)
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Unable to access selected folder")

        val fileName = buildFileName()
        val targetFile = directory.findFile(fileName)
            ?: directory.createFile("application/json", fileName)
            ?: throw IOException("Unable to create backup file")

        val payload = BackupPayload(records = historyDao.getAll())
        contentResolver.openOutputStream(targetFile.uri, "wt")?.bufferedWriter()?.use { writer ->
            gson.toJson(payload, writer)
        } ?: throw IOException("Unable to open backup file for writing")

        return targetFile.name ?: fileName
    }

    suspend fun importFromFile(contentResolver: ContentResolver, fileUri: Uri): Int {
        val payload = contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { reader ->
            val listType = object : TypeToken<BackupPayload>() {}.type
            gson.fromJson<BackupPayload>(reader, listType)
        } ?: throw IOException("Unable to open backup file")

        val records = payload.records
            .filter { it.carParkId.isNotBlank() && it.carParkName.isNotBlank() }
            .map { it.copy(id = 0) }
        historyDao.insertAll(records)
        return records.size
    }

    private fun buildFileName(): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        return "parking-history-$timestamp.json"
    }

    private fun ContentResolver.takePersistableReadWritePermission(treeUri: Uri): ContentResolver {
        runCatching {
            takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        return this
    }

    private data class BackupPayload(
        val records: List<CarParkHistoryRecord> = emptyList()
    )
}
