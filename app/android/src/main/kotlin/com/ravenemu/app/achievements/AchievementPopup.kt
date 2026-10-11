package com.ravenemu.app.achievements

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bandeau en jeu, sans interaction et mis en file : les commandes du jeu
 * continuent de recevoir les touches.
 *
 * Un succès annoncé par son seul texte se lit, mais ne se reconnaît pas : le
 * joueur a vu son badge sur le site, et c'est lui qu'il attend. Le bandeau
 * réserve donc sa place à gauche et y pose l'image dès qu'elle arrive. Elle
 * vient du réseau : le texte ne l'attend jamais, et un badge qui n'arrive pas
 * laisse un bandeau de texte plutôt qu'un carré vide.
 */
internal class AchievementPopup(private val activity: Activity) : AutoCloseable {
    /** [imageUrl] vide pour les messages qui ne sont pas un succès. */
    data class Notice(val text: String, val imageUrl: String? = null)

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Notice>()
    private var banner: View? = null
    private val hide = Runnable {
        banner?.let { (it.parent as? FrameLayout)?.removeView(it) }
        banner = null
        next()
    }

    fun show(message: String) = show(Notice(message))

    fun show(notice: Notice) {
        queue.addLast(notice)
        if (banner == null) next()
    }

    private fun next() {
        if (queue.isEmpty() || activity.isDestroyed || activity.isFinishing) return
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        val density = activity.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(root)?.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
        )
        val notice = queue.removeFirst()
        val label = TextView(activity).apply {
            text = notice.text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 39, 51))
                cornerRadius = 12 * density
            }
            elevation = 16 * density
            isClickable = false
            isFocusable = false
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            // Le badge est décoratif : le texte du bandeau dit déjà tout, et le
            // faire relire par la synthèse vocale doublerait l'annonce.
            val badge = ImageView(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 255, 255, 255))
                    cornerRadius = 8 * density
                }
            }
            if (notice.imageUrl != null) {
                val side = (48 * density).toInt()
                addView(badge, LinearLayout.LayoutParams(side, side).apply { marginEnd = pad })
                AchievementBadges.of(activity).into(badge, notice.imageUrl)
            }
            addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        banner = content
        root.addView(content, FrameLayout.LayoutParams(
            minOf((400 * density).toInt(), root.width - 2 * pad).coerceAtLeast(pad),
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply { topMargin = pad + (insets?.top ?: 0) })
        handler.postDelayed(hide, 4500)
    }

    override fun close() {
        handler.removeCallbacks(hide)
        queue.clear()
        banner?.let { (it.parent as? FrameLayout)?.removeView(it) }
        banner = null
    }
}
