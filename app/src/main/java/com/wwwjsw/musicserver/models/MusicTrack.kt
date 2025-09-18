package com.wwwjsw.musicserver.models
import android.graphics.Bitmap

data class MusicTrack(
    val id: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long?,
    val uri: String,
    val thumbnail: Bitmap? = null,
)
