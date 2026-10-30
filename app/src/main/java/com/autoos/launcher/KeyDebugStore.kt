package com.autoos.launcher

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object KeyDebugStore {

    private const val MAX_LINES = 220
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(message: String) {
        val time = timeFormat.format(Date())
        lines.addLast("[$time] $message")
        while (lines.size > MAX_LINES) {
            lines.removeFirst()
        }
    }

    @Synchronized
    fun text(): String {
        return if (lines.isEmpty()) "No key logs yet" else lines.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
