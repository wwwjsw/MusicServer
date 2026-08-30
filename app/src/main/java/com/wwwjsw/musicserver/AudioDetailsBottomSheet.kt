package com.wwwjsw.musicserver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.wwwjsw.musicserver.models.Album
import com.wwwjsw.musicserver.models.MusicTrack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class AudioDetailsBottomSheet {

    private var isVisible by mutableStateOf(false)
    private var trackID by mutableStateOf<Long?>(null)
    private var localAddress by mutableStateOf("")
    private var actualMusic by mutableStateOf<MusicTrack?>(null)
    private var actualAlbum by mutableStateOf<Album?>(null)

    fun open(
        id: Long? = null,
        localNetworkIp: String,
        music: MusicTrack? = null,
        album: Album? = null,
    ) {
        trackID = id
        isVisible = true
        localAddress = localNetworkIp
        actualMusic = music
        actualAlbum = album
    }

    @Composable
    fun Content(
        controller: MediaController?,
        content: @Composable () -> Unit,
    ) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coroutineScope = rememberCoroutineScope()
        val musicUrl = "http://$localAddress:8080/music?audio_id=$trackID"

        Box(modifier = Modifier.fillMaxSize()) {
            content()

            if (isVisible) {
                ModalBottomSheet(
                    // Dismiss hides the sheet but does NOT stop playback —
                    // the player lives in MusicPlayerService, not in this composable.
                    onDismissRequest = { isVisible = false },
                    sheetState = bottomSheetState,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Spacer(Modifier.height(16.dp))
                        AudioPlayer(
                            controller = controller,
                            url = musicUrl.takeIf { trackID != null },
                            actualMusic = actualMusic,
                            actualAlbum = actualAlbum,
                        )
                        Text(
                            text = "Close",
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable {
                                    coroutineScope
                                        .launch { bottomSheetState.hide() }
                                        .invokeOnCompletion { isVisible = false }
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
