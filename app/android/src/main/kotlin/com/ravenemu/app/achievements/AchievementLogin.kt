package com.ravenemu.app.achievements

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ravenemu.app.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Optional account dialog. Cancellation destroys the SDK on its owning worker. */
object AchievementLogin {
    fun show(activity: FragmentActivity, onDismiss: () -> Unit = {}) {
        val account = AchievementAccount(activity)
        val existing = account.read()
        if (existing != null) {
            showProfile(activity, account, existing, onDismiss)
            return
        }
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val info = TextView(activity).apply {
            text = "Connectez votre compte retroachievements.org pour obtenir des succès en jeu. Mode normal (Softcore), GB / GBC / GBA. Le mot de passe ne sera pas conservé."
        }
        val username = EditText(activity).apply {
            hint = "Nom d’utilisateur"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val password = EditText(activity).apply {
            hint = "Mot de passe"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            transformationMethod = HiddenPasswordTransformation
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        content.addView(info); content.addView(username); content.addView(password)
        val dialog = MaterialAlertDialogBuilder(activity).setTitle("RetroAchievements")
            .setView(content).setNegativeButton("Plus tard", null).setPositiveButton("Se connecter", null).create()
        val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "RavenEmu-RA-Login").apply { isDaemon = true } }
        val dismissed = AtomicBoolean(false)
        var client: AchievementClient? = null // worker only
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            activity.lifecycle.removeObserver(observer)
            onDismiss()
            dismissed.set(true)
            password.text.clear()
            worker.execute { client?.close(); client = null }
            worker.shutdown()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val user = username.text.toString().trim()
                val secret = password.text.toString()
                if (user.isEmpty() || secret.isEmpty()) {
                    info.text = "Saisissez le nom d’utilisateur et le mot de passe."
                    return@setOnClickListener
                }
                password.text.clear()
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                info.text = "Connexion en cours…"
                worker.execute {
                    runCatching {
                        client?.close()
                        client = AchievementClient { event ->
                            if (dismissed.get()) return@AchievementClient
                            var saved = false
                            val message = if (event.kind == "login") {
                                runCatching { account.save(event.title, event.extra, event.detail, event.image) }
                                    .fold({ saved = true; "Connecté : ${event.detail.ifBlank { event.title }}. Connexion mémorisée sur cet appareil. Fermez ce dialogue pour activer les succès dans la partie en cours." },
                                        { "Impossible de conserver la connexion sur cet appareil." })
                            } else event.title
                            activity.runOnUiThread {
                                if (!dismissed.get() && !activity.isFinishing && !activity.isDestroyed) {
                                    info.text = message
                                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = !saved
                                    if (saved) dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).text = "Fermer"
                                }
                            }
                        }.also { it.login(user, secret, false) }
                    }.onFailure {
                        activity.runOnUiThread {
                            if (!dismissed.get()) {
                                info.text = "RetroAchievements indisponible sur cet appareil."
                                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            }
                        }
                    }
                }
            }
        }
        worker.scheduleWithFixedDelay({
            if (!dismissed.get()) runCatching { client?.tick(false) }
        }, 0, 30, TimeUnit.MILLISECONDS)
        dialog.show()
    }

    /**
     * Carte du compte connecté.
     *
     * L'identité mémorisée s'affiche immédiatement : elle est sur l'appareil,
     * et la faire attendre le réseau donnerait une carte vide à l'ouverture.
     * Les points, eux, ne sont pas mémorisés et n'auraient aucun sens figés :
     * une connexion par jeton les demande au serveur, sur un client qui vit le
     * temps du dialogue et meurt avec lui, exactement comme celui du formulaire.
     *
     * Le mot de passe n'intervient pas : le jeton obtenu à la première
     * connexion suffit, et c'est la seule chose que l'appareil conserve.
     */
    private fun showProfile(
        activity: FragmentActivity,
        account: AchievementAccount,
        existing: AchievementAccount.Credentials,
        onDismiss: () -> Unit,
    ) {
        val card = AchievementProfileCard(activity)
        card.identity(existing.displayName, existing.username, existing.avatarUrl)
        card.points(null)
        card.showNote(activity.getString(R.string.retroachievements_profile_note))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.retroachievements_title)
            .setView(card.view)
            // « Fermer » occupe la place de l'action confirmée ; se déconnecter
            // n'est pas ce qu'on vient faire ici et ne doit pas tomber sous le
            // pouce par habitude.
            .setPositiveButton(R.string.retroachievements_close, null)
            .setNegativeButton(R.string.retroachievements_sign_out) { _, _ -> account.clear() }
            .setNeutralButton(R.string.retroachievements_open_site) { _, _ ->
                val uri = android.net.Uri.parse("https://retroachievements.org/user/")
                    .buildUpon().appendPath(existing.username).build()
                runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
            }
            .create()
        val worker = Executors.newSingleThreadScheduledExecutor {
            Thread(it, "RavenEmu-RA-Profile").apply { isDaemon = true }
        }
        val dismissed = AtomicBoolean(false)
        var client: AchievementClient? = null // worker only
        var refreshed = false // worker only
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            activity.lifecycle.removeObserver(observer)
            dismissed.set(true)
            onDismiss()
            worker.execute { client?.close(); client = null }
            worker.shutdown()
        }
        fun publish(profile: AchievementProfile?, note: Int?) {
            activity.runOnUiThread {
                if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (profile != null) {
                    card.identity(profile.displayName, profile.username, profile.avatarUrl)
                    card.points(profile)
                }
                note?.let { card.showNote(activity.getString(it)) }
            }
        }
        worker.execute {
            runCatching {
                client = AchievementClient { event ->
                    if (event.kind == "login_error" || event.kind == "offline") {
                        publish(null, R.string.retroachievements_profile_offline)
                    }
                }.also { it.login(existing.username, existing.token, true) }
            }.onFailure { publish(null, R.string.retroachievements_profile_offline) }
        }
        worker.scheduleWithFixedDelay({
            runCatching {
                val active = client
                if (dismissed.get() || active == null) return@runCatching
                active.tick(false)
                if (refreshed) return@runCatching
                // Le profil n'est lisible qu'une fois la session ouverte. Tant
                // que la colonne du nom de compte est vide, le serveur n'a pas
                // répondu : lire les points maintenant afficherait zéro et ne
                // les rafraîchirait plus jamais.
                val summary = active.snapshot().summary
                if (summary.size < 11 || summary[9].isBlank()) return@runCatching
                val profile = AchievementProfile.fromSummary(
                    summary, existing.displayName, existing.username,
                ) ?: return@runCatching
                refreshed = true
                // Le nom affiché et le portrait peuvent avoir changé sur le
                // site : les réécrire évite que la prochaine ouverture reparte
                // d'une identité périmée. Le jeton, lui, ne bouge pas.
                runCatching {
                    account.save(existing.username, existing.token, profile.displayName, profile.avatarUrl.orEmpty())
                }
                publish(profile, R.string.retroachievements_profile_note)
            }
        }, 0, 30, TimeUnit.MILLISECONDS)
        dialog.show()
    }
}

