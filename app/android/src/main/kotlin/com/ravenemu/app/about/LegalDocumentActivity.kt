package com.ravenemu.app.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import com.google.android.material.appbar.MaterialToolbar
import com.ravenemu.app.R
import com.ravenemu.app.RavenActivity

/**
 * Affiche un document légal embarqué dans l'application.
 *
 * Conditions d'utilisation, politique de confidentialité et textes de licence
 * voyagent avec l'application plutôt que derrière un lien. Un document qu'il
 * faut aller chercher en ligne manque précisément quand on le cherche, et
 * l'AGPL demande que la licence accompagne le programme, pas qu'elle soit
 * joignable.
 *
 * Les documents Markdown passent par [LegalMarkdown] ; les textes de licence
 * sont affichés tels quels, leur mise en page faisant partie du texte officiel
 * et ne devant pas être réinterprétée.
 */
class LegalDocumentActivity : RavenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_document)
        val title = intent.getIntExtra(EXTRA_TITLE, 0)
        val document = intent.getIntExtra(EXTRA_DOCUMENT, 0)
        val markdown = intent.getBooleanExtra(EXTRA_MARKDOWN, false)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(if (title != 0) title else R.string.about_title)
            setNavigationOnClickListener { finish() }
        }
        val view = findViewById<TextView>(R.id.documentText)
        val text = runCatching {
            resources.openRawResource(document).bufferedReader().use { it.readText() }
        }.getOrElse { "" }
        if (text.isEmpty()) {
            view.text = getString(R.string.about_document_unavailable)
            return
        }
        if (markdown) {
            view.text = HtmlCompat.fromHtml(LegalMarkdown.toHtml(text), HtmlCompat.FROM_HTML_MODE_COMPACT)
            view.movementMethod = LinkMovementMethod.getInstance()
        } else {
            // Un texte de licence se lit tel qu'il est écrit : son retrait et
            // ses retours à la ligne font partie du document officiel.
            view.typeface = android.graphics.Typeface.MONOSPACE
            view.textSize = 12f
            view.text = text
        }
    }

    companion object {
        private const val EXTRA_TITLE = "com.ravenemu.app.about.TITLE"
        private const val EXTRA_DOCUMENT = "com.ravenemu.app.about.DOCUMENT"
        private const val EXTRA_MARKDOWN = "com.ravenemu.app.about.MARKDOWN"

        /** Document Markdown du dépôt, mis en forme à l'affichage. */
        fun markdown(context: Context, @StringRes title: Int, @RawRes document: Int): Intent =
            intent(context, title, document, true)

        /** Texte de licence, affiché sans retouche. */
        fun plainText(context: Context, @StringRes title: Int, @RawRes document: Int): Intent =
            intent(context, title, document, false)

        private fun intent(context: Context, title: Int, document: Int, markdown: Boolean): Intent =
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_DOCUMENT, document)
                .putExtra(EXTRA_MARKDOWN, markdown)
    }
}
