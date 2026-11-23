package com.ameerscreens.app

import android.content.ContentUris
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class CarMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private var player: MediaPlayer? = null
    private var currentTitle: String = "أمير للشاشات"

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AmeerScreens")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (player != null) {
                    player?.start()
                    updateState(PlaybackStateCompat.STATE_PLAYING)
                }
            }

            override fun onPause() {
                player?.pause()
                updateState(PlaybackStateCompat.STATE_PAUSED)
            }

            override fun onStop() {
                releasePlayer()
                updateState(PlaybackStateCompat.STATE_STOPPED)
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                if (mediaId.isNullOrBlank()) return
                if (mediaId == "music") {
                    updateState(PlaybackStateCompat.STATE_STOPPED)
                    return
                }
                playTrack(mediaId)
            }
        })
        mediaSession.isActive = true
        sessionToken = mediaSession.sessionToken
        updateState(PlaybackStateCompat.STATE_STOPPED)
    }

    private fun updateState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot("root", null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (parentId == "root") {
            items.add(
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("music")
                        .setTitle("الموسيقى")
                        .setSubtitle("مكتبة الصوتيات")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                )
            )
            result.sendResult(items)
            return
        }

        if (parentId == "music") {
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                android.os.Build.VERSION.SDK_INT < 33
            ) {
                val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST
                )
                try {
                    contentResolver.query(
                        collection,
                        projection,
                        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                        null,
                        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        while (cursor.moveToNext() && items.size < 300) {
                            val id = cursor.getLong(idColumn)
                            val title = cursor.getString(titleColumn) ?: "مقطع صوتي"
                            val artist = cursor.getString(artistColumn) ?: ""
                            items.add(
                                MediaBrowserCompat.MediaItem(
                                    MediaDescriptionCompat.Builder()
                                        .setMediaId(id.toString())
                                        .setTitle(title)
                                        .setSubtitle(artist)
                                        .build(),
                                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                                )
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    // The user must grant audio access from the phone app first.
                }
            }
        }
        result.sendResult(items)
    }

    private fun playTrack(id: String) {
        val trackUri = try {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong())
        } catch (_: Exception) {
            return
        }
        releasePlayer()
        try {
            player = MediaPlayer().apply {
                setDataSource(this@CarMediaBrowserService, trackUri)
                setOnPreparedListener { mp ->
                    mp.start()
                    updateState(PlaybackStateCompat.STATE_PLAYING)
                    mediaSession.setMetadata(
                        MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                            .build()
                    )
                }
                setOnCompletionListener {
                    updateState(PlaybackStateCompat.STATE_STOPPED)
                }
                setOnErrorListener { _, _, _ ->
                    updateState(PlaybackStateCompat.STATE_ERROR)
                    true
                }
                prepareAsync()
            }
            val title = contentResolver.query(
                trackUri,
                arrayOf(MediaStore.Audio.Media.TITLE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            currentTitle = title ?: "مقطع صوتي"
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                    .build()
            )
            updateState(PlaybackStateCompat.STATE_BUFFERING)
        } catch (_: Exception) {
            releasePlayer()
            updateState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (_: Exception) {}
        player?.release()
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }
}
