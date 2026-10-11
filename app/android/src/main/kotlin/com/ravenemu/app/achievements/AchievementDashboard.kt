package com.ravenemu.app.achievements

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
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
        val filters = RadioGroup(activity).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Tous", "À obtenir", "Obtenus").forEachIndexed { index, label ->
            filters.addView(RadioButton(activity).apply { id = index + 1; text = label })
        }
        filters.check(1)
        val adapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1)
        val list = ListView(activity).apply { this.adapter = adapter }
        content.addView(header)
        content.addView(filters)
        content.addView(list, LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.4).toInt()))
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
                adapter.clear()
                adapter.addAll(entries.map { it.label() })
            }
        }
        filters.setOnCheckedChangeListener { _, id -> filter = id - 1; render() }
        list.setOnItemClickListener { _, _, position, _ ->
            val entry = visible[position]
            val date = if (entry.unlockedAt > 0) "\nObtenu le ${DateFormat.getDateTimeInstance().format(Date(entry.unlockedAt * 1000))}" else ""
            MaterialAlertDialogBuilder(activity).setTitle(entry.title)
                .setMessage("${entry.description}\n\n${entry.label()}$date")
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
                val before = account.read()
                AchievementLogin.show(activity) {
                    if (active && before != account.read()) reconnect()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { reconnect() }
            refresh.run()
        }
        dialog.show()
    }
}
