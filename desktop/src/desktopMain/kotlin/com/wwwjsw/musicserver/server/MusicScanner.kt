package com.wwwjsw.musicserver.server

import com.wwwjsw.musicserver.models.Album
import com.wwwjsw.musicserver.models.MusicTrack
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.Base64
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Filesystem-based music scanner for the Linux/Desktop port.
 *
 * Walks a root directory recursively, reads ID3/FLAC/OGG tags via JAudioTagger,
 * and produces the same [MusicTrack] and [Album] lists that the Android MediaStore
 * queries produce on the mobile side — keeping the HTTP API identical on both platforms.
 *
 * Folders matching [EXCLUDED_FOLDER_KEYWORDS] are skipped to mirror the Android
 * [MusicFilter] helper that strips WhatsApp / Telegram audio from the results.
 */
object MusicScanner {

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "aac", "wav", "opus", "wma")

    private val EXCLUDED_FOLDER_KEYWORDS = listOf(
        "WhatsApp Audio",
        "WhatsApp Voice Notes",
        "Telegram Audio",
    )

    init {
        // Suppress JAudioTagger's very noisy logging
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    fun getMusicTracks(root: File): List<MusicTrack> =
        walk(root).mapIndexedNotNull { index, file -> readTrack(file, index.toLong()) }

    fun getAlbums(root: File): List<Album> {
        val tracks = getMusicTracks(root)
        return tracks
            .groupBy { it.album }
            .entries
            .mapIndexed { index, (albumName, albumTracks) ->
                Album(
                    id = index.toLong(),
                    album = albumName,
                    musics = albumTracks,
                    thumbnail = albumTracks.firstOrNull()?.thumbnail,
                )
            }
    }

    fun getTrack(root: File, id: Long): MusicTrack? =
        walk(root)
            .mapIndexedNotNull { index, file -> readTrack(file, index.toLong()) }
            .find { it.id == id }

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    private fun walk(root: File): Sequence<File> =
        root.walkTopDown()
            .onEnter { dir -> EXCLUDED_FOLDER_KEYWORDS.none { kw -> dir.path.contains(kw) } }
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }

    private fun readTrack(file: File, id: Long): MusicTrack? {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
                ?: "Unknown Album"
            val durationMs = (header.trackLength * 1000L)

            val thumbnail: String? = try {
                tag?.firstArtwork?.binaryData?.let { bytes ->
                    Base64.getEncoder().encodeToString(bytes)
                }
            } catch (_: Exception) { null }

            MusicTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = durationMs,
                uri = "http://localhost/music?audio_id=$id",
                file = file,
                thumbnail = thumbnail,
            )
        } catch (e: Exception) {
            // Unreadable file — skip gracefully
            null
        }
    }
}
