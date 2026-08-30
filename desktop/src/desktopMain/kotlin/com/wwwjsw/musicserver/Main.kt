package com.wwwjsw.musicserver

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.wwwjsw.musicserver.models.MusicTrack
import com.wwwjsw.musicserver.server.DesktopMediaServer
import com.wwwjsw.musicserver.server.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import java.io.File

private const val DEFAULT_PORT = 8080

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Music Server – Desktop",
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            MusicServerApp()
        }
    }
}

@Composable
fun MusicServerApp() {
    val scope = rememberCoroutineScope()

    var musicRoot by remember { mutableStateOf(defaultMusicDir()) }
    var port by remember { mutableStateOf(DEFAULT_PORT.toString()) }
    var server by remember { mutableStateOf<DesktopMediaServer?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }
    var showPicker by remember { mutableStateOf(false) }

    val serverUrl = ipAddress?.let { "http://$it:$port" }
    val qrBitmap = remember(serverUrl) { serverUrl?.let { generateQr(it) } }

    fun startServer() {
        val portInt = port.toIntOrNull() ?: DEFAULT_PORT
        val root = musicRoot ?: return
        MusicScanner.invalidate()
        val frontendZip = extractFrontendZip()
        val srv = DesktopMediaServer(port = portInt, musicRoot = root, frontendZip = frontendZip)
        srv.start()
        server = srv
        isRunning = true
        ipAddress = srv.getLocalIpAddress()
        statusMessage = "Server running on port $portInt"

        scope.launch {
            isScanning = true
            statusMessage = "Scanning music library…"
            val found = withContext(Dispatchers.IO) { MusicScanner.getMusicTracks(root) }
            tracks = found
            isScanning = false
            statusMessage = "Server running – ${found.size} track(s) found"
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
        isRunning = false
        ipAddress = null
        tracks = emptyList()
        statusMessage = "Server stopped"
    }

    if (showPicker) {
        DirectoryPickerDialog(
            onDismiss = { showPicker = false },
            onConfirm = { dir ->
                musicRoot = dir
                showPicker = false
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header
            Text("Music Server", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(statusMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Music folder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { showPicker = true },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            musicRoot?.absolutePath ?: "Select folder…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                        )
                    }
                }

                Column(modifier = Modifier.width(100.dp)) {
                    Text("Port", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = port,
                        onValueChange = { if (!isRunning) port = it.filter { c -> c.isDigit() }.take(5) },
                        enabled = !isRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { if (isRunning) stopServer() else startServer() },
                        enabled = musicRoot != null,
                        colors = if (isRunning)
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        else ButtonDefaults.buttonColors(),
                        modifier = Modifier.width(100.dp),
                    ) {
                        Text(if (isRunning) "Stop" else "Start")
                    }
                }
            }

            // Server info + QR code
            if (isRunning && serverUrl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoRow(label = "Local IP", value = ipAddress ?: "unknown")
                        InfoRow(label = "Port", value = port)
                        InfoRow(label = "Player", value = "$serverUrl/")
                        InfoRow(label = "Tracks API", value = "$serverUrl/music")
                        InfoRow(label = "Albums API", value = "$serverUrl/albuns")
                    }

                    if (qrBitmap != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "QR code for $serverUrl",
                                modifier = Modifier
                                    .size(140.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                            )
                            Text("Scan to open player", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider()
            }

            // Track list
            if (isScanning) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Scanning library…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (tracks.isNotEmpty()) {
                Text(
                    "${tracks.size} track(s)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track)
                    }
                }
            } else if (isRunning) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No audio files found in selected folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Select a music folder and start the server.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${track.id + 1}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${track.artist} · ${track.album}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.duration),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Compose-native directory picker — works on any Linux WM/DE,
 * starts at "/" so /mnt, /media, and external drives are reachable.
 */
@Composable
fun DirectoryPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit,
) {
    var currentDir by remember { mutableStateOf(File("/")) }
    val entries = remember(currentDir) {
        currentDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.canRead() }, { it.name.lowercase() }))
            ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                currentDir.absolutePath,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                if (currentDir.parentFile != null) {
                    TextButton(onClick = { currentDir = currentDir.parentFile!! }) {
                        Text("⬆  ..")
                    }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(entries) { dir ->
                        TextButton(
                            onClick = { currentDir = dir },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "📁  ${dir.name}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                color = if (dir.canRead())
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentDir) }) {
                Text("Select \"${currentDir.name.ifEmpty { "/" }}\"")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val total = ms / 1_000
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun defaultMusicDir(): File? {
    val home = System.getProperty("user.home") ?: return null
    val homeCandidates = listOf("Music", "music", "Música", "Musica").map { File(home, it) }
    val mntCandidates = File("/mnt").listFiles()?.filter { it.isDirectory }.orEmpty()
    return (homeCandidates + mntCandidates).firstOrNull { it.isDirectory }
        ?: File(home).takeIf { it.isDirectory }
}

/**
 * Extracts music.zip from the JAR resources to a temp file so
 * [DesktopMediaServer] can open it as a [java.util.zip.ZipFile].
 */
private fun extractFrontendZip(): File? = try {
    val stream = object {}.javaClass.getResourceAsStream("/music.zip") ?: return null
    val tmp = File.createTempFile("music_frontend", ".zip").also { it.deleteOnExit() }
    tmp.outputStream().use { out -> stream.copyTo(out) }
    tmp
} catch (_: Exception) { null }

/**
 * Generates a ZXing QR code and converts it to a Compose [ImageBitmap] via Skia.
 */
private fun generateQr(content: String, size: Int = 300): ImageBitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val skBitmap = Bitmap().apply {
        allocPixels(ImageInfo.makeN32(size, size, ColorAlphaType.OPAQUE))
    }
    for (y in 0 until size) {
        for (x in 0 until size) {
            val color = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            skBitmap.erase(color, org.jetbrains.skia.IRect.makeXYWH(x, y, 1, 1))
        }
    }
    org.jetbrains.skia.Image.makeFromBitmap(skBitmap).asImageBitmap()
} catch (_: Exception) { null }
