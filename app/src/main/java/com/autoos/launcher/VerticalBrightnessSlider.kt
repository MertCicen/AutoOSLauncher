package com.autoos.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class VerticalBrightnessSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private var value = 100

    var onValueChanged: ((value: Int, tracking: Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        minimumWidth = dp(56)
        minimumHeight = dp(180)
        updateContentDescription()
    }

    fun setValue(nextValue: Int, notify: Boolean = false) {
        updateValue(nextValue, notify, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val top = paddingTop + dp(14).toFloat()
        val bottom = height - paddingBottom - dp(14).toFloat()
        val halfTrackWidth = dp(6).toFloat()
        track.set(centerX - halfTrackWidth, top, centerX + halfTrackWidth, bottom)

        paint.color = Color.rgb(40, 49, 59)
        canvas.drawRoundRect(track, dp(6).toFloat(), dp(6).toFloat(), paint)

        val thumbY = bottom - (bottom - top) * value / 100f
        paint.color = Color.rgb(0, 20, 196)
        canvas.drawRoundRect(
            track.left,
            thumbY,
            track.right,
            track.bottom,
            dp(6).toFloat(),
            dp(6).toFloat(),
            paint
        )

        val thumbHalfWidth = dp(18).toFloat()
        val thumbHalfHeight = dp(6).toFloat()
        paint.color = Color.WHITE
        canvas.drawRoundRect(
            centerX - thumbHalfWidth,
            thumbY - thumbHalfHeight,
            centerX + thumbHalfWidth,
            thumbY + thumbHalfHeight,
            dp(4).toFloat(),
            dp(4).toFloat(),
            paint
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2).toFloat()
        paint.color = Color.rgb(112, 128, 143)
        canvas.drawRoundRect(
            centerX - thumbHalfWidth,
            thumbY - thumbHalfHeight,
            centerX + thumbHalfWidth,
            thumbY + thumbHalfHeight,
            dp(4).toFloat(),
            dp(4).toFloat(),
            paint
        )
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.y, true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.y, true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateFromTouch(event.y, false)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onValueChanged?.invoke(value, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(y: Float, tracking: Boolean) {
        val top = paddingTop + dp(14).toFloat()
        val bottom = height - paddingBottom - dp(14).toFloat()
        val usableHeight = (bottom - top).coerceAtLeast(1f)
        val next = (((bottom - y) / usableHeight) * 100f).roundToInt().coerceIn(0, 100)
        updateValue(next, true, tracking)
    }

    private fun updateValue(nextValue: Int, notify: Boolean, tracking: Boolean) {
        val next = nextValue.coerceIn(0, 100)
        if (value != next) {
            value = next
            updateContentDescription()
            invalidate()
        }
        if (notify) onValueChanged?.invoke(value, tracking)
    }

    private fun updateContentDescription() {
        contentDescription = "Brightness $value%"
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()
}
