package com.wwwjsw.musicserver

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.system.Os
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface

class MediaServer(port: Int, private val context: Context) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val params = session.parameters
        val audioIdString = params["audio_id"]?.get(0)

        return if (!audioIdString.isNullOrEmpty()) {
            val audioId = audioIdString.toLongOrNull()
            if (audioId != null) {
                val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)

                try {
                    val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "r")

                    if (parcelFileDescriptor != null) {
                        val duplicatedFd = Os.dup(parcelFileDescriptor.fileDescriptor)
                        val fileInputStream = FileInputStream(duplicatedFd)

                        val fileSize = parcelFileDescriptor.statSize

                        val response = newFixedLengthResponse(
                            Response.Status.OK,
                            "audio/mpeg",
                            fileInputStream,
                            fileSize
                        )

                        val musicName = Musics.getMusic(context, audioId).getOrNull()?.title
                        val musicArtist = Musics.getMusic(context, audioId).getOrNull()?.artist

                        response.addHeader("Content-Type", "audio/mpeg")
                        response.addHeader("Accept-Ranges", "bytes")

                        response.addHeader("icy-name", musicName)
                        response.addHeader("icy-artist", musicArtist)

                        parcelFileDescriptor.close()

                        return response
                    } else {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Audio file not found")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Error accessing audio file: ${e.message}")
                }
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid audio ID")
            }
        } else {
            val musics = Musics.getMusicTracks(context)
            val response = mapOf(
                "status" to Response.Status.OK,
                "ipAddress" to getLocalIpAddress(),
                "data" to musics
            )
            val jsonResponse = Gson().toJson(response)

            newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse)
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (inetAddress in addresses) {
                    // Ignore loopback (like 127.0.0.1)
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null // return null if IP was not find
    }
}
