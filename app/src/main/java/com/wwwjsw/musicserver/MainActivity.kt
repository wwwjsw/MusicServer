package com.wwwjsw.musicserver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wwwjsw.musicserver.local.StaticLists.menuItems
import com.wwwjsw.musicserver.models.FilterType
import com.wwwjsw.musicserver.models.MusicTrack
import com.wwwjsw.musicserver.ui.theme.MusicServerTheme

class MainActivity : ComponentActivity() {
    private lateinit var server: MediaServer
    private lateinit var selectionFilter: FilterType
    private lateinit var musicListState: MutableState<List<MusicTrack>>
    private lateinit var albumsListState: MutableState<List<Album>>


    private fun startServer() {
        val musicPaths = Musics.getMusicPaths(this)
        loadMusics()
        selectionFilter = FilterType.ALL
        Log.d("com.wwwjsw.musicserver.MediaServer", "Music paths: $musicPaths")
        server = MediaServer(8080, this)

        server.start()
        musicPaths.forEach {}
    }

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach { entry ->
                Log.w("com.wwwjsw.musicserver.MediaServer", "Permission: ${entry.key}, Granted: ${entry.value}")
            }

            val allGranted = permissions.all { it.value }
            if (allGranted) {
                loadMusics()
            } else {
                Log.w("com.wwwjsw.musicserver.MediaServer", "Not all permissions were granted.")
            }
        }


    private fun loadMusics() {
        // Load music tracks
        val tracks = Musics.getMusicTracks(this)
        musicListState.value = tracks
        Log.d("com.wwwjsw.musicserver.MediaServer", "Music tracks loaded: ${tracks.size}  $tracks")

        // Load albums
        val albums = Musics.getAlbums(this)
        albumsListState.value = albums
        Log.d("com.wwwjsw.musicserver.MediaServer", "Albums: $albums")
    }

    override fun onStart() {
        super.onStart()

        val neededPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.w(
                "com.wwwjsw.musicserver.MediaServer",
                "Requesting permissions: $permissionsToRequest"
            )
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.i(
                "com.wwwjsw.musicserver.MediaServer",
                "All permissions are already granted."
            )
            loadMusics()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        musicListState = mutableStateOf(emptyList())
        albumsListState = mutableStateOf(emptyList())

        setContent {
            MusicServerTheme {
                MainActivityContent(
                    localNetworkIp = server.getLocalIpAddress(),
                    colors = MaterialTheme.colorScheme,
                    musicListState,
                    albumsListState,
                    context = this
                )
            }
        }

        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}

fun openWebPlayer(context: Context, localNetworkIp: String?) {
    localNetworkIp?.let {
        val url = "http://$it:8080"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        context.startActivity(intent)
    }
}

@Composable
fun MainActivityContent(
    localNetworkIp: String?,
    colors: ColorScheme,
    musicListState: MutableState<List<MusicTrack>>,
    albumsListState: MutableState<List<Album>>,
    context: Context,
) {
    // Replace regular variable with mutableState
    var selectionFilter by remember { mutableStateOf(FilterType.ALL) }
    val musicList = remember { musicListState }
    val albumsList = remember { albumsListState }
    
    MusicServerTheme {
        Box(modifier = Modifier
            .background(colors.background)
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            Column {
                if (localNetworkIp != null) {
                    Box(modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 15.dp)
                        .align(Alignment.CenterHorizontally)
                    ) {
                        QrCodeView(content = "http://${localNetworkIp}:8080", size = 200)
                    }
                }
                Card(
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable(onClick = { openWebPlayer(context, localNetworkIp) })
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Open Web Player in Browser",
                        modifier = Modifier
                            .padding(16.dp)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), // Define 2 colunas fixas
                    modifier = Modifier.padding(8.dp)
                ) {
                    items(menuItems) { item ->
                        Card(
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable(
                                    onClick = {
                                        Log.i(
                                            "com.wwwjsw.musicserver",
                                            "Selected filter: ${item.filter}"
                                        )
                                        selectionFilter = item.filter
                                    },
                                    indication = rememberRipple(bounded = true),
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = item.imageVector),
                                    contentDescription = item.name
                                )
                                Text(
                                    text = item.name,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }







                Column {
                    Box(modifier = Modifier
                        .background(colors.background)
                        .fillMaxWidth()
                    ) {
                        if (selectionFilter == FilterType.ALL) {
                            ListOfMusic(
                                musicList = musicList.value,
                                colors = colors,
                                localNetworkIp = localNetworkIp
                            )
                        }
                        if (selectionFilter == FilterType.ALBUMS) {
                            Text(text = albumsList.value.toString())
//                            ListOfAlbums(
//                                albumList = emptyList(),
//                                colors = colors,
//                                localNetworkIp = localNetworkIp
//                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListOfMusic(
    musicList: List<MusicTrack> = emptyList(),
    colors: ColorScheme,
    localNetworkIp: String?
) {
    val audioDetailsBottomSheet = remember { AudioDetailsBottomSheet() }
    var selectedDetails by remember { mutableStateOf("") }

    Log.i("com.wwwjsw.musicserver", "Music tracks: $musicList  $localNetworkIp")

    audioDetailsBottomSheet.Content {
        LazyColumn {
            items(musicList) { music ->
                Column(modifier = Modifier
                    .clickable {
                        selectedDetails = "TODO: Remove this data"
                        if (localNetworkIp != null) {
                            audioDetailsBottomSheet.open(
                                selectedDetails,
                                music.id,
                                localNetworkIp,
                                music
                            )
                        }
                    }
                    .background(colors.surface)
                    .fillMaxWidth()
                    .wrapContentHeight()
                ) {
                    music.title?.let {
                        Text(
                            text = it,
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxWidth(),
                            color = colors.onSurface,
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
                            color = colors.secondary,
                            maxLines = 1,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun Main(
    name: String, onFetchFiles: () -> Unit,
    musicList: List<MusicTrack> = emptyList(),
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
        localNetworkIp = "127.0.0.1",
        colors = colors,
    )
}