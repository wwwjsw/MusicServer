package com.wwwjsw.musicserver

import AudioDetailsBottomSheet
import MediaServer
import MusicTrack
import android.content.ContentUris
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wwwjsw.musicserver.ui.theme.MusicServerTheme
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    private lateinit var server: MediaServer

    private fun getMusicPaths(context: Context): List<String> {
        val audioUri =  MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        return context.contentResolver.query(audioUri, projection, selection, null, null)?.use { cursor ->
            List(cursor.count) { index ->
                cursor.moveToPosition(index)
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                ContentUris.withAppendedId(audioUri, id).toString()
            }
        } ?: emptyList()
    }

    private fun getMusicTracks(context: Context): List<MusicTrack> {
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        return context.contentResolver.query(audioUri, projection, selection, null, null)?.use { cursor ->
            mutableListOf<MusicTrack>().apply {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val uri = ContentUris.withAppendedId(audioUri, id).toString()

                    add(MusicTrack(id, title, artist, album, duration, uri))
                }
            }
        } ?: emptyList()
    }

    private fun startServer() {
        val musicPaths = getMusicPaths(this)
        Log.d("MediaServer", "Music paths: $musicPaths")
        server = MediaServer(8080, this)

        server.start()
        musicPaths.forEach {}
    }

    private fun restartServer() {
        server.stop()
        startServer()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (inetAddress in addresses) {
                    // Ignorar endereços loopback (como 127.0.0.1)
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null // Retorna null se o endereço IP não for encontrado
    }

    private fun openWebPage(context: Context, url: String) {
        try {
            val webpage: Uri = Uri.parse(url)
            val intent = Intent(Intent.ACTION_VIEW, webpage).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Forçar seletor para evitar bloqueio
            val chooser = Intent.createChooser(intent, "Chose a browser")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro to open URL!", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permissão concedida
                // Aqui você pode executar o código que necessita da permissão
                println("Permissão concedida")
            } else {
                // Permissão negada
                // Você pode mostrar uma mensagem ou tratar a falta da permissão
                println("Permissão negada")
            }
        }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Verifica se a permissão foi concedida
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permissão já concedida
                println("Permissão já concedida")
            } else {
                // Solicita a permissão
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startServer()

        setContent {
            MusicServerTheme {
                val colors = MaterialTheme.colorScheme

                Main(
                    name = "Server 🛜",
                    onFetchFiles = { restartServer() },
                    musicList = getMusicTracks(this),
                    openLinkToMusic = { id -> openWebPage(this, "http://localhost:8080?audio_id=$id") }, // @DEPRECATED - Use AudioDetailsBottomSheet instead
                    localNetworkIp = getLocalIpAddress(),
                    colors = colors
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}

@Composable
fun ListOfMusic(
    musicList: List<MusicTrack> = emptyList(),
    openLinkToMusic: (id: Long) -> Unit, // @DEPRECATED - Use AudioDetailsBottomSheet instead
    colors: ColorScheme,
    localNetworkIp: String?
) {
    val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }
    var selectedDetails by remember { mutableStateOf("") }

    Log.i("MediaServer", "Music tracks: $musicList")

    audioDetailsBottomSheet.Content {
        LazyColumn {
            items(musicList) { music ->
                Column(modifier = Modifier
                    .clickable {
                        selectedDetails =
                            "Artist: ${music.artist ?: "Unknown"}\nDuração: ${((music.duration ?: 0) / 1000) / 60}:${((music.duration ?: 0) / 1000) % 60}\nÁlbum: ${music.album ?: "Unknown"}"
                        if (localNetworkIp != null) {
                            audioDetailsBottomSheet.open(selectedDetails, music.id, localNetworkIp)
                        }
                    }
                    .background(colors.background)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(10.dp)
                    .border(1.dp, colors.primary, RoundedCornerShape(8.dp)) // Borda arredondada
                    .clip(RoundedCornerShape(8.dp))
                ) {
                    music.title?.let {
                        Text(
                            text = it,
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxWidth(),
                            color = colors.primary,
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
                            color = colors.primary,
                            maxLines = 1,
                        )
                    }
                    music.duration?.let {
                        Text(
                            text = "${((it / 1000) / 60).toString()}:${((it / 1000) % 60).toString()}",
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxWidth(),
                            maxLines = 1,
                            color = colors.secondary
                        )
                    }
                    HorizontalDivider()
                }
//            }
            }
        }
    }
}

@Composable
fun Main(
    name: String, onFetchFiles: () -> Unit,
    musicList: List<MusicTrack> = emptyList(),
    openLinkToMusic: (id: Long) -> Unit, // @DEPRECATED - Use AudioDetailsBottomSheet instead
    localNetworkIp: String?,
    colors: ColorScheme
) {
    MusicServerTheme {
        Box(modifier = Modifier
            .background(colors.background)
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            Column {
                Button(onClick = { onFetchFiles() }, modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(text = "Fetch new files -  $name")
                }
                if (localNetworkIp != null) {
                    Text(
                        text = "Server Address: ${localNetworkIp}:8080",
                        modifier = Modifier
                            .align(
                                Alignment.CenterHorizontally
                            )
                            .padding(bottom = 5.dp)
                            .padding(horizontal = 16.dp),
                        color = colors.primary
                    )
                }
                Column {
                    Box(modifier = Modifier
                        .background(colors.onBackground)
                        .fillMaxWidth()
                    ) {
                        ListOfMusic(
                            musicList,
                            openLinkToMusic,
                            colors,
                            localNetworkIp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    val colors = MaterialTheme.colorScheme

    val musicList = listOf(
        MusicTrack(1, "Title 1", "Artist 1", "Album 1", 1000, ""),
        MusicTrack(2, "Title 2", "Artist 2", "Album 2", 2000, ""),
    )

    Main(
        name = "Server 🛜",
        onFetchFiles = {},
        musicList = musicList,
        openLinkToMusic = {},
        localNetworkIp = "127.0.0.1",
        colors = colors,
    )
}