package com.autoos.launcher

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

object LauncherTheme : Application.ActivityLifecycleCallbacks {
    private val backgrounds=WeakHashMap<View,Drawable.ConstantState>()
    private val backgroundTints=WeakHashMap<View,ColorStateList>()
    private val imageTints=WeakHashMap<ImageView,ColorStateList>()
    private val progressTints=WeakHashMap<ProgressBar,ColorStateList>()
    private val texts=WeakHashMap<TextView,ColorStateList>()
    private val bitmaps=object:LruCache<String,Bitmap>(12*1024*1024) { override fun sizeOf(key:String,value:Bitmap)=value.byteCount }
    private val customWallpapers=object:LruCache<String,Bitmap>(12*1024*1024) { override fun sizeOf(key:String,value:Bitmap)=value.byteCount }
    private val wallpaperUris=WeakHashMap<View,String>()
    fun accent(hue:Float)=Color.HSVToColor(floatArrayOf(hue,0.92f,0.95f))
    private fun recolor(color:Int,hue:Float):Int {
        val hsv=FloatArray(3); Color.colorToHSV(color,hsv)
        if(Color.alpha(color)==0 || hsv[1]<0.2f || hsv[0] !in 185f..265f) return color
        hsv[0]=(hue+hsv[0]-230f+360f)%360f
        return Color.HSVToColor(Color.alpha(color),hsv)
    }
    private fun colors(c:ColorStateList,h:Float):ColorStateList {
        val states=arrayOf(intArrayOf(android.R.attr.state_pressed,android.R.attr.state_enabled),intArrayOf(android.R.attr.state_selected),intArrayOf(-android.R.attr.state_enabled),intArrayOf())
        return ColorStateList(states,IntArray(states.size) { recolor(c.getColorForState(states[it],c.defaultColor),h) })
    }
    private fun drawable(d:Drawable,h:Float):Drawable {
        if(h==230f) return d
        when(d) {
            is ColorDrawable -> d.color=recolor(d.color,h)
            is GradientDrawable -> if(Build.VERSION.SDK_INT>=29) {
                val cs=d.colors
                if(cs!=null) d.colors=cs.map { recolor(it,h) }.toIntArray() else d.color?.let { d.color=colors(it,h) }
            } else d.colorFilter=hueFilter(h-230f)
            is LayerDrawable -> for(i in 0 until d.numberOfLayers) drawable(d.getDrawable(i),h)
            is StateListDrawable -> d.colorFilter=hueFilter(h-230f)
            is BitmapDrawable -> {
                val b=d.bitmap; val key="${System.identityHashCode(b)}:${h.toInt()}"
                var out=bitmaps.get(key)
                if(out==null) {
                    val pixels=IntArray(b.width*b.height); b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
                    for(i in pixels.indices) pixels[i]=recolor(pixels[i],h)
                    out=Bitmap.createBitmap(pixels,b.width,b.height,Bitmap.Config.ARGB_8888); bitmaps.put(key,out)
                }
                return BitmapDrawable(android.content.res.Resources.getSystem(),out).apply { gravity=d.gravity }
            }
        }
        return d
    }
    private fun hueFilter(degrees:Float):ColorMatrixColorFilter {
        val c=kotlin.math.cos(Math.toRadians(degrees.toDouble())).toFloat(); val s=kotlin.math.sin(Math.toRadians(degrees.toDouble())).toFloat()
        return ColorMatrixColorFilter(floatArrayOf(.213f+c*.787f-s*.213f,.715f-c*.715f-s*.715f,.072f-c*.072f+s*.928f,0f,0f,.213f-c*.213f+s*.143f,.715f+c*.285f+s*.140f,.072f-c*.072f-s*.283f,0f,0f,.213f-c*.213f-s*.787f,.715f-c*.715f+s*.715f,.072f+c*.928f+s*.072f,0f,0f,0f,0f,0f,1f,0f))
    }
    fun previewHue(a:Activity,hue:Float) {
        val root=a.findViewById<View>(R.id.launcher_options_wallpaper) ?: return
        if(a.getSharedPreferences("AutoOS_Launcher",0).getString("launcher_custom_wallpaper_uri",null)==null) {
            backgrounds[root]?.let { root.background=drawable(it.newDrawable(a.resources).mutate(),hue) }
        }
        fun redraw(v:View) {
            v.invalidate()
            if(v is ViewGroup) for(i in 0 until v.childCount) redraw(v.getChildAt(i))
        }
        a.findViewById<View>(R.id.launcher_options_root)?.let(::redraw)
        a.findViewById<View>(R.id.launcher_options_title_line)?.setBackgroundColor(accent(hue))
        a.findViewById<ProgressBar>(R.id.launcher_options_hue_slider)?.progressTintList=ColorStateList.valueOf(accent(hue))
    }
    fun apply(a:Activity) {
        val root=a.findViewById<View>(android.R.id.content) ?: return
        val p=a.getSharedPreferences("AutoOS_Launcher",0); val h=p.getFloat("launcher_accent_hue",230f)
        fun visit(v:View) {
            // MainActivity applies the exact selected accent to these views itself.
            if(v.id==R.id.home_bottom_accent || v.id==R.id.home_apps_underline ||
                v.id==R.id.shortcut_1_icon || v.id==R.id.shortcut_2_icon ||
                v.id==R.id.shortcut_3_icon || v.id==R.id.shortcut_4_icon) return
            // The slider preview already shows the exact chosen HSV color.
            if(v.id==R.id.launcher_options_hue_preview) return
            if(!backgrounds.containsKey(v)) v.background?.constantState?.let { backgrounds[v]=it }
            val uri=if(v.tag=="launcher_wallpaper_root") p.getString("launcher_custom_wallpaper_uri",null) else null
            if(uri!=null && a is ComponentActivity) {
                if(wallpaperUris[v]!=uri) {
                    wallpaperUris[v]=uri
                    a.lifecycleScope.launch {
                        val b=customWallpapers.get(uri) ?: withContext(Dispatchers.IO) {
                            AutoOsUi.decode(a,Uri.parse(uri))
                        }?.also { customWallpapers.put(uri,it) }
                        if(wallpaperUris[v]==uri) {
                            if(b!=null) v.background=BitmapDrawable(a.resources,b).apply {
                                gravity=android.view.Gravity.FILL
                                isFilterBitmap=true
                            } else wallpaperUris.remove(v) // Retry if the document provider was temporarily unavailable.
                        }
                    }
                }
            } else {
                wallpaperUris.remove(v)
                backgrounds[v]?.let { v.background=drawable(it.newDrawable(a.resources).mutate(),h) }
            }
            if(v is TextView) {
                val original=texts.getOrPut(v) { v.textColors }; v.setTextColor(colors(original,h))
            }
            v.backgroundTintList?.let { if(!backgroundTints.containsKey(v)) backgroundTints[v]=it }
            backgroundTints[v]?.let { v.backgroundTintList=colors(it,h) }
            if(v is ImageView) {
                v.imageTintList?.let { if(!imageTints.containsKey(v)) imageTints[v]=it }
                imageTints[v]?.let { v.imageTintList=colors(it,h) }
            }
            if(v is ProgressBar) {
                v.progressTintList?.let { if(!progressTints.containsKey(v)) progressTints[v]=it }
                progressTints[v]?.let { v.progressTintList=colors(it,h) }
            }
            if(v is ViewGroup) for(i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(root)
    }
    override fun onActivityResumed(activity:Activity) { apply(activity) }
    override fun onActivityCreated(a:Activity,b:Bundle?)=Unit
    override fun onActivityStarted(a:Activity)=Unit
    override fun onActivityPaused(a:Activity)=Unit
    override fun onActivityStopped(a:Activity)=Unit
    override fun onActivitySaveInstanceState(a:Activity,b:Bundle)=Unit
    override fun onActivityDestroyed(a:Activity)=Unit
}
