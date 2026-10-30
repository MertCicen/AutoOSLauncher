package com.autoos.launcher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import org.json.JSONArray
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs

class AppsActivity:AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("AutoOS_Launcher",MODE_PRIVATE) }
    private var page=0
    private var downX=0f
    private var downY=0f
    private lateinit var primary:List<View>
    private lateinit var pages:ConstraintLayout
    private lateinit var dots:LinearLayout
    private var activeGrid:View?=null
    private var changingPage=false
    private val custom=mutableListOf<String>()
    private fun open(c:Class<*>) { startActivity(Intent(this,c).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)); overridePendingTransition(R.anim.fade_in,R.anim.fade_out) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_apps); window.decorView.systemUiVisibility=5894
        pages=findViewById(R.id.apps_page2_container); dots=findViewById(R.id.apps_page_indicator)
        val ids=listOf(R.id.navigation_button,R.id.media_button,R.id.phone_button,R.id.radio_button,R.id.bluetooth_button,R.id.mainmenu_button,R.id.extra_button_2,R.id.extra_button_5)
        primary=ids.map { findViewById(it) }
        runCatching { val a=JSONArray(prefs.getString("launcher_custom_apps_json","[]")); for(i in 0 until a.length()) { val p=a.getString(i); if(p !in custom) custom.add(p) } }
        val defaults=listOf<Pair<String,()->Unit>>(
            "apps_navigation" to { open(MapActivity::class.java) },
            "apps_media" to { open(MediaActivity::class.java) },
            "apps_phone" to { open(PhoneActivity::class.java) },
            "key_radio" to { Toast.makeText(this,R.string.long_press_to_assign,0).show() },
            "key_bt" to { Toast.makeText(this,R.string.long_press_to_assign,0).show() },
            "apps_main_menu" to { open(MainActivity::class.java) },
            "apps_tone" to { open(ToneActivity::class.java) },
            "apps_settings" to { open(LauncherSettingsActivity::class.java) })
        primary.zip(defaults).forEach { (view,target) ->
            val (key,action)=target
            view.setOnClickListener { val pkg=prefs.getString(key,null); if(pkg.isNullOrBlank()) action() else AutoOsUi.launch(this,pkg) }
            view.setOnLongClickListener { AutoOsUi.pickApp(this,true) { pkg -> prefs.edit().apply { if(pkg==null) remove(key) else putString(key,pkg) }.apply() }; true }
            press(view)
        }
        page=savedInstanceState?.getInt("page") ?: 0
        showPage(page)
        if(page>0) pages.post { showPage(page) }
    }
    private fun press(v:View) { v.setOnTouchListener { view,e -> view.alpha=if(e.action==MotionEvent.ACTION_DOWN) .85f else 1f; false } }
    private fun count()=1+(custom.size+2+7)/8
    private fun save() { prefs.edit().putString("launcher_custom_apps_json",JSONArray(custom).toString()).apply() }
    private fun showPage(target:Int,animate:Boolean=false) {
        val next=target.coerceIn(0,count()-1)
        if(changingPage && animate || animate && next==page) return
        val previous=page
        page=next
        if(!animate) {
            primary.forEach { it.animate().cancel(); it.translationX=0f; it.visibility=if(page==0) View.VISIBLE else View.GONE }
            pages.removeAllViews()
            pages.visibility=if(page==0) View.GONE else View.VISIBLE
            activeGrid=if(page>0) buildPage(page).also { pages.addView(it,ConstraintLayout.LayoutParams(-1,-1)) } else null
        } else {
            changingPage=true
            val width=resources.displayMetrics.widthPixels.toFloat()
            val incoming=if(page>previous) width else -width
            val outgoing=-incoming
            if(previous==0) {
                val grid=buildPage(page)
                pages.removeAllViews(); pages.addView(grid,ConstraintLayout.LayoutParams(-1,-1))
                pages.visibility=View.VISIBLE; activeGrid=grid
                grid.translationX=incoming
                grid.animate().translationX(0f).setDuration(220).withEndAction { changingPage=false }.start()
                primary.forEach { it.animate().cancel(); it.animate().translationX(outgoing).setDuration(220).withEndAction { it.visibility=View.GONE; it.translationX=0f }.start() }
            } else if(page==0) {
                primary.forEach { it.visibility=View.VISIBLE; it.translationX=incoming; it.animate().translationX(0f).setDuration(220).start() }
                activeGrid?.let { old -> old.animate().translationX(outgoing).setDuration(220).withEndAction { pages.removeAllViews(); pages.visibility=View.GONE; activeGrid=null; changingPage=false }.start() }
                    ?: run { pages.visibility=View.GONE; changingPage=false }
            } else {
                val old=activeGrid
                val grid=buildPage(page)
                pages.addView(grid,ConstraintLayout.LayoutParams(-1,-1)); activeGrid=grid
                grid.translationX=incoming
                grid.animate().translationX(0f).setDuration(220).withEndAction { changingPage=false }.start()
                old?.animate()?.translationX(outgoing)?.setDuration(220)?.withEndAction { pages.removeView(old) }?.start()
            }
        }
        dots.removeAllViews()
        repeat(count()) { n -> dots.addView(View(this).apply {
            setBackgroundResource(if(n==page) R.drawable.apps_page_dot_active else R.drawable.apps_page_dot_inactive)
            setOnClickListener { showPage(n,true) }
        },LinearLayout.LayoutParams(AutoOsUi.dp(this,9),AutoOsUi.dp(this,9)).apply { val margin=AutoOsUi.dp(this@AppsActivity,6); setMargins(margin,margin,margin,margin) }) }
        LauncherTheme.apply(this)
    }
    private fun buildPage(page:Int):View {
            val grid=GridLayout(this).apply { columnCount=4; rowCount=2 }
            val dm=resources.displayMetrics
            // Read the same guides as the fixed first page. The page container
            // is GONE before the first swipe, so its height can still be zero.
            val cellWidth=(if(pages.width>0) pages.width else dm.widthPixels)/4
            val guideTop=findViewById<View>(R.id.guidelineyatay3).top
            val guideBottom=findViewById<View>(R.id.guidelineyatay2).top
            val cellHeight=if(guideBottom>guideTop) (guideBottom-guideTop)/2 else (dm.heightPixels*.3f).toInt()
            val entries=(custom+listOf("__launcher_options__","__add_app__")).drop((page-1)*8).take(8)
            entries.forEachIndexed { index,pkg ->
                val frame=FrameLayout(this)
                grid.addView(frame,GridLayout.LayoutParams(GridLayout.spec(index/4),GridLayout.spec(index%4)).apply { width=cellWidth; height=cellHeight })
                val card=ConstraintLayout(this).apply { setBackgroundResource(R.drawable.bg_app_button); isClickable=true; isFocusable=true }
                val width=((if(pages.width>0) pages.width else dm.widthPixels)*.2f).toInt()
                frame.addView(card,FrameLayout.LayoutParams(width,(width*.75f).toInt(),Gravity.CENTER))
                val title=when(pkg) { "__launcher_options__" -> getString(R.string.launcher_options); "__add_app__" -> getString(R.string.add_app); else -> runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg) }
                val borderId=View.generateViewId()
                val icon:View=if(pkg=="__add_app__") TextView(this).apply { text="+"; textSize=46f; gravity=Gravity.CENTER; setTextColor(Color.WHITE) }
                else ImageView(this).apply {
                    scaleType=ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(if(pkg=="__launcher_options__") getDrawable(R.drawable.gear) else runCatching { AutoOsUi.monoIcon(packageManager.getApplicationIcon(pkg)) }.getOrNull())
                }
                card.addView(icon,ConstraintLayout.LayoutParams(0,0).apply { topToTop=0; bottomToTop=borderId; startToStart=0; endToEnd=0; val margin=AutoOsUi.dp(this@AppsActivity,15); setMargins(margin,margin,margin,margin) })
                card.addView(View(this).apply { id=borderId; setBackgroundColor(getColor(R.color.launcher_base_accent)) },ConstraintLayout.LayoutParams(0,AutoOsUi.dp(this,5)).apply { startToStart=0; endToEnd=0; topToTop=0; bottomToBottom=0; topMargin=AutoOsUi.dp(this@AppsActivity,50) })
                card.addView(AppCompatTextView(this).apply { text=title; textAlignment=View.TEXT_ALIGNMENT_CENTER; typeface=android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; setAutoSizeTextTypeWithDefaults(1) },ConstraintLayout.LayoutParams(0,0).apply { startToStart=0; endToEnd=0; topToBottom=borderId; bottomToBottom=0; matchConstraintDefaultWidth=2; matchConstraintPercentWidth=1f; matchConstraintDefaultHeight=2; matchConstraintPercentHeight=.25f })
                card.setOnClickListener {
                    when(pkg) {
                        "__launcher_options__" -> open(LauncherOptionsActivity::class.java)
                        "__add_app__" -> AutoOsUi.pickApp(this) { p -> if(p!=null && p !in custom) { custom.add(p); save(); showPage(page) } }
                        else -> AutoOsUi.launch(this,pkg)
                    }
                }
                if(!pkg.startsWith("__")) card.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle(title).setItems(arrayOf(getString(R.string.remove))) { _,_ -> custom.remove(pkg); save(); showPage(page) }.setNegativeButton(R.string.cancel,null).show(); true
                }
                press(card)
            }
            return grid
    }
    override fun dispatchTouchEvent(e:MotionEvent):Boolean {
        if(e.action==MotionEvent.ACTION_DOWN) { downX=e.x; downY=e.y }
        if(e.action==MotionEvent.ACTION_UP && abs(e.x-downX)>90 && abs(e.x-downX)>abs(e.y-downY)*1.4) {
            val cancel=MotionEvent.obtain(e); cancel.action=MotionEvent.ACTION_CANCEL; super.dispatchTouchEvent(cancel); cancel.recycle()
            val next=page+if(e.x<downX) 1 else -1
            if(next in 0 until count()) { showPage(next,true); return true }
        }
        return super.dispatchTouchEvent(e)
    }
    override fun onSaveInstanceState(out:Bundle) { out.putInt("page",page); super.onSaveInstanceState(out) }
    override fun onResume() { super.onResume(); if(::pages.isInitialized) showPage(page) }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) window.decorView.systemUiVisibility=5894 }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { open(MainActivity::class.java) }
}
