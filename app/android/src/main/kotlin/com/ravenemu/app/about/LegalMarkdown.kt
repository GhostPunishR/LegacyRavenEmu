package com.ravenemu.app.about

/**
 * Rendu du sous-ensemble de Markdown qu'emploient les documents légaux.
 *
 * Les conditions d'utilisation et la politique de confidentialité vivent dans
 * le dépôt, en Markdown, parce que c'est là qu'elles se relisent et se
 * discutent. L'application en embarque la copie exacte, pour qu'elles restent
 * lisibles sans réseau : un texte légal qu'il faut aller chercher en ligne
 * n'est pas disponible au moment où on le cherche.
 *
 * Reste à les afficher. Cette lecture découpe le document en blocs, et laisse
 * l'écran décider de leur mise en page : c'est elle qui fait la différence
 * entre un document et un pavé. Rendre le tout en un seul morceau de HTML
 * revenait à confier l'espacement à `Html.fromHtml`, qui sépare les blocs
 * d'une seule fin de ligne et ne distingue pas un titre d'un paragraphe.
 *
 * Chaque bloc porte son contenu en HTML **en ligne** seulement : gras, code et
 * liens. Les titres, les paragraphes et les puces sont des blocs distincts ;
 * tout le reste est rendu tel quel, plutôt que d'être avalé par une syntaxe
 * qu'on n'aurait pas prévue.
 *
 * Le texte d'origine est échappé avant toute insertion : un document est une
 * donnée, et un chevron qui s'y trouve doit s'afficher, pas ouvrir une
 * balise.
 *
 * Aucune dépendance Android : la conversion se vérifie sur la JVM seule.
 */
internal object LegalMarkdown {

    /** Un morceau du document, avec son contenu en HTML en ligne. */
    sealed interface Block {
        /** Gras, code et liens uniquement : aucune balise de bloc. */
        val html: String
    }

    /** [level] vaut 1 pour `#`, 2 pour `##`, et ainsi de suite. */
    data class Heading(val level: Int, override val html: String) : Block
    data class Paragraph(override val html: String) : Block
    data class Bullet(override val html: String) : Block

    /** Le document découpé, dans l'ordre. Les lignes vides ne donnent rien. */
    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        for (raw in markdown.lineSequence()) {
            val line = raw.trimEnd()
            val heading = headingLevel(line)
            when {
                line.isBlank() -> Unit
                heading > 0 -> blocks += Heading(heading, inline(line.substring(heading).trim()))
                isBullet(line) -> blocks += Bullet(inline(line.trimStart().substring(2)))
                else -> blocks += Paragraph(inline(line))
            }
        }
        return blocks
    }

    /** Nombre de `#` ouvrant un titre, 0 si la ligne n'en est pas un. */
    private fun headingLevel(line: String): Int {
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes == 0 || hashes > 6) return 0
        // `#Titre` sans espace n'est pas un titre : c'est la règle de Markdown,
        // et elle évite qu'un mot-dièse en début de ligne en devienne un.
        return if (line.length > hashes && line[hashes] == ' ') hashes else 0
    }

    private fun isBullet(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.length > 2 && (trimmed.startsWith("- ") || trimmed.startsWith("* "))
    }

    /**
     * Gras, code et liens, dans un texte d'abord échappé.
     *
     * L'échappement vient en premier, et les balises sont posées ensuite : un
     * document qui contiendrait `<b>` l'affiche au lieu de le faire exécuter.
     */
    private fun inline(text: String): String {
        var result = escape(text)
        result = LINK.replace(result) { match ->
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            // Seuls http(s) deviennent cliquables. Un lien `javascript:` ou
            // `intent:` dans un document n'a rien à faire dans une vue de
            // texte, et le laisser passer ferait d'un document un déclencheur.
            if (url.startsWith("http://") || url.startsWith("https://")) {
                "<a href=\"${url.replace("\"", "&quot;")}\">$label</a>"
            } else label
        }
        result = BOLD.replace(result) { "<b>${it.groupValues[1]}</b>" }
        result = CODE.replace(result) { "<tt>${it.groupValues[1]}</tt>" }
        return result
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // Appliqués après échappement : les crochets et astérisques ne sont pas
    // touchés par celui-ci, les motifs restent donc valables.
    // L'adresse accepte une paire de parenthèses internes : les liens
    // encyclopédiques en portent couramment, et s'arrêter à la première
    // fermante couperait l'adresse en laissant une parenthèse orpheline
    // derrière le libellé.
    private val LINK = Regex("""\[([^\]\n]+)]\(([^()\s]*(?:\([^()\s]*\)[^()\s]*)*)\)""")
    private val BOLD = Regex("""\*\*([^*\n]+)\*\*""")
    private val CODE = Regex("""`([^`\n]+)`""")
}
