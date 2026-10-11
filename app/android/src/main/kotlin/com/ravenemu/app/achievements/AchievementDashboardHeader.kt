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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ravenemu.app.R

/**
 * Bandeau du tableau de bord en jeu.
 *
 * Il empilait quatre lignes de texte séparées par des puces, où le compte, son
 * état, le jeu et l'avancement se suivaient à la même taille : « GhostPunishR
 * 485 points Softcore 0 points Hardcore / Connecté Mode normal / Pokémon
 * Emerald Version / 4 197 succès 7 2086 points ». Tout y était, et rien ne s'y
 * trouvait d'un coup d'œil.
 *
 * Le bandeau sépare maintenant les deux choses qu'on vient y lire. En haut le
 * compte : portrait, nom, état de la connexion, et les points gagnés. En bas
 * le jeu en cours, avec une barre qui dit l'avancement avant qu'on ait lu les
 * chiffres.
 *
 * Les points Hardcore n'y figurent plus. Ils valent zéro dans RavenEmu, qui
 * désactive ce mode à la création du client ; les afficher en jeu occupait une
 * ligne pour une valeur qui ne bouge jamais. La carte des paramètres, elle,
 * les montre, parce qu'un compte peut en avoir gagné ailleurs.
 */
internal class AchievementDashboardHeader(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private val avatar = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    private val initial = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(color(R.color.raven_on_accent))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color(R.color.raven_accent))
        }
    }

    private val name = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(color(R.color.raven_text_primary))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setSingleLine()
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private val status = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(color(R.color.raven_text_muted))
    }

    private val points = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(color(R.color.raven_accent))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.END
    }

    private val account = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(FrameLayout(context).apply {
            addView(initial, FrameLayout.LayoutParams(dp(36), dp(36)))
            addView(avatar, FrameLayout.LayoutParams(dp(36), dp(36)))
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(status)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(points, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(12) })
    }

    private val separator = View(context).apply { setBackgroundColor(color(R.color.raven_outline)) }

    private val game = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(color(R.color.raven_text_primary))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setSingleLine()
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = AchievementGameProgress.BAR_MAX
        progressTintList = android.content.res.ColorStateList.valueOf(color(R.color.raven_accent))
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.raven_outline))
    }

    private val progress = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(color(R.color.raven_text_secondary))
    }

    /** Racine à ajouter au contenu du dialogue. */
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(account)
        addView(separator, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        })
        addView(game)
        addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
            topMargin = dp(8); bottomMargin = dp(6)
        })
        addView(progress)
    }

    /**
     * Le bandeau au complet, depuis un instantané du client.
     *
     * Trois états se succèdent dans une partie : aucun compte, un compte
     * connecté mais un jeu que le serveur n'a pas encore reconnu, puis le jeu
     * et son avancement. Chacun montre ce qu'il a plutôt que de laisser les
     * lignes suivantes vides.
     */
    fun bind(snapshot: AchievementSnapshot?) {
        val profile = AchievementProfile.fromSummary(snapshot?.summary)
        if (profile == null) {
            account.visibility = View.GONE
            separator.visibility = View.GONE
            bar.visibility = View.GONE
            game.text = context.getString(R.string.retroachievements_dashboard_signed_out)
            progress.text = snapshot?.status.orEmpty()
            progress.visibility = if (progress.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            return
        }
        account.visibility = View.VISIBLE
        separator.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        name.text = profile.displayName
        initial.text = profile.initial
        AchievementBadges.of(context).into(avatar, profile.avatarUrl)
        status.text = snapshot?.status.orEmpty()
        status.visibility = if (status.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        points.text = context.getString(
            R.string.retroachievements_dashboard_points, profile.formattedSoftcorePoints(),
        )

        val current = AchievementGameProgress.fromSummary(snapshot?.summary)
        if (current == null) {
            game.text = context.getString(R.string.retroachievements_dashboard_no_game)
            bar.visibility = View.GONE
            progress.visibility = View.GONE
            return
        }
        game.text = current.title
        if (current.empty) {
            bar.visibility = View.GONE
            progress.text = context.getString(R.string.retroachievements_dashboard_no_achievement)
            return
        }
        bar.visibility = View.VISIBLE
        bar.progress = current.progressBasisPoints
        progress.text = context.getString(
            R.string.retroachievements_dashboard_progress,
            current.achievementsLabel(), current.pointsLabel(),
        )
    }
}
