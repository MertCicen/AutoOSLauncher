package com.autoos.launcher

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private data class NotificationText(
        val title: String,
        val artist: String
    )

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishMediaState(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaArtworkRepository.updatePlaybackState(state)
            refreshActiveController()
        }

        override fun onSessionDestroyed() {
            refreshActiveController()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaArtworkRepository.updateNotificationAccess(true)
        refreshActiveController()
    }

    override fun onListenerDisconnected() {
        unregisterActiveController()
        MediaArtworkRepository.updateNotificationAccess(false)
        MediaArtworkRepository.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && !isMediaNotification(sbn)) return
        refreshActiveController(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveController()
    }

    private fun refreshActiveController(postedNotification: StatusBarNotification? = null) {
        val manager = mediaSessionManager ?: return
        val controllers = try {
            manager.getActiveSessions(ComponentName(this, javaClass))
        } catch (_: SecurityException) {
            emptyList()
        }

        val postedPackage = postedNotification
            ?.takeIf(::isMediaNotification)
            ?.packageName
            .orEmpty()

        val nextController = controllers
            .filter { hasUsableMetadata(it.metadata) || it.playbackState?.state == PlaybackState.STATE_PLAYING }
            .sortedWith(
                compareByDescending<MediaController> {
                    postedPackage.isNotBlank() && it.packageName == postedPackage
                }.thenByDescending {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                }.thenByDescending {
                    hasUsableMetadata(it.metadata)
                }
            )
            .firstOrNull()

        if (activeController?.sessionToken != nextController?.sessionToken) {
            unregisterActiveController()
            activeController = nextController
            activeController?.registerCallback(controllerCallback)
        }

        publishMediaState(activeController, postedNotification)
    }

    private fun publishMediaState(
        controller: MediaController?,
        postedNotification: StatusBarNotification? = null
    ) {
        val metadata = controller?.metadata
        val mediaNotification = postedNotification?.takeIf {
            isMediaNotification(it)
        } ?: activeNotifications
            ?.asSequence()
            ?.filter { isMediaNotification(it) && (controller == null || it.packageName == controller.packageName) }
            ?.sortedByDescending { it.postTime }
            ?.firstOrNull()
        val notificationText = mediaNotification?.let(::extractTextFromNotification)
        val metadataTitle = metadata.metadataText(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE
        )
        val metadataArtist = metadata.metadataText(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        )
        val title = notificationText
            ?.title
            ?.takeIf(::isUsableText)
            ?: metadataTitle
        val artist = notificationText
            ?.artist
            ?.takeIf { isUsableText(it) && it != title }
            ?: metadataArtist
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: mediaNotification?.let(::extractBitmapFromNotification)
            ?: activeNotifications
                ?.asSequence()
                ?.filter { isMediaNotification(it) && (controller == null || it.packageName == controller.packageName) }
                ?.mapNotNull(::extractBitmapFromNotification)
                ?.firstOrNull()
        val albumArtUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        MediaArtworkRepository.updateMetadata(
            title,
            artist,
            albumArtUri,
            controller?.packageName ?: mediaNotification?.packageName
        )
        MediaArtworkRepository.updateAlbumArt(bitmap)
        MediaArtworkRepository.updateDuration(duration)
        MediaArtworkRepository.updatePlaybackState(controller?.playbackState)
    }

    private fun extractTextFromNotification(sbn: StatusBarNotification): NotificationText? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.cleanExtra(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG
        )
        val artist = extras.cleanExtra(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_BIG_TEXT
        )?.takeIf { it != title }

        if (title.isNullOrBlank() && artist.isNullOrBlank()) return null
        return NotificationText(title.orEmpty(), artist.orEmpty())
    }

    private fun Bundle.cleanExtra(vararg keys: String): String? {
        return keys.asSequence()
            .mapNotNull { key ->
                getCharSequence(key)?.toString() ?: getString(key)
            }
            .map(::cleanText)
            .firstOrNull(::isUsableText)
    }

    private fun cleanText(value: String?): String {
        return value
            ?.substringBefore('\u0000')
            ?.replace('\uFFFD', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun isUsableText(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return false
        if (text.equals("android head unit", ignoreCase = true)) return false
        if (text.equals("unknown", ignoreCase = true)) return false
        if (text.equals("null", ignoreCase = true)) return false

        val lettersOrDigits = text.count { it.isLetterOrDigit() }
        val badControls = text.count { it.isISOControl() && !it.isWhitespace() }
        if (lettersOrDigits == 0) return false
        if (badControls > 0) return false
        return true
    }

    private fun unregisterActiveController() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    private fun extractBitmapFromNotification(sbn: StatusBarNotification): Bitmap? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val bitmap = getParcelableCompat<Bitmap>(extras, Notification.EXTRA_PICTURE)
            ?: getParcelableCompat<Bitmap>(extras, Notification.EXTRA_LARGE_ICON_BIG)
            ?: getParcelableCompat<Bitmap>(extras, Notification.EXTRA_LARGE_ICON)
            ?: extractBitmapFromIcon(getParcelableCompat(extras, Notification.EXTRA_LARGE_ICON_BIG))
            ?: extractBitmapFromIcon(getParcelableCompat(extras, Notification.EXTRA_LARGE_ICON))
            ?: extractBitmapFromIcon(notification.getLargeIcon())

        return bitmap?.takeIf { it.width > 1 && it.height > 1 }
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        if (notification.category == Notification.CATEGORY_TRANSPORT) return true

        val extras = notification.extras ?: return false
        if (getParcelableCompat<MediaSession.Token>(extras, Notification.EXTRA_MEDIA_SESSION) != null) {
            return true
        }

        val template = extras.getString("android.template").orEmpty()
        return template.contains("MediaStyle", ignoreCase = true)
    }

    private fun hasUsableMetadata(metadata: MediaMetadata?): Boolean {
        return metadata.metadataText(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE
        ).isNotBlank() ||
            metadata.metadataText(
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
            ).isNotBlank() ||
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) != null ||
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null
    }

    private fun MediaMetadata?.metadataText(vararg keys: String): String {
        if (this == null) return ""
        return keys.asSequence()
            .mapNotNull { getString(it) }
            .map(::cleanText)
            .firstOrNull(::isUsableText)
            .orEmpty()
    }

    private fun extractBitmapFromIcon(icon: Icon?): Bitmap? {
        if (icon == null) return null
        return runCatching {
            val drawable = icon.loadDrawable(this)
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> null
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> getParcelableCompat(extras: Bundle, key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(key, T::class.java)
        } else {
            extras.getParcelable(key) as? T
        }
    }
}
