package com.autoos.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AutoOsUi {
    fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density+0.5f).toInt()
    fun header(c:Context):String {
        val p=c.getSharedPreferences("AutoOS_Launcher",0)
        val stored=p.getString("custom_header_text",null)
        return if(stored==null) c.getString(R.string.custom_header_default) else stored
    }
    fun monoIcon(input:Drawable):Drawable {
        val base=if(Build.VERSION.SDK_INT>=26 && input is android.graphics.drawable.AdaptiveIconDrawable) input.foreground else input
        return (base.constantState?.newDrawable()?.mutate() ?: base.mutate()).apply {
            colorFilter=ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
    }
    fun launch(c:Context,pkg:String) {
        val intent=c.packageManager.getLaunchIntentForPackage(pkg)
        if(intent==null || runCatching { c.startActivity(intent); true }.getOrDefault(false)==false) Toast.makeText(c,R.string.cant_open_app,Toast.LENGTH_SHORT).show()
    }
    fun pickApp(a:ComponentActivity,reset:Boolean=false,onPick:(String?)->Unit) {
        a.lifecycleScope.launch {
            val apps=withContext(Dispatchers.IO) {
                a.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                    .distinctBy { it.activityInfo.packageName }.map { it.loadLabel(a.packageManager).toString() to it.activityInfo.packageName }.sortedBy { it.first.lowercase() }
            }
            if(a.isFinishing || a.isDestroyed) return@launch
            val options=(if(reset) listOf(a.getString(R.string.restore_default)) else emptyList())+apps.map { it.first }
            AlertDialog.Builder(a).setTitle(R.string.select_app).setItems(options.toTypedArray()) { _,i ->
                if(reset && i==0) onPick(null) else onPick(apps[i-(if(reset) 1 else 0)].second)
            }.setNegativeButton(R.string.cancel,null).show()
        }
    }
    fun decode(c:Context,uri:Uri,max:Int=1600):Bitmap? = runCatching {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        var sample=1
        while(kotlin.math.max(bounds.outWidth,bounds.outHeight)/sample>max) sample*=2
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) }
    }.getOrNull()
    fun loadImage(a:ComponentActivity,v:ImageView,key:String,fallback:Int) {
        val uri=a.getSharedPreferences("AutoOS_Launcher",0).getString(key,null)
        v.setImageResource(fallback)
        if(uri!=null) a.lifecycleScope.launch {
            val bitmap=withContext(Dispatchers.IO) { decode(a,Uri.parse(uri)) }
            if(bitmap!=null && a.getSharedPreferences("AutoOS_Launcher",0).getString(key,null)==uri) v.setImageBitmap(bitmap)
        }
    }
}
