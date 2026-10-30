package com.autoos.launcher

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.view.WindowCallbackWrapper
import android.widget.FrameLayout
import android.widget.Toast
import java.util.WeakHashMap
import kotlin.math.roundToInt

object LauncherVolumeOverlay {

    private const val HIDE_DELAY_MS = 1_400L
    private const val VOLUME_POLL_INTERVAL_MS = 250L
    private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    private val wrappedCallbacks = WeakHashMap<Activity, Window.Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeActivity: Activity? = null
    private var appContext: Context? = null
    private var toast: Toast? = null
    private var volumeReceiverInstalled = false
    private var volumePollInstalled = false
    private var lastMusicVolume: Int? = null
    private var lastMusicMax: Int? = null
    private val volumePollRunnable = object : Runnable {
        override fun run() {
            pollMusicVolume()
            mainHandler.postDelayed(this, VOLUME_POLL_INTERVAL_MS)
        }
    }

    fun install(application: Application) {
        appContext = application.applicationContext
        KeyDebugStore.add("volume overlay install sdk=${Build.VERSION.SDK_INT}")
        installVolumeChangedReceiver(application)
        installVolumePolling(application)
        KeyDebugStore.add("volume overlay trigger mode=broadcast-poll-515")
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activeActivity = activity
                installOnActivity(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) {
                if (activeActivity == activity) activeActivity = null
            }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                wrappedCallbacks.remove(activity)
                if (activeActivity == activity) activeActivity = null
            }
        })
    }

    fun handleExternalVolume(direction: Int): Boolean {
        val activity = activeActivity ?: run {
            KeyDebugStore.add("external volume ignored: no active activity direction=$direction")
            return false
        }
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        KeyDebugStore.add("external volume direction=$direction value=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        show(activity, audio, AudioManager.STREAM_MUSIC, "external")
        return true
    }

    fun showCurrentVolume(reason: String): Boolean {
        val context = appContext ?: return false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        KeyDebugStore.add(
            "show current volume reason=$reason value=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        )
        activeActivity?.let { activity ->
            show(activity, audio, AudioManager.STREAM_MUSIC, reason)
            return true
        }
        KeyDebugStore.add("show current volume skipped: no active activity")
        return false
    }

    fun showVolumeLevel(current: Int, max: Int, label: String, reason: String): Boolean {
        val activity = activeActivity ?: run {
            KeyDebugStore.add("show volume level skipped: no active activity reason=$reason")
            return false
        }
        showActivityOverlay(
            activity = activity,
            current = current,
            max = max,
            label = label,
            reason = reason
        )
        return true
    }

    fun showDebugProbe(activity: Activity) {
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        KeyDebugStore.add("manual volume overlay probe")
        showActivityOverlay(activity, audio)
        showToastOverlay(activity.applicationContext, audio)
    }

    private fun installVolumeChangedReceiver(application: Application) {
        if (volumeReceiverInstalled) return
        volumeReceiverInstalled = true
        KeyDebugStore.add("volume receiver install action=$ACTION_VOLUME_CHANGED")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_VOLUME_CHANGED) return
                val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val safeStream = if (stream >= 0) stream else AudioManager.STREAM_MUSIC
                val current = runCatching { audio.getStreamVolume(safeStream) }
                    .getOrElse { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }
                val max = runCatching { audio.getStreamMaxVolume(safeStream) }
                    .getOrElse { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
                KeyDebugStore.add(
                    "volume broadcast stream=$stream value=$current/$max active=" +
                        (activeActivity?.javaClass?.simpleName ?: "none")
                )
                activeActivity?.let { activity ->
                    show(activity, audio, safeStream, "broadcast")
                } ?: KeyDebugStore.add("volume broadcast skipped: no active activity")
            }
        }
        val filter = IntentFilter(ACTION_VOLUME_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            application.registerReceiver(receiver, filter)
        }
    }

    private fun installVolumePolling(application: Application) {
        if (volumePollInstalled) return
        volumePollInstalled = true
        val audio = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lastMusicMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        lastMusicVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        KeyDebugStore.add("volume poll install music=${lastMusicVolume}/${lastMusicMax}")
        mainHandler.postDelayed(volumePollRunnable, VOLUME_POLL_INTERVAL_MS)
    }

    private fun pollMusicVolume() {
        val context = appContext ?: return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val previous = lastMusicVolume
        val previousMax = lastMusicMax
        if (previous != null && (current != previous || max != previousMax)) {
            KeyDebugStore.add("volume poll changed music=$current/$max previous=$previous/$previousMax")
            activeActivity?.let { activity ->
                show(activity, audio, AudioManager.STREAM_MUSIC, "poll")
            } ?: KeyDebugStore.add("volume poll skipped: no active activity")
        }
        lastMusicVolume = current
        lastMusicMax = max
    }

    private fun installOnActivity(activity: Activity) {
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
        val currentCallback = activity.window.callback
        if (currentCallback !is VolumeWindowCallback) {
            wrappedCallbacks[activity] = currentCallback
            activity.window.callback = VolumeWindowCallback(currentCallback, activity)
        }

        val decor = activity.window.decorView
        decor.isFocusableInTouchMode = true
        decor.requestFocus()
        decor.setOnKeyListener { _, keyCode, event ->
            handleVolumeKey(activity, keyCode, event)
        }
    }

    private fun handleVolumeKey(activity: Activity, keyCode: Int, event: KeyEvent): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return false
        }

        val actionName = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
            else -> event.action.toString()
        }
        KeyDebugStore.add(
            "launcher volume key action=$actionName code=$keyCode scan=${event.scanCode} " +
                "repeat=${event.repeatCount} direction=$direction device=${event.deviceId} " +
                "source=0x${event.source.toString(16)} flags=0x${event.flags.toString(16)}"
        )
        if (event.action == KeyEvent.ACTION_DOWN) {
            val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            show(activity, audio, AudioManager.STREAM_MUSIC, "key")
        }
        return true
    }

    private class VolumeWindowCallback(
        callback: Window.Callback,
        private val activity: Activity
    ) : WindowCallbackWrapper(callback) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            return handleVolumeKey(activity, event.keyCode, event) || super.dispatchKeyEvent(event)
        }
    }

    private fun show(activity: Activity, audio: AudioManager, stream: Int, reason: String) {
        showActivityOverlay(activity, audio, stream, reason)
    }

    private fun showActivityOverlay(
        activity: Activity,
        audio: AudioManager,
        stream: Int = AudioManager.STREAM_MUSIC,
        reason: String = "manual"
    ) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = root.findViewWithTag<VolumeHudView>(VolumeHudView.TAG)
            ?: VolumeHudView(activity).also { view ->
                view.tag = VolumeHudView.TAG
                view.visibility = View.GONE
                val metrics = activity.resources.displayMetrics
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (metrics.heightPixels * 0.13f).roundToInt()
                    ).apply {
                        gravity = Gravity.TOP
                    }
                )
            }

        val max = runCatching { audio.getStreamMaxVolume(stream) }
            .getOrElse { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .coerceAtLeast(1)
        val current = runCatching { audio.getStreamVolume(stream) }
            .getOrElse { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }
            .coerceIn(0, max)
        overlay.setVolume(current, max, streamLabel(stream))
        overlay.bringToFront()
        overlay.showAnimated()
        overlay.scheduleHide()
        KeyDebugStore.add(
            "activity overlay shown reason=$reason stream=$stream value=$current/$max " +
                "activity=${activity.javaClass.simpleName} size=${root.width}x${root.height}"
        )
    }

    private fun showActivityOverlay(
        activity: Activity,
        current: Int,
        max: Int,
        label: String,
        reason: String
    ) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = root.findViewWithTag<VolumeHudView>(VolumeHudView.TAG)
            ?: VolumeHudView(activity).also { view ->
                view.tag = VolumeHudView.TAG
                view.visibility = View.GONE
                val metrics = activity.resources.displayMetrics
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (metrics.heightPixels * 0.13f).roundToInt()
                    ).apply {
                        gravity = Gravity.TOP
                    }
                )
            }

        val safeMax = max.coerceAtLeast(1)
        val safeCurrent = current.coerceIn(0, safeMax)
        overlay.setVolume(safeCurrent, safeMax, label)
        overlay.bringToFront()
        overlay.showAnimated()
        overlay.scheduleHide()
        KeyDebugStore.add(
            "activity overlay shown reason=$reason value=$safeCurrent/$safeMax " +
                "activity=${activity.javaClass.simpleName} size=${root.width}x${root.height}"
        )
    }

    private fun showToastOverlay(context: Context, audio: AudioManager) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        val view = VolumeHudView(context).apply {
            setVolume(current, max, streamLabel(AudioManager.STREAM_MUSIC))
        }
        toast?.cancel()
        toast = Toast(context).apply {
            duration = Toast.LENGTH_SHORT
            setGravity(Gravity.TOP or Gravity.START, 0, 0)
            @Suppress("DEPRECATION")
            this.view = view
            show()
        }
        KeyDebugStore.add("toast overlay shown value=$current/$max")
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun streamLabel(stream: Int): String = when (stream) {
        AudioManager.STREAM_MUSIC -> appContext?.let { AppLanguage.get(it,R.string.audio) } ?: "Audio"
        AudioManager.STREAM_RING -> appContext?.let { AppLanguage.get(it,R.string.ring) } ?: "Ringtone"
        AudioManager.STREAM_ALARM -> appContext?.let { AppLanguage.get(it,R.string.alarm) } ?: "Alarm"
        AudioManager.STREAM_NOTIFICATION -> appContext?.let { AppLanguage.get(it,R.string.notifications) } ?: "Notifications"
        AudioManager.STREAM_SYSTEM -> appContext?.let { AppLanguage.get(it,R.string.system) } ?: "System"
        AudioManager.STREAM_VOICE_CALL -> appContext?.let { AppLanguage.get(it,R.string.call) } ?: "Call"
        else -> "${appContext?.let { AppLanguage.get(it,R.string.audio) } ?: "Audio"} $stream"
    }
}

class VolumeHudView(context: Context) : View(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var current = 0
    private var max = 1
    private var label = "Audio"
    private val soundOnIcon: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.sesacik)
    private val soundOffIcon: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.seskapali)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(4, 5, 8) }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(185, 255, 255, 255) }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = resources.displayMetrics.scaledDensity * 17f
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(145, 178, 194, 224) }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 41, 224) }
    private val rect = RectF()

    private val hideRunnable = Runnable {
        hideAnimated()
    }

    fun setVolume(current: Int, max: Int, label: String) {
        this.current = current
        this.max = max.coerceAtLeast(1)
        this.label = label
        invalidate()
    }

    fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 1_400L)
    }

    fun showAnimated() {
        handler.removeCallbacks(hideRunnable)
        animate().cancel()
        if (visibility != VISIBLE) {
            val hiddenOffset = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels * 0.13f
            translationY = -hiddenOffset
        }
        visibility = VISIBLE
        alpha = 1f
        animate()
            .translationY(0f)
            .setDuration(180L)
            .start()
    }

    private fun hideAnimated() {
        animate().cancel()
        animate()
            .translationY(-height.toFloat())
            .setDuration(180L)
            .withEndAction {
                visibility = GONE
                translationY = -height.toFloat()
            }
            .start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = resources.displayMetrics.widthPixels
        val desiredHeight = (resources.displayMetrics.heightPixels * 0.13f).roundToInt()
        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(rect, bgPaint)

        val centerY = height * 0.50f
        val iconLeft = 28f * density
        val iconSize = 28f * density
        val iconTop = centerY - iconSize / 2f
        val icon = if (current == 0) soundOffIcon else soundOnIcon
        rect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        canvas.drawBitmap(icon, null, rect, bitmapPaint)

        val percent = current / max.toFloat()
        val textLeft = 72f * density
        val trackLeft = 178f * density
        val trackRight = width - 36f * density
        val trackHeight = 3f * density
        val trackTop = centerY - trackHeight / 2f

        canvas.drawText(label, textLeft, centerY + 6f * density, textPaint)

        rect.set(trackLeft, trackTop, trackRight, trackTop + trackHeight)
        canvas.drawRoundRect(rect, trackHeight / 2f, trackHeight / 2f, trackPaint)

        rect.set(trackLeft, trackTop, trackLeft + (trackRight - trackLeft) * percent, trackTop + trackHeight)
        canvas.drawRoundRect(rect, trackHeight / 2f, trackHeight / 2f, progressPaint)

        canvas.drawRect(0f, height - 2f * density, width.toFloat(), height.toFloat(), dividerPaint)
    }

    companion object {
        const val TAG = "launcher_volume_hud"
    }
}
