package com.manytwo.ncplayer

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import java.text.DateFormat
import java.util.Date

class EntryAdapter(
    private val onClick: (Entry) -> Unit,
    private val onLongClick: (Entry) -> Unit,
    /** A file row's full Nextcloud path (its history and download key). */
    private val idFor: (Entry) -> String,
) : RecyclerView.Adapter<EntryAdapter.Holder>() {

    var items: List<Entry> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    /** Start shared by all audio names in the folder; shown once above the list instead of on every row. */
    var prefix: String = ""

    /** Name of the file playing in the folder on screen, or null. */
    var playing: String? = null
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    /** Full path of the track tapped once (highlighted, not playing). */
    var selected: String? = null
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    /** True while the player bar is showing [playing]; its progress is then not repeated in the row. */
    var playingInBar = false
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    private val dateFmt = DateFormat.getDateInstance(DateFormat.MEDIUM)

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
        val meta: TextView = v.findViewById(R.id.meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, position: Int) {
        val e = items[position]
        val isPlaying = !e.isDir && e.folder == null && e.name == playing
        h.icon.text = when {
            e.isDir -> "📁"
            isPlaying -> "🔊"
            e.isAudio -> "🎵"
            else -> "📄"
        }
        h.title.text = if (e.isDir) prettyFolder(e.name) else shortName(prettyName(e.name), prefix)
        h.title.setTypeface(null, if (e.isDir || isPlaying) Typeface.BOLD else Typeface.NORMAL)
        val ext = e.name.substringAfterLast('.', "").lowercase()
        val id = if (e.isDir) "" else idFor(e)
        val mark = if (e.isDir || (isPlaying && playingInBar)) null else History.get(id)
        val local = if (e.isAudio && !isLocal(id) && Downloads.localFile(id) != null) "on phone" else null
        val length = if (e.isAudio) Durations.get(id)?.let { "⏱ " + formatTime(it) } else null
        val favId = when {
            e.target != null -> e.target
            e.folder != null && e.isDir -> e.folder
            e.isDir -> idFor(e)
            else -> id
        }
        val heart = if (Favourites.has(favId)) "♥  " else ""
        h.meta.text = heart + when {
            e.note != null -> e.note
            e.isDir -> "folder"
            e.folder != null -> listOfNotNull(length, progressLabel(mark), local, prettyFolder(e.folder.substringAfterLast('/')),
                if (e.modified > 0) dateFmt.format(Date(e.modified)) else null).joinToString("  ·  ")
            e.isAudio -> listOfNotNull(length, progressLabel(mark), local, ext, formatSize(e.size)).joinToString("  ·  ")
            else -> listOfNotNull(ext, formatSize(e.size),
                if (e.modified > 0) dateFmt.format(Date(e.modified)) else null).joinToString("  ·  ")
        }
        h.itemView.setBackgroundColor(when {
            isPlaying -> MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorSecondaryContainer)
            !e.isDir && id == selected -> MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorTertiaryContainer)
            else -> Color.TRANSPARENT
        })
        h.itemView.setOnClickListener { onClick(e) }
        h.itemView.setOnLongClickListener { onLongClick(e); true }
    }
}
