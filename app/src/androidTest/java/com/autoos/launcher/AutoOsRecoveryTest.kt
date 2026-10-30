package com.autoos.launcher

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoOsRecoveryTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun launch(cls:Class<out Activity>):Activity {
        val activity=instrumentation.startActivitySync(Intent(context,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        return activity
    }
    @Test fun allAutoOsScreensOpen() {
        val screens=listOf(MainActivity::class.java,AppsActivity::class.java,LauncherOptionsActivity::class.java,LauncherSettingsActivity::class.java,MapActivity::class.java,MediaActivity::class.java,ToneActivity::class.java,DebugLogActivity::class.java)
        for(screen in screens) {
            val a=launch(screen)
            instrumentation.runOnMainSync { assertNotNull(a.findViewById<View>(android.R.id.content)); assertFalse(a.isFinishing); a.finish() }
            instrumentation.waitForIdleSync()
        }
    }
    @Test fun homeCustomizationSurvivesRecreation() {
        val prefs=context.getSharedPreferences("AutoOS_Launcher",0)
        val old=prefs.getString("custom_header_text",null)
        try {
            prefs.edit().putString("custom_header_text","AutoOS recovery test").commit()
            repeat(2) {
                val a=launch(MainActivity::class.java)
                instrumentation.runOnMainSync {
                    assertEquals("AutoOS recovery test",a.findViewById<TextView>(R.id.current_road_name_text).text.toString())
                    assertNotNull(a.findViewById<View>(R.id.current_speed_text))
                    assertNotNull(a.findViewById<View>(R.id.shortcut_4_label))
                    a.finish()
                }
                instrumentation.waitForIdleSync()
            }
        } finally { prefs.edit().apply { if(old==null) remove("custom_header_text") else putString("custom_header_text",old) }.commit() }
    }
    @Test fun packageIdentityIsAutoOs() { assertEquals("com.autoos.launcher",context.packageName) }
}
