package com.autoos.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SharedWallpaperTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun chosenWallpaperAppearsOnEveryLauncherScreen() {
        val prefs = context.getSharedPreferences("AutoOS_Launcher", 0)
        val key = "launcher_custom_wallpaper_uri"
        val old = prefs.getString(key, null)
        val file = File(context.cacheDir, "wallpaper-test.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(18, 113, 201))
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        prefs.edit().putString(key, Uri.fromFile(file).toString()).commit()
        try {
            val screens = listOf(
                MainActivity::class.java, AppsActivity::class.java,
                LauncherOptionsActivity::class.java, LauncherSettingsActivity::class.java,
                ToneActivity::class.java, MediaActivity::class.java,
                MapActivity::class.java, DebugLogActivity::class.java
            )
            for (screen in screens) {
                val activity = instrumentation.startActivitySync(
                    Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                instrumentation.waitForIdleSync()
                val background = activity.window.decorView.findViewWithTag<View>("launcher_wallpaper_root")
                assertNotNull("${screen.simpleName} must expose the shared wallpaper", background)
                val deadline = SystemClock.uptimeMillis() + 4000
                while ((background.background as? BitmapDrawable)?.bitmap?.let {
                        it.getPixel(it.width / 2, it.height / 2) == Color.rgb(18, 113, 201)
                    } != true && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(50)
                }
                val image = background.background as? BitmapDrawable
                assertNotNull("${screen.simpleName} did not load the chosen image", image)
                assertEquals("${screen.simpleName} displayed another image", Color.rgb(18, 113, 201),
                    image!!.bitmap.getPixel(image.bitmap.width / 2, image.bitmap.height / 2))
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        } finally {
            prefs.edit().apply { if (old == null) remove(key) else putString(key, old) }.commit()
            file.delete()
        }
    }
}
