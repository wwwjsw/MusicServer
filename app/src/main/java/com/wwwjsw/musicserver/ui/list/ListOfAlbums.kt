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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.wwwjsw.musicserver.AudioDetailsBottomSheet
import com.wwwjsw.musicserver.models.Album

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
            LazyColumn (modifier = Modifier.fillMaxWidth().padding(16.dp)){
                items(albumList) { album ->
                    Row (modifier = Modifier
                        .clickable {
                            selectedDetails = "TODO: Remove this data"
                            if (localNetworkIp != null) {
                                audioDetailsBottomSheet.open(
                                    selectedDetails,
                                    null,
                                    localNetworkIp,
                                    null,
                                    album
                                )
                            }
                        }
                        .background(colors.surface)
                        .padding(4.dp)
                        .fillMaxWidth()
                        .wrapContentHeight()
                    ) {
                        album.thumbnail.let { thumbnail ->
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(78.dp)
                                )
                            }
                        }
                        Column (modifier = Modifier.align(Alignment.CenterVertically).padding(16.dp)) {
                            Text(text = album.album)
                        }
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