import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wwwjsw.musicserver.QrCodeView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class AudioDetailsBottomSheet {

    private var isVisible by mutableStateOf(false)
    private var trackDetailsState by mutableStateOf("")
    private var trackID by mutableLongStateOf(0L)
    private var localAddress by mutableStateOf("")

    /**
     * open bottom sheet with details.
     */
    fun open(details: String, id: Long, localNetworkIp: String) {
        trackDetailsState = details
        trackID = id
        isVisible = true
        localAddress = localNetworkIp
    }

    /**
     * close bottom sheet.
     */
    private fun close() {
        isVisible = false
    }
    /**
     * composable to button to play music on server
     */
    @Composable
    fun PlayMusicButton(musicUrl: String) {
        val context = LocalContext.current
        Button(onClick = {
            try {
                Log.d("MusicIntent", "Attempting to play music from URL: $musicUrl")

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(musicUrl), "audio/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val resolvedActivity = intent.resolveActivity(context.packageManager)
                if (resolvedActivity != null) {
                    Log.d("MusicIntent", "Found an app to handle the intent: ${resolvedActivity.className}")
                    context.startActivity(intent)
                } else {
                    Log.w("MusicIntent", "No app found to handle audio intent.")
                    Toast.makeText(context, "No apps available to play audio.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MusicIntent", "Error while trying to play music: ${e.message}")
                Toast.makeText(context, "Error opening the music.", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }) {
            Text("Open Music")
        }
    }

    /**
     * composable to encapsulate the bottom sheet content.
     */
    @Composable
    fun Content(content: @Composable () -> Unit) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coroutineScope = rememberCoroutineScope()
        val musicUrl = "http://${localAddress}:8080?audio_id=${trackID}"

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
                        Text(
                            text = "Track details",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = trackDetailsState,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        PlayMusicButton(musicUrl)
                        Spacer(modifier = Modifier.height(8.dp))
                        QrCodeView(
                            content = musicUrl,
                            size = 512,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                audioDetailsBottomSheet.open("Details string", 75, "localhost")
            }) {
                Text(text = "Open bottom sheet", modifier = Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Text(text = "Content of the bottom sheet", modifier = Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }

}