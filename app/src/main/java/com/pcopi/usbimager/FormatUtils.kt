package com.pcopi.usbimager

import java.util.Locale

object FormatUtils {
    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = -1
        do {
            v /= 1024.0
            i++
        } while (v >= 1024.0 && i < units.lastIndex)
        return String.format(Locale.US, "%.2f %s", v, units[i])
    }

    fun speed(bytesPerSecond: Double): String = "${bytes(bytesPerSecond.toLong().coerceAtLeast(0L))}/s"

    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
            else -> "%d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    fun eta(completed: Long, total: Long, bytesPerSecond: Double): String {
        if (bytesPerSecond <= 0.0 || total <= completed) {
            return if (total <= completed && total > 0L) "เหลือ 0:00" else "กำลังคำนวณเวลา..."
        }
        val remaining = (total - completed).coerceAtLeast(0L)
        val millis = ((remaining / bytesPerSecond) * 1000.0).toLong().coerceAtLeast(0L)
        return "เหลือประมาณ ${duration(millis)}"
    }
}
