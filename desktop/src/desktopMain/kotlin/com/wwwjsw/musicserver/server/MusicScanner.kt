package com.wwwjsw.musicserver.server

import com.wwwjsw.musicserver.models.Album
import com.wwwjsw.musicserver.models.MusicTrack
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Filesystem-based music scanner with in-memory cache and parallel tag reading.
 *
 * First scan: walks the filesystem in parallel using a thread pool sized to
 * available CPUs, reads tags via JAudioTagger, and builds a stable ID→track map.
 *
 * Subsequent calls to [getMusicTracks], [getAlbums], or [getTrack] return
 * immediately from the cache — no disk I/O on API requests.
 *
 * Call [invalidate] (e.g. when the user changes the music folder) to force
 * a fresh scan on the next request.
 */
object MusicScanner {

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "aac", "wav", "opus", "wma")

    private val EXCLUDED_FOLDER_KEYWORDS = listOf(
        "WhatsApp Audio",
        "WhatsApp Voice Notes",
        "Telegram Audio",
    )

    // ── Cache ────────────────────────────────────────────────────────────────
    // LinkedHashMap preserves insertion order (walk order) → stable IDs
    @Volatile private var cachedTracks: List<MusicTrack> = emptyList()
    @Volatile private var cachedRoot: File? = null
    private val cacheValid = AtomicBoolean(false)

    // Stable ID → track lookup, built once alongside cachedTracks
    private val trackById = ConcurrentHashMap<Long, MusicTrack>()

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns tracks for [root], scanning once and caching results. */
    fun getMusicTracks(root: File): List<MusicTrack> {
        ensureCache(root)
        return cachedTracks
    }

    fun getAlbums(root: File): List<Album> =
        getMusicTracks(root)
            .groupBy { it.album }
            .entries
            .mapIndexed { index, (albumName, albumTracks) ->
                Album(
                    id = index.toLong(),
                    album = albumName,
                    musics = albumTracks,
                    thumbnail = null, // omit artwork from album list — fetched per track if needed
                )
            }

    /** O(1) lookup via cache — no disk walk per stream request. */
    fun getTrack(root: File, id: Long): MusicTrack? {
        ensureCache(root)
        return trackById[id]
    }

    /** Force re-scan on next access (e.g. folder changed). */
    fun invalidate() {
        cacheValid.set(false)
        cachedTracks = emptyList()
        trackById.clear()
        cachedRoot = null
    }

    // ── Cache population ──────────────────────────────────────────────────────

    private fun ensureCache(root: File) {
        if (cacheValid.get() && cachedRoot == root) return
        synchronized(this) {
            if (cacheValid.get() && cachedRoot == root) return // double-check
            val tracks = scanParallel(root)
            cachedTracks = tracks
            trackById.clear()
            tracks.forEach { trackById[it.id] = it }
            cachedRoot = root
            cacheValid.set(true)
        }
    }

    // ── Parallel scan ─────────────────────────────────────────────────────────

    private fun scanParallel(root: File): List<MusicTrack> {
        val files = walk(root).toList() // collect file list first (fast, no tag I/O)

        val cpus = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val pool = Executors.newFixedThreadPool(cpus)

        // Assign stable IDs by walk order, then read tags in parallel
        val futures = files.mapIndexed { index, file ->
            pool.submit<MusicTrack?> { readTrack(file, index.toLong()) }
        }

        pool.shutdown()

        return futures.mapIndexedNotNull { _, future ->
            try { future.get() } catch (_: Exception) { null }
        }
    }

    // ── Filesystem walk ───────────────────────────────────────────────────────

    private fun walk(root: File): Sequence<File> =
        root.walkTopDown()
            .onEnter { dir -> EXCLUDED_FOLDER_KEYWORDS.none { kw -> dir.path.contains(kw) } }
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }

    // ── Tag reading ───────────────────────────────────────────────────────────

    private fun readTrack(file: File, id: Long): MusicTrack? = try {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag
        val header = audioFile.audioHeader

        val title  = tag?.getFirst(FieldKey.TITLE)?.takeIf  { it.isNotBlank() } ?: file.nameWithoutExtension
        val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val album  = tag?.getFirst(FieldKey.ALBUM)?.takeIf  { it.isNotBlank() } ?: "Unknown Album"

        // Artwork: encode only if present; omit from JSON by default to keep
        // the /music response small. The field is included in the model for
        // future per-track artwork endpoints.
        val thumbnail: String? = try {
            tag?.firstArtwork?.binaryData?.let { Base64.getEncoder().encodeToString(it) }
        } catch (_: Exception) { null }

        MusicTrack(
            id       = id,
            title    = title,
            artist   = artist,
            album    = album,
            duration = header.trackLength * 1_000L,
            uri      = "/music?audio_id=$id",
            file     = file,
            thumbnail = thumbnail,
        )
    } catch (_: Exception) { null }
}
