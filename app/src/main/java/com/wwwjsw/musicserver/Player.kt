package com.wwwjsw.musicserver

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.wwwjsw.musicserver.helpers.formatTime
import com.wwwjsw.musicserver.models.Album
import com.wwwjsw.musicserver.models.MusicTrack
import kotlinx.coroutines.delay

/**
 * Player UI that drives [MusicPlayerService] through a [MediaController].
 *
 * The controller (and the ExoPlayer inside the service) stay alive when the
 * BottomSheet is dismissed — audio never stops unless the user explicitly pauses
 * or the service is destroyed.
 */
@Composable
fun AudioPlayer(
    modifier: Modifier = Modifier,
    controller: MediaController?,
    url: String? = null,
    actualMusic: MusicTrack? = null,
    actualAlbum: Album? = null,
) {
    var isPlaying by remember { mutableStateOf(controller?.isPlaying == true) }
    var currentPosition by remember { mutableStateOf(controller?.currentPosition ?: 0L) }
    var duration by remember { mutableStateOf(controller?.duration?.coerceAtLeast(0L) ?: 0L) }
    var currentMediaIndex by remember { mutableStateOf(controller?.currentMediaItemIndex ?: 0) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Attach listener to the controller (re-attached whenever controller changes)
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                currentMediaIndex = controller?.currentMediaItemIndex ?: 0
                duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message
                Log.e("AudioPlayer", "Playback error", error)
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
        // NOTE: we intentionally do NOT release the controller here.
        // Lifecycle belongs to MusicPlayerConnection / MusicPlayerService.
    }

    // Load media into the service player when the composable first appears
    // (or when the target track/album changes). Skip if already playing same item.
    LaunchedEffect(url, actualAlbum) {
        val ctrl = controller ?: return@LaunchedEffect
        if (url != null) {
            val item = buildMediaItem(url, actualMusic)
            if (ctrl.currentMediaItem?.localConfiguration?.uri?.toString() != url) {
                ctrl.setMediaItem(item)
                ctrl.prepare()
                ctrl.play()
            }
        } else if (actualAlbum != null) {
            val items = actualAlbum.musics.mapNotNull { music ->
                try { buildMediaItem(music.uri, music) }
                catch (e: Exception) { Log.e("AudioPlayer", "Bad URI ${music.uri}", e); null }
            }
            if (items.isNotEmpty()) {
                ctrl.setMediaItems(items)
                ctrl.prepare()
                ctrl.play()
            }
        }
    }

    // Poll position every second (lightweight; controller already on main thread)
    LaunchedEffect(controller) {
        while (true) {
            currentPosition = controller?.currentPosition ?: 0L
            currentMediaIndex = controller?.currentMediaItemIndex ?: 0
            delay(1_000)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        playbackError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        actualMusic?.title?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
        actualAlbum?.album?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (actualAlbum != null) {
            Spacer(Modifier.height(16.dp))
            PlaylistTracks(
                album = actualAlbum,
                currentTrackIndex = currentMediaIndex,
                onTrackSelected = { index ->
                    controller?.seekToDefaultPosition(index)
                    controller?.play()
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            )
        }

        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { currentPosition = it.toLong() },
            onValueChangeFinished = { controller?.seekTo(currentPosition) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition))
            Text(formatTime(duration))
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Previous
            if (actualAlbum != null) {
                IconButton(
                    onClick = { controller?.seekToPreviousMediaItem() },
                    enabled = controller?.hasPreviousMediaItem() == true,
                    modifier = Modifier.rotate(180f),
                ) {
                    Icon(painterResource(R.drawable.baseline_skip_next_24), "Previous")
                }
            } else Spacer(Modifier.size(48.dp))

            // Rewind 10s
            IconButton(onClick = {
                controller?.seekTo((controller.currentPosition - 10_000).coerceAtLeast(0))
            }) {
                Icon(painterResource(R.drawable.twotone_replay_10_24), "Rewind 10s")
            }

            // Play / Pause
            IconButton(onClick = {
                if (isPlaying) controller?.pause() else controller?.play()
            }) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.twotone_pause_circle_24 else R.drawable.twotone_play_circle_24),
                    if (isPlaying) "Pause" else "Play",
                )
            }

            // Forward 10s
            IconButton(onClick = {
                controller?.seekTo((controller.currentPosition + 10_000).coerceAtMost(duration))
            }) {
                Icon(painterResource(R.drawable.twotone_forward_10_24), "Forward 10s")
            }

            // Next
            if (actualAlbum != null) {
                IconButton(
                    onClick = { controller?.seekToNextMediaItem() },
                    enabled = controller?.hasNextMediaItem() == true,
                ) {
                    Icon(painterResource(R.drawable.baseline_skip_next_24), "Next")
                }
            } else Spacer(Modifier.size(48.dp))
        }
    }
}

private fun buildMediaItem(url: String, track: MusicTrack? = null): MediaItem =
    MediaItem.Builder()
        .setUri(url.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track?.title ?: "Unknown Title")
                .setArtist(track?.artist)
                .setAlbumTitle(track?.album)
                .setDisplayTitle(track?.title ?: "Unknown Title")
                .build(),
        )
        .setMimeType(mimeTypeFor(url.toUri()))
        .build()

private fun mimeTypeFor(uri: Uri): String? = when {
    uri.toString().endsWith(".mp3")  -> "audio/mpeg"
    uri.toString().endsWith(".m4a")  -> "audio/mp4"
    uri.toString().endsWith(".ogg")  -> "audio/ogg"
    uri.toString().endsWith(".opus") -> "audio/ogg"
    uri.toString().endsWith(".wav")  -> "audio/wav"
    uri.toString().endsWith(".flac") -> "audio/flac"
    else -> null
}
