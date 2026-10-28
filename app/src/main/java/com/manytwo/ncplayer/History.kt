package com.manytwo.ncplayer

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Where each track was left: position, length, when, and whether it was played to the end. */
data class Mark(val pos: Long, val dur: Long, val at: Long, val done: Boolean)

/** Per-track resume points, kept in files/history.json. Keys are paths relative to the Nextcloud root. */
object History {
    private const val MAX = 1000
    private var file: File? = null
    private val marks = HashMap<String, Mark>()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "history.json")
        file = f
        runCatching {
            val o = JSONObject(f.readText())
            for (k in o.keys()) {
                val m = o.getJSONObject(k)
                marks[k] = Mark(m.getLong("pos"), m.getLong("dur"), m.getLong("at"), m.getBoolean("done"))
            }
        }
    }

    @Synchronized
    fun get(id: String): Mark? = marks[id]

    /** Where to start this track: a few seconds before where it was left, or 0 if new or finished. */
    @Synchronized
    fun resumePos(id: String): Long {
        val m = marks[id] ?: return 0
        return if (m.done || m.pos < 10_000) 0 else maxOf(0, m.pos - 3_000)
    }

    @Synchronized
    /** Records the position in memory; writes the file now if [force], otherwise at most every 30 s. */
    fun update(id: String, pos: Long, dur: Long, force: Boolean = true) {
        if (id.isEmpty() || pos < 0) return
        val prev = marks[id]
        val d = if (dur > 0) dur else prev?.dur ?: 0
        val nearEnd = d > 0 && pos >= d - 15_000
        // A finished track stays "done" until it is listened to again past the first few seconds.
        val done = nearEnd || (prev?.done == true && pos < 10_000)
        marks[id] = Mark(if (nearEnd) 0 else pos, d, System.currentTimeMillis(), done)
        if (force || System.currentTimeMillis() - lastSave >= 30_000) save()
    }

    @Synchronized
    fun markDone(id: String) {
        val prev = marks[id]
        marks[id] = Mark(0, prev?.dur ?: 0, System.currentTimeMillis(), true)
        save()
    }

    @Synchronized
    fun recent(): List<Pair<String, Mark>> = marks.entries.sortedByDescending { it.value.at }.map { it.key to it.value }

    private var lastSave = 0L

    private fun save() {
        lastSave = System.currentTimeMillis()
        if (marks.size > MAX) marks.entries.sortedBy { it.value.at }.take(marks.size - MAX).forEach { marks.remove(it.key) }
        val o = JSONObject()
        for ((k, m) in marks) o.put(k, JSONObject().put("pos", m.pos).put("dur", m.dur).put("at", m.at).put("done", m.done))
        runCatching {
            val f = file ?: return
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
        }
    }
}

/** "✓ played" or "▶ 12:30 of 38:04" for a list row; null if never played. */
fun progressLabel(m: Mark?): String? = when {
    m == null -> null
    m.done -> "✓ played"
    m.pos >= 10_000 -> "▶ " + formatTime(m.pos) + if (m.dur > 0) " of " + formatTime(m.dur) else ""
    else -> null
}
