package com.manytwo.ncplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One stretch of listening to one track: when it started and ended (wall clock), the position it
 * started at and reached, the track's length, and how it ended.
 */
data class Play(
    val id: String, val start: Long, val from: Long,
    val to: Long, val dur: Long, val end: Long, val how: String,
) {
    /** How far into the track it got, 0-100, or -1 if the length isn't known. */
    val percent get() = if (dur > 0) (to * 100 / dur).toInt().coerceIn(0, 100) else -1
    val finished get() = how.startsWith("finished")
}

/** The listening log, newest last, kept in files/playlog.json. Written by the player service. */
object PlayLog {
    private const val MAX = 300
    private var file: File? = null
    private val list = ArrayList<Play>()
    /** The entry being played now (index into [list]), or -1. */
    private var open = -1
    private var lastSave = 0L

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "playlog.json")
        file = f
        runCatching {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).run {
                list += Play(getString("id"), getLong("start"), getLong("from"), getLong("to"), getLong("dur"), getLong("end"), getString("how"))
            }
        }
    }

    /**
     * Playing [id] from [pos]. Carrying on where the last entry stopped (same track, within 30 s of
     * its position, within 6 hours) continues that entry, so short pauses don't fill the log.
     */
    @Synchronized
    fun begin(id: String, pos: Long, dur: Long) {
        val now = System.currentTimeMillis()
        val last = list.lastOrNull()
        if (last != null && last.id == id && kotlin.math.abs(last.to - pos) < 30_000 && now - last.end < 6 * 3600_000L) {
            list[list.size - 1] = last.copy(to = pos, dur = if (dur > 0) dur else last.dur, end = now, how = "playing")
        } else {
            list += Play(id, now, pos, pos, if (dur > 0) dur else 0, now, "playing")
            if (list.size > MAX) list.subList(0, list.size - MAX).clear()
        }
        open = list.size - 1
        save()
    }

    /** Still playing: how far it has got (written to the file at most every 30 s). */
    @Synchronized
    fun progress(id: String, pos: Long, dur: Long) {
        val p = list.getOrNull(open)?.takeIf { it.id == id } ?: return
        list[open] = p.copy(to = maxOf(0, pos), dur = if (dur > 0) dur else p.dur, end = System.currentTimeMillis())
        if (System.currentTimeMillis() - lastSave >= 30_000) save()
    }

    /** Stopped playing [id] at [pos], because of [how] ("paused", "finished, next track started", ...). */
    @Synchronized
    fun finish(id: String, pos: Long, dur: Long, how: String) {
        val p = list.getOrNull(open)?.takeIf { it.id == id } ?: return
        val d = if (dur > 0) dur else p.dur
        // Paused within 15 s of the end counts as finished.
        val h = if (how == "paused" && d > 0 && pos >= d - 15_000) "finished" else how
        list[open] = p.copy(to = maxOf(0, pos), dur = d, end = System.currentTimeMillis(), how = h)
        open = -1
        save()
    }

    /** The last [n] entries, newest first. */
    @Synchronized
    fun recent(n: Int = 50): List<Play> = list.takeLast(n).reversed()

    private fun save() {
        lastSave = System.currentTimeMillis()
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("id", p.id).put("start", p.start).put("from", p.from)
            .put("to", p.to).put("dur", p.dur).put("end", p.end).put("how", p.how))
        runCatching {
            val f = file ?: return
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(f)
        }
    }
}
