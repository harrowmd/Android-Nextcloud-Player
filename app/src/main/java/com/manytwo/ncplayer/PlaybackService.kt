package com.manytwo.ncplayer

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import okhttp3.Credentials
import okhttp3.OkHttpClient

/** Plays in the background with lock-screen / notification controls. Streams straight from Nextcloud. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val saver = object : Runnable {
        override fun run() {
            session?.player?.let { if (it.isPlaying) savePosition(it, force = false) }
            handler.postDelayed(this, 5_000)
        }
    }

    private val sleepTick = object : Runnable {
        override fun run() {
            val p = session?.player as? ExoPlayer ?: return
            val left = SleepTimer.remainingMs()
            if (SleepTimer.endAt == 0L) { p.volume = 1f; return }
            if (left <= 0) {
                AppLog.log("sleep timer: paused")
                p.pause(); p.volume = 1f; SleepTimer.cancel(); return
            }
            // Fade out over the last 20 seconds.
            p.volume = if (left < 20_000) left / 20_000f else 1f
            handler.postDelayed(this, 1_000)
        }
    }

    private fun applySleep(p: ExoPlayer) {
        p.pauseAtEndOfMediaItems = SleepTimer.endOfTrack
        handler.removeCallbacks(sleepTick)
        if (SleepTimer.endAt > 0) handler.post(sleepTick) else p.volume = 1f
    }

    /** When listening time was last added to the stats (0 = not playing). */
    private var listenMark = 0L

    private fun savePosition(p: Player, force: Boolean = true) {
        val id = p.currentMediaItem?.mediaId ?: return
        History.update(id, p.currentPosition, p.duration, force)
        val now = android.os.SystemClock.elapsedRealtime()
        if (listenMark > 0) Stats.listened(id, now - listenMark)
        listenMark = if (p.isPlaying) now else 0
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.log("service start")
        History.init(this)
        Stats.init(this)
        Downloads.init(this)
        val prefs = Prefs(this)
        // Read the login on every request so a changed app password takes effect without restarting.
        val http = OkHttpClient.Builder()
            .addInterceptor {
                it.proceed(it.request().newBuilder()
                    .header("Authorization", Credentials.basic(prefs.user, prefs.pass)).build())
            }
            .build()
        val player = ExoPlayer.Builder(this, NormalisingRenderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, OkHttpDataSource.Factory(http))))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                AppLog.log("play: ${item?.mediaId}")
                Stats.newSession()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AppLog.log(if (isPlaying) "playing → ${outputDevices()}" else "stopped/paused")
                if (isPlaying) listenMark = android.os.SystemClock.elapsedRealtime() else savePosition(player)
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                val oldId = old.mediaItem?.mediaId ?: return
                val newId = new.mediaItem?.mediaId ?: return
                if (oldId == newId) return
                if (listenMark > 0) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    Stats.listened(oldId, now - listenMark)
                    listenMark = now
                }
                // Leaving a track: remember where (or that it finished); arriving at one: resume it.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) History.markDone(oldId)
                else History.update(oldId, old.positionMs, C.TIME_UNSET)
                val resume = History.resumePos(newId)
                if (resume > 0) player.seekTo(resume)
            }
            override fun onPlayerError(error: PlaybackException) =
                AppLog.log("ERROR ${error.errorCodeName}: ${error.message} ${error.cause}")
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(t: AnalyticsListener.EventTime, f: Format, e: DecoderReuseEvaluation?) =
                AppLog.log("file format: ${f.sampleMimeType} ${f.sampleRate} Hz, ${f.channelCount} ch, ${f.bitrate / 1000} kbps")
            override fun onAudioTrackInitialized(t: AnalyticsListener.EventTime, c: AudioSink.AudioTrackConfig) =
                AppLog.log("output: ${c.sampleRate} Hz, channelMask ${c.channelConfig}, encoding ${c.encoding}, offload ${c.offload}")
            override fun onAudioSinkError(t: AnalyticsListener.EventTime, e: Exception) =
                AppLog.log("audio sink error: $e")
            override fun onAudioUnderrun(t: AnalyticsListener.EventTime, size: Int, ms: Long, elapsed: Long) =
                AppLog.log("underrun ${elapsed} ms since last feed")
        })
        player.setPlaybackSpeed(prefs.speed)
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // "End of track" sleep timer fired, or the user paused: either way the timer is done.
                if (!playWhenReady && SleepTimer.active &&
                    (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM || SleepTimer.endOfTrack)) {
                    AppLog.log("sleep timer: stopped at end of track")
                    SleepTimer.cancel()
                }
            }
        })
        SleepTimer.onChange = { handler.post { applySleep(player) } }
        applySleep(player)
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                val item = p.currentMediaItem ?: return
                PlayerWidget.update(this@PlaybackService, item.mediaMetadata.title ?: "", item.mediaMetadata.artist ?: "", p.playWhenReady)
            }
        })
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            // Play pressed (widget, headset, lock screen) with nothing loaded: carry on with the last track.
            override fun onPlaybackResumption(s: MediaSession, c: MediaSession.ControllerInfo) =
                try { com.google.common.util.concurrent.Futures.immediateFuture(lastTrackItem()) }
                catch (e: Exception) { com.google.common.util.concurrent.Futures.immediateFailedFuture(e) }
        }).build()
        handler.post(saver)
    }

    private fun lastTrackItem(): MediaSession.MediaItemsWithStartPosition {
        val prefs = Prefs(this)
        val id = prefs.lastTrack
        if (id.isEmpty()) throw IllegalStateException("nothing played yet")
        val uri = Downloads.localFile(id)?.let { android.net.Uri.fromFile(it) }
            ?: android.net.Uri.parse(Dav(prefs.server, prefs.user, prefs.pass).url(id))
        val item = MediaItem.Builder().setUri(uri).setMediaId(id)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                .setTitle(prettyName(id.substringAfterLast('/')))
                .setArtist(prettyFolder(id.substringBeforeLast('/', "").substringAfterLast('/')))
                .build())
            .build()
        AppLog.log("resume last track: $id")
        return MediaSession.MediaItemsWithStartPosition(listOf(item), 0, History.resumePos(id))
    }

    private fun outputDevices(): String {
        val am = getSystemService(AudioManager::class.java)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            .joinToString { "${it.productName} (type ${it.type})" }
            .ifEmpty { "speaker" } + ", volume ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saver)
        handler.removeCallbacks(sleepTick)
        SleepTimer.onChange = null
        session?.player?.let { savePosition(it) }
        Stats.save()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/**
 * Sends every file to Android in the same format (stereo, 44.1 kHz), so switching between e.g. a mono
 * 22 kHz audiobook and a stereo 44.1 kHz album never makes the Bluetooth path renegotiate.
 */
@OptIn(UnstableApi::class)
private class NormalisingRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val mix = ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 2))
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2))
        }
        val resample = SonicAudioProcessor().apply { setOutputSampleRateHz(44_100) }
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(mix, resample))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
