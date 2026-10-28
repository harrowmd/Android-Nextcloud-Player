package com.manytwo.ncplayer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/** Paths of files on the phone start with this, so they never clash with Nextcloud paths in history or stats. */
const val LOCAL = "local:"

fun isLocal(p: String) = p.startsWith(LOCAL)

/** Browses the phone's own storage (phone memory and SD card) with plain file access. */
object LocalStorage {
    /** (name, absolute path) of each mounted volume, e.g. ("Internal shared storage", "/storage/emulated/0"). */
    fun roots(context: Context): List<Pair<String, String>> {
        val sm = context.getSystemService(StorageManager::class.java)
        val vols = if (Build.VERSION.SDK_INT >= 30) sm.storageVolumes.mapNotNull { v ->
            v.directory?.takeIf { v.state == Environment.MEDIA_MOUNTED }?.let { v.getDescription(context) to it.path }
        } else emptyList()
        return vols.ifEmpty { listOf("Phone storage" to Environment.getExternalStorageDirectory().path) }
    }

    /** Folder contents, or null if it can't be read. Hidden (dot) files are left out. */
    fun list(path: String): List<Entry>? =
        File(path.removePrefix(LOCAL)).listFiles()?.filter { !it.name.startsWith(".") }?.map {
            Entry(it.name, it.isDirectory, if (it.isFile) it.length() else 0, it.lastModified(), "")
        }

    /** Files and folders below [path] whose names match [filter]; stops after [limit] results or 20,000 entries looked at. */
    fun search(path: String, filter: String, limit: Int = 200): List<Pair<String, Entry>> {
        val out = ArrayList<Pair<String, Entry>>()
        var seen = 0
        File(path.removePrefix(LOCAL)).walkTopDown().maxDepth(8)
            .onEnter { !it.name.startsWith(".") }
            .drop(1)
            .forEach { f ->
                if (out.size >= limit || ++seen > 20_000) return out
                if (!f.name.startsWith(".") && matches(f.name, filter))
                    out += (LOCAL + f.path) to Entry(f.name, f.isDirectory, if (f.isFile) f.length() else 0, f.lastModified(), "")
            }
        return out
    }
}
