package com.autoos.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.CopyOnWriteArrayList

object MediaArtworkRepository {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notifyPending = false

    @Volatile
    var albumArtBitmap: Bitmap? = null
        private set

    @Volatile
    var title: String = ""
        private set

    @Volatile
    var artist: String = ""
        private set

    @Volatile
    var albumArtUri: String = ""
        private set

    @Volatile
    var sourcePackage: String = ""
        private set

    @Volatile
    var hasNotificationAccess: Boolean = false
        private set

    @Volatile
    var durationMs: Long = 0L
        private set

    @Volatile
    private var playbackPositionMs: Long = 0L

    @Volatile
    private var playbackSpeed: Float = 1f

    @Volatile
    private var playbackState: Int = PlaybackState.STATE_NONE

    @Volatile
    private var playbackStateUpdatedAtMs: Long = 0L

    val isPlaying: Boolean get() = playbackState == PlaybackState.STATE_PLAYING

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun refreshNotificationAccess(context: Context) {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val componentName = ComponentName(context, MediaNotificationListenerService::class.java)
        updateNotificationAccess(
            enabledListeners.contains(componentName.flattenToString()) ||
                enabledListeners.contains(componentName.flattenToShortString())
        )
    }

    fun updateNotificationAccess(enabled: Boolean) {
        if (hasNotificationAccess == enabled) return
        hasNotificationAccess = enabled
        notifyListeners()
    }

    fun updateAlbumArt(bitmap: Bitmap?) {
        if (albumArtBitmap === bitmap) return
        albumArtBitmap = bitmap
        notifyListeners()
    }

    fun updateMetadata(
        newTitle: CharSequence?,
        newArtist: CharSequence?,
        newAlbumArtUri: String?,
        newSourcePackage: String?
    ) {
        val cleanedTitle = cleanText(newTitle)
        val cleanedArtist = cleanText(newArtist)
        val cleanedAlbumArtUri = newAlbumArtUri?.trim().orEmpty()
        val cleanedSourcePackage = newSourcePackage?.trim().orEmpty()

        if (
            title == cleanedTitle &&
            artist == cleanedArtist &&
            albumArtUri == cleanedAlbumArtUri &&
            sourcePackage == cleanedSourcePackage
        ) {
            return
        }

        title = cleanedTitle
        artist = cleanedArtist
        albumArtUri = cleanedAlbumArtUri
        sourcePackage = cleanedSourcePackage
        notifyListeners()
    }

    fun updateBtMusicMetadata(
        newTitle: CharSequence?,
        newArtist: CharSequence?,
        newAlbumArtUri: String?,
        newSourcePackage: String?
    ) {
        updateMetadata(newTitle, newArtist, newAlbumArtUri, newSourcePackage)
    }

    fun updateDuration(duration: Long) {
        val normalized = duration.coerceAtLeast(0L)
        if (durationMs == normalized) return
        durationMs = normalized
        notifyListeners()
    }

    fun updatePlaybackState(state: PlaybackState?) {
        playbackState = state?.state ?: PlaybackState.STATE_NONE
        playbackPositionMs = state?.position?.coerceAtLeast(0L) ?: 0L
        playbackSpeed = state?.playbackSpeed ?: 1f
        playbackStateUpdatedAtMs = SystemClock.elapsedRealtime()
        notifyListeners()
    }

    fun currentPositionMs(): Long {
        val base = playbackPositionMs
        if (playbackState != PlaybackState.STATE_PLAYING) {
            return clampToDuration(base)
        }

        val elapsed = SystemClock.elapsedRealtime() - playbackStateUpdatedAtMs
        val advanced = base + (elapsed * playbackSpeed).toLong()
        return clampToDuration(advanced)
    }

    fun clear() {
        val hadValues = albumArtBitmap != null ||
            title.isNotBlank() ||
            artist.isNotBlank() ||
            albumArtUri.isNotBlank() ||
            sourcePackage.isNotBlank() ||
            durationMs != 0L ||
            playbackPositionMs != 0L
        albumArtBitmap = null
        title = ""
        artist = ""
        albumArtUri = ""
        sourcePackage = ""
        durationMs = 0L
        playbackPositionMs = 0L
        playbackSpeed = 1f
        playbackState = PlaybackState.STATE_NONE
        playbackStateUpdatedAtMs = 0L
        if (hadValues) {
            notifyListeners()
        }
    }

    private fun clampToDuration(value: Long): Long {
        val duration = durationMs
        if (duration <= 0L) return value.coerceAtLeast(0L)
        return value.coerceIn(0L, duration)
    }

    private fun cleanText(value: CharSequence?): String {
        val text = value?.toString()
            ?.substringBefore('\u0000')
            ?.replace('\uFFFD', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (text.isBlank()) return ""
        if (text.equals("android head unit", ignoreCase = true)) return ""
        if (text.equals("unknown", ignoreCase = true)) return ""
        if (text.equals("null", ignoreCase = true)) return ""
        if (text.none { it.isLetterOrDigit() }) return ""
        if (text.any { it.isISOControl() && !it.isWhitespace() }) return ""
        return text
    }

    private fun notifyListeners() {
        if (notifyPending) return
        notifyPending = true
        mainHandler.post {
            notifyPending = false
            listeners.forEach { it() }
        }
    }
}
