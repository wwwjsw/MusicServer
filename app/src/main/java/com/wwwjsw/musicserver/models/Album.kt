package com.wwwjsw.musicserver.models

import android.graphics.Bitmap

data  class Album(
    val id: Long,
    val album: String,
    val musics: List<MusicTrack>,
    val thumbnail: Bitmap? = null,
)