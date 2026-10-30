package com.autoos.launcher

import android.view.MotionEvent
import android.view.View

fun View.installAlphaPressFeedback(pressedAlpha: Float = 0.55f) {
    val normalAlpha = alpha
    setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                alpha = pressedAlpha
                false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                alpha = normalAlpha
                false
            }
            else -> false
        }
    }
}

fun installCompositeAlphaPressFeedback(
    hitArea: View,
    vararg targets: View,
    pressedAlpha: Float = 0.55f
) {
    val normalAlphas = targets.associateWith { it.alpha }
    hitArea.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                targets.forEach { it.alpha = pressedAlpha }
                false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                targets.forEach { it.alpha = normalAlphas[it] ?: 1f }
                false
            }
            else -> false
        }
    }
}
