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

    fun speed(bytesPerSecond: Double): String = "${bytes(bytesPerSecond.toLong())}/s"
}
