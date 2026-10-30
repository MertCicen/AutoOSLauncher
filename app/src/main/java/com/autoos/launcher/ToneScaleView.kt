package com.autoos.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ToneScaleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var progress = 9
    private var accentColor = LauncherTheme.accent(
        context.getSharedPreferences("AutoOS_Launcher", Context.MODE_PRIVATE)
            .getFloat("launcher_accent_hue", 230f)
    )
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 230, 236, 248)
        style = Paint.Style.FILL
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2.4f
        strokeCap = Paint.Cap.ROUND
    }
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2.2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = resources.displayMetrics.scaledDensity * 14f
    }

    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 18)
        invalidate()
    }

    fun refreshAccent() {
        accentColor = LauncherTheme.accent(
            context.getSharedPreferences("AutoOS_Launcher", Context.MODE_PRIVATE)
                .getFloat("launcher_accent_hue", 230f)
        )
        markerPaint.color = accentColor
        markerFillPaint.color = accentColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val centerY = height * 0.52f
        val left = 34f * density
        val right = width - 34f * density
        val span = right - left
        val dotRadius = 2.1f * density

        canvas.drawText("-9", 14f * density, centerY + 5f * density, textPaint)
        canvas.drawText("9", width - 14f * density, centerY + 5f * density, textPaint)

        for (i in 0..18) {
            val x = left + (span * i / 18f)
            canvas.drawCircle(x, centerY, dotRadius, dotPaint)
        }

        val markerX = left + (span * (progress / 18f))
        canvas.drawLine(markerX, centerY - 22f * density, markerX, centerY + 22f * density, markerPaint)
        canvas.drawCircle(markerX, centerY, 6f * density, markerFillPaint)
    }
}
