package com.wwwjsw.musicserver.ui.list

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.wwwjsw.musicserver.AudioDetailsBottomSheet
import com.wwwjsw.musicserver.Musics
import com.wwwjsw.musicserver.models.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THUMBNAIL_SIZE = 78.dp

class ListOfAlbums {
    @Composable
    fun Render(
        albumList: List<Album> = emptyList(),
        colors: ColorScheme,
        localNetworkIp: String?,
        controller: MediaController?,
    ) {
        val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }

        audioDetailsBottomSheet.Content(controller = controller) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                items(albumList, key = { it.id }) { album ->
                    AlbumRow(
                        album = album,
                        colors = colors,
                        onClick = {
                            if (localNetworkIp != null) {
                                audioDetailsBottomSheet.open(
                                    localNetworkIp = localNetworkIp,
                                    album = album,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Single album row. Thumbnail is fetched via [produceState] on a background
 * thread only when this composable enters the composition (i.e. becomes
 * visible in the lazy list). No thumbnail I/O blocks the initial list render.
 */
@Composable
private fun AlbumRow(
    album: Album,
    colors: ColorScheme,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    // produceState starts a coroutine scoped to this composable's lifecycle.
    // When the item scrolls off screen and is disposed, the coroutine is cancelled.
    val thumbnailState by produceState<Bitmap?>(initialValue = null, key1 = album.id) {
        value = withContext(Dispatchers.IO) {
            Musics.loadAlbumThumbnail(context, album.id)
        }
    }

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(colors.surface)
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed-size slot so the row doesn't shift when the thumbnail loads
        Box(
            modifier = Modifier.size(THUMBNAIL_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumbnailState
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = album.album,
                    modifier = Modifier.size(THUMBNAIL_SIZE),
                )
            } else {
                // Subtle spinner while the thumbnail is being fetched
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(horizontal = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = album.album,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = "${album.musics.size} faixa${if (album.musics.size != 1) "s" else ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
