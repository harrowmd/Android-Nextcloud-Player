package com.manytwo.ncplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A saved moment in a track: its full path, the position, the user's text and when it was made. */
data class Bookmark(val id: String, val pos: Long, val text: String, val at: Long)

/** Bookmarks, newest first, kept in files/bookmarks.json. */
object Bookmarks {
    private var file: File? = null
    private val list = ArrayList<Bookmark>()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "bookmarks.json")
        file = f
        runCatching {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                list += Bookmark(it.getString("id"), it.getLong("pos"), it.getString("text"), it.getLong("at"))
            }
        }
    }

    @Synchronized
    fun all(): List<Bookmark> = list.toList()

    @Synchronized
    fun add(b: Bookmark) { list.add(0, b); save() }

    @Synchronized
    fun remove(at: Long) { list.removeAll { it.at == at }; save() }

    @Synchronized
    fun edit(at: Long, text: String) {
        val i = list.indexOfFirst { it.at == at }
        if (i >= 0) { list[i] = list[i].copy(text = text); save() }
    }

    private fun save() {
        val a = JSONArray()
        for (b in list) a.put(JSONObject().put("id", b.id).put("pos", b.pos).put("text", b.text).put("at", b.at))
        runCatching {
            val f = file ?: return
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(f)
        }
    }
}
