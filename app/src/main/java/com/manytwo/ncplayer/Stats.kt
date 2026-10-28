package com.manytwo.ncplayer

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Listening time and play counts, kept in files/stats.json. A "play" is counted after 30 s of listening to a track. */
object Stats {
    class Track(var ms: Long = 0, var plays: Int = 0, var last: Long = 0)

    private const val PLAY_AFTER = 30_000L
    private var file: File? = null
    val tracks = HashMap<String, Track>()
    /** "yyyy-MM-dd" → ms listened that day. */
    val days = HashMap<String, Long>()
    var since = 0L
        private set

    // Current listening session (one track, from when it started playing).
    private var sessionId: String? = null
    private var sessionMs = 0L
    private var sessionCounted = false
    private var dirty = 0L

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "stats.json")
        file = f
        runCatching {
            val o = JSONObject(f.readText())
            since = o.optLong("since")
            o.getJSONObject("tracks").let { t ->
                for (k in t.keys()) t.getJSONObject(k).let { tracks[k] = Track(it.getLong("ms"), it.getInt("plays"), it.getLong("last")) }
            }
            o.getJSONObject("days").let { d -> for (k in d.keys()) days[k] = d.getLong(k) }
        }
        if (since == 0L) since = System.currentTimeMillis()
    }

    /** Add [ms] of listening to track [id]. Called by the player every few seconds while playing. */
    @Synchronized
    fun listened(id: String, ms: Long) {
        if (ms <= 0 || ms > 60_000) return
        if (id != sessionId) { sessionId = id; sessionMs = 0; sessionCounted = false }
        val now = System.currentTimeMillis()
        val t = tracks.getOrPut(id) { Track() }
        t.ms += ms
        t.last = now
        sessionMs += ms
        if (!sessionCounted && sessionMs >= PLAY_AFTER) { sessionCounted = true; t.plays++ }
        val day = dayFmt.format(Date(now))
        days[day] = (days[day] ?: 0) + ms
        dirty += ms
        if (dirty >= 30_000) save()
    }

    /** A new track (or the same one again) is starting: the next 30 s count as a new play. */
    @Synchronized
    fun newSession() { sessionId = null }

    @Synchronized
    fun save() {
        dirty = 0
        val t = JSONObject()
        for ((k, v) in tracks) t.put(k, JSONObject().put("ms", v.ms).put("plays", v.plays).put("last", v.last))
        val d = JSONObject()
        for ((k, v) in days) d.put(k, v)
        runCatching { file?.writeText(JSONObject().put("since", since).put("tracks", t).put("days", d).toString()) }
    }

    /** Forget all listening time and play counts; counting starts again from now. */
    @Synchronized
    fun clear() {
        tracks.clear()
        days.clear()
        since = System.currentTimeMillis()
        sessionId = null
        save()
    }

    /** Total ms listened on days from [daysBack] days ago up to today (0 = today only). */
    @Synchronized
    fun lastDays(daysBack: Int): Long {
        val from = dayFmt.format(Date(System.currentTimeMillis() - daysBack * 86_400_000L))
        return days.filterKeys { it >= from }.values.sum()
    }
}

/** "3 h 05 m", "12 m", "40 s". */
fun formatListen(ms: Long): String {
    val m = ms / 60_000
    return when {
        m >= 60 -> "%d h %02d m".format(m / 60, m % 60)
        m >= 1 -> "$m m"
        else -> "${ms / 1000} s"
    }
}
