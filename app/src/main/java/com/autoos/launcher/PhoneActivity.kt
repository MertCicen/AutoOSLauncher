package com.autoos.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Calls are handled by the device phone application. */
class PhoneActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opened = runCatching { startActivity(Intent(Intent.ACTION_DIAL)); true }.getOrDefault(false)
        if (!opened) {
            Toast.makeText(this, R.string.phone_use_device_app, Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        finish()
    }
}
