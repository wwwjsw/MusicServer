package com.wwwjsw.musicserver.server

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.wwwjsw.musicserver.models.MusicTrack
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.zip.ZipFile

class DesktopMediaServer(
    private val port: Int,
    private val musicRoot: File,
    private val frontendZip: File? = null,
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    // Reuse mapper; NON_NULL so thumbnail is omitted when null (keeps /music JSON small)
    private val json = jacksonObjectMapper().apply {
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    // Pre-read frontend assets once at startup so every HTTP request is served from memory
    private val indexHtml: ByteArray? by lazy { frontendZip?.readEntry("index.html") }
    private val appJs:     ByteArray? by lazy { frontendZip?.readEntry("js/app.js") }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        // Warm the scanner cache on a background thread so the UI is responsive
        // and the first /music request is answered instantly
        CoroutineScope(Dispatchers.IO).launch { MusicScanner.getMusicTracks(musicRoot) }

        server = embeddedServer(Netty, port = port) {
            installCors()
            configureRouting()
        }
        CoroutineScope(Dispatchers.IO).launch { server?.start(wait = true) }
    }

    fun stop() {
        server?.stop(500, 1_000)
        server = null
        MusicScanner.invalidate()
    }

    fun getLocalIpAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (_: Exception) { null }

    // ── Ktor ─────────────────────────────────────────────────────────────────

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
        }
    }

    private fun Application.configureRouting() {
        routing {
            options("/{...}") { call.respond(HttpStatusCode.OK) }

            // ── Frontend ──────────────────────────────────────────────────────
            get("/") {
                if (indexHtml != null) {
                    call.respondBytes(indexHtml!!, ContentType.Text.Html)
                } else {
                    call.respondText("Music Server running — no frontend bundled")
                }
            }

            get("/js/app.js") {
                if (appJs != null) {
                    call.respondBytes(appJs!!, ContentType.Application.JavaScript)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // ── Track list ────────────────────────────────────────────────────
            get("/music") {
                val audioIdStr = call.request.queryParameters["audio_id"]

                if (audioIdStr.isNullOrEmpty()) {
                    // Full list — served from cache, no disk I/O
                    val tracks = MusicScanner.getMusicTracks(musicRoot)
                    call.respondText(
                        json.writeValueAsString(mapOf(
                            "status"    to 200,
                            "ipAddress" to getLocalIpAddress(),
                            "data"      to tracks,
                        )),
                        ContentType.Application.Json,
                    )
                    return@get
                }

                // ── Stream single track ───────────────────────────────────────
                val id = audioIdStr.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid audio ID")

                val track: MusicTrack = MusicScanner.getTrack(musicRoot, id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Track not found")

                val file: File = track.file
                    ?: return@get call.respond(HttpStatusCode.NotFound, "File missing")

                if (!file.exists())
                    return@get call.respond(HttpStatusCode.NotFound, "File deleted")

                streamAudio(file, track)
            }

            // ── Albums ────────────────────────────────────────────────────────
            get("/albuns") {
                val albums = MusicScanner.getAlbums(musicRoot)
                call.respondText(
                    json.writeValueAsString(mapOf(
                        "status"    to 200,
                        "ipAddress" to getLocalIpAddress(),
                        "data"      to albums,
                    )),
                    ContentType.Application.Json,
                )
            }
        }
    }

    // ── Audio streaming with Range support ────────────────────────────────────
    // Uses RandomAccessFile for zero-copy seek — no need to skip bytes
    // from the start of the stream for every partial request.

    private suspend fun RoutingContext.streamAudio(file: File, track: MusicTrack) {
        val fileSize = file.length()
        val mimeType = mimeTypeFor(file)

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header("icy-name",   track.title)
        call.response.header("icy-artist", track.artist)

        val rangeHeader = call.request.headers[HttpHeaders.Range]

        if (rangeHeader == null) {
            // Full file
            call.response.header(HttpHeaders.ContentLength, fileSize.toString())
            call.respondOutputStream(ContentType.parse(mimeType), HttpStatusCode.OK) {
                file.inputStream().buffered(BUFFER_SIZE).use { it.copyTo(this) }
            }
            return
        }

        // Parse "bytes=start-end"
        val match = RANGE_RE.find(rangeHeader)
        if (match == null) {
            call.respond(HttpStatusCode.BadRequest, "Malformed Range header")
            return
        }
        val (startStr, endStr) = match.destructured
        val start = if (startStr.isEmpty()) 0L else startStr.toLong()
        val end   = if (endStr.isEmpty()) fileSize - 1 else endStr.toLong().coerceAtMost(fileSize - 1)

        if (start > end || start >= fileSize) {
            call.response.header(HttpHeaders.ContentRange, "bytes */$fileSize")
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }

        val length = end - start + 1
        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")
        call.response.header(HttpHeaders.ContentLength, length.toString())

        call.respondOutputStream(ContentType.parse(mimeType), HttpStatusCode.PartialContent) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(BUFFER_SIZE)
                var remaining = length
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read == -1) break
                    write(buf, 0, read)
                    remaining -= read
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mimeTypeFor(file: File) = when (file.extension.lowercase()) {
        "mp3"  -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg"  -> "audio/ogg"
        "opus" -> "audio/ogg"
        "m4a"  -> "audio/mp4"
        "aac"  -> "audio/aac"
        "wav"  -> "audio/wav"
        "wma"  -> "audio/x-ms-wma"
        else   -> "audio/mpeg"
    }

    companion object {
        private const val BUFFER_SIZE = 128 * 1024 // 128 KB
        private val RANGE_RE = Regex("""bytes=(\d*)-(\d*)""")
    }
}

// ── ZipFile extension ─────────────────────────────────────────────────────────

private fun File.readEntry(entryPath: String): ByteArray? = try {
    ZipFile(this).use { zip ->
        zip.getInputStream(zip.getEntry(entryPath))?.use { it.readBytes() }
    }
} catch (_: Exception) { null }
