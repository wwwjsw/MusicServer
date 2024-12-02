import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wwwjsw.musicserver.GlideImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class AudioDetailsBottomSheet {

    private var isVisible by mutableStateOf(false)
    private var trackDetailsState by mutableStateOf("")
    private var trackID by mutableStateOf(0L)

    /**
     * Função para abrir o BottomSheet com os detalhes fornecidos.
     */
    fun open(details: String, id: Long) {
        trackDetailsState = details
        trackID = id
        isVisible = true
    }

    /**
     * Função para fechar o BottomSheet.
     */
    private fun close() {
        isVisible = false
    }

    /**
     * Composable que encapsula o ModalBottomSheet.
     */
    @Composable
    fun Content(content: @Composable () -> Unit) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coroutineScope = rememberCoroutineScope()

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
                            text = "Detalhes da Faixa",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = trackDetailsState,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GlideImage(
                            imageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=http://192.168.1.4:8080?audio_id=${trackID}",
                            modifier = Modifier.width(160.dp).height(160.dp).align(Alignment.CenterHorizontally),
                            contentDescription = "Imagem de exemplo"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Fechar",
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable {
                                    coroutineScope.launch {
                                        bottomSheetState.hide()
                                    }.invokeOnCompletion {
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
