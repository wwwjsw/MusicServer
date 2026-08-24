package com.wwwjsw.musicserver.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.wwwjsw.musicserver.models.MusicTrack
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.zip.ZipFile

/**
 * Desktop (Linux/JVM) equivalent of the Android [MediaServer].
 *
 * Exposes the same HTTP endpoints so the web front-end (served from the bundled
 * music.zip) works identically on both platforms:
 *
 *  GET /              → index.html from music.zip
 *  GET /js/app.js     → app.js from music.zip
 *  GET /music         → JSON list of all tracks  (no query param)
 *  GET /music?audio_id=<id> → stream audio file with HTTP Range support
 *  GET /albuns        → JSON list of albums (typo kept for API compatibility)
 *
 * Music is discovered by [MusicScanner] walking [musicRoot] on the filesystem.
 */
class DesktopMediaServer(
    private val port: Int,
    private val musicRoot: File,
    private val frontendZip: File? = null,
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val json = jacksonObjectMapper()

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    fun start() {
        server = embeddedServer(Netty, port = port) {
            installCors()
            configureRouting()
        }
        CoroutineScope(Dispatchers.IO).launch {
            server?.start(wait = true)
        }
    }

    fun stop() {
        server?.stop(1_000, 2_000)
        server = null
    }

    fun getLocalIpAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (_: Exception) { null }

    // -----------------------------------------------------------------------
    //  Ktor configuration
    // -----------------------------------------------------------------------

    private fun Application.installCors() {
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
    }

    private fun Application.configureRouting() {
        // Open the bundled zip once and reuse it (or null if no zip provided)
        val zip: ZipFile? = frontendZip?.let { ZipFile(it) }

        routing {
            // Preflight
            options("/{...}") { call.respond(HttpStatusCode.OK) }

            // ── Frontend ─────────────────────────────────────────────────────
            get("/") {
                if (zip != null) {
                    call.respondBytes(
                        bytes = zip.getInputStream(zip.getEntry("index.html")).use { it.readBytes() },
                        contentType = ContentType.Text.Html,
                    )
                } else {
                    call.respondText("Music Server is running on Linux (no frontend bundled)")
                }
            }

            get("/js/app.js") {
                if (zip != null) {
                    call.respondBytes(
                        bytes = zip.getInputStream(zip.getEntry("js/app.js")).use { it.readBytes() },
                        contentType = ContentType.Application.JavaScript,
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound, "No frontend bundled")
                }
            }

            // ── Music list / stream ───────────────────────────────────────────
            get("/music") {
                val audioIdString = call.request.queryParameters["audio_id"]

                if (!audioIdString.isNullOrEmpty()) {
                    // ── Stream a single track ─────────────────────────────────
                    val audioId = audioIdString.toLongOrNull()
                    if (audioId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid audio ID")
                        return@get
                    }

                    val track: MusicTrack? = MusicScanner.getTrack(musicRoot, audioId)
                    val file: File? = track?.file

                    if (file == null || !file.exists()) {
                        call.respond(HttpStatusCode.NotFound, "Audio file not found")
                        return@get
                    }

                    val fileSize = file.length()
                    val rangeHeader = call.request.headers[HttpHeaders.Range]

                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    call.response.header(HttpHeaders.ContentType, "audio/mpeg")
                    track.title.let { call.response.header("icy-name", it) }
                    track.artist.let { call.response.header("icy-artist", it) }

                    if (rangeHeader != null) {
                        val match = Regex("""bytes=(\d*)-(\d*)""").find(rangeHeader)
                        if (match == null) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid range format")
                            return@get
                        }
                        val (startStr, endStr) = match.destructured
                        val start = if (startStr.isEmpty()) 0L else startStr.toLong()
                        val end = if (endStr.isEmpty()) fileSize - 1 else endStr.toLong().coerceAtMost(fileSize - 1)

                        if (start > end || start >= fileSize) {
                            call.response.header(HttpHeaders.ContentRange, "bytes */$fileSize")
                            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable, "Requested range not satisfiable")
                            return@get
                        }

                        val length = end - start + 1
                        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")
                        call.respondBytesWriter(
                            status = HttpStatusCode.PartialContent,
                            contentType = ContentType.Audio.MPEG,
                            contentLength = length,
                        ) {
                            file.inputStream().use { fis ->
                                fis.skip(start)
                                BufferedInputStream(fis, minOf(length, 65_536L).toInt())
                                    .copyTo(this.toOutputStream())
                            }
                        }
                    } else {
                        call.respondBytesWriter(
                            status = HttpStatusCode.OK,
                            contentType = ContentType.Audio.MPEG,
                            contentLength = fileSize,
                        ) {
                            file.inputStream().use { it.copyTo(this.toOutputStream()) }
                        }
                    }
                } else {
                    // ── Return full track list ────────────────────────────────
                    val tracks = MusicScanner.getMusicTracks(musicRoot)
                    val response = mapOf(
                        "status" to HttpStatusCode.OK.value,
                        "ipAddress" to getLocalIpAddress(),
                        "data" to tracks,
                    )
                    call.respondText(json.writeValueAsString(response), ContentType.Application.Json)
                }
            }

            // ── Albums ────────────────────────────────────────────────────────
            // Endpoint name kept as "/albuns" (typo in original) for API compat
            get("/albuns") {
                val albums = MusicScanner.getAlbums(musicRoot)
                val response = mapOf(
                    "status" to HttpStatusCode.OK.value,
                    "ipAddress" to getLocalIpAddress(),
                    "data" to albums,
                )
                call.respondText(json.writeValueAsString(response), ContentType.Application.Json)
            }
        }
    }
}
