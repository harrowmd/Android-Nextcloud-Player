package com.manytwo.ncplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver

/** Home-screen widget: title of the current/last track and prev / play-pause / next. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val last = Prefs(context).lastTrack
        update(context, if (last.isEmpty()) "Nextcloud Player" else prettyName(last.substringAfterLast('/')),
            prettyFolder(last.substringBeforeLast('/', "").substringAfterLast('/')), false)
    }

    companion object {
        /** Redraw every placed widget. Called by the player service whenever the track or play state changes. */
        fun update(context: Context, title: CharSequence, sub: CharSequence, playing: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(context.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_title, title)
            v.setTextViewText(R.id.w_sub, sub)
            v.setImageViewResource(R.id.w_play, if (playing) R.drawable.w_pause else R.drawable.w_play)
            v.setOnClickPendingIntent(R.id.w_play, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            v.setOnClickPendingIntent(R.id.w_prev, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            v.setOnClickPendingIntent(R.id.w_next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            v.setOnClickPendingIntent(R.id.w_text, PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            manager.updateAppWidget(ids, v)
        }

        /** A media-button press delivered through Media3's receiver, which starts the player service if needed. */
        private fun mediaKey(context: Context, code: Int): PendingIntent {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
            return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
