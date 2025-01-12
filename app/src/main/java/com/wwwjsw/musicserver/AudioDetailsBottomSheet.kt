package com.wwwjsw.musicserver

import MusicTrack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class AudioDetailsBottomSheet {

    private var isVisible by mutableStateOf(false)
    private var trackDetailsState by mutableStateOf("")
    private var trackID by mutableLongStateOf(0L)
    private var localAddress by mutableStateOf("")
    private var actualMusic by mutableStateOf(MusicTrack(0, "", "", "", 0, ""))

    /**
     * open bottom sheet with details.
     */
    fun open(details: String, id: Long, localNetworkIp: String, music: MusicTrack) {
        trackDetailsState = details
        trackID = id
        isVisible = true
        localAddress = localNetworkIp
        actualMusic = music
    }

    /**
     * close bottom sheet.
     */
    private fun close() {
        isVisible = false
    }

    /**
     * composable to encapsulate the bottom sheet content.
     */
    @Composable
    fun Content(content: @Composable () -> Unit) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coroutineScope = rememberCoroutineScope()
        val musicUrl = "http://${localAddress}:8080/music?audio_id=${trackID}"

        Box(modifier = Modifier.fillMaxSize()) {
            content()

            if (isVisible) {
                ModalBottomSheet(
                    onDismissRequest = { close() },
                    sheetState = bottomSheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AudioPlayer(url = musicUrl, actualMusic = actualMusic)
                        Text(
                            text = "Close",
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable {
                                    coroutineScope
                                        .launch {
                                            bottomSheetState.hide()
                                        }
                                        .invokeOnCompletion {
                                            close()
                                        }
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Preview (showBackground = true)
@Composable
fun PreviewAudioDetailsBottomSheet() {
    val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }

    audioDetailsBottomSheet.Content {
        Column {
            Button(onClick = {
                audioDetailsBottomSheet.open(
                    "Details string",
                    75,
                    "localhost",
                    MusicTrack(0, "", "", "", 0, "")
                )
            }) {
                Text(text = "Open bottom sheet", modifier = Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Text(text = "Content of the bottom sheet", modifier = Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }

}