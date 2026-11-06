package com.wwwjsw.musicserver.ui.list

import android.graphics.Bitmap
import android.util.LruCache
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.wwwjsw.musicserver.AudioDetailsBottomSheet
import com.wwwjsw.musicserver.Musics
import com.wwwjsw.musicserver.R
import com.wwwjsw.musicserver.models.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THUMBNAIL_SIZE = 78.dp
private const val THUMBNAIL_CACHE_SIZE = 64

/** Resultado de uma leitura de capa sob demanda. */
private sealed interface ThumbnailLoad {
    data class Loaded(val bitmap: Bitmap) : ThumbnailLoad
    data object Unavailable : ThumbnailLoad
    data object Loading : ThumbnailLoad
}

class ListOfAlbums {
    @Composable
    fun Render(
        albumList: List<Album> = emptyList(),
        colors: ColorScheme,
        localNetworkIp: String?,
        controller: MediaController?,
    ) {
        val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }
        // Vive enquanto a lista existir, para que cada capa seja lida do disco
        // uma única vez, mesmo quando a linha é descartada e recomposta na rolagem.
        val thumbnailCache = remember { LruCache<Long, Bitmap>(THUMBNAIL_CACHE_SIZE) }

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
                        thumbnailCache = thumbnailCache,
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
 * Linha de um álbum. A capa é buscada via [produceState] em uma thread de IO
 * somente quando este composable entra na composição (ou seja, fica visível
 * na lista lazy). Nenhuma leitura de capa bloqueia a primeira renderização.
 */
@Composable
private fun AlbumRow(
    album: Album,
    colors: ColorScheme,
    thumbnailCache: LruCache<Long, Bitmap>,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    // Acerto no cache é resolvido de forma síncrona, então voltar na lista não
    // mostra spinner. Um miss carrega fora da main thread; a corrotina do
    // produceState é cancelada quando a linha sai da lista.
    val cachedThumbnail = remember(album.id) { thumbnailCache.get(album.id) }
    val loadState by produceState<ThumbnailLoad>(
        initialValue = if (cachedThumbnail != null) ThumbnailLoad.Loaded(cachedThumbnail) else ThumbnailLoad.Loading,
        key1 = album.id,
    ) {
        if (value !is ThumbnailLoad.Loaded) {
            val loaded = withContext(Dispatchers.IO) {
                Musics.loadAlbumThumbnail(context, album.id)
            }
            value = if (loaded != null) {
                thumbnailCache.put(album.id, loaded)
                ThumbnailLoad.Loaded(loaded)
            } else {
                ThumbnailLoad.Unavailable
            }
        }
    }
    val bmp = (loadState as? ThumbnailLoad.Loaded)?.bitmap
    val thumbnailFailed = loadState is ThumbnailLoad.Unavailable

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(colors.surface)
            .padding(4.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Espaço de tamanho fixo para a linha não pular quando a capa carrega
        Box(
            modifier = Modifier.size(THUMBNAIL_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Decorativa: o título do álbum já é anunciado pelo Text abaixo
                bmp != null -> Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(THUMBNAIL_SIZE),
                )
                // Sem capa disponível (ex.: permissão de mídia negada)
                thumbnailFailed -> Icon(
                    painter = painterResource(R.drawable.cd),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(THUMBNAIL_SIZE),
                )
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }

        Column(
            modifier = Modifier
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
