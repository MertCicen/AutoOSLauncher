package com.autoos.launcher
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DebugLogActivity:AppCompatActivity() {
    private var tab=0
    private lateinit var text:TextView
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() { render(); handler.postDelayed(this,250) } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_debug_log); window.decorView.systemUiVisibility=5894
        text=findViewById(R.id.debug_log_text)
        findViewById<View>(R.id.debug_close).setOnClickListener { finish() }
        listOf(R.id.debug_tab_keys,R.id.debug_tab_logs).forEachIndexed { i,id ->
            findViewById<View>(id).setOnClickListener { tab=i; render() }
        }
        findViewById<View>(R.id.debug_clear).setOnClickListener { if(tab==0) KeyDebugStore.clear() else DebugLogStore.clear(); render() }
        findViewById<View>(R.id.debug_volume_probe).setOnClickListener { LauncherVolumeOverlay.showDebugProbe(this) }
    }
    private fun render() { text.text=if(tab==0) KeyDebugStore.text() else DebugLogStore.text() }
    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun dispatchKeyEvent(e:KeyEvent):Boolean { KeyDebugStore.add("action=${e.action} code=${e.keyCode} scan=${e.scanCode} repeat=${e.repeatCount}"); return super.dispatchKeyEvent(e) }
    override fun onWindowFocusChanged(focus:Boolean) { super.onWindowFocusChanged(focus); if(focus) window.decorView.systemUiVisibility=5894 }
}
