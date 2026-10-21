package com.xlabsx27.naveeoffline.export

import android.content.Context
import android.net.Uri
import com.xlabsx27.naveeoffline.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies the trip database to and from a user-chosen file. Nothing leaves the device on its own. */
object Backup {
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray()

    suspend fun export(context: Context, db: AppDatabase, target: Uri) = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main file so a single file is a complete copy.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val file = context.getDatabasePath(AppDatabase.NAME)
        context.contentResolver.openOutputStream(target, "wt").use { out ->
            requireNotNull(out) { "Cannot write backup" }
            file.inputStream().use { it.copyTo(out) }
        }
    }

    /**
     * Replaces the database with [source]. The caller must restart the process afterwards, since the
     * open Room instance still points at the old file.
     */
    suspend fun import(context: Context, db: AppDatabase, source: Uri) = withContext(Dispatchers.IO) {
        val file = context.getDatabasePath(AppDatabase.NAME)
        val tmp = File(file.parentFile, "restore.tmp")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot read backup" }
            tmp.outputStream().use { input.copyTo(it) }
        }
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = tmp.inputStream().use { it.read(header) }
        if (read != header.size || !header.contentEquals(SQLITE_MAGIC)) {
            tmp.delete()
            error("Not a Navee Offline backup")
        }
        db.close()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        check(tmp.renameTo(file)) { "Could not restore backup" }
    }
}
