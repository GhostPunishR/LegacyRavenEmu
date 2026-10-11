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
        val reader = AchievementProfileReader() // worker only
        val card = AchievementProfileCard(activity)
        var connected = false // UI thread only
        /**
         * Une fois connecté, le formulaire n'a plus de raison d'être : laisser
         * ses deux champs et son bouton « Se connecter » à l'écran fait croire
         * que la connexion a échoué. Le panneau devient la carte du compte, la
         * même qu'à sa réouverture depuis les paramètres.
         */
        fun showConnected(displayName: String, accountName: String, avatarUrl: String) {
            if (connected || activity.isFinishing || activity.isDestroyed) return
            connected = true
            content.removeAllViews()
            // La carte porte déjà ses marges : les cumuler la décalerait.
            content.setPadding(0, 0, 0, 0)
            content.addView(card.view)
            card.identity(displayName, accountName, avatarUrl)
            card.points(null)
            card.showNote(activity.getString(R.string.retroachievements_signed_in_note))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).visibility = View.GONE
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply {
                text = activity.getString(R.string.retroachievements_close)
                isEnabled = true
                setOnClickListener { dialog.dismiss() }
            }
        }
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
                            if (event.kind != "login") {
                                val message = event.title
                                activity.runOnUiThread {
                                    if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                                    // Le formulaire a cédé la place : son texte
                                    // d'information n'est plus à l'écran, et un
                                    // avis écrit dedans serait perdu.
                                    if (connected) card.showNote(message)
                                    else {
                                        info.text = message
                                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                    }
                                }
                                return@AchievementClient
                            }
                            // Le compte n'est connecté pour de bon que s'il a pu
                            // être écrit : sans cela, la prochaine ouverture
                            // redemanderait le mot de passe, et annoncer une
                            // connexion mémorisée serait faux.
                            val stored = runCatching {
                                account.save(event.title, event.extra, event.detail, event.image)
                            }.isSuccess
                            val name = event.detail.ifBlank { event.title }
                            activity.runOnUiThread {
                                if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                                if (stored) showConnected(name, event.title, event.image)
                                else {
                                    info.text = "Impossible de conserver la connexion sur cet appareil."
                                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
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
            runCatching {
                val active = client
                if (dismissed.get() || active == null) return@runCatching
                active.tick(false)
                // Le client vient de se connecter : ses points sont lisibles
                // sans repasser par le serveur une seconde fois.
                val profile = reader.read(active.snapshot().summary) ?: return@runCatching
                activity.runOnUiThread {
                    if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    showConnected(profile.displayName, profile.username, profile.avatarUrl.orEmpty())
                    card.identity(profile.displayName, profile.username, profile.avatarUrl)
                    card.points(profile)
                }
            }
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
        val reader = AchievementProfileReader(existing.displayName, existing.username) // worker only
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
                val profile = reader.read(active.snapshot().summary) ?: return@runCatching
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

