package com.manytwo.ncplayer

import android.net.Uri
import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

data class Entry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val type: String,
    /** Set only for rows in the History view: the folder the file is in. */
    val folder: String? = null,
    /** History view: replaces the details line. */
    val note: String? = null,
    /** Folder rows that don't open path + "/" + name (storage volumes): the path to open. */
    val target: String? = null,
) {
    val isAudio get() = !isDir && (type.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT)

    companion object {
        val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")
    }
}

class DavException(message: String) : IOException(message)

/** Minimal Nextcloud WebDAV client: lists one folder with PROPFIND. Paths are relative to the user's files root. */
class Dav(server: String, private val user: String, pass: String) {
    private val auth = Credentials.basic(user, pass)
    private val base = server.trimEnd('/') + "/remote.php/dav/files/" + Uri.encode(user) + "/"
    private val davRoot = server.trimEnd('/') + "/remote.php/dav/"

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { it.proceed(it.request().newBuilder().header("Authorization", auth).build()) }
        .build()

    fun url(path: String, dir: Boolean = false): String {
        val enc = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
        return base + enc + if (dir && enc.isNotEmpty()) "/" else ""
    }

    fun list(path: String): List<Entry> {
        val url = url(path, dir = true)
        val req = Request.Builder()
            .url(url)
            .header("Depth", "1")
            .method("PROPFIND", PROPS.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            when (resp.code) {
                207 -> {}
                401 -> throw DavException("Login failed: check the user name and app password (menu → Nextcloud account).")
                404 -> throw DavException("Folder not found: /$path")
                else -> throw DavException("Server replied ${resp.code} ${resp.message}")
            }
            val self = Uri.decode(Uri.parse(url).encodedPath).trimEnd('/')
            return parse(resp.body!!.charStream()).filter { it.first != self }.map { it.second }
        }
    }

    /**
     * Finds files and folders below [path] (any depth) whose name contains every word, using Nextcloud's
     * WebDAV SEARCH. Returns (path relative to the files root, entry); at most [limit] results.
     */
    fun search(path: String, words: List<String>, limit: Int = 200): List<Pair<String, Entry>> {
        fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val likes = words.joinToString("") {
            "<d:like><d:prop><d:displayname/></d:prop><d:literal>%${esc(it)}%</d:literal></d:like>"
        }
        val where = if (words.size > 1) "<d:and>$likes</d:and>" else likes
        val scope = "/files/$user/" + path.trim('/')
        val body = """<?xml version="1.0" encoding="UTF-8"?>
<d:searchrequest xmlns:d="DAV:"><d:basicsearch>
<d:select><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getcontenttype/></d:prop></d:select>
<d:from><d:scope><d:href>${esc(scope)}</d:href><d:depth>infinity</d:depth></d:scope></d:from>
<d:where>$where</d:where><d:orderby/>
<d:limit><d:nresults>$limit</d:nresults></d:limit>
</d:basicsearch></d:searchrequest>"""
        val req = Request.Builder()
            .url(davRoot)
            .method("SEARCH", body.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code != 207) throw DavException("Search failed: ${resp.code} ${resp.message}")
            val marker = "/remote.php/dav/files/$user/"
            return parse(resp.body!!.charStream()).mapNotNull { (href, e) ->
                val i = href.indexOf(marker)
                if (i < 0) null else href.substring(i + marker.length) to e
            }
        }
    }

    private fun parse(reader: java.io.Reader): List<Pair<String, Entry>> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(reader)
        val out = mutableListOf<Pair<String, Entry>>()
        var href = ""; var dir = false; var size = 0L; var mod = 0L; var type = ""
        var text = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (p.name) {
                        "response" -> { href = ""; dir = false; size = 0; mod = 0; type = "" }
                        "collection" -> dir = true
                    }
                }
                XmlPullParser.TEXT -> text.append(p.text)
                XmlPullParser.END_TAG -> {
                    val t = text.toString().trim()
                    when (p.name) {
                        "href" -> href = Uri.decode(t).trimEnd('/')
                        "getcontentlength" -> size = t.toLongOrNull() ?: 0
                        "getlastmodified" -> mod = runCatching { HTTP_DATE.get()!!.parse(t)!!.time }.getOrDefault(0)
                        "getcontenttype" -> type = t
                        "response" -> if (href.isNotEmpty())
                            out += href to Entry(href.substringAfterLast('/'), dir, size, mod, type)
                    }
                }
            }
        }
        return out
    }

    companion object {
        private const val PROPS = """<?xml version="1.0"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getcontenttype/></d:prop></d:propfind>"""
        private val HTTP_DATE = ThreadLocal.withInitial { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US) }
    }
}
