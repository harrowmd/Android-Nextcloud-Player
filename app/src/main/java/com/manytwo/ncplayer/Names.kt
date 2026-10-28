package com.manytwo.ncplayer

/** Turns a file name into something easier to read: no extension, underscores become spaces, freesound-style ids dropped. */
fun prettyName(file: String): String {
    var s = if ('.' in file) file.substringBeforeLast('.') else file
    s = s.replace(Regex("^\\d{3,}__"), "")
    s = s.replace("__", " · ").replace('_', ' ')
    s = s.replace(Regex("\\s+"), " ").trim()
    return s.ifEmpty { file }
}

/** Every word of the filter must appear somewhere in the name (any order, any case). */
fun matches(name: String, filter: String): Boolean {
    if (filter.isBlank()) return true
    val hay = (name + " " + prettyName(name)).lowercase()
    return filter.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.all { it in hay }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private const val SEPARATORS = " -–—_.,:;·"

/**
 * The start shared by all names (e.g. "Dombey and Son-Charles Dickens-"), cut back to a word boundary.
 * Empty when there are fewer than 2 names or the shared part is too short to be worth hiding.
 */
fun commonPrefix(names: List<String>): String {
    if (names.size < 2) return ""
    var n = names.minOf { it.length }
    for (name in names) n = minOf(n, names[0].commonPrefixWith(name, ignoreCase = true).length)
    var p = names[0].substring(0, n)
    // Only cut at a separator, so "Chapter 1" and "Chapter 12" don't lose half a word.
    val cut = p.indexOfLast { it in SEPARATORS }
    p = if (cut < 0) "" else p.substring(0, cut + 1)
    if (p.trim(*SEPARATORS.toCharArray()).length < 6) return ""
    // Every name must keep something after the prefix.
    return if (names.all { shortName(it, p) != it }) p else ""
}

fun shortName(name: String, prefix: String): String {
    if (prefix.isEmpty() || !name.startsWith(prefix, ignoreCase = true)) return name
    val rest = name.substring(prefix.length).trimStart(*SEPARATORS.toCharArray())
    return rest.ifEmpty { name }
}

fun prefixLabel(prefix: String) = prefix.trimEnd(*SEPARATORS.toCharArray())

/** Underscores become spaces, except leading ones ("_Books" stays "_Books": it is a sort-to-top marker). */
fun prettyFolder(name: String): String {
    val lead = name.takeWhile { it == '_' }
    val rest = name.substring(lead.length).replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    return (lead + rest).ifEmpty { name }
}
