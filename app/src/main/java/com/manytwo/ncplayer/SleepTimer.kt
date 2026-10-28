package com.manytwo.ncplayer

import android.os.SystemClock

/** Shared between the screen and the player service (same process). The service does the pausing. */
object SleepTimer {
    /** elapsedRealtime when playback should stop, or 0. */
    @Volatile var endAt = 0L
        private set
    @Volatile var endOfTrack = false
        private set
    /** Set by the service so a change applies straight away. */
    @Volatile var onChange: (() -> Unit)? = null

    val active get() = endAt > 0 || endOfTrack

    fun startMinutes(min: Int) { endAt = SystemClock.elapsedRealtime() + min * 60_000L; endOfTrack = false; onChange?.invoke() }
    fun startEndOfTrack() { endAt = 0; endOfTrack = true; onChange?.invoke() }
    fun cancel() { endAt = 0; endOfTrack = false; onChange?.invoke() }

    fun remainingMs() = if (endAt > 0) maxOf(0, endAt - SystemClock.elapsedRealtime()) else 0

    fun label(): String = when {
        endOfTrack -> "Sleep: end of track"
        endAt > 0 -> "Sleep: " + formatTime(remainingMs())
        else -> "Sleep timer"
    }
}
