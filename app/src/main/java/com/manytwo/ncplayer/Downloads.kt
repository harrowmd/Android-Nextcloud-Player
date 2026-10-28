package com.manytwo.ncplayer

import android.content.Context
import android.net.ConnectivityManager
import android.os.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

/**
 * Keeps whole folders on the phone. Files go to <volume>/Android/data/com.manytwo.ncplayer/files/music/<Nextcloud path>,
 * so a track's id (its Nextcloud path) is also its local path. Folders are fetched one at a time, file by file.
 */
object Downloads {
    private const val KEEP_FREE = 500L * 1024 * 1024

    private lateinit var app: Context
    private lateinit var prefs: Prefs
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedHashSet<String>()
    private var job: Job? = null

    private val _status = MutableStateFlow("")
    /** One line for the screen, empty when idle. */
    val status: StateFlow<String> = _status

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = Prefs(app)
    }

    /** App storage on each volume that is mounted now (phone first, then SD card). */
    fun volumes(): List<File> = app.getExternalFilesDirs(null).filterNotNull()

    fun volumeName(dir: File): String =
        app.getSystemService(StorageManager::class.java).getStorageVolume(dir)?.getDescription(app) ?: dir.path

    fun currentVolume(): File {
        val v = volumes()
        val i = prefs.storageIndex
        return if (i in v.indices) v[i] else v.last()
    }

    fun musicDir(volume: File = currentVolume()) = File(volume, "music")

    /** The downloaded copy of a track, on whichever volume it is, or null. */
    fun localFile(id: String): File? =
        if (isLocal(id)) File(id.removePrefix(LOCAL)).takeIf { it.isFile } else volumes().map { File(musicDir(it), id) }.firstOrNull { it.isFile }

    fun localFolder(path: String): List<File> =
        volumes().map { File(musicDir(it), path) }.filter { it.isDirectory }

    /** Queue a folder for download (if automatic downloads are on, or [force]). */
    fun requestFolder(path: String, force: Boolean = false) {
        if (isLocal(path)) return
        if (!force && !prefs.autoDownload) return
        synchronized(queue) { queue += path }
        if (job?.isActive != true) job = scope.launch { run() }
    }

    private fun next(): String? = synchronized(queue) { queue.firstOrNull()?.also { queue.remove(it) } }

    private suspend fun run() {
        while (true) {
            val path = next() ?: break
            if (prefs.wifiOnly && app.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered) {
                _status.value = "Download waiting for Wi-Fi (menu → Offline storage)"
                AppLog.log("download skipped (metered network): $path")
                return
            }
            try {
                fetchFolder(path)
            } catch (e: Exception) {
                AppLog.log("download failed $path: $e")
                _status.value = "Download stopped: ${e.message ?: e.javaClass.simpleName}"
                return
            }
        }
        _status.value = ""
    }

    private suspend fun fetchFolder(path: String) {
        val dav = Dav(prefs.server, prefs.user, prefs.pass)
        val files = dav.list(path).filter { it.isAudio }
        val dir = File(musicDir(), path).apply { mkdirs() }
        val label = prettyFolder(path.substringAfterLast('/').ifEmpty { "Nextcloud" })
        var got = 0
        for ((i, e) in files.withIndex()) {
            if (!scope.isActive) return
            val id = if (path.isEmpty()) e.name else "$path/${e.name}"
            val have = localFile(id)
            if (have != null && (e.size <= 0 || have.length() == e.size)) continue
            if (dir.usableSpace < e.size + KEEP_FREE) throw java.io.IOException("storage full")
            _status.value = "Saving to phone: $label — ${i + 1} of ${files.size}"
            val part = File(dir, e.name + ".part")
            dav.client.newCall(Request.Builder().url(dav.url(id)).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("server replied ${resp.code}")
                part.outputStream().use { out -> resp.body!!.byteStream().copyTo(out, 64 * 1024) }
            }
            if (e.size > 0 && part.length() != e.size) { part.delete(); throw java.io.IOException("incomplete download") }
            part.renameTo(File(dir, e.name))
            got++
        }
        AppLog.log("download done $path: $got new of ${files.size}")
    }

    /** Folders that hold downloaded files, with total size, on all volumes. */
    fun folders(): List<Pair<String, Long>> {
        val out = HashMap<String, Long>()
        for (v in volumes()) {
            val root = musicDir(v)
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.parentFile!!.relativeTo(root).path
                out[rel] = (out[rel] ?: 0) + f.length()
            }
        }
        return out.toList()
    }

    /** Delete a folder's downloaded files (not subfolders) on all volumes. */
    fun deleteFolder(path: String) {
        for (d in localFolder(path)) {
            d.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            var p: File? = d
            // Remove now-empty parent folders up to the music root.
            while (p != null && p.list()?.isEmpty() == true && p.name != "music") { p.delete(); p = p.parentFile }
        }
    }
}
