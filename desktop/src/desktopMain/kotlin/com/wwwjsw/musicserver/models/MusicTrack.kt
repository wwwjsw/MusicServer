package com.wwwjsw.musicserver.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.File

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    @JsonIgnore val file: File? = null,
    val thumbnail: String? = null, // base64-encoded JPEG for desktop
)
