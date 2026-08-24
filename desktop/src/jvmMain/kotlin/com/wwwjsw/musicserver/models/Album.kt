package com.wwwjsw.musicserver.models

data class Album(
    val id: Long,
    val album: String,
    val musics: List<MusicTrack>,
    val thumbnail: String? = null, // base64-encoded JPEG for desktop
)
