package com.manytwo.ncplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private var dav: Dav? = null
    private var path = ""
    private var all: List<Entry> = emptyList()
    private var loadJob: Job? = null
    private val scrollPos = mutableMapOf<String, Int>()
    /** Track to scroll to once its folder has loaded (the last one played, at startup). */
    private var scrollToTrack: String? = null
    private lateinit var histToggle: com.google.android.material.button.MaterialButtonToggleGroup
    /** True in the History and Favourites lists (rows from many folders); [favMode] tells them apart. */
    private var historyMode = false
    /** Matches found below the current folder (rows from other folders), shown after this folder's own. */
    private var deep: List<Entry> = emptyList()
    private var searchJob: Job? = null
    private var favMode = false
    private lateinit var speedBtn: MaterialButton
    private lateinit var sleepBtn: MaterialButton
    /** Track to play once the folder being opened has loaded (from the History view). */
    private var pendingPlay: String? = null

    private lateinit var adapter: EntryAdapter
    private lateinit var list: RecyclerView
    private lateinit var parentView: TextView
    private lateinit var folderView: TextView
    private lateinit var prefixView: TextView
    private lateinit var barSub: TextView
    private lateinit var status: TextView
    private lateinit var filter: EditText
    private lateinit var bar: LinearLayout
    private lateinit var barTitle: TextView
    private lateinit var seek: SeekBar
    private lateinit var time: TextView
    private lateinit var play: MaterialButton

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var dragging = false
    private var lastBarState = ""
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateBar(); handler.postDelayed(this, 500) }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        History.init(this)
        Downloads.init(this)
        Durations.init(this)
        Stats.init(this)
        Favourites.init(this)

        val root = findViewById<LinearLayout>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(left = i.left, top = i.top, right = i.right, bottom = i.bottom)
            insets
        }
        findViewById<View>(R.id.more).setOnClickListener { v ->
            androidx.appcompat.widget.PopupMenu(this, v).apply {
                menuInflater.inflate(R.menu.main, menu)
                menu.findItem(R.id.m_sort).isChecked = prefs.newestFirst
                menu.findItem(R.id.m_audio).isChecked = prefs.audioOnly
                setOnMenuItemClickListener { onOptionsItemSelected(it) }
                show()
            }
        }

        list = findViewById(R.id.list)
        parentView = findViewById(R.id.parent)
        folderView = findViewById(R.id.folder)
        prefixView = findViewById(R.id.prefix)
        barSub = findViewById(R.id.barSub)
        status = findViewById(R.id.status)
        filter = findViewById(R.id.filter)
        bar = findViewById(R.id.bar)
        barTitle = findViewById(R.id.barTitle)
        seek = findViewById(R.id.seek)
        time = findViewById(R.id.time)
        play = findViewById(R.id.play)

        adapter = EntryAdapter(::onRowTap, ::showDetails) { e ->
            val folder = e.folder ?: path
            if (folder.isEmpty()) e.name else "$folder/${e.name}"
        }
        val dlStatus = findViewById<TextView>(R.id.dlStatus)
        val dlRow = findViewById<View>(R.id.dlRow)
        val dlSpinner = findViewById<View>(R.id.dlSpinner)
        lifecycleScope.launch { Durations.changed.collect { adapter.notifyDataSetChanged() } }
        lifecycleScope.launch {
            var was = ""
            Downloads.status.collect {
                dlStatus.text = it
                dlRow.isVisible = it.isNotEmpty()
                // Spin only while files are actually arriving, not for "waiting for Wi-Fi" or errors.
                dlSpinner.isVisible = it.startsWith("Saving")
                // Refresh "on phone" labels as files arrive.
                if (it != was) { was = it; adapter.notifyDataSetChanged() }
            }
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        filter.doAfterTextChanged { applyFilter(); searchSubfolders() }
        findViewById<MaterialButton>(R.id.clear).setOnClickListener { filter.setText("") }
        findViewById<MaterialButton>(R.id.up).setOnClickListener { goUp() }
        onBackPressedDispatcher.addCallback(this) {
            if (historyMode || path.isNotEmpty()) goUp() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
        }

        play.setOnClickListener { controller?.run { if (playWhenReady) pause() else play() } }
        findViewById<MaterialButton>(R.id.prev).setOnClickListener { controller?.seekToPreviousMediaItem() }
        findViewById<MaterialButton>(R.id.next).setOnClickListener { controller?.seekToNextMediaItem() }
        findViewById<MaterialButton>(R.id.back15).setOnClickListener { controller?.run { seekTo(maxOf(0, currentPosition - 15_000)) } }
        findViewById<MaterialButton>(R.id.fwd15).setOnClickListener { controller?.run { seekTo(currentPosition + 15_000) } }
        barTitle.setOnClickListener { jumpToPlaying() }
        speedBtn = findViewById(R.id.speed)
        sleepBtn = findViewById(R.id.sleep)
        speedBtn.setOnClickListener { chooseSpeed() }
        sleepBtn.setOnClickListener { chooseSleep() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) time.text = "${formatTime(p * 1000L)} / ${formatTime(controller?.duration ?: -1)}"
            }
            override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar) { dragging = false; controller?.seekTo(s.progress * 1000L) }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        path = prefs.lastPath
        scrollToTrack = prefs.lastTrack
        histToggle = findViewById(R.id.histToggle)
        histToggle.check(if (prefs.historyFolders) R.id.histFolders else R.id.histTracks)
        histToggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            prefs.historyFolders = id == R.id.histFolders
            if (historyMode && !favMode) showHistory()
        }
        if (prefs.configured) connect() else showAccount()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        controllerFuture = f
        f.addListener({
            controller = runCatching { f.get() }.getOrNull()?.also { it.addListener(playerListener) }
            updateBar()
        }, ContextCompat.getMainExecutor(this))
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.m_history -> { favMode = false; showHistory() }
            R.id.m_stats -> showStats()
            R.id.m_refresh -> if (favMode) showFavourites() else if (historyMode) showHistory() else load()
            R.id.m_favs -> showFavourites()
            R.id.m_home -> { historyMode = false; openFolder("") }
            R.id.m_local -> { historyMode = false; openFolder(LOCAL) }
            R.id.m_sort -> { prefs.newestFirst = !prefs.newestFirst; applyFilter() }
            R.id.m_audio -> { prefs.audioOnly = !prefs.audioOnly; applyFilter() }
            R.id.m_account -> showAccount()
            R.id.m_storage -> showStorage()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // --- browsing ---

    private fun connect() {
        dav = Dav(prefs.server, prefs.user, prefs.pass)
        load()
    }

    private fun openFolder(newPath: String) {
        (list.layoutManager as LinearLayoutManager).let { scrollPos[path] = it.findFirstVisibleItemPosition() }
        path = newPath
        filter.setText("")
        load()
    }

    private fun goUp() {
        if (historyMode) { historyMode = false; favMode = false; filter.setText(""); load(); return }
        if (path.isEmpty() || path == LOCAL) return
        if (isLocal(path)) {
            val roots = LocalStorage.roots(this).map { LOCAL + it.second }
            openFolder(if (path in roots) LOCAL else path.substringBeforeLast('/'))
            return
        }
        openFolder(path.substringBeforeLast('/', ""))
    }

    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && isLocal(path)) load()
    }

    /** Header text for a local path: (parent line, folder name). */
    private fun localTitles(p: String): Pair<String, String> {
        if (p == LOCAL) return "" to "Local storage"
        val abs = p.removePrefix(LOCAL)
        val root = LocalStorage.roots(this).filter { abs == it.second || abs.startsWith(it.second + "/") }
            .maxByOrNull { it.second.length } ?: return "Local storage" to abs
        if (abs == root.second) return "Local storage" to root.first
        val inside = abs.removePrefix(root.second + "/")
        val parent = listOf("Local storage", root.first) + inside.split('/').dropLast(1)
        return parent.joinToString(" › ") to prettyFolder(inside.substringAfterLast('/'))
    }

    private fun loadLocal(p: String) {
        val (parent, name) = localTitles(p)
        folderView.text = name
        parentView.text = parent
        parentView.isVisible = parent.isNotEmpty()
        loadJob?.cancel()
        if (!hasAudioPermission()) {
            all = emptyList(); setPrefix(""); applyFilter()
            status.text = "Nextcloud Player needs permission to read music files on the phone."
            status.isVisible = true
            requestPermissions(arrayOf(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE), 2)
            return
        }
        status.isVisible = false
        all = if (p == LOCAL) LocalStorage.roots(this).map { (n, abs) -> Entry(n, true, 0, 0, "", target = LOCAL + abs) }
        else LocalStorage.list(p) ?: run {
            status.text = "Can't read this folder."; status.isVisible = true; emptyList()
        }
        setPrefix(commonPrefix(all.filter { it.isAudio }.map { prettyName(it.name) }))
        prefs.lastPath = p
        applyFilter()
        val last = scrollToTrack ?: ""
        scrollToTrack = null
        val lastIdx = if (last.substringBeforeLast('/', "") == p)
            adapter.items.indexOfFirst { it.name == last.substringAfterLast('/') } else -1
        val pending = pendingPlay?.let { n -> adapter.items.indexOfFirst { it.name == n } } ?: -1
        pendingPlay = null
        val lm = list.layoutManager as LinearLayoutManager
        when {
            pending >= 0 -> { lm.scrollToPositionWithOffset(pending, list.height / 3); onEntryClick(adapter.items[pending]) }
            lastIdx >= 0 -> lm.scrollToPositionWithOffset(lastIdx, list.height / 3)
            else -> list.scrollToPosition(scrollPos[p] ?: 0)
        }
    }

    private fun showHistory() {
        if (!historyMode) (list.layoutManager as LinearLayoutManager).let { scrollPos[path] = it.findFirstVisibleItemPosition() }
        historyMode = true
        favMode = false
        loadJob?.cancel()
        filter.setText("")
        folderView.text = "History"
        parentView.isVisible = false
        setPrefix("")
        status.isVisible = false
        histToggle.isVisible = true
        val recent = History.recent()
        all = if (prefs.historyFolders) {
            // One row per folder, most recently played first.
            val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
            recent.groupBy { it.first.substringBeforeLast('/', "") }.map { (folder, marks) ->
                val done = marks.count { it.second.done }
                val listened = marks.sumOf { Stats.tracks[it.first]?.ms ?: 0 }
                val note = listOfNotNull(
                    "${marks.size} played" + if (done > 0) " ($done finished)" else "",
                    if (listened > 0) formatListen(listened) else null,
                    "last " + df.format(Date(marks[0].second.at)),
                ).joinToString("  ·  ")
                Entry(folder.substringAfterLast('/').ifEmpty { "Nextcloud" }, true, 0, marks[0].second.at, "",
                    folder = folder, note = note)
            }
        } else recent.map { (id, m) ->
            Entry(id.substringAfterLast('/'), false, 0, m.at, "", folder = id.substringBeforeLast('/', ""))
        }
        applyFilter()
        list.scrollToPosition(0)
    }

    private fun showStats() {
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val tracks = Stats.tracks.entries.toList()
        val total = tracks.sumOf { it.value.ms }
        val plays = tracks.sumOf { it.value.plays }
        val folders = tracks.groupBy { it.key.substringBeforeLast('/', "") }
            .mapValues { (_, l) -> Pair(l.sumOf { it.value.ms }, l.sumOf { it.value.plays }) }
        val finished = History.recent().count { it.second.done }
        val bestDay = Stats.days.maxByOrNull { it.value }
        fun trackName(id: String) = prettyName(id.substringAfterLast('/'))
        fun folderName(f: String) = prettyFolder(f.substringAfterLast('/').ifEmpty { "Nextcloud" })
        val text = buildString {
            append("Listening since ${df.format(Date(Stats.since))}\n\n")
            append("Total listened:  ${formatListen(total)}\n")
            append("Today:  ${formatListen(Stats.lastDays(0))}\n")
            append("Last 7 days:  ${formatListen(Stats.lastDays(6))}\n")
            append("Last 30 days:  ${formatListen(Stats.lastDays(29))}\n")
            if (bestDay != null) append("Best day:  ${bestDay.key} (${formatListen(bestDay.value)})\n")
            append("\nPlays:  $plays  (a play = 30 s or more)\n")
            append("Different tracks:  ${tracks.size} in ${folders.size} folders\n")
            append("Tracks finished:  $finished\n")
            if (folders.isNotEmpty()) {
                append("\nTOP FOLDERS (time listened)\n")
                folders.entries.sortedByDescending { it.value.first }.take(10).forEachIndexed { i, (f, v) ->
                    append("${i + 1}. ${folderName(f)}\n     ${formatListen(v.first)}, ${v.second} plays\n")
                }
                append("\nTOP TRACKS (time listened)\n")
                tracks.sortedByDescending { it.value.ms }.take(10).forEachIndexed { i, (id, t) ->
                    append("${i + 1}. ${trackName(id)}\n     ${formatListen(t.ms)}, ${t.plays} plays\n")
                }
                val most = tracks.filter { it.value.plays > 1 }.sortedByDescending { it.value.plays }.take(10)
                if (most.isNotEmpty()) {
                    append("\nMOST REPLAYED\n")
                    most.forEachIndexed { i, (id, t) -> append("${i + 1}. ${trackName(id)} — ${t.plays} plays\n") }
                }
            } else append("\nNothing counted yet. Statistics start with this version.")
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply { this.text = text; textSize = 15f; setPadding(pad, pad / 2, pad, 0); setTextIsSelectable(true) }
        AlertDialog.Builder(this)
            .setTitle("Statistics")
            .setView(android.widget.ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear stats") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("Clear all statistics (listening time, play counts, top lists)?\n\nHistory, resume points and favourites are kept.")
                    .setPositiveButton("Clear") { _, _ -> Stats.clear(); toastStatus("Statistics cleared") }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    private fun showFavourites() {
        if (!historyMode) (list.layoutManager as LinearLayoutManager).let { scrollPos[path] = it.findFirstVisibleItemPosition() }
        historyMode = true
        favMode = true
        loadJob?.cancel()
        filter.setText("")
        folderView.text = "♥ Favourites"
        parentView.isVisible = false
        histToggle.isVisible = false
        setPrefix("")
        status.isVisible = false
        val roots = LocalStorage.roots(this).associate { LOCAL + it.second to it.first }
        all = Favourites.all().map { id ->
            val audio = Entry(id.substringAfterLast('/'), false, 0, 0, "").isAudio
            val parent = id.substringBeforeLast('/', "")
            if (audio) Entry(id.substringAfterLast('/'), false, 0, 0, "", folder = parent)
            else Entry(roots[id] ?: id.substringAfterLast('/').ifEmpty { "Nextcloud" }, true, 0, 0, "", folder = id,
                note = "folder" + if (isLocal(id)) " on the phone" else "")
        }
        applyFilter()
        list.scrollToPosition(0)
        if (all.isEmpty()) { status.text = "No favourites yet. Long-press a track or folder → Add to favourites."; status.isVisible = true }
    }

    private fun load() {
        searchJob?.cancel()
        deep = emptyList()
        historyMode = false
        favMode = false
        histToggle.isVisible = false
        val p = path
        if (isLocal(p)) { loadLocal(p); return }
        val d = dav ?: return
        folderView.text = if (p.isEmpty()) "Nextcloud" else prettyFolder(p.substringAfterLast('/'))
        parentView.text = if (p.isEmpty()) "" else ("Nextcloud/" + p.substringBeforeLast('/', "")).trimEnd('/').replace("/", " › ")
        parentView.isVisible = p.isNotEmpty()
        status.text = "Loading…"
        status.isVisible = true
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                all = withContext(Dispatchers.IO) { d.list(p) }
                setPrefix(commonPrefix(all.filter { it.isAudio }.map { prettyName(it.name) }))
                prefs.lastPath = p
                status.isVisible = false
                applyFilter()
                val last = scrollToTrack ?: ""
                val lastIdx = if (last.substringBeforeLast('/', "") == p)
                    adapter.items.indexOfFirst { it.name == last.substringAfterLast('/') } else -1
                scrollToTrack = null
                val pending = pendingPlay?.let { n -> adapter.items.indexOfFirst { it.name == n } } ?: -1
                pendingPlay = null
                if (pending >= 0) {
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pending, list.height / 3)
                    onEntryClick(adapter.items[pending])
                } else if (lastIdx >= 0) {
                    // Put the last played track about a third of the way down the list.
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(lastIdx, list.height / 3)
                } else list.scrollToPosition(scrollPos[p] ?: 0)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val local = if (e is DavException) emptyList() else withContext(Dispatchers.IO) { localEntries(p) }
                if (local.isNotEmpty()) {
                    all = local
                    setPrefix(commonPrefix(all.filter { it.isAudio }.map { prettyName(it.name) }))
                    status.isVisible = false
                    applyFilter()
                    status.text = "Offline: showing what is saved on the phone."
                    status.isVisible = true
                    return@launch
                }
                all = emptyList()
                setPrefix("")
                applyFilter()
                status.text = "Couldn't open folder.\n${e.message ?: e.javaClass.simpleName}"
                status.isVisible = true
            }
        }
    }

    /** Folder contents from the downloaded copies, for when Nextcloud can't be reached. */
    private fun localEntries(p: String): List<Entry> {
        val seen = HashMap<String, Entry>()
        for (dir in Downloads.localFolder(p)) dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".part")) return@forEach
            seen[f.name] = Entry(f.name, f.isDirectory, if (f.isFile) f.length() else 0, f.lastModified(), "")
        }
        return seen.values.toList()
    }

    private fun setPrefix(p: String) {
        adapter.prefix = p
        updateInfo()
    }

    private fun showCount(text: String) { prefixView.text = text; prefixView.isVisible = true }

    /** Line under the folder name: track count, shared start of the names, sub-folder search hits. */
    private fun updateInfo() {
        if (historyMode) { prefixView.isVisible = false; return }
        val tracks = all.count { it.isAudio }
        val deepShown = adapter.items.count { it.folder != null }
        val parts = listOfNotNull(
            if (tracks > 0) "Folder has $tracks audio " + (if (tracks == 1) "track" else "tracks") +
                (if (adapter.prefix.isNotEmpty()) ", all start with: “${prefixLabel(adapter.prefix)}”" else "") else null,
            if (deepShown > 0) "$deepShown found in sub-folders" else null,
        )
        prefixView.text = parts.joinToString("\n")
        prefixView.isVisible = parts.isNotEmpty()
    }

    /** After a short pause in typing, search all sub-folders too (3+ letters). */
    private fun searchSubfolders() {
        searchJob?.cancel()
        val f = filter.text.toString().trim()
        val p = path
        if (historyMode || f.length < 3 || p == LOCAL) {
            if (deep.isNotEmpty()) { deep = emptyList(); applyFilter() }
            return
        }
        searchJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(600)
            status.text = "Searching sub-folders…"
            status.isVisible = true
            val found = try {
                withContext(Dispatchers.IO) {
                    if (isLocal(p)) LocalStorage.search(p, f)
                    else dav?.search(p, f.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }) ?: emptyList()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.log("search failed: $e")
                emptyList()
            }
            deep = found
                .filter { (id, _) -> id.substringBeforeLast('/', "") != p.removeSuffix("/") }
                .filter { (_, e) -> e.isDir || !prefs.audioOnly || e.isAudio }
                .filter { (_, e) -> matches(e.name, f) }
                .sortedWith(compareBy<Pair<String, Entry>>({ !it.second.isDir }, { it.first.lowercase() }))
                .map { (id, e) ->
                    val parent = id.substringBeforeLast('/', "")
                    if (e.isDir) e.copy(folder = id, note = "folder in " + prettyFolder(parent.substringAfterLast('/').ifEmpty { "Nextcloud" }))
                    else e.copy(folder = parent)
                }
            status.isVisible = false
            applyFilter()
        }
    }

    private fun visibleEntries(): List<Entry> {
        val f = filter.text.toString()
        // Names starting with "_" first (a common way to pin folders to the top), then A-Z ignoring case.
        val byName = compareBy<Entry> { !it.name.startsWith("_") }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { if (it.isDir) prettyFolder(it.name) else prettyName(it.name) }
        val here = all
            .filter { it.isDir || !prefs.audioOnly || it.isAudio }
            .filter { matches(it.name, f) }
            .let { l -> if (historyMode) return l else l }
            .sortedWith(compareBy<Entry> { !it.isDir }.then(
                if (prefs.newestFirst) compareByDescending<Entry> { it.modified }.then(byName) else byName
            ))
        return if (historyMode || filter.text.isBlank()) here else here + deep
    }

    private fun applyFilter() {
        adapter.items = visibleEntries()
        updateBar()
        if (status.text.startsWith("Couldn't") && status.isVisible) return
        if (all.isNotEmpty() && adapter.items.isEmpty()) {
            status.text = "Nothing matches “${filter.text}”."
            status.isVisible = true
        } else if (loadJob?.isActive != true) {
            status.isVisible = false
        }
        if (favMode) { showCount("${adapter.items.size} favourites"); return }
        if (historyMode) {
            showCount("${adapter.items.size} " + if (prefs.historyFolders) "folders played" else "tracks played")
            if (all.isEmpty()) { status.text = "Nothing played yet."; status.isVisible = true }
            return
        }
        updateInfo()
    }

    private fun onEntryClick(e: Entry) {
        if (e.folder != null && e.isDir) {
            // History folder row: open the folder at the track last played there.
            historyMode = false
            favMode = false
            filter.setText("")
            path = e.folder
            scrollToTrack = History.recent().firstOrNull { it.first.substringBeforeLast('/', "") == e.folder }?.first
            load()
            return
        }
        if (e.folder != null) {
            // History row: open its folder, then play it from where it was left.
            historyMode = false
            favMode = false
            pendingPlay = e.name
            filter.setText("")
            path = e.folder
            load()
            return
        }
        val sep = if (path.isEmpty() || path == LOCAL) "" else "/"
        if (e.isDir) { openFolder(e.target ?: (path + sep + e.name)); return }
        if (!e.isAudio) { showDetails(e); return }
        val d = dav
        if (d == null && !isLocal(path)) return
        val c = controller ?: run { toastStatus("Player not ready yet, try again."); return }
        // Queue = the audio files currently shown, in the order shown, starting from the tapped one.
        val queue = adapter.items.filter { it.isAudio }
        val folderName = if (path.isEmpty()) "Nextcloud" else path.substringAfterLast('/')
        val items = queue.map {
            MediaItem.Builder()
                .setUri(Downloads.localFile(path + sep + it.name)?.let { f -> android.net.Uri.fromFile(f) }
                    ?: android.net.Uri.parse(d!!.url(path + sep + it.name)))
                .setMediaId(path + sep + it.name)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(shortName(prettyName(it.name), adapter.prefix))
                    .setArtist(if (adapter.prefix.isEmpty()) folderName else prefixLabel(adapter.prefix))
                    .build())
                .build()
        }
        c.setMediaItems(items, queue.indexOf(e), History.resumePos(path + sep + e.name))
        c.setPlaybackSpeed(prefs.speed)
        c.prepare()
        c.play()
        Downloads.requestFolder(path)
    }

    /**
     * A short tap opens folders but only selects tracks, so a stray touch never starts playback.
     * Tracks play from the long-press menu.
     */
    private fun onRowTap(e: Entry) {
        if (e.isDir) { onEntryClick(e); return }
        if (!e.isAudio) { showDetails(e); return }
        val id = fullId(e)
        adapter.selected = if (adapter.selected == id) null else id
        if (adapter.selected != null && prefs.tapHints < 3) {
            prefs.tapHints = prefs.tapHints + 1
            toastStatus("Long-press a track to play it")
        }
    }

    /** Full path of a row: what history, downloads and favourites are keyed on. */
    private fun fullId(e: Entry): String = when {
        e.target != null -> e.target
        e.folder != null -> if (e.isDir) e.folder else "${e.folder}/${e.name}"
        path.isEmpty() || path == LOCAL -> e.name
        else -> "$path/${e.name}"
    }

    /** Long-press menu for a row. */
    private fun showDetails(e: Entry) {
        val id = fullId(e)
        val acts = ArrayList<Pair<String, () -> Unit>>()
        if (e.isDir) acts += "📂  Open" to { onEntryClick(e) }
        else if (e.isAudio) acts += "▶  Play" to { onEntryClick(e) }
        if (e.isDir || e.isAudio) {
            val fav = Favourites.has(id)
            acts += (if (fav) "💔  Remove from favourites" else "♥  Add to favourites") to {
                val added = Favourites.toggle(id)
                toastStatus(if (added) "Added to favourites" else "Removed from favourites")
                if (favMode) showFavourites() else adapter.notifyDataSetChanged()
            }
        }
        if (e.isDir && !historyMode && !isLocal(id))
            acts += "⬇  Save to phone" to { Downloads.requestFolder(id, force = true) }
        acts += "ℹ  Details" to {
            val msg = buildString {
                append(e.name)
                append("\n\nFolder: /").append(if (e.isDir) id.substringBeforeLast('/', "") else id.substringBeforeLast('/', ""))
                if (!e.isDir && e.size > 0) append("\nSize: ").append(formatSize(e.size))
                if (e.modified > 0) append("\nModified: ").append(DateFormat.getDateTimeInstance().format(Date(e.modified)))
            }
            AlertDialog.Builder(this).setTitle(if (e.isDir) prettyFolder(e.name) else prettyName(e.name))
                .setMessage(msg).setPositiveButton("OK", null).show()
        }
        AlertDialog.Builder(this)
            .setTitle(if (e.isDir) prettyFolder(e.name) else prettyName(e.name))
            .setItems(acts.map { it.first }.toTypedArray()) { _, i -> acts[i].second() }
            .setNegativeButton("Close", null)
            .show()
    }

    /** 1.0×, 1.25×, 0.9×, 2.0× */
    private fun speedLabel(v: Float) = "%.2f".format(java.util.Locale.US, v).trimEnd('0').let { if (it.endsWith(".")) it + "0" else it } + "×"

    private fun chooseSpeed() {
        val speeds = floatArrayOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { speedLabel(it) + if (it == 1f) " (normal)" else "" }
        AlertDialog.Builder(this)
            .setTitle("Playback speed")
            .setSingleChoiceItems(labels.toTypedArray(), speeds.indexOfFirst { it == prefs.speed }) { d, i ->
                prefs.speed = speeds[i]
                controller?.setPlaybackSpeed(speeds[i])
                updateBar()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseSleep() {
        val mins = intArrayOf(5, 10, 15, 30, 45, 60, 90)
        val labels = mins.map { "$it minutes" } + "End of this track" + "Off"
        AlertDialog.Builder(this)
            .setTitle("Sleep timer")
            .setItems(labels.toTypedArray()) { _, i ->
                when {
                    i < mins.size -> SleepTimer.startMinutes(mins[i])
                    i == mins.size -> SleepTimer.startEndOfTrack()
                    else -> SleepTimer.cancel()
                }
                updateBar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toastStatus(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    // --- player bar ---

    private fun updateBar() {
        val c = controller
        val item = c?.currentMediaItem
        bar.isVisible = item != null
        adapter.playingInBar = item != null
        if (c == null || item == null) {
            // Nothing loaded in the player: highlight the last track played instead.
            val last = prefs.lastTrack
            adapter.playing = if (last.substringBeforeLast('/', "") == path) last.substringAfterLast('/') else null
            return
        }
        if (item.mediaId != prefs.lastTrack) prefs.lastTrack = item.mediaId
        barTitle.text = item.mediaMetadata.title ?: item.mediaId
        barSub.text = item.mediaMetadata.artist
        speedBtn.text = "Speed " + speedLabel(c.playbackParameters.speed)
        sleepBtn.text = SleepTimer.label()
        play.setIconResource(if (c.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
        val dur = c.duration
        val pos = c.currentPosition
        if (!dragging) {
            seek.max = if (dur > 0) (dur / 1000).toInt() else 0
            seek.progress = (pos / 1000).toInt()
            time.text = "${formatTime(pos)} / ${formatTime(if (dur > 0) dur else -1)}" +
                if (c.mediaItemCount > 1) "    (${c.currentMediaItemIndex + 1} of ${c.mediaItemCount})" else ""
        }
        val id = item.mediaId
        val state = id + c.playWhenReady
        if (state != lastBarState) { lastBarState = state; adapter.notifyDataSetChanged() }
        adapter.playing = if (id.substringBeforeLast('/', "") == path) id.substringAfterLast('/') else null
    }

    /** Tap the title in the player bar to go to the folder of the playing file. */
    private fun jumpToPlaying() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        val folder = id.substringBeforeLast('/', "")
        if (folder != path) openFolder(folder)
        else adapter.items.indexOfFirst { it.name == id.substringAfterLast('/') }.takeIf { it >= 0 }?.let { list.scrollToPosition(it) }
    }

    // --- offline storage ---

    private fun showStorage() {
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) { Downloads.folders() }
            val vol = Downloads.currentVolume()
            val used = folders.sumOf { it.second }
            val pad = (16 * resources.displayMetrics.density).toInt()
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
            box.addView(TextView(this@MainActivity).apply {
                text = "Saving to: ${Downloads.volumeName(vol)} (${formatSize(vol.usableSpace)} free)\n" +
                    "Saved: ${folders.size} folders, ${formatSize(used)}"
                textSize = 15f
            })
            fun check(label: String, value: Boolean, set: (Boolean) -> Unit) = box.addView(
                android.widget.CheckBox(this@MainActivity).apply {
                    text = label; isChecked = value; setOnCheckedChangeListener { _, b -> set(b) }
                })
            check("When I play a track, save its whole folder to the phone", prefs.autoDownload) { prefs.autoDownload = it }
            check("Only download on Wi-Fi", prefs.wifiOnly) { prefs.wifiOnly = it }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Offline storage")
                .setView(box)
                .setPositiveButton("Saved folders…") { _, _ -> showSavedFolders() }
                .setNeutralButton("Change location…") { _, _ -> chooseVolume() }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun chooseVolume() {
        val vols = Downloads.volumes()
        val names = vols.map { "${Downloads.volumeName(it)} — ${formatSize(it.usableSpace)} free" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Save new downloads to")
            .setSingleChoiceItems(names, vols.indexOf(Downloads.currentVolume())) { d, i ->
                prefs.storageIndex = i
                d.dismiss()
                toastStatus("New downloads go to ${Downloads.volumeName(vols[i])}. Files already saved stay where they are.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Last time any track in the folder was played (0 = never). */
    private fun lastPlayed(folder: String) =
        History.recent().firstOrNull { it.first.substringBeforeLast('/', "") == folder }?.second?.at ?: 0L

    private fun showSavedFolders() {
        lifecycleScope.launch {
            // Least recently played first: those are the ones to delete.
            val folders = withContext(Dispatchers.IO) { Downloads.folders() }
                .map { Triple(it.first, it.second, lastPlayed(it.first)) }
                .sortedBy { it.third }
            if (folders.isEmpty()) { toastStatus("Nothing saved on the phone yet."); return@launch }
            val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
            val labels = folders.map { (p, size, at) ->
                prettyFolder(p.substringAfterLast('/').ifEmpty { "Nextcloud" }) + "\n" + formatSize(size) + "  ·  " +
                    (if (at > 0) "played " + df.format(Date(at)) else "not played")
            }.toTypedArray()
            val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            val old = folders.filter { it.third < monthAgo }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Saved folders (tap to delete)")
                .setItems(labels) { _, i ->
                    val (p, size, _) = folders[i]
                    confirmDelete("Delete the saved copy of “${prettyFolder(p.substringAfterLast('/'))}” (${formatSize(size)})?\nIt stays on Nextcloud.", listOf(p))
                }
                .apply {
                    if (old.isNotEmpty()) setNeutralButton("Delete not played for 30 days") { _, _ ->
                        confirmDelete("Delete ${old.size} saved folders not played for 30 days (${formatSize(old.sumOf { it.second })})?\nThey stay on Nextcloud.",
                            old.map { it.first })
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun confirmDelete(msg: String, paths: List<String>) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { paths.forEach { Downloads.deleteFolder(it) } }
                    adapter.notifyDataSetChanged()
                    toastStatus("Deleted.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- account ---

    private fun showAccount() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        fun field(hint: String, value: String, type: Int) = EditText(this).apply {
            this.hint = hint; setText(value); inputType = type; isSingleLine = true; box.addView(this)
        }
        box.addView(TextView(this).apply {
            text = "Use a Nextcloud app password, not your main password:\nNextcloud web → Settings → Security → Create new app password."
        })
        val server = field("Server, e.g. https://cloud.example.com", prefs.server, InputType.TYPE_TEXT_VARIATION_URI)
        val user = field("User name", prefs.user, InputType.TYPE_CLASS_TEXT)
        val pass = field(if (prefs.configured) "App password (leave blank to keep)" else "App password", "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        AlertDialog.Builder(this)
            .setTitle("Nextcloud account")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                prefs.server = server.text.toString().trim().trimEnd('/')
                prefs.user = user.text.toString().trim()
                if (pass.text.isNotEmpty()) prefs.pass = pass.text.toString()
                if (prefs.configured) connect() else showAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
