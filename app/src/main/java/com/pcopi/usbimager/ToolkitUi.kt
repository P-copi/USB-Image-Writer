package com.pcopi.usbimager

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object ToolkitUi {
    const val BG = 0xFFF4F7FC.toInt()
    const val INK = 0xFF17223C.toInt()
    const val SUB = 0xFF61708B.toInt()
    const val BLUE = 0xFF2354C6.toInt()
    const val GREEN = 0xFF128F82.toInt()
    const val VIOLET = 0xFF7953BD.toInt()
    const val AMBER = 0xFFC27B22.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + .5f).toInt()

    fun shape(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radiusPx }

    fun text(ctx: Context, value: String, size: Float, color: Int = INK, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }

    fun column(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun card(ctx: Context): LinearLayout =
        column(ctx).apply {
            background = shape(Color.WHITE, dp(ctx, 19).toFloat())
            elevation = dp(ctx, 2).toFloat()
            setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 18))
        }

    fun button(ctx: Context, title: String, color: Int, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = title
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(color)
            minHeight = dp(ctx, 50)
            setOnClickListener { onClick() }
        }

    fun margin(ctx: Context, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ctx, top)
            bottomMargin = dp(ctx, bottom)
        }

    /** Target SDK 35+ draws behind system bars on recent Samsung firmware. */
    fun applySafeInsets(root: View, ctx: Context, side: Int = 18, top: Int = 14, bottom: Int = 24) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(ctx, side), dp(ctx, top) + bars.top, dp(ctx, side), dp(ctx, bottom) + bars.bottom)
            insets
        }
    }
}