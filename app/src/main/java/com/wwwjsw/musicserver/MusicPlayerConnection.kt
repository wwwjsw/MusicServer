package com.wwwjsw.musicserver

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.wwwjsw.musicserver.models.Album
import com.wwwjsw.musicserver.models.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton that manages the [MediaController] connection to [MusicPlayerService].
 *
 * The UI observes [controller] to drive play/pause state; call [release] from
 * [MainActivity.onDestroy] to clean up.
 */
object MusicPlayerConnection {

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    fun connect(context: Context) {
        if (controllerFuture != null) return // already connecting / connected
        val token = SessionToken(
            context,
            ComponentName(context, MusicPlayerService::class.java),
        )
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener(
                { _controller.value = future.get() },
                MoreExecutors.directExecutor(),
            )
        }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _controller.value = null
    }

    // ── Playback helpers ──────────────────────────────────────────────────────

    fun playTrack(track: MusicTrack, serverIp: String, port: Int = 8080) {
        val ctrl = _controller.value ?: return
        val url = "http://$serverIp:$port/music?audio_id=${track.id}"
        val item = buildMediaItem(url, track)
        ctrl.setMediaItem(item)
        ctrl.prepare()
        ctrl.play()
    }

    fun playAlbum(album: Album, serverIp: String, port: Int = 8080, startIndex: Int = 0) {
        val ctrl = _controller.value ?: return
        val items = album.musics.map { track ->
            val url = "http://$serverIp:$port/music?audio_id=${track.id}"
            buildMediaItem(url, track)
        }
        ctrl.setMediaItems(items, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun buildMediaItem(url: String, track: MusicTrack): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setDisplayTitle(track.title)
                    .build(),
            )
            .build()
}
