package com.tongpin.app.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 5000"
        val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sort,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val title = cursor.getString(titleCol).orEmpty().trim()
                if (title.isBlank()) continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                songs += Song(
                    id = id,
                    uri = uri,
                    title = title,
                    artist = cursor.getString(artistCol).orEmpty()
                        .replace("<unknown>", "")
                        .trim(),
                    album = cursor.getString(albumCol).orEmpty().trim(),
                    albumId = albumId,
                    durationMs = cursor.getLong(durationCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol).orEmpty()
                        .ifBlank { "audio/*" },
                    artworkUri = albumArtUri(albumId),
                    dateAdded = cursor.getLong(dateCol) * 1000L,
                )
            }
        }
        songs
    }

    fun openAudio(uri: Uri) = context.contentResolver.openInputStream(uri)

    /**
     * Produces a reasonably small JPEG for the room cover. Album art may be
     * embedded, supplied by the media provider, or missing entirely.
     */
    suspend fun loadCoverJpeg(song: Song): ByteArray? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && song.artworkUri != null) {
                bitmap = runCatching {
                    context.contentResolver.loadThumbnail(
                        song.artworkUri,
                        Size(768, 768),
                        null,
                    )
                }.getOrNull()
            }
            if (bitmap == null && song.artworkUri != null) {
                bitmap = runCatching {
                    context.contentResolver.openInputStream(song.artworkUri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, song.uri)
                    bitmap = retriever.embeddedPicture?.let {
                        BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }
            val source = bitmap ?: return@withContext null
            val software = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }
            val scaled = if (software.width > 960 || software.height > 960) {
                val ratio = minOf(960f / software.width, 960f / software.height)
                Bitmap.createScaledBitmap(
                    software,
                    (software.width * ratio).toInt().coerceAtLeast(1),
                    (software.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                software
            }
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
            if (source !== software) source.recycle()
            if (software !== scaled) software.recycle()
            scaled.recycle()
            bytes
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun albumArtUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )
    }
}
