package com.ravenemu.app.achievements

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ravenemu.app.R

/**
 * Carte de profil RetroAchievements.
 *
 * Le site montre un bloc de lignes `clé : valeur` les unes sous les autres,
 * toutes de la même taille. Ici l'identité passe devant : portrait, nom
 * affiché, nom de compte, puis les points sur des lignes alignées à droite,
 * ce qui les rend comparables d'un coup d'œil au lieu de les noyer.
 *
 * Trois valeurs du site manquent et manqueront : le **rang**, la **date
 * d'inscription** et la **dernière activité**. Elles ne sont pas dans l'API
 * client du SDK, seulement dans l'API Web, qui demande une clé de développeur
 * que RavenEmu n'utilise pas. La carte ne les approche pas : elle renvoie au
 * site, qui les porte.
 *
 * La carte s'affiche d'abord avec ce que l'appareil a retenu de la dernière
 * connexion, puis se complète quand le serveur répond. Sans réseau, elle reste
 * sur l'identité mémorisée plutôt que de ne rien montrer.
 */
internal class AchievementProfileCard(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private val avatar = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    /** Montré sous le portrait : la première lettre, le temps qu'il arrive. */
    private val initial = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(color(R.color.raven_on_accent))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color(R.color.raven_accent))
        }
    }

    private val name = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(color(R.color.raven_text_primary))
        setSingleLine()
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val handle = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(color(R.color.raven_text_muted))
        setSingleLine()
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private val softcorePoints = valueLabel(context)
    private val hardcorePoints = valueLabel(context)
    private val note = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(color(R.color.raven_text_muted))
    }

    /** Racine à passer à `setView`. */
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(FrameLayout(context).apply {
                addView(initial, FrameLayout.LayoutParams(dp(56), dp(56)))
                addView(avatar, FrameLayout.LayoutParams(dp(56), dp(56)))
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(name)
                addView(handle)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            })
        })
        addView(View(context).apply { setBackgroundColor(color(R.color.raven_outline)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(16); bottomMargin = dp(12)
            })
        addView(row(R.string.retroachievements_points_softcore, softcorePoints))
        addView(row(R.string.retroachievements_points_hardcore, hardcorePoints))
        addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
    }

    /** Ce que l'appareil sait déjà : affiché sans attendre le réseau. */
    fun identity(displayName: String, username: String, avatarUrl: String?) {
        name.text = displayName
        handle.text = if (username.isBlank() || username == displayName) "" else "@$username"
        handle.visibility = if (handle.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        initial.text = displayName.firstOrNull()?.uppercase() ?: "?"
        avatar.setImageDrawable(null)
        AchievementBadges.of(context).into(avatar, avatarUrl)
    }

    /** Les points, une fois le serveur consulté. `null` les laisse en attente. */
    fun points(profile: AchievementProfile?) {
        softcorePoints.text = profile?.formattedSoftcorePoints() ?: PENDING
        hardcorePoints.text = profile?.formattedHardcorePoints() ?: PENDING
    }

    fun showNote(text: CharSequence) { note.text = text }

    private fun row(label: Int, value: TextView): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            addView(TextView(context).apply {
                text = context.getString(label)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(color(R.color.raven_text_secondary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }

    private companion object {
        const val PENDING = "…"
        fun valueLabel(context: Context) = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, R.color.raven_text_primary))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            text = PENDING
        }
    }
}
