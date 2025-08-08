package com.wwwjsw.musicserver.ui.list

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.wwwjsw.musicserver.AudioDetailsBottomSheet
import com.wwwjsw.musicserver.Album
import com.wwwjsw.musicserver.models.MusicTrack

class ListOfAlbums {
    @Composable
    fun Render(
        albumList: List<Album> = emptyList(),
        colors: ColorScheme,
        localNetworkIp: String?
    ) {
        val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }
        var selectedDetails by remember { mutableStateOf("") }

        Log.i("com.wwwjsw.musicserver", "Album tracks: ${albumList.toString()}  $localNetworkIp")

        audioDetailsBottomSheet.Content {
            LazyColumn {
                items(albumList) { album ->
                    Column(modifier = Modifier
                        .clickable {
                            selectedDetails = "TODO: Remove this data"
                            if (localNetworkIp != null) {
                                audioDetailsBottomSheet.open(
                                    selectedDetails,
                                    album.id,
                                    localNetworkIp,
                                    MusicTrack(0, "", "", "", 0, "")
                                )
                            }
                        }
                        .background(colors.surface)
                        .fillMaxWidth()
                        .wrapContentHeight()
                    ) {
                        Text(text = album.toString())
                    }
                }
            }
        }
//        audioDetailsBottomSheet.Content {
//            LazyColumn {
//                Text(text = albumList.toString())
//            }
//        }
    }
}