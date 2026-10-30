package com.autoos.launcher

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/** Resolves strings for overlays that use the application context. */
object AppLanguage {
    /** Device language must not select a paid customization automatically. */
    fun selected(context: Context): String =
        context.getSharedPreferences("AutoOS_Launcher",Context.MODE_PRIVATE)
            .getString("app_language",null)?.takeIf { it.isNotBlank() } ?: "en"

    fun get(context: Context, @StringRes id: Int): String {
        val code=selected(context)
        val config=Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(code))
        return context.createConfigurationContext(config).getString(id)
    }
}
