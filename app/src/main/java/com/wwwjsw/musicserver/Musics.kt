package com.wwwjsw.musicserver

import com.wwwjsw.musicserver.models.MusicTrack
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.graphics.Bitmap
import android.util.Size

object Musics {
    fun getMusicPaths(context: Context): List<String> {
        val audioUri =  MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        return context.contentResolver.query(audioUri, projection, selection, null, null)?.use { cursor ->
            List(cursor.count) { index ->
                cursor.moveToPosition(index)
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                ContentUris.withAppendedId(audioUri, id).toString()
            }
        } ?: emptyList()
    }

    fun getMusicTracks(context: Context): List<MusicTrack> {
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        return try {
            context.contentResolver.query(audioUri, projection, selection, null, null)?.use { cursor ->
                mutableListOf<MusicTrack>().apply {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown Title"
                        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown Artist"
                        val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown Album"
                        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                        val uri = ContentUris.withAppendedId(audioUri, id).toString()

                        add(MusicTrack(id, title, artist, album, duration, uri))
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicServer", "Error fetching music tracks", e)
            emptyList()
        }
    }

    fun getAlbums(context: Context): List<Album> {
        val albumUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )
        val albumList = mutableListOf<Album>()

        context.contentResolver.query(albumUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumName = cursor.getString(albumNameColumn)
                val musics = getMusics(context, id)

                val albumArt = loadAlbumArtThumbnail(context, id)

                albumList.add(Album(id, albumName, musics, albumArt))
            }
        }

        return albumList
    }

    private fun loadAlbumArtThumbnail(context: Context, albumId: Long): Bitmap? {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
            context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
        } catch (e: Exception) {
            Log.e("AlbumLoader", "Failed to load thumbnail for album $albumId: ${e.message}")
            null
        }
    }

    fun getMusic(context: Context, id: Long): Result<MusicTrack?> {
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media._ID} = $id"

        val musicTrack: MusicTrack? = try {
            context.contentResolver.query(audioUri, projection, selection, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val uid = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown Title"
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown Artist"
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown Album"
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val uri = ContentUris.withAppendedId(audioUri, id).toString()

                    MusicTrack(uid, title, artist, album, duration, uri)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("MusicServer", "Error fetching music track", e)
            null
        }

        if (musicTrack != null) {
            println("Track find: $musicTrack")
            return Result.success(musicTrack)
        } else {
            println("Track not found.")
            return Result.failure(Exception("Track not found"))
        }
    }

    fun getMusics(context: Context, albumId: Long): List<MusicTrack> {
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ALBUM_ID} = $albumId"
        val musicList = mutableListOf<MusicTrack>()

        context.contentResolver.query(audioUri, projection, selection, null, null)?.use { cursor ->
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = cursor.getString(albumColumn)
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown Artist"
                val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown Album"
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val uri = ContentUris.withAppendedId(audioUri, id).toString()

                musicList.add(MusicTrack(id, title, artist, album, duration, uri))
            }
        }

        return musicList
    }
}