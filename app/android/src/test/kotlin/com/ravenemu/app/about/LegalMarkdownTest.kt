package com.ravenemu.app.about

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Le document affiché est une donnée du dépôt. Ce qui compte ici est autant ce
 * que la conversion rend que ce qu'elle refuse de rendre : un texte légal ne
 * doit pas pouvoir devenir un balisage ni un lien exécutable.
 */
class LegalMarkdownTest {

    /** Le HTML en ligne du premier bloc : ce que l'écran donnera à mettre en forme. */
    private fun html(markdown: String): String = LegalMarkdown.parse(markdown).single().html

    @Test
    fun `les titres portent leur niveau`() {
        // C'est l'écran qui choisit la taille : la lecture ne fait que dire
        // de quel rang est le titre.
        assertEquals(listOf(LegalMarkdown.Heading(1, "Conditions")), LegalMarkdown.parse("# Conditions"))
        assertEquals(listOf(LegalMarkdown.Heading(2, "Marques")), LegalMarkdown.parse("## Marques"))
        assertEquals(listOf(LegalMarkdown.Heading(3, "Détail")), LegalMarkdown.parse("### Détail"))
    }

    @Test
    fun `un dièse sans espace n'est pas un titre`() {
        assertEquals(listOf(LegalMarkdown.Paragraph("#RavenEmu")), LegalMarkdown.parse("#RavenEmu"))
        assertEquals(listOf(LegalMarkdown.Paragraph("####### sept")), LegalMarkdown.parse("####### sept"))
    }

    @Test
    fun `chaque puce est un bloc, distinct du texte qui suit`() {
        assertEquals(
            listOf(
                LegalMarkdown.Bullet("aucune ROM"),
                LegalMarkdown.Bullet("aucun BIOS"),
                LegalMarkdown.Paragraph("Suite."),
            ),
            LegalMarkdown.parse("- aucune ROM\n- aucun BIOS\n\nSuite."),
        )
        assertEquals(listOf(LegalMarkdown.Bullet("seule")), LegalMarkdown.parse("- seule"))
        assertEquals(
            listOf(LegalMarkdown.Bullet("a"), LegalMarkdown.Heading(2, "T")),
            LegalMarkdown.parse("- a\n## T"),
        )
        // L'astérisque est l'autre marque de puce admise.
        assertEquals(listOf(LegalMarkdown.Bullet("autre")), LegalMarkdown.parse("* autre"))
    }

    @Test
    fun `gras, code et liens sont rendus dans le bloc`() {
        assertEquals("en <b>l'état</b>", html("en **l'état**"))
        assertEquals("le fichier <tt>PRIVACY.md</tt>", html("le fichier `PRIVACY.md`"))
        assertEquals(
            """voir <a href="https://retroachievements.org">le site</a>""",
            html("voir [le site](https://retroachievements.org)"),
        )
    }

    /**
     * Le document est une donnée : un chevron qu'il contient s'affiche, il
     * n'ouvre pas une balise. L'échappement passe avant la pose du balisage,
     * sans quoi `<b>` écrit dans le texte serait mis en forme.
     */
    @Test
    fun `le texte du document ne peut pas devenir du balisage`() {
        assertEquals("&lt;b&gt;pas gras&lt;/b&gt;", html("<b>pas gras</b>"))
        assertEquals("a &amp; b", html("a & b"))
        assertEquals("1 &lt; 2", html("1 < 2"))
        // Et dans un titre comme dans une puce.
        assertEquals("&lt;script&gt;", html("# <script>"))
        assertEquals("&lt;img&gt;", html("- <img>"))
    }

    /**
     * Un lien n'est cliquable que s'il mène au web. Rendre cliquable un
     * `javascript:` ou un `intent:` ferait d'un document un déclencheur.
     */
    @Test
    fun `seuls les liens web deviennent cliquables`() {
        assertEquals("texte", html("[texte](javascript:alert)"))
        // Et avec des parenthèses dans l'adresse, qui ne doivent pas en
        // laisser traîner une derrière le libellé.
        assertEquals("texte", html("[texte](javascript:alert(1))"))
        assertEquals("texte", html("[texte](intent://pirate)"))
        assertEquals("texte", html("[texte](file:///etc/passwd)"))
        assertEquals("""<a href="http://exemple.test">texte</a>""", html("[texte](http://exemple.test)"))
    }

    @Test
    fun `une adresse portant des parenthèses reste entière`() {
        assertEquals(
            """<a href="https://fr.wikipedia.org/wiki/Game_Boy_(console)">GB</a>""",
            html("[GB](https://fr.wikipedia.org/wiki/Game_Boy_(console))"),
        )
    }

    @Test
    fun `un guillemet dans une adresse ne sort pas de l'attribut`() {
        assertEquals(
            """<a href="https://a.test/&quot;x">t</a>""",
            html("""[t](https://a.test/"x)"""),
        )
    }

    @Test
    fun `un document vide ne rend aucun bloc`() {
        assertEquals(emptyList<LegalMarkdown.Block>(), LegalMarkdown.parse(""))
        assertEquals(emptyList<LegalMarkdown.Block>(), LegalMarkdown.parse("\n\n   \n"))
    }

    /**
     * C'est le découpage en blocs qui donne son air au document : un bloc par
     * paragraphe, et l'écran met l'espacement entre eux. Les rendre d'un seul
     * tenant laissait `Html.fromHtml` les coller par une fin de ligne unique.
     */
    @Test
    fun `les paragraphes successifs sont des blocs distincts`() {
        assertEquals(
            listOf(LegalMarkdown.Paragraph("un"), LegalMarkdown.Paragraph("deux")),
            LegalMarkdown.parse("un\n\ndeux"),
        )
        // Et deux lignes qui se suivent sans ligne vide restent deux blocs :
        // les documents du dépôt écrivent un paragraphe par ligne.
        assertEquals(2, LegalMarkdown.parse("un\ndeux").size)
    }
}
