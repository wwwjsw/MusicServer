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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.wwwjsw.musicserver.models.Album
import com.wwwjsw.musicserver.models.FilterType
import com.wwwjsw.musicserver.models.MusicTrack
import com.wwwjsw.musicserver.ui.theme.MusicServerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var server: MediaServer
    private lateinit var selectionFilter: FilterType
    private lateinit var musicListState: MutableState<List<MusicTrack>>
    private lateinit var albumsListState: MutableState<List<Album>>

    // ── Permissions ───────────────────────────────────────────────────────────

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            perms.entries.forEach { Log.w(TAG, "Permission ${it.key}: ${it.value}") }
            if (perms.all { it.value }) loadMusicsCoroutine()
            else Log.w(TAG, "Some permissions denied")
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        musicListState  = mutableStateOf(emptyList())
        albumsListState = mutableStateOf(emptyList())

        // Connect UI to the player service (creates service if needed)
        MusicPlayerConnection.connect(this)

        setContent {
            val controller by MusicPlayerConnection.controller.collectAsState()

            MusicServerTheme {
                MainActivityContent(
                    localNetworkIp  = server.getLocalIpAddress(),
                    colors          = MaterialTheme.colorScheme,
                    musicListState  = musicListState,
                    albumsListState = albumsListState,
                    context         = this,
                    controller      = controller,
                )
            }
        }

        startServer()
    }

    override fun onStart() {
        super.onStart()
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) requestPermissionsLauncher.launch(missing.toTypedArray())
        else loadMusicsCoroutine()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
        // Release the MediaController binding (service keeps running until OS kills it)
        MusicPlayerConnection.release()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun startServer() {
        selectionFilter = FilterType.ALL
        server = MediaServer(8080, this)
        server.start()
        Log.d(TAG, "Music paths: ${Musics.getMusicPaths(this)}")
    }

    private fun loadMusicsCoroutine() {
        lifecycleScope.launch {
            val (tracks, albums) = withContext(Dispatchers.IO) {
                Pair(Musics.getMusicTracks(this@MainActivity), Musics.getAlbums(this@MainActivity))
            }
            musicListState.value  = tracks
            albumsListState.value = albums
        }
    }

    companion object { private const val TAG = "MainActivity" }
}

fun openWebPlayer(context: Context, localNetworkIp: String?) {
    localNetworkIp?.let {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply { data = "http://$it:8080".toUri() }
        )
    }
}
