package com.wwwjsw.musicserver

import com.wwwjsw.musicserver.models.MusicTrack

data  class Album(
    val id: Long,
    val album: String,
    val musics: List<MusicTrack>
)