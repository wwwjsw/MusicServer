package com.wwwjsw.musicserver

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream

class MediaServer(port: Int, private val context: Context) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val params = session.parameters
        val audioIdString = params["audio_id"]?.get(0)

        return if (!audioIdString.isNullOrEmpty()) {
            val audioId = audioIdString.toLongOrNull()
            if (audioId != null) {
                val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
                val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "r")

                if (parcelFileDescriptor != null) {
                    val fileInputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
                    val fileSize = parcelFileDescriptor.statSize

                    parcelFileDescriptor.close()

                    val response = newFixedLengthResponse(
                        Response.Status.OK,
                        "audio/mpeg",
                        fileInputStream,
                        fileSize
                    )

                    val musicName = Musics.getMusic(context, audioId).getOrNull()?.title
                    val musicArtist = Musics.getMusic(context, audioId).getOrNull()?.artist

                    response.addHeader("Content-Type", "audio/*")
                    response.addHeader("Accept-Ranges", "bytes")

                    response.addHeader("icy-name", musicName)
                    response.addHeader("icy-artist", musicArtist)

                    return response
                } else { newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Audio file not found")
                }

            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid audio ID")
            }
        } else {
            val musics = Musics.getMusicTracks(context)
            val response = mapOf(
                "status" to Response.Status.OK,
                "data" to musics
            ).toString()
            val jsonResponse = Gson().toJson(response)

            newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse)
        }
    }
}
