package com.manytwo.ncplayer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Checks GitHub for a newer release and installs its APK. Uses its own HTTP client: no Nextcloud login is ever sent. */
object Updater {
    const val REPO = "harrowmd/Android-Nextcloud-Player"
    const val REPO_URL = "https://github.com/$REPO"
    const val HELP_URL = "$REPO_URL/blob/main/USERGUIDE.md"
    private const val APK = "m21-nextcloud-player.apk"

    data class Release(val version: String, val published: String, val notes: String, val apkUrl: String?)

    private val http = OkHttpClient()

    /** The latest published release (blocking; call off the main thread). */
    fun latest(): Release {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub replied ${r.code}")
            val o = JSONObject(r.body!!.string())
            val assets = o.optJSONArray("assets")
            var url: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == APK) url = a.getString("browser_download_url")
            }
            return Release(o.getString("tag_name").removePrefix("v"), o.optString("published_at").take(10), o.optString("body"), url)
        }
    }

    /** True if version a (e.g. "1.22") is newer than b. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** Downloads the APK into the app cache (blocking) and returns it. */
    fun download(context: Context, url: String, progress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, APK)
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("download failed: ${r.code}")
            val body = r.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        }
        return out
    }

    /** Hands the APK to Android's installer (which asks the user to confirm). */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
