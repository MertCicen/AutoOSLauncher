package com.autoos.launcher

object TripTimer {
    private var startTime = 0L

    fun initIfNeeded() {
        if (startTime == 0L) startTime = System.currentTimeMillis()
    }

    fun formatElapsed(): String {
        val total = ((System.currentTimeMillis() - startTime) / 1000 / 60).toInt()
        return String.format("%02d:%02d", total / 60, total % 60)
    }
}