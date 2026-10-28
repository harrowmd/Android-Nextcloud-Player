package com.manytwo.ncplayer

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small text log in Android/data/com.manytwo.ncplayer/files/ncplayer-log.txt (readable over adb). */
object AppLog {
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(context: Context) {
        if (file != null) return
        file = File(context.getExternalFilesDir(null), "ncplayer-log.txt").also {
            if (it.length() > 500_000) it.renameTo(File(it.parentFile, "ncplayer-log.old.txt"))
        }
    }

    @Synchronized
    fun log(msg: String) {
        runCatching { file?.appendText("${fmt.format(Date())} $msg\n") }
    }
}
