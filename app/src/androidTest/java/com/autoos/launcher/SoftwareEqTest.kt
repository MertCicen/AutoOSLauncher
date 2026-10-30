package com.autoos.launcher

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoftwareEqTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test fun invalidSessionsDoNotActivateEq() {
        main {
            EqManager.open(0, context.packageName)
            EqManager.open(-1, context.packageName)
            assertFalse(EqManager.active)
        }
    }

    @Test fun realSessionAppliesGainsSurvivesScreenExitAndCloses() {
        val size = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM)
        assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
        val id = track.audioSessionId
        val old = EqManager.ToneBand.entries.associateWith { EqManager.gain(it) }
        var observer: Equalizer? = null
        try {
            main {
                AudioEffectSessionReceiver().onReceive(context,
                    Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
                        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName))
                assertTrue("Device software EQ must attach to the real track", EqManager.active)
                EqManager.setToneBand(EqManager.ToneBand.BASS, 3)
                EqManager.setToneBand(EqManager.ToneBand.MID, -2)
                EqManager.setToneBand(EqManager.ToneBand.TREBLE, 4)
            }
            observer = Equalizer(-1, id)
            val effect = observer
            assertTrue(effect.enabled)
            for(index in 0 until effect.numberOfBands.toInt()) {
                val band = index.toShort()
                val hz = effect.getCenterFreq(band) / 1000
                val expected = if(hz < 300) 300 else if(hz < 4000) -200 else 400
                val range = effect.bandLevelRange
                assertEquals(expected.coerceIn(range[0].toInt(),range[1].toInt()),effect.getBandLevel(band).toInt())
            }
            val activity = instrumentation.startActivitySync(Intent(context, ToneActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            main {
                assertEquals(12, activity.findViewById<SeekBar>(R.id.tone_bass_seek).progress)
                assertEquals(context.getString(R.string.software_eq_active),activity.findViewById<TextView>(R.id.tone_status_text).text.toString())
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            main { assertTrue("Leaving EQ screen must not release the effect", EqManager.active) }
            assertTrue(effect.enabled)
            main {
                EqManager.close(id, "unrelated.owner")
                assertTrue(EqManager.active)
                AudioEffectSessionReceiver().onReceive(context,
                    Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
                        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName))
                assertFalse(EqManager.active)
            }
        } finally {
            main {
                EqManager.close(id, context.packageName)
                old.forEach { (band, db) -> EqManager.setToneBand(band, db) }
            }
            observer?.release()
            track.release()
        }
    }
}
