package com.autoos.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.roundToInt

/** AutoOS home, reconstructed from the supplied 1.27 APK. */
class MainActivity : AppCompatActivity(), LocationListener {
    private val prefs by lazy { getSharedPreferences("AutoOS_Launcher", MODE_PRIVATE) }
    private val gps by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val handler = Handler(Looper.getMainLooper())
    private var requested = false
    private var tracking = false
    private var previous: Location? = null
    private var previousSpeed = 0.0
    private var lastFix = 0L
    private var distance = 0.0
    private var seconds = 0.0
    private lateinit var current: TextView
    private lateinit var average: TextView
    private lateinit var trip: TextView
    private data class Slot(val key: String, val label: TextView, val icon: ImageView?, val defaultText: CharSequence, val defaultIcon: android.graphics.drawable.Drawable?)
    private val slots = mutableListOf<Slot>()
    private val tick = object : Runnable {
        override fun run() {
            if (prefs.getString("home_shortcut_trip_time", null).isNullOrEmpty()) trip.text = TripTimer.formatElapsed()
            if (lastFix == 0L || SystemClock.elapsedRealtime() - lastFix > 7000) current.setText(R.string.current_speed_waiting)
            handler.postDelayed(this, 2000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()
        TripTimer.initIfNeeded()
        current = findViewById(R.id.current_speed_text)
        average = findViewById(R.id.average_speed_text)
        trip = findViewById(R.id.trip_time_text2)
        fun slot(key: String, text: Int, icon: Int? = null) {
            val label = findViewById<TextView>(text)
            val image = icon?.let { findViewById<ImageView>(it) }
            val s = Slot(key,label,image,label.text,image?.drawable)
            slots.add(s)
            listOfNotNull(label,image).forEach { v ->
                v.setOnClickListener { prefs.getString("home_shortcut_$key",null)?.let { AutoOsUi.launch(this,it) } }
                v.setOnLongClickListener {
                    AutoOsUi.pickApp(this,true) { pkg ->
                        prefs.edit().apply { if(pkg==null) remove("home_shortcut_$key") else putString("home_shortcut_$key",pkg) }.apply()
                        renderSlots()
                    }; true
                }
            }
        }
        slot("trip_time",R.id.trip_time_text2)
        slot("fuel",R.id.shortcut_1_label,R.id.shortcut_1_icon)
        slot("battery",R.id.shortcut_2_label,R.id.shortcut_2_icon)
        slot("water_temp",R.id.shortcut_3_label,R.id.shortcut_3_icon)
        slot("mileage",R.id.shortcut_4_label,R.id.shortcut_4_icon)
        findViewById<View>(R.id.apps_button_bg).setOnClickListener { startActivity(Intent(this,AppsActivity::class.java)); overridePendingTransition(R.anim.fade_in,R.anim.fade_out) }
        findViewById<View>(R.id.settings).apply {
            setOnClickListener { askPasswordAndLaunchSettingsApp() }
            setOnLongClickListener { openSettingsAppPicker(); true }
        }
    }
    private fun renderSlots() {
        slots.forEach { s ->
            val pkg=prefs.getString("home_shortcut_${s.key}",null)
            val info=pkg?.let { runCatching { packageManager.getApplicationInfo(it,0) }.getOrNull() }
            if(info!=null && packageManager.getLaunchIntentForPackage(pkg!!)!=null) {
                s.label.text=packageManager.getApplicationLabel(info)
                val icon=AutoOsUi.monoIcon(packageManager.getApplicationIcon(info))
                if(s.icon!=null) {
                    s.icon.imageTintList=null
                    s.icon.setImageDrawable(icon)
                } else {
                    val size=AutoOsUi.dp(this,32); icon.setBounds(0,0,size,size)
                    s.label.setCompoundDrawables(icon,null,null,null)
                }
            } else {
                s.label.text=s.defaultText; s.label.setCompoundDrawables(null,null,null,null)
                s.icon?.apply {
                    setImageDrawable(s.defaultIcon)
                    imageTintList=ColorStateList.valueOf(LauncherTheme.accent(prefs.getFloat("launcher_accent_hue",230f)))
                }
                if(pkg!=null) prefs.edit().remove("home_shortcut_${s.key}").apply()
            }
        }
    }
    override fun onResume() {
        super.onResume(); hideSystemUI()
        val accent=LauncherTheme.accent(prefs.getFloat("launcher_accent_hue",230f))
        findViewById<View>(R.id.home_bottom_accent).setBackgroundColor(accent)
        findViewById<View>(R.id.home_apps_underline).setBackgroundColor(accent)
        findViewById<TextView>(R.id.current_road_name_text).text=AutoOsUi.header(this)
        AutoOsUi.loadImage(this,findViewById(R.id.custom_sign_image),"custom_sign_image_uri",R.drawable.autoos_home_logo)
        AutoOsUi.loadImage(this,findViewById(R.id.imageView),"custom_vehicle_image_uri",R.drawable.autoos_concept_car)
        renderSlots(); handler.removeCallbacks(tick); handler.post(tick); startGps()
    }
    override fun onPause() {
        handler.removeCallbacks(tick)
        if(tracking) gps.removeUpdates(this)
        tracking=false; previous=null; previousSpeed=0.0
        super.onPause()
    }
    @SuppressLint("MissingPermission")
    private fun startGps() {
        val granted=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any { ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED }
        if(!granted) {
            if(!requested) { requested=true; ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),4101) }
            return
        }
        if(!tracking) runCatching { gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,this,Looper.getMainLooper()); tracking=true }
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==4101 && grantResults.any { it==PackageManager.PERMISSION_GRANTED }) startGps()
    }
    override fun onLocationChanged(location:Location) {
        if(location.hasAccuracy() && location.accuracy>50f) return
        val prev=previous
        val elapsed=prev?.let { if(location.elapsedRealtimeNanos>it.elapsedRealtimeNanos) (location.elapsedRealtimeNanos-it.elapsedRealtimeNanos)/1e9 else (location.time-it.time)/1000.0 } ?: 0.0
        var speed=if(location.hasSpeed()) location.speed.toDouble().coerceIn(0.0,100.0) else 0.0
        if(!location.hasSpeed() && prev!=null && elapsed in 0.5..10.0) {
            val meters=prev.distanceTo(location).toDouble()
            if(meters>max(4.0,max(location.accuracy.toDouble(),prev.accuracy.toDouble()))) speed=(meters/elapsed).coerceIn(0.0,100.0)
        }
        if(prev!=null && elapsed in 0.5..10.0) { distance+=(previousSpeed+speed)*0.5*elapsed; seconds+=elapsed }
        previous=Location(location); previousSpeed=speed; lastFix=SystemClock.elapsedRealtime()
        current.text=getString(R.string.current_speed_format,(speed*3.6).roundToInt())
        average.text=getString(R.string.average_speed_format,((if(seconds>0) distance/seconds else speed)*3.6).roundToInt())
    }
    override fun onProviderDisabled(provider:String) { if(provider==LocationManager.GPS_PROVIDER) { lastFix=0; previous=null; current.setText(R.string.current_speed_waiting) } }
    override fun onProviderEnabled(provider:String) = Unit
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) = Unit

    // Original PIN dialog implementation is appended by the restoration script.

    private fun openSettingsAppPicker() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val packages = apps.map { it.activityInfo.packageName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.select_app)
            .setItems(names) { _, which ->
                saveSettingsApp(packages[which])
                showCustomPinDialog { pin ->
                    getSharedPreferences("AutoOS_Launcher", MODE_PRIVATE)
                        .edit()
                        .putString("settings_app_password", pin)
                        .apply()

                    Toast.makeText(this, R.string.password_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveSettingsApp(packageName: String) {
        getSharedPreferences("AutoOS_Launcher", MODE_PRIVATE)
            .edit()
            .putString("settings_app_package", packageName)
            .apply()

        Toast.makeText(this, R.string.app_set, Toast.LENGTH_SHORT).show()
    }

    private fun askPasswordAndLaunchSettingsApp() {
        val prefs = getSharedPreferences("AutoOS_Launcher", MODE_PRIVATE)
        val savedPackage = prefs.getString("settings_app_package", null)
        val savedPassword = prefs.getString("settings_app_password", null)

        if (savedPackage.isNullOrBlank()) {
            Toast.makeText(this, R.string.long_press_to_set, Toast.LENGTH_SHORT).show()
            return
        }

        if (savedPassword.isNullOrBlank()) {
            Toast.makeText(this, R.string.long_press_password, Toast.LENGTH_SHORT).show()
            return
        }

        showWarningDialog {
            showCustomPinDialog { enteredPin ->
                if (enteredPin == savedPassword) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(savedPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    } else {
                        Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showWarningDialog(onContinue: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_warning, null, false)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setCancelable(true)

        dialogView.findViewById<TextView>(R.id.warning_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.warning_continue).setOnClickListener {
            dialog.dismiss()
            onContinue()

        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showCustomPinDialog(onConfirm: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null, false)
        val displayView = dialogView.findViewById<TextView>(R.id.pin_display)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setCancelable(true)

        val pinBuilder = StringBuilder()

        fun renderPin() {
            displayView.text = "•".repeat(pinBuilder.length)
        }

        fun appendDigit(digit: String) {
            if (pinBuilder.length < 8) {
                pinBuilder.append(digit)
                renderPin()
            }
        }

        fun bindDigit(id: Int, value: String) {
            dialogView.findViewById<TextView>(id).setOnClickListener {
                appendDigit(value)
            }
        }

        bindDigit(R.id.pin_key_1, "1")
        bindDigit(R.id.pin_key_2, "2")
        bindDigit(R.id.pin_key_3, "3")
        bindDigit(R.id.pin_key_4, "4")
        bindDigit(R.id.pin_key_5, "5")
        bindDigit(R.id.pin_key_6, "6")
        bindDigit(R.id.pin_key_7, "7")
        bindDigit(R.id.pin_key_8, "8")
        bindDigit(R.id.pin_key_9, "9")
        bindDigit(R.id.pin_key_0, "0")

        dialogView.findViewById<TextView>(R.id.pin_key_clear).setOnClickListener {
            pinBuilder.clear()
            renderPin()
        }

        dialogView.findViewById<TextView>(R.id.pin_key_delete).setOnClickListener {
            if (pinBuilder.isNotEmpty()) {
                pinBuilder.deleteCharAt(pinBuilder.lastIndex)
                renderPin()
            }
        }

        dialogView.findViewById<TextView>(R.id.pin_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.pin_ok).setOnClickListener {
            val pin = pinBuilder.toString()
            if (pin.isBlank()) {
                Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                onConfirm(pin)
            }
        }

        renderPin()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
