package com.ravenemu.app.about

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
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
 * Chaque bloc du document reçoit sa propre vue, et c'est là que se joue la
 * lisibilité. Rendre le document en un seul morceau de HTML confiait
 * l'espacement à `Html.fromHtml`, qui sépare les blocs d'une seule fin de
 * ligne et ne distingue pas un titre d'un paragraphe : les quarante lignes
 * d'une politique de confidentialité arrivaient en un pavé continu. Les
 * titres ont maintenant leur taille et leur air au-dessus, les paragraphes
 * leur interligne, et les puces un retrait qui tient sur plusieurs lignes.
 *
 * Les textes de licence, eux, sont affichés sans retouche : leur mise en page
 * fait partie du texte officiel et ne doit pas être réinterprétée.
 */
class LegalDocumentActivity : RavenActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Int) = (value * density).toInt()

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
        val container = findViewById<LinearLayout>(R.id.documentContent)
        val text = runCatching {
            resources.openRawResource(document).bufferedReader().use { it.readText() }
        }.getOrElse { "" }
        if (text.isEmpty()) {
            container.addView(body().apply { setText(R.string.about_document_unavailable) })
            return
        }
        if (markdown) renderMarkdown(container, text) else renderPlainText(container, text)
    }

    private fun renderMarkdown(container: LinearLayout, text: String) {
        val parsed = LegalMarkdown.parse(text)
        // Chaque document s'ouvre sur son propre titre, que la barre affiche
        // déjà : le garder donnerait deux fois le même mot l'un sous l'autre.
        // La ligne qui le suit porte la date de révision, et devient alors la
        // première chose lue : elle est traitée comme une légende.
        val titled = parsed.firstOrNull().let { it is LegalMarkdown.Heading && it.level <= 1 }
        val blocks = if (titled) parsed.drop(1) else parsed
        for ((index, block) in blocks.withIndex()) {
            val first = index == 0
            val view = when {
                block is LegalMarkdown.Heading -> heading(block.level)
                titled && first -> caption()
                else -> body()
            }
            view.text = HtmlCompat.fromHtml(block.html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            view.movementMethod = LinkMovementMethod.getInstance()
            container.addView(if (block is LegalMarkdown.Bullet) bulleted(view) else view, spacing(block, first))
        }
    }

    /**
     * Le retrait d'une puce doit tenir quand le texte passe à la ligne : un
     * simple « • » collé devant le ferait revenir à la marge. La puce occupe
     * donc sa propre colonne, et le texte la sienne.
     */
    private fun bulleted(content: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(body().apply {
            text = "•"
            gravity = Gravity.START
        }, LinearLayout.LayoutParams(dp(16), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** L'air au-dessus d'un bloc : c'est lui qui sépare les sections. */
    private fun spacing(block: LegalMarkdown.Block, first: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = when {
                first -> 0
                block is LegalMarkdown.Heading && block.level <= 1 -> dp(28)
                block is LegalMarkdown.Heading && block.level == 2 -> dp(24)
                block is LegalMarkdown.Heading -> dp(18)
                block is LegalMarkdown.Bullet -> dp(6)
                else -> dp(14)
            }
        }

    private fun heading(level: Int): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (level <= 1) 20f else if (level == 2) 17f else 15f)
        setTextColor(ContextCompat.getColor(this@LegalDocumentActivity, R.color.raven_text_primary))
        typeface = Typeface.DEFAULT_BOLD
    }

    /** Ligne de date, en tête : présente sans occuper la place d'un titre. */
    private fun caption(): TextView = body().apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(ContextCompat.getColor(this@LegalDocumentActivity, R.color.raven_text_muted))
    }

    private fun body(): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
        setTextColor(ContextCompat.getColor(this@LegalDocumentActivity, R.color.raven_text_secondary))
        setLineSpacing(0f, 1.35f)
        setTextIsSelectable(true)
    }

    private fun renderPlainText(container: LinearLayout, text: String) {
        container.addView(TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(this@LegalDocumentActivity, R.color.raven_text_secondary))
            setLineSpacing(0f, 1.1f)
            setTextIsSelectable(true)
            this.text = text
        })
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
