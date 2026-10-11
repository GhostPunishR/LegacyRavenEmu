package com.ravenemu.app.about

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Le document affiché est une donnée du dépôt. Ce qui compte ici est autant ce
 * que la conversion rend que ce qu'elle refuse de rendre : un texte légal ne
 * doit pas pouvoir devenir un balisage ni un lien exécutable.
 */
class LegalMarkdownTest {

    @Test
    fun `les titres descendent d'un niveau`() {
        // L'écran porte déjà son titre : un h1 de plus en ferait deux.
        assertEquals("<h2>Conditions</h2>", LegalMarkdown.toHtml("# Conditions"))
        assertEquals("<h3>Marques</h3>", LegalMarkdown.toHtml("## Marques"))
        assertEquals("<h4>Détail</h4>", LegalMarkdown.toHtml("### Détail"))
    }

    @Test
    fun `un dièse sans espace n'est pas un titre`() {
        assertEquals("<p>#RavenEmu</p>", LegalMarkdown.toHtml("#RavenEmu"))
        assertEquals("<p>####### sept</p>", LegalMarkdown.toHtml("####### sept"))
    }

    @Test
    fun `les puces forment une liste, fermée par la ligne vide`() {
        assertEquals(
            "<ul><li>aucune ROM</li><li>aucun BIOS</li></ul><p>Suite.</p>",
            LegalMarkdown.toHtml("- aucune ROM\n- aucun BIOS\n\nSuite."),
        )
        // Une liste en fin de document se ferme quand même.
        assertEquals("<ul><li>seule</li></ul>", LegalMarkdown.toHtml("- seule"))
        // Un titre interrompt la liste.
        assertEquals("<ul><li>a</li></ul><h3>T</h3>", LegalMarkdown.toHtml("- a\n## T"))
    }

    @Test
    fun `gras, code et liens sont rendus`() {
        assertEquals("<p>en <b>l'état</b></p>", LegalMarkdown.toHtml("en **l'état**"))
        assertEquals("<p>le fichier <tt>PRIVACY.md</tt></p>", LegalMarkdown.toHtml("le fichier `PRIVACY.md`"))
        assertEquals(
            """<p>voir <a href="https://retroachievements.org">le site</a></p>""",
            LegalMarkdown.toHtml("voir [le site](https://retroachievements.org)"),
        )
    }

    /**
     * Le document est une donnée : un chevron qu'il contient s'affiche, il
     * n'ouvre pas une balise. L'échappement passe avant la pose du balisage,
     * sans quoi `<b>` écrit dans le texte serait mis en forme.
     */
    @Test
    fun `le texte du document ne peut pas devenir du balisage`() {
        assertEquals("<p>&lt;b&gt;pas gras&lt;/b&gt;</p>", LegalMarkdown.toHtml("<b>pas gras</b>"))
        assertEquals("<p>a &amp; b</p>", LegalMarkdown.toHtml("a & b"))
        assertEquals("<p>1 &lt; 2</p>", LegalMarkdown.toHtml("1 < 2"))
        // Et dans un titre comme dans une puce.
        assertEquals("<h2>&lt;script&gt;</h2>", LegalMarkdown.toHtml("# <script>"))
        assertEquals("<ul><li>&lt;img&gt;</li></ul>", LegalMarkdown.toHtml("- <img>"))
    }

    /**
     * Un lien n'est cliquable que s'il mène au web. Rendre cliquable un
     * `javascript:` ou un `intent:` ferait d'un document un déclencheur.
     */
    @Test
    fun `seuls les liens web deviennent cliquables`() {
        assertEquals("<p>texte</p>", LegalMarkdown.toHtml("[texte](javascript:alert)"))
        // Et avec des parenthèses dans l'adresse, qui ne doivent pas en
        // laisser traîner une derrière le libellé.
        assertEquals("<p>texte</p>", LegalMarkdown.toHtml("[texte](javascript:alert(1))"))
        assertEquals("<p>texte</p>", LegalMarkdown.toHtml("[texte](intent://pirate)"))
        assertEquals("<p>texte</p>", LegalMarkdown.toHtml("[texte](file:///etc/passwd)"))
        assertEquals(
            """<p><a href="http://exemple.test">texte</a></p>""",
            LegalMarkdown.toHtml("[texte](http://exemple.test)"),
        )
    }

    @Test
    fun `une adresse portant des parenthèses reste entière`() {
        assertEquals(
            """<p><a href="https://fr.wikipedia.org/wiki/Game_Boy_(console)">GB</a></p>""",
            LegalMarkdown.toHtml("[GB](https://fr.wikipedia.org/wiki/Game_Boy_(console))"),
        )
    }

    @Test
    fun `un guillemet dans une adresse ne sort pas de l'attribut`() {
        assertEquals(
            """<p><a href="https://a.test/&quot;x">t</a></p>""",
            LegalMarkdown.toHtml("""[t](https://a.test/"x)"""),
        )
    }

    @Test
    fun `un document vide ne rend rien`() {
        assertEquals("", LegalMarkdown.toHtml(""))
        assertEquals("", LegalMarkdown.toHtml("\n\n   \n"))
    }

    @Test
    fun `les paragraphes successifs restent distincts`() {
        assertEquals("<p>un</p><p>deux</p>", LegalMarkdown.toHtml("un\n\ndeux"))
    }
}
