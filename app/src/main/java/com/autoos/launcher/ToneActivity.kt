package com.autoos.launcher

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ToneActivity : AppCompatActivity() {
    private data class Row(val band: EqManager.ToneBand, val seek: SeekBar, val value: TextView, val scale: ToneScaleView)
    private val rows = mutableListOf<Row>()
    private val listener: () -> Unit = { renderStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tone)
        playScreenEnterAnimation()
        EqManager.install(this)
        attachSession(intent)
        findViewById<View>(R.id.btn_tone_back).setOnClickListener { onBackPressed() }
        bind(EqManager.ToneBand.BASS, R.id.tone_bass_seek, R.id.tone_bass_value, R.id.tone_bass_scale, R.id.tone_bass_minus, R.id.tone_bass_plus)
        bind(EqManager.ToneBand.MID, R.id.tone_mid_seek, R.id.tone_mid_value, R.id.tone_mid_scale, R.id.tone_mid_minus, R.id.tone_mid_plus)
        bind(EqManager.ToneBand.TREBLE, R.id.tone_treble_seek, R.id.tone_treble_value, R.id.tone_treble_scale, R.id.tone_treble_minus, R.id.tone_treble_plus)
        findViewById<View>(R.id.tone_reset_button).setOnClickListener {
            rows.forEach { EqManager.setToneBand(it.band, 0); it.seek.progress = 9 }
        }
    }
    private fun bind(band: EqManager.ToneBand, seekId: Int, textId: Int, scaleId: Int, minusId: Int, plusId: Int) {
        val row = Row(band, findViewById(seekId), findViewById(textId), findViewById(scaleId))
        rows.add(row)
        row.seek.max = 18
        row.seek.progress = EqManager.gain(band) + 9
        fun display(progress: Int) {
            val db = progress - 9
            row.value.text = if(db > 0) "+$db" else "$db"
            row.scale.setProgress(progress)
        }
        display(row.seek.progress)
        row.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                display(progress)
                if(fromUser) EqManager.setToneBand(band, progress - 9)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        fun nudge(delta: Int) {
            row.seek.progress = (row.seek.progress + delta).coerceIn(0, 18)
            EqManager.setToneBand(band, row.seek.progress - 9)
        }
        findViewById<View>(minusId).setOnClickListener { nudge(-1) }
        findViewById<View>(plusId).setOnClickListener { nudge(1) }
    }
    private fun attachSession(intent: Intent) {
        if(intent.action == AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) {
            EqManager.open(intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1),
                intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty())
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); attachSession(intent) }
    override fun onResume() {
        super.onResume()
        EqManager.addListener(listener)
        rows.forEach { it.scale.refreshAccent(); it.seek.progress = EqManager.gain(it.band) + 9 }
        renderStatus()
    }
    override fun onPause() {
        EqManager.removeListener(listener)
        // The effect belongs to the music session, not to this screen.
        super.onPause()
    }
    private fun renderStatus() {
        findViewById<TextView>(R.id.tone_status_text).setText(when {
            EqManager.failed -> R.string.software_eq_unavailable
            EqManager.active -> R.string.software_eq_active
            else -> R.string.software_eq_waiting
        })
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if(intent.action == AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) finish()
        else startActivity(Intent(this, AppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) window.decorView.systemUiVisibility = 5894
    }
}
