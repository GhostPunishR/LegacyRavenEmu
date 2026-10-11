package com.ravenemu.app.achievements

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

internal data class AchievementEntry(
    val id: String, val title: String, val description: String, val points: String,
    val state: Int, val progress: String, val unlockedAt: Long, val bucket: Int,
    /** Badge de l'état courant : en couleur une fois obtenu, grisé avant. */
    val badgeUrl: String = "",
) {
    val unlocked: Boolean get() = state == 2
    fun label(): String {
        val status = when {
            bucket == 8 -> "En attente de synchronisation"
            unlocked -> "Débloqué"
            state == 3 -> "Non pris en charge"
            bucket == 6 -> "Défi actif"
            else -> "Verrouillé"
        }
        return "$title • $points points\n$status" + if (progress.isNotBlank() && !unlocked) " • $progress" else ""
    }
}

/**
 * Détail d'un succès : son badge en grand, puis sa description et son état.
 * Le badge passe devant le texte parce que c'est lui qu'on vient regarder ;
 * sous le texte, il aurait l'air d'une illustration ajoutée après coup.
 */
private fun detailView(activity: FragmentActivity, entry: AchievementEntry, date: String): View {
    val density = activity.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val badge = android.widget.ImageView(activity).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    AchievementBadges.of(activity).into(badge, entry.badgeUrl)
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(badge, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })
        addView(TextView(activity).apply {
            text = "${entry.description}\n\n${entry.label()}$date"
            setTextColor(androidx.core.content.ContextCompat.getColor(
                activity, com.ravenemu.app.R.color.raven_text_secondary))
        })
    }
}

/**
 * Lignes de la liste : le badge à gauche, le texte à droite.
 *
 * Android réutilise les vues en défilant. Le badge d'une ligne recyclée doit
 * donc être réassigné à chaque passage, sans quoi un succès hériterait de
 * l'image de celui qui occupait la vue avant lui ; `AchievementBadges` s'en
 * charge en comparant l'adresse posée en étiquette.
 */
private class AchievementRows(
    private val activity: FragmentActivity,
) : android.widget.BaseAdapter() {
    private val badges = AchievementBadges.of(activity)
    private val density = activity.resources.displayMetrics.density
    var entries: List<AchievementEntry> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    override fun getCount() = entries.size
    override fun getItem(position: Int) = entries[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
        val row = convertView as? LinearLayout ?: newRow()
        val entry = entries[position]
        badges.into(row.getChildAt(0) as android.widget.ImageView, entry.badgeUrl)
        (row.getChildAt(1) as TextView).text = entry.label()
        return row
    }

    private fun newRow(): LinearLayout {
        val side = (44 * density).toInt()
        val gap = (12 * density).toInt()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, gap / 2, 0, gap / 2)
            addView(android.widget.ImageView(activity).apply {
                // Décoratif : le texte de la ligne porte déjà titre et état.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                // Occupe la place le temps du téléchargement : sans elle, la
                // liste se décale quand les badges arrivent un à un.
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.argb(28, 255, 255, 255))
                    cornerRadius = 6 * density
                }
            }, LinearLayout.LayoutParams(side, side).apply { marginEnd = gap })
            addView(TextView(activity).apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(
                    activity, com.ravenemu.app.R.color.raven_text_primary))
            }, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}

internal data class AchievementSnapshot(val status: String, val summary: List<String>, val entries: List<AchievementEntry>)

/** The supplied request runs SDK reads on the emulation thread and returns on the UI thread. */
internal object AchievementDashboard {
    fun show(
        activity: FragmentActivity,
        request: ((AchievementSnapshot?) -> Unit) -> Unit,
        reconnect: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        var active = true
        var filter = 0
        var latest: AchievementSnapshot? = null
        var visible = emptyList<AchievementEntry>()
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val header = TextView(activity)
        val filters = Spinner(activity).apply {
            contentDescription = "Filtrer les succès"
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Tous les succès", "À obtenir", "Obtenus"))
        }
        val adapter = AchievementRows(activity)
        val list = ListView(activity).apply {
            this.adapter = adapter
            divider = null
        }
        content.addView(header)
        content.addView(filters)
        content.addView(list, LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.32).toInt()))
        fun render() {
            val snapshot = latest
            val s = snapshot?.summary
            header.text = if (snapshot == null || s == null) "Connectez votre compte pour afficher les succès."
            else "${s[0]} • ${s[1]} points Softcore • ${s[2]} points Hardcore\n${snapshot.status}\n" +
                if (s[3].isBlank()) "Aucun jeu identifié pour le moment."
                else "${s[3]}\n${s[5]} / ${s[6]} succès • ${s[7]} / ${s[8]} points" +
                    if (snapshot.entries.isEmpty()) "\nAucun succès disponible pour ce jeu." else ""
            val entries = snapshot?.entries.orEmpty().filter { filter == 0 || (filter == 2) == it.unlocked }
            if (entries != visible) {
                visible = entries
                adapter.entries = entries
            }
        }
        filters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filter = position
                render()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val entry = visible[position]
            val date = if (entry.unlockedAt > 0) "\nObtenu le ${DateFormat.getDateTimeInstance().format(Date(entry.unlockedAt * 1000))}" else ""
            MaterialAlertDialogBuilder(activity).setTitle(entry.title)
                .setView(detailView(activity, entry, date))
                .setPositiveButton("Fermer", null)
                .setNeutralButton("Voir sur le site") { _, _ ->
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org/achievement/${entry.id}"))) }
                }.show()
        }
        val dialog = MaterialAlertDialogBuilder(activity).setTitle("RetroAchievements")
            .setView(content).setPositiveButton("Reprendre", null)
            .setNegativeButton("Compte", null).setNeutralButton("Réessayer", null).create()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        val refresh = object : Runnable {
            override fun run() {
                if (!active) return
                request { snapshot ->
                    if (active && !activity.isDestroyed && !activity.isFinishing) {
                        latest = snapshot
                        render()
                        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).isEnabled =
                            snapshot == null || snapshot.summary[3].isBlank()
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            active = false
            handler.removeCallbacksAndMessages(null)
            activity.lifecycle.removeObserver(observer)
            onDismiss()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val account = AchievementAccount(activity)
                // Seule l'identité compte : la carte de profil réécrit le nom
                // affiché et le portrait au passage, et reconnecter la partie
                // pour un portrait rafraîchi couperait la session pour rien.
                fun identity() = account.read()?.let { it.username to it.token }
                val before = identity()
                AchievementLogin.show(activity) {
                    if (active && before != identity()) reconnect()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { reconnect() }
            refresh.run()
        }
        dialog.show()
    }
}
