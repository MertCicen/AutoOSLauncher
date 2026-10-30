package com.autoos.launcher

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class AutoOsApplication : Application() {
    override fun attachBaseContext(base: Context) {
        val code=AppLanguage.selected(base)
        val locale=Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config=Configuration(base.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate() {
        super.onCreate()
        val language=AppLanguage.selected(this)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        LauncherVolumeOverlay.install(this)
        EqManager.install(this)
        val filter = android.content.IntentFilter().apply {
            addAction(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        androidx.core.content.ContextCompat.registerReceiver(this, AudioEffectSessionReceiver(), filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        registerActivityLifecycleCallbacks(LauncherTheme)
        registerActivityLifecycleCallbacks(LocaleRefresh)
    }

}
