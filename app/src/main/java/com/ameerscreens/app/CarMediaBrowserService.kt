package com.ameerscreens.app

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaBrowserServiceCompat

class CarMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "AmeerScreens").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    setState(PlaybackStateCompat.STATE_PLAYING)
                }

                override fun onPause() {
                    setState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onStop() {
                    setState(PlaybackStateCompat.STATE_STOPPED)
                }

                override fun onPlayFromMediaId(
                    mediaId: String?,
                    extras: Bundle?
                ) {
                    setState(PlaybackStateCompat.STATE_PLAYING)
                }
            })

            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        setState(PlaybackStateCompat.STATE_STOPPED)
    }

    private fun setState(state: Int) {
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
    ): BrowserRoot {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        val items = listOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("music")
                    .setTitle("الموسيقى")
                    .setSubtitle("أمير للشاشات")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )

        result.sendResult(items)
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }
}
