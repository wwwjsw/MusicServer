package com.wwwjsw.musicserver

import java.util.Locale

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}