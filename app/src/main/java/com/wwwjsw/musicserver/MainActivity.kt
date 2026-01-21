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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.wwwjsw.musicserver.models.Album
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
            data = url.toUri()
        }
        context.startActivity(intent)
    }
}

