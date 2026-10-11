package com.ravenemu.app.achievements

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Non-interactive, queued in-game banner: game controls keep receiving touches. */
internal class AchievementPopup(private val activity: Activity) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var banner: TextView? = null
    private val hide = Runnable {
        banner?.let { (it.parent as? FrameLayout)?.removeView(it) }
        banner = null
        next()
    }

    fun show(message: String) {
        queue.addLast(message)
        if (banner == null) next()
    }

    private fun next() {
        if (queue.isEmpty() || activity.isDestroyed || activity.isFinishing) return
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        val density = activity.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        banner = TextView(activity).apply {
            text = queue.removeFirst()
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 39, 51))
                cornerRadius = 12 * density
            }
            elevation = 16 * density
            isClickable = false
            isFocusable = false
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also {
            root.addView(it, FrameLayout.LayoutParams(
                minOf((400 * density).toInt(), root.width - 2 * pad).coerceAtLeast(pad),
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = pad + (insets?.top ?: 0) })
        }
        handler.postDelayed(hide, 4500)
    }

    override fun close() {
        handler.removeCallbacks(hide)
        queue.clear()
        banner?.let { (it.parent as? FrameLayout)?.removeView(it) }
        banner = null
    }
}
