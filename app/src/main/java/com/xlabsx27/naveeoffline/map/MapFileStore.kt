package com.xlabsx27.naveeoffline.map

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.mapsforge.map.reader.MapFile
import java.io.File

/**
 * Offline map files (Mapsforge `.map`, e.g. from download.mapsforge.org) that the user imported
 * through the system file picker. They are copied into app storage so they survive the source going away.
 */
class MapFileStore(private val context: Context) {
    private val dir = File(context.filesDir, "maps").apply { mkdirs() }

    private val _files = MutableStateFlow(scan())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private fun scan(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".map") }?.sortedBy { it.name }.orEmpty()

    /** Copies [uri] in and checks it is a readable map. Returns the error message on failure. */
    suspend fun import(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri)?.takeIf { it.endsWith(".map", ignoreCase = true) }
                ?: error("Pick a Mapsforge .map file")
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val tmp = File(dir, "$safe.part")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open file" }
                tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
            }
            try {
                MapFile(tmp).close()
            } catch (e: Exception) {
                tmp.delete()
                error("Not a valid Mapsforge map: ${e.message}")
            }
            val target = File(dir, safe)
            target.delete()
            check(tmp.renameTo(target)) { "Could not store map" }
            _files.value = scan()
            target
        }
    }

    fun delete(file: File) {
        if (file.parentFile == dir) file.delete()
        _files.value = scan()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
}
