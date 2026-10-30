package com.autoos.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.LocaleListCompat

class LauncherOptionsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("AutoOS_Launcher",MODE_PRIVATE) }
    private val wallpaper=registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImage("launcher_custom_wallpaper_uri",it) }
    private val sign=registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImage("custom_sign_image_uri",it) }
    private val vehicle=registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImage("custom_vehicle_image_uri",it) }
    private fun saveImage(key:String,uri:Uri?) {
        if(uri==null) return
        runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val old=prefs.getString(key,null)
        prefs.edit().putString(key,uri.toString()).apply()
        releaseUnused(old)
        LauncherTheme.apply(this)
        Toast.makeText(this,R.string.image_saved,Toast.LENGTH_SHORT).show()
    }
    private fun releaseUnused(old:String?) {
        if(old==null) return
        if(listOf("launcher_custom_wallpaper_uri","custom_sign_image_uri","custom_vehicle_image_uri").none { prefs.getString(it,null)==old }) runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    private fun reset(key:String) {
        val old=prefs.getString(key,null); prefs.edit().remove(key).apply(); releaseUnused(old); LauncherTheme.apply(this)
        Toast.makeText(this,R.string.default_restored,Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_launcher_options)
        findViewById<View>(R.id.launcher_options_root).setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        window.decorView.systemUiVisibility=5894
        setupLanguageSelector()
        findViewById<View>(R.id.launcher_options_back).setOnClickListener { openAppsMenu() }
        val text=findViewById<EditText>(R.id.launcher_options_home_text)
        text.setText(AutoOsUi.header(this))
        findViewById<View>(R.id.launcher_options_home_text_save).setOnClickListener {
            val value=text.text.toString().trim()
            if(value.isEmpty()) Toast.makeText(this,R.string.custom_header_empty,Toast.LENGTH_SHORT).show()
            else { prefs.edit().putString("custom_header_text",value).apply(); Toast.makeText(this,R.string.saved,Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.launcher_options_wallpaper_choose).setOnClickListener { wallpaper.launch(arrayOf("image/*")) }
        findViewById<View>(R.id.launcher_options_sign_choose).setOnClickListener { sign.launch(arrayOf("image/*")) }
        findViewById<View>(R.id.launcher_options_vehicle_choose).setOnClickListener { vehicle.launch(arrayOf("image/*")) }
        findViewById<View>(R.id.launcher_options_wallpaper_reset).setOnClickListener { reset("launcher_custom_wallpaper_uri") }
        findViewById<View>(R.id.launcher_options_sign_reset).setOnClickListener { reset("custom_sign_image_uri") }
        findViewById<View>(R.id.launcher_options_vehicle_reset).setOnClickListener { reset("custom_vehicle_image_uri") }
        val slider=findViewById<SeekBar>(R.id.launcher_options_hue_slider)
        val label=findViewById<TextView>(R.id.launcher_options_hue_value)
        val preview=findViewById<View>(R.id.launcher_options_hue_preview)
        slider.max=359; slider.progress=prefs.getFloat("launcher_accent_hue",230f).toInt().coerceIn(0,359)
        var lastWallpaperUpdate=0L
        fun render() { label.text="${slider.progress}°"; preview.setBackgroundColor(LauncherTheme.accent(slider.progress.toFloat())) }
        render()
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean) {
                if(fromUser) {
                    val now=android.os.SystemClock.uptimeMillis()
                    if(now-lastWallpaperUpdate>=75L) {
                        LauncherTheme.previewHue(this@LauncherOptionsActivity,progress.toFloat())
                        lastWallpaperUpdate=now
                    }
                }
                render()
            }
            override fun onStartTrackingTouch(bar:SeekBar)=Unit
            override fun onStopTrackingTouch(bar:SeekBar) {
                prefs.edit().putFloat("launcher_accent_hue",bar.progress.toFloat()).apply()
                LauncherTheme.previewHue(this@LauncherOptionsActivity,bar.progress.toFloat())
            }
        })
    }
    private fun setupLanguageSelector() {
        val selector=findViewById<View>(R.id.launcher_options_language)
        val value=findViewById<TextView>(R.id.launcher_options_language_value)
        val codes=resources.getStringArray(R.array.supported_language_codes)
        val names=resources.getStringArray(R.array.supported_language_names)
        val current=AppLanguage.selected(this)
        value.text=names[codes.indexOf(current).coerceAtLeast(0)]
        selector.contentDescription=getString(R.string.language)+": "+value.text
        selector.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(names,codes.indexOf(AppLanguage.selected(this)).coerceAtLeast(0)) { dialog,position ->
                dialog.dismiss()
                val code=codes.getOrNull(position) ?: return@setSingleChoiceItems
                if(code==AppLanguage.selected(this)) return@setSingleChoiceItems
                value.text=names[position]
                selector.contentDescription=getString(R.string.language)+": "+value.text
                prefs.edit().putString("app_language",code).apply()
                val locale=Locale.forLanguageTag(code)
                Locale.setDefault(locale)
                // Apply the choice at application scope so every Activity,
                // dialog and popup uses the new resources immediately.
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                }
                .setNegativeButton(R.string.cancel,null)
                .show()
        }
    }
    private fun openAppsMenu() {
        startActivity(Intent(this,AppsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        overridePendingTransition(R.anim.fade_in,R.anim.fade_out)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { openAppsMenu() }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) window.decorView.systemUiVisibility=5894 }
}
