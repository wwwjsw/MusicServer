package com.wwwjsw.musicserver

import android.net.Uri
import com.wwwjsw.musicserver.models.MusicTrack
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import kotlin.toString

@Composable
fun AudioPlayer(
    modifier: Modifier = Modifier,
    url: String? = null,
    actualMusic: MusicTrack? = null,
    actualAlbum: Album? = null
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d("AudioPlayer", url.toString())
                        if (state == Player.STATE_READY) {
                            duration = this@apply.duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = "Playback error: ${error.message}"
                        Log.d("AudioPlayer", actualAlbum.toString())
                        Log.e("AudioPlayer", "Playback error", error)
                    }
                })
            }
    }

    LaunchedEffect(url) {
        if (url?.isNotEmpty() == true) {
            try {
                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            } catch (e: Exception) {
                playbackError = "Invalid URL: $url"
                Log.e("AudioPlayer", "URL error", e)
            }
        }
    }

    LaunchedEffect(actualAlbum) {
        if (url === null && actualAlbum != null) {
            Log.d("AudioPlayer", "Loading album: ${actualAlbum.album}")

            val mediaItems = actualAlbum.musics.mapNotNull { music ->
                try {
                    createMediaItemFromUri(music.uri, music)
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Invalid music URI: ${music.uri}", e)
                    null
                }
            }

            if (mediaItems.isNotEmpty()) {
                exoPlayer.setMediaItems(mediaItems)
                exoPlayer.prepare()
                Log.d("AudioPlayer", "Loaded ${mediaItems.size} tracks")
            } else {
                playbackError = "No valid tracks found in album"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        playbackError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        actualMusic?.title?.let { actualMusicTitle  ->
            Text(
                text = actualMusicTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        actualAlbum?.album?.let { actualAlbumTitle ->
            Text(
                text = actualAlbumTitle,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (actualAlbum != null && exoPlayer.mediaItemCount > 0) {
            val currentMediaItem = exoPlayer.currentMediaItem
            Text(
                text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Track ${exoPlayer.currentMediaItemIndex + 1}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { newValue ->
                currentPosition = newValue.toLong()
                exoPlayer.seekTo(currentPosition)
            },
            onValueChangeFinished = {
                exoPlayer.seekTo(currentPosition)
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition))
            Text(formatTime(duration))
        }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (actualAlbum != null) {
                IconButton(
                    onClick = {
                        if (exoPlayer.hasPreviousMediaItem()) {
                            exoPlayer.seekToPreviousMediaItem()
                        }
                    },
                    enabled = exoPlayer.hasPreviousMediaItem(),
                    modifier = Modifier.rotate(180f)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_skip_next_24),
                        contentDescription = "Previous track"
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            IconButton(onClick = {
                exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10000))
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.twotone_replay_10_24),
                    contentDescription = "Rewind 10 seconds"
                )
            }

            IconButton(
                onClick = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            ) {
                Icon(
                    painter = painterResource(id =
                        if (isPlaying) R.drawable.twotone_pause_circle_24
                        else R.drawable.twotone_play_circle_24
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }

            IconButton(onClick = {
                exoPlayer.seekTo(minOf(duration, exoPlayer.currentPosition + 10000))
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.twotone_forward_10_24),
                    contentDescription = "Forward 10 seconds"
                )
            }

            if (actualAlbum != null) {
                IconButton(
                    onClick = {
                        if (exoPlayer.hasNextMediaItem()) {
                            exoPlayer.seekToNextMediaItem()
                        }
                    },
                    enabled = exoPlayer.hasNextMediaItem()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_skip_next_24),
                        contentDescription = "Next track"
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}

private fun createMediaItemFromUri(uriString: String, music: MusicTrack? = null): MediaItem {
    val uri = uriString.toUri()

    return MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().apply {
                music?.let {
                    setTitle(it.title)
                    setArtist(it.artist)
                    setAlbumTitle(it.album)
                }
                setDisplayTitle(music?.title ?: "Unknown Title")
            }.build()
        )
        .setMimeType(getMimeTypeFromUri(uri))
        .build()
}

private fun getMimeTypeFromUri(uri: Uri): String? {
    return when {
        uri.toString().contains(".mp3") -> "audio/mpeg"
        uri.toString().contains(".m4a") -> "audio/mp4"
        uri.toString().contains(".ogg") -> "audio/ogg"
        uri.toString().contains(".wav") -> "audio/wav"
        uri.toString().contains(".flac") -> "audio/flac"
        else -> null
    }
}