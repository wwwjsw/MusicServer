package com.wwwjsw.musicserver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wwwjsw.musicserver.local.StaticLists.menuItems
import com.wwwjsw.musicserver.models.FilterType
import com.wwwjsw.musicserver.models.MusicTrack
import com.wwwjsw.musicserver.ui.list.ListOfAlbums
import com.wwwjsw.musicserver.ui.list.ListOfMusic
import com.wwwjsw.musicserver.ui.theme.MusicServerTheme
import androidx.core.net.toUri

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
            data = url.toUri()
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
                            ListOfMusic().Render(
                                musicList = musicList.value,
                                colors = colors,
                                localNetworkIp = localNetworkIp
                            )
                        }
                        if (selectionFilter == FilterType.ALBUMS) {
                            Text(text = albumsList.value.toString())
                            ListOfAlbums().Render(
                                albumList = albumsList.value,
                                localNetworkIp = localNetworkIp,
                                colors = colors,
                            )
                        }
                    }
                }
            }
        }
    }
}