package com.ravenemu.app.achievements

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Non-interactive, persistent status of active RA challenges, measured
 * achievements and leaderboard trackers. Unlike achievement popups these must
 * remain visible until the SDK explicitly sends a HIDE event.
 */
internal class AchievementIndicators(private val activity: Activity) : AutoCloseable {
    private val active = linkedMapOf<String, String>()
    private var panel: LinearLayout? = null

    fun update(kind: String, id: String, title: String, detail: String) {
        val key = kind.substringBeforeLast('_') + ":" + id
        if (kind.endsWith("_hide")) active.remove(key)
        else active[key] = listOf(title, detail).filter { it.isNotBlank() }.joinToString(" • ")
        render()
    }

    private fun render() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (active.isEmpty()) {
            closePanel()
            return
        }
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        val density = activity.resources.displayMetrics.density
        val padding = (12 * density).toInt()
        val widget = panel ?: LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 39, 51))
                cornerRadius = density * 10
            }
            elevation = density * 8
            isClickable = false
            isFocusable = false
            root.addView(this, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = padding * 2 })
            panel = this
        }
        widget.removeAllViews()
        active.values.take(4).forEach { label ->
            widget.addView(TextView(activity).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13f
            })
        }
    }

    private fun closePanel() {
        panel?.let { (it.parent as? FrameLayout)?.removeView(it) }
        panel = null
    }

    override fun close() {
        active.clear()
        closePanel()
    }
}
