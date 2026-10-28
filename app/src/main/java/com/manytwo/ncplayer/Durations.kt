package com.manytwo.ncplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Track lengths, cached in files/durations.json. Nextcloud doesn't report them, so they come from
 * the player (tracks already played) or from reading the downloaded copy's header.
 */
object Durations {
    private var file: File? = null
    private val known = HashMap<String, Long>()
    private val pending = LinkedHashSet<String>()
    private var working = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _changed = MutableStateFlow(0)
    /** Ticks when new lengths are known, so the list can refresh. */
    val changed: StateFlow<Int> = _changed

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "durations.json")
        file = f
        runCatching { JSONObject(f.readText()).let { o -> o.keys().forEach { known[it] = o.getLong(it) } } }
    }

    /** Length in ms, or null if not known yet (then it is looked up in the background if the file is on the phone). */
    @Synchronized
    fun get(id: String): Long? {
        known[id]?.let { return it }
        History.get(id)?.dur?.takeIf { it > 0 }?.let { known[id] = it; return it }
        if (pending.add(id) && !working) { working = true; scope.launch { work() } }
        return null
    }

    private fun work() {
        var found = 0
        while (true) {
            val id = synchronized(this) { pending.firstOrNull()?.also { pending.remove(it) } }
            if (id == null) break
            val f = Downloads.localFile(id) ?: continue
            val ms = runCatching {
                MediaMetadataRetriever().run {
                    try { setDataSource(f.path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() }
                    finally { release() }
                }
            }.getOrNull() ?: continue
            synchronized(this) { known[id] = ms }
            if (++found % 10 == 0) { save(); _changed.value++ }
        }
        synchronized(this) { working = false }
        if (found % 10 != 0) { save(); _changed.value++ }
    }

    @Synchronized
    private fun save() {
        val o = JSONObject()
        for ((k, v) in known) o.put(k, v)
        runCatching { file?.writeText(o.toString()) }
    }
}
