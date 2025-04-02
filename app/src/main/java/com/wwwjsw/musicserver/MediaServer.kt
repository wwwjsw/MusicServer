package com.wwwjsw.musicserver

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.system.Os
import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import io.ktor.server.plugins.cors.routing.*
import io.ktor.utils.io.jvm.javaio.toOutputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipFile

class MediaServer(port: Int, private val context: Context) {
    private var server: NettyApplicationEngine? = null
    private var serverContext: Context = context

    fun start() {
        server = embeddedServer(Netty, port = 8080) {
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Range)
                exposeHeader(HttpHeaders.ContentRange)
                exposeHeader(HttpHeaders.AcceptRanges)
                exposeHeader(HttpHeaders.ContentLength)
                exposeHeader(HttpHeaders.ContentType)
            }

            routing {
                options("/{...}") {
                    call.respond(HttpStatusCode.OK)
                }
                val inputStream: InputStream = context.resources.openRawResource(R.raw.music)
                val tempFile = File.createTempFile("temp_music", ".zip", context.cacheDir)


                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val zipFilePath = tempFile.absolutePath
                val zipFile = ZipFile(zipFilePath)

                // Serve index.html for root path
                get("/") {
                    call.respondBytes(
                        zipFile.getInputStream(zipFile.getEntry("index.html")).use { it.readBytes() },
                        ContentType.Text.Html
                    )
                }

                // Serve app.js from the zip file
                get("/js/app.js") {
                    call.respondBytes(
                        zipFile.getInputStream(zipFile.getEntry("js/app.js")).use { it.readBytes() },
                        ContentType.Application.JavaScript
                    )
                }

                get("/music/{id}") {
                    call.respondText("music ${call.parameters["id"]}")
                }

                get("/music") {
                    val audioIdString = call.request.queryParameters["audio_id"]
                    if (!audioIdString.isNullOrEmpty()) {
                        val audioId = audioIdString.toLongOrNull()
                        if (audioId != null) {
                            val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
                            try {
                                val parcelFileDescriptor = serverContext.contentResolver.openFileDescriptor(audioUri, "r")
                                if (parcelFileDescriptor != null) {
                                    val duplicatedFd = Os.dup(parcelFileDescriptor.fileDescriptor)
                                    val fileInputStream = FileInputStream(duplicatedFd)
                                    val fileSize = parcelFileDescriptor.statSize

                                    val rangeHeader = call.request.headers["Range"]
                                    val response = if (rangeHeader != null) {
                                        val rangeMatch = Regex("bytes=(\\d*)-(\\d*)").find(rangeHeader)
                                        if (rangeMatch != null) {
                                            val (startStr, endStr) = rangeMatch.destructured
                                            val start = if (startStr.isEmpty()) 0L else startStr.toLong()
                                            val end = if (endStr.isEmpty()) fileSize - 1 else endStr.toLong().coerceAtMost(fileSize - 1)

                                            if (start <= end && start < fileSize) {
                                                val length = end - start + 1
                                                fileInputStream.skip(start)
                                                call.respondBytesWriter(
                                                    status = HttpStatusCode.PartialContent,
                                                    contentType = ContentType.Audio.MPEG,
                                                    contentLength = length
                                                ) {
                                                    BufferedInputStream(fileInputStream, length.toInt()).copyTo(this.toOutputStream())
                                                }
                                                call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")
                                            } else {
                                                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable, "Requested range not satisfiable")
                                                call.response.header(HttpHeaders.ContentRange, "bytes */$fileSize")
                                            }
                                        } else {
                                            call.respond(HttpStatusCode.BadRequest, "Invalid range format")
                                        }
                                    } else {
                                        call.respondBytesWriter(
                                            status = HttpStatusCode.OK,
                                            contentType = ContentType.Audio.MPEG,
                                            contentLength = fileSize
                                        ) {
                                            fileInputStream.copyTo(this.toOutputStream())
                                        }
                                    }

                                    val musicName = Musics.getMusic(serverContext, audioId).getOrNull()?.title
                                    val musicArtist = Musics.getMusic(serverContext, audioId).getOrNull()?.artist

                                    call.response.header(HttpHeaders.ContentType, "audio/mpeg")
                                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                    if (musicName != null) {
                                        call.response.header("icy-name", musicName)
                                    }
                                    if (musicArtist != null) {
                                        call.response.header("icy-artist", musicArtist)
                                    }

                                    parcelFileDescriptor.close()
                                } else {
                                    call.respond(HttpStatusCode.NotFound, "Audio file not found")
                                }
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.NotFound, "Error accessing audio file: ${e.message}")
                            }
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Invalid audio ID")
                        }
                    } else {
                        val musics = Musics.getMusicTracks(serverContext)
                        val response = mapOf(
                            "status" to HttpStatusCode.OK,
                            "ipAddress" to getLocalIpAddress(),
                            "data" to musics
                        )
                        call.respondText(Gson().toJson(response), ContentType.Application.Json)
                    }
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            server?.start(wait = true)
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
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