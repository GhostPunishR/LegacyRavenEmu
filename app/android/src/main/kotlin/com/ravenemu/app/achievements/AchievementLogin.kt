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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Optional account dialog. Cancellation destroys the SDK on its owning worker. */
object AchievementLogin {
    fun show(activity: FragmentActivity, onDismiss: () -> Unit = {}) {
        val account = AchievementAccount(activity)
        val existing = account.read()
        if (existing != null) {
            MaterialAlertDialogBuilder(activity).setTitle("RetroAchievements")
                .setMessage("Connecté : ${existing.displayName}\nConnexion mémorisée sur cet appareil, même après fermeture de l’application.\nMode normal (Softcore).\nLes changements prennent effet à la prochaine partie.")
                .setNegativeButton("Fermer", null)
                .setPositiveButton("Se déconnecter") { _, _ -> account.clear() }
                .setOnDismissListener { onDismiss() }.show()
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
                                runCatching { account.save(event.title, event.extra, event.detail) }
                                    .fold({ saved = true; "Connecté : ${event.detail.ifBlank { event.title }}. Connexion mémorisée sur cet appareil. Relancez la partie pour activer les succès." },
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
}

