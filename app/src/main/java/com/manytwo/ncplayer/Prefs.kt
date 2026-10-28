package com.manytwo.ncplayer

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ncplayer", Context.MODE_PRIVATE)

    var server: String
        get() = sp.getString("server", "")!!
        set(v) = sp.edit().putString("server", v).apply()
    var user: String
        get() = sp.getString("user", "")!!
        set(v) = sp.edit().putString("user", v).apply()
    var pass: String
        get() = sp.getString("pass", "")!!
        set(v) = sp.edit().putString("pass", v).apply()
    var lastPath: String
        get() = sp.getString("lastPath", "")!!
        set(v) = sp.edit().putString("lastPath", v).apply()
    /** Full path of the last track played, to highlight it after a restart. */
    var lastTrack: String
        get() = sp.getString("lastTrack", "")!!
        set(v) = sp.edit().putString("lastTrack", v).apply()
    var newestFirst: Boolean
        get() = sp.getBoolean("newestFirst", false)
        set(v) = sp.edit().putBoolean("newestFirst", v).apply()
    var audioOnly: Boolean
        get() = sp.getBoolean("audioOnly", true)
        set(v) = sp.edit().putBoolean("audioOnly", v).apply()

    /** Index into Context.getExternalFilesDirs(): 0 = phone storage, 1 = SD card. -1 = not chosen yet (use SD if present). */
    var storageIndex: Int
        get() = sp.getInt("storageIndex", -1)
        set(v) = sp.edit().putInt("storageIndex", v).apply()
    var autoDownload: Boolean
        get() = sp.getBoolean("autoDownload", true)
        set(v) = sp.edit().putBoolean("autoDownload", v).apply()
    var wifiOnly: Boolean
        get() = sp.getBoolean("wifiOnly", true)
        set(v) = sp.edit().putBoolean("wifiOnly", v).apply()

    /** History view grouped by folder (true) or one row per track. */
    var historyFolders: Boolean
        get() = sp.getBoolean("historyFolders", false)
        set(v) = sp.edit().putBoolean("historyFolders", v).apply()

    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = sp.edit().putFloat("speed", v).apply()

    /** How many times the "long-press to play" hint has been shown. */
    var tapHints: Int
        get() = sp.getInt("tapHints", 0)
        set(v) = sp.edit().putInt("tapHints", v).apply()

    val configured get() = pass.isNotEmpty()
}
