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
 * Reste à les afficher. Plutôt qu'une bibliothèque de rendu complète pour six
 * constructions, cette conversion produit le HTML qu'Android sait déjà mettre
 * en forme. Elle traite les titres, les paragraphes, les listes à puces, le
 * gras, le code en ligne et les liens ; tout le reste est rendu tel quel,
 * plutôt que d'être avalé par une syntaxe qu'on n'aurait pas prévue.
 *
 * Le texte d'origine est échappé avant toute insertion : un document est une
 * donnée, et un chevron qui s'y trouve doit s'afficher, pas ouvrir une
 * balise.
 *
 * Aucune dépendance Android : la conversion se vérifie sur la JVM seule.
 */
internal object LegalMarkdown {

    /** Le document converti en HTML, prêt pour `HtmlCompat.fromHtml`. */
    fun toHtml(markdown: String): String {
        val html = StringBuilder()
        var inList = false
        fun closeList() {
            if (inList) { html.append("</ul>"); inList = false }
        }
        for (raw in markdown.lineSequence()) {
            val line = raw.trimEnd()
            val heading = headingLevel(line)
            when {
                line.isBlank() -> closeList()
                heading > 0 -> {
                    closeList()
                    // Les titres de niveau 1 et 2 du document deviennent h2 et
                    // h3 : l'écran porte déjà son propre titre, et un h1 de
                    // plus en ferait deux.
                    val tag = "h${minOf(heading + 1, 6)}"
                    html.append("<$tag>").append(inline(line.substring(heading).trim())).append("</$tag>")
                }
                isBullet(line) -> {
                    if (!inList) { html.append("<ul>"); inList = true }
                    html.append("<li>").append(inline(line.trimStart().substring(2))).append("</li>")
                }
                else -> {
                    closeList()
                    html.append("<p>").append(inline(line)).append("</p>")
                }
            }
        }
        closeList()
        return html.toString()
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
