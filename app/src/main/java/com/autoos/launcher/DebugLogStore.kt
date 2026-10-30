package com.autoos.launcher

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object DebugLogStore {

    private const val MAX_LINES = 120
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(tag: String, message: String) {
        val time = timeFormat.format(Date())
        lines.addLast("[$time] $tag $message")
        while (lines.size > MAX_LINES) {
            lines.removeFirst()
        }
    }

    @Synchronized
    fun text(): String {
        return if (lines.isEmpty()) "No logs yet" else lines.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }
}

