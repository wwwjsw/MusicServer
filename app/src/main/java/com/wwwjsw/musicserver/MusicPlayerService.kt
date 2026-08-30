package com.wwwjsw.musicserver

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service that owns the ExoPlayer and MediaSession.
 *
 * Benefits over a player created inside a Composable:
 *  - Audio keeps playing when the BottomSheet / Activity is closed
 *  - MediaSession publishes controls to the lock screen automatically
 *  - media3 DefaultMediaNotificationProvider shows a persistent notification
 *    with play/pause, previous, and next buttons
 *
 * Bound by [MusicPlayerConnection] so the UI can control it via [MediaController].
 */
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause on headphone unplug
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /** Called by media3 to know which session to connect to. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
