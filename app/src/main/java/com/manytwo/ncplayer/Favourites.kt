package com.manytwo.ncplayer

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Favourite tracks and folders (by full path), newest first, kept in files/favourites.json. */
object Favourites {
    private var file: File? = null
    private val ids = ArrayList<String>()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "favourites.json")
        file = f
        runCatching { JSONArray(f.readText()).let { a -> for (i in 0 until a.length()) ids += a.getString(i) } }
    }

    @Synchronized
    fun has(id: String) = id in ids

    @Synchronized
    fun all(): List<String> = ids.toList()

    @Synchronized
    fun toggle(id: String): Boolean {
        val added = if (ids.remove(id)) false else { ids.add(0, id); true }
        runCatching { file?.writeText(JSONArray(ids).toString()) }
        return added
    }
}
