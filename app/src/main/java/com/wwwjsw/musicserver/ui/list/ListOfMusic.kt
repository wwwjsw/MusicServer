package com.wwwjsw.musicserver.ui.list

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwjsw.musicserver.AudioDetailsBottomSheet
import com.wwwjsw.musicserver.models.MusicTrack

class ListOfMusic {
    @Composable
    fun Render(
        musicList: List<MusicTrack> = emptyList(),
        colors: ColorScheme,
        localNetworkIp: String?
    ) {
        val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }
        var selectedDetails by remember { mutableStateOf("") }

        Log.i("com.wwwjsw.musicserver", "Music tracks: $musicList  $localNetworkIp")

        audioDetailsBottomSheet.Content {
            LazyColumn (modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                items(musicList) { music ->
                    Column(modifier = Modifier
                        .clickable {
                            selectedDetails = "TODO: Remove this data"
                            if (localNetworkIp != null) {
                                audioDetailsBottomSheet.open(
                                    selectedDetails,
                                    music.id,
                                    localNetworkIp,
                                    music,
                                    null
                                )
                            }
                        }
                        .padding(4.dp)
                        .background(colors.surface)
                        .fillMaxWidth()
                        .wrapContentHeight()
                    ) {
                        Row {
                            music.thumbnail.let { thumbnail ->
                                if (thumbnail != null) {
                                    Image(
                                        bitmap = thumbnail.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(78.dp)
                                    )
                                }
                            }
                            Column {
                                music.title?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .fillMaxWidth(),
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                music.artist?.let {
                                    Text(
                                        text = if (it.isBlank() || it.equals(
                                                "<unknown>",
                                                ignoreCase = true
                                            )
                                        ) "Unknown Artist" else it,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .fillMaxWidth(),
                                        color = colors.secondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}