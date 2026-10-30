package com.autoos.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class MediaActivity : AppCompatActivity() {

    private val flashDurationMs = 280L
    private var prevFlashUntil = 0L
    private var playPauseFlashUntil = 0L
    private var nextFlashUntil = 0L
    private var permissionSettingsOpened = false
    private var albumArtUriJob: Job? = null
    private var renderedAlbumArtUri: String? = null
    private val zlinkAlbumArtPaths = listOf(
        "/storage/emulated/0/cp.jpg",
        "/sdcard/cp.jpg"
    )

    private lateinit var btnBack: View
    private lateinit var btnPrev: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: ImageView

    private lateinit var albumArt: ImageView
    private lateinit var sourceText: TextView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var albumText: TextView
    private lateinit var permissionHint: TextView
    private lateinit var progressBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private var albumArtPlaceholderDrawable: Drawable? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            renderProgress()
            progressHandler.postDelayed(this, 1000L)
        }
    }

    private val btListener: () -> Unit = {
        runOnUiThread { render() }
    }

    private val artworkListener: () -> Unit = {
        runOnUiThread { render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContentView(R.layout.activity_media)
        playScreenEnterAnimation()

        btnBack = findViewById(R.id.btn_back_media)
        installCompositeAlphaPressFeedback(
            btnBack,
            findViewById(R.id.apps_button3),
            findViewById(R.id.return_text)
        )
        btnPrev = findViewById(R.id.media_prev)
        btnPlayPause = findViewById(R.id.media_play_pause)
        btnNext = findViewById(R.id.media_next)

        albumArt = findViewById(R.id.album_art_empty)
        albumArtPlaceholderDrawable = albumArt.drawable
        sourceText = findViewById(R.id.media_source_text)
        titleText = findViewById(R.id.media_title_text)
        artistText = findViewById(R.id.media_artist_text)
        albumText = findViewById(R.id.media_album_text)
        permissionHint = findViewById(R.id.media_permission_hint)
        progressBar = findViewById(R.id.progress_bar)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)

        progressBar.isEnabled = false

        permissionHint.setOnClickListener {
            openNotificationAccessSettings()
        }

        btnBack.setOnClickListener { onBackPressed() }
        btnPrev.setOnClickListener {
            prevFlashUntil = System.currentTimeMillis() + flashDurationMs
            flashButton(btnPrev)
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        btnPlayPause.setOnClickListener {
            playPauseFlashUntil = System.currentTimeMillis() + flashDurationMs
            flashButton(btnPlayPause)
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        btnNext.setOnClickListener {
            nextFlashUntil = System.currentTimeMillis() + flashDurationMs
            flashButton(btnNext)
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        MediaArtworkRepository.addListener(artworkListener)
        MediaArtworkRepository.refreshNotificationAccess(this)
        if (!MediaArtworkRepository.hasNotificationAccess && !permissionSettingsOpened) {
            permissionSettingsOpened = true
            openNotificationAccessSettings()
        }
        render()
        progressHandler.post(progressTicker)
    }

    override fun onPause() {
        progressHandler.removeCallbacks(progressTicker)
        MediaArtworkRepository.removeListener(artworkListener)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        albumArtUriJob?.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        startActivity(
            Intent(this, AppsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun sendMediaKey(code: Int) {
        val audio = getSystemService(android.media.AudioManager::class.java)
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
    }

    private fun render() {
        val mediaTitle = MediaArtworkRepository.title
        val mediaArtist = MediaArtworkRepository.artist

        sourceText.text = if (MediaArtworkRepository.sourcePackage.isNotBlank()) {
            "Source: Media"

        } else {
            "Source: No media"
        }

        titleText.text = mediaTitle
        artistText.text = mediaArtist
        albumText.text = if (MediaArtworkRepository.sourcePackage.isNotBlank()) {
            "Android media session active"

        } else {
            ""
        }

        val albumArtBitmap = MediaArtworkRepository.albumArtBitmap
        if (albumArtBitmap != null) {
            albumArtUriJob?.cancel()
            renderedAlbumArtUri = null
            albumArt.setImageBitmap(albumArtBitmap)
        } else {
            loadAlbumArtUri(MediaArtworkRepository.albumArtUri)
        }

        permissionHint.visibility = if (MediaArtworkRepository.hasNotificationAccess) {
            View.GONE
        } else {
            View.VISIBLE
        }

        btnPlayPause.setImageResource(
            if (MediaArtworkRepository.isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
        )
        applyButtonFlashState()
        renderProgress()


    }

    private fun loadAlbumArtUri(uriText: String) {
        if (uriText.isBlank()) {
            loadZlinkAlbumArtFile()
            return
        }

        if (renderedAlbumArtUri == uriText) return
        renderedAlbumArtUri = uriText
        albumArtUriJob?.cancel()
        albumArtUriJob = lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = runCatching { openBitmapFromUri(uriText) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (renderedAlbumArtUri != uriText) return@withContext
                if (bitmap != null) {
                    albumArt.setImageBitmap(bitmap)
                } else {
                    loadZlinkAlbumArtFile()
                }
            }
        }
    }

    private fun loadZlinkAlbumArtFile() {
        val file = findZlinkAlbumArtFile()
        if (file == null) {
            albumArtUriJob?.cancel()
            renderedAlbumArtUri = null
            showAlbumArtPlaceholder()
            return
        }

        val renderKey = "zlink-file:${file.absolutePath}:${file.lastModified()}:${file.length()}"
        if (renderedAlbumArtUri == renderKey) return
        renderedAlbumArtUri = renderKey
        albumArtUriJob?.cancel()
        albumArtUriJob = lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                BitmapFactory.decodeFile(file.absolutePath)
            }.getOrNull()?.takeIf { it.width > 1 && it.height > 1 }

            withContext(Dispatchers.Main) {
                if (renderedAlbumArtUri != renderKey) return@withContext
                if (bitmap != null) {
                    albumArt.setImageBitmap(bitmap)
                    if (MediaArtworkRepository.sourcePackage.isBlank()) {
                        albumText.text = getString(R.string.zlink_album_art)
                    }
                } else {
                    showAlbumArtPlaceholder()
                }
            }
        }
    }

    private fun findZlinkAlbumArtFile(): File? {
        return zlinkAlbumArtPaths
            .asSequence()
            .map(::File)
            .filter { it.isFile && it.length() > 1024L }
            .maxByOrNull { it.lastModified() }
    }

    private fun showAlbumArtPlaceholder() {
        albumArt.setImageDrawable(albumArtPlaceholderDrawable)
    }

    private fun openBitmapFromUri(uriText: String): Bitmap? {
        val uri = Uri.parse(uriText)
        return when (uri.scheme?.lowercase()) {
            "content", "file", "android.resource" -> {
                contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
            "http", "https" -> {
                URL(uriText).openStream().use(BitmapFactory::decodeStream)
            }
            else -> null
        }?.takeIf { it.width > 1 && it.height > 1 }
    }

    private fun renderProgress() {
        val duration = MediaArtworkRepository.durationMs
        val position = MediaArtworkRepository.currentPositionMs()

        if (duration > 0L) {
            progressBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            progressBar.progress = position.coerceAtMost(progressBar.max.toLong()).toInt()
            timeCurrent.text = formatTime(position)
            timeTotal.text = "-${formatTime(duration)}"
        } else {
            progressBar.max = 100
            progressBar.progress = 0
            timeCurrent.text = "0:00"
            timeTotal.text = "-0:00"
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    private fun flashButton(button: ImageView) {
        button.isSelected = true
        button.postDelayed({
            applyButtonFlashState()
        }, flashDurationMs)
    }

    private fun applyButtonFlashState() {
        val now = System.currentTimeMillis()
        btnPrev.isSelected = now < prevFlashUntil
        btnPlayPause.isSelected = now < playPauseFlashUntil
        btnNext.isSelected = now < nextFlashUntil
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    private fun openNotificationAccessSettings() {
        val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this@MediaActivity, MediaNotificationListenerService::class.java).flattenToString()
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val intentToLaunch = when {
            detailIntent.resolveActivity(packageManager) != null -> detailIntent
            fallbackIntent.resolveActivity(packageManager) != null -> fallbackIntent
            else -> null
        }

        intentToLaunch?.let { startActivity(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
