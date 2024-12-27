package com.wwwjsw.musicserver

import MusicTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioPlayer(
                url = "https://archive.org/download/01.CrucifyMeFeat.Lights/01.%20Crucify%20Me%20%28Feat.%20Lights%29.mp3",
                actualMusic = MusicTrack(0, "", "", "", 0, "")
            )
        }
    }
}

@Composable
fun AudioPlayer(
    url: String,
    modifier: Modifier = Modifier,
    actualMusic: MusicTrack,
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    // Optimized for audio handle
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                // audio configuration
                setHandleAudioBecomingNoisy(true) // pause when disconnect headphone
                setWakeMode(C.WAKE_MODE_NETWORK) // keep CPU active during streaming

                val mediaItem = MediaItem.fromUri(url)
                setMediaItem(mediaItem)
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            duration = actualMusic.duration!!
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            }
    }

    // Player UI
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = actualMusic.title!!,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = {
                currentPosition = it.toLong()
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
            IconButton(onClick = { exoPlayer.seekTo(maxOf(0, currentPosition - 10000)) }) {
                val replayIcon = painterResource(id = R.drawable.twotone_replay_10_24)
                Icon(
                    replayIcon,
                    contentDescription = "Voltar 10 segundos"
                )
            }
            IconButton(
                onClick = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            ) {
                val pauseIcon = painterResource(id = R.drawable.twotone_pause_circle_24)
                val playIcon = painterResource(id = R.drawable.twotone_play_circle_24)
                Icon(
                    if (isPlaying) pauseIcon else playIcon,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir"
                )
            }
            IconButton(onClick = { exoPlayer.seekTo(minOf(duration, currentPosition + 10000)) }) {
                val forwardIcon = painterResource(id = R.drawable.twotone_forward_10_24)

                Icon(forwardIcon, contentDescription = "Avançar 10 segundos")
            }
        }
    }

    // Current position Update in every second
    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            delay(1000)
        }
    }

    // Clear composable on release player
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}