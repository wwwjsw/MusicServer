package com.wwwjsw.musicserver.models

data class MusicTrack(
    val id: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long?,
    val uri: String
)
