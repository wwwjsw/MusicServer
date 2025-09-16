package com.wwwjsw.musicserver

import android.graphics.Bitmap
import com.wwwjsw.musicserver.models.MusicTrack

data  class Album(
    val id: Long,
    val album: String,
    val musics: List<MusicTrack>,
    val thumbnail: Bitmap? = null,
)