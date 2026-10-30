package com.autoos.launcher

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper

/** Software effects for player-announced sessions. Never uses global session 0. */
object EqManager {
    enum class ToneBand { BASS, MID, TREBLE }
    private data class Session(val owner: String, val effect: Equalizer, var applied: Boolean = false)
    private val sessions = mutableMapOf<Int, Session>()
    private val listeners = mutableSetOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var context: Context
    private val gains = IntArray(3)
    var failed = false
        private set
    val active: Boolean get() = sessions.values.any { it.applied }

    fun install(base: Context) {
        if (::context.isInitialized) return
        context = base.applicationContext
        val prefs = context.getSharedPreferences("software_eq", 0)
        ToneBand.entries.forEach { gains[it.ordinal] = prefs.getInt(it.name, 0).coerceIn(-9, 9) }
    }
    fun gain(band: ToneBand) = gains[band.ordinal]
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { listeners.toList().forEach { it() } }

    fun open(id: Int, owner: String) {
        if (id <= 0 || owner.isBlank()) return
        if (sessions[id]?.owner == owner) return
        close(id, sessions[id]?.owner.orEmpty())
        var effect: Equalizer? = null
        try {
            effect = Equalizer(0, id)
            check(effect.numberOfBands > 0)
            val session = Session(owner, effect)
            sessions[id] = session
            effect.setControlStatusListener { _, _ -> main.post {
                if (sessions[id] === session) { apply(session); notifyChanged() }
            } }
            apply(session)
        } catch (_: Exception) {
            runCatching { effect?.release() }
            sessions.remove(id)
            failed = true
        }
        notifyChanged()
    }
    fun close(id: Int, owner: String) {
        val session = sessions[id] ?: return
        if (session.owner != owner) return
        sessions.remove(id)
        runCatching { session.effect.release() }
        if (sessions.isEmpty()) failed = false
        notifyChanged()
    }
    fun setToneBand(band: ToneBand, db: Int) {
        gains[band.ordinal] = db.coerceIn(-9, 9)
        context.getSharedPreferences("software_eq", 0).edit().putInt(band.name, gain(band)).apply()
        failed = false
        sessions.values.forEach(::apply)
        notifyChanged()
    }
    private fun apply(session: Session) {
        session.applied = false
        try {
            val effect = session.effect
            check(effect.hasControl())
            val range = effect.bandLevelRange
            for (index in 0 until effect.numberOfBands.toInt()) {
                val band = index.toShort()
                val hz = effect.getCenterFreq(band) / 1000
                val group = if (hz < 300) ToneBand.BASS else if (hz < 4000) ToneBand.MID else ToneBand.TREBLE
                effect.setBandLevel(band, (gain(group) * 100).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            check(effect.setEnabled(true) == AudioEffect.SUCCESS)
            session.applied = true
        } catch (_: Exception) { failed = true }
    }
}
