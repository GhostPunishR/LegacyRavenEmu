package com.ravenemu.ci

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Documents légaux embarqués dans l'application.
 *
 * Les conditions d'utilisation et la politique de confidentialité existent en
 * deux exemplaires : celui du dépôt, où on les relit et les discute, et celui
 * de `res/raw`, qui part dans l'APK pour rester lisible sans réseau. Deux
 * copies dérivent, et une politique de confidentialité périmée affichée dans
 * l'application est pire que pas de politique du tout : elle affirme quelque
 * chose de faux sur le traitement des données.
 *
 * Rien dans la compilation ne rapproche ces fichiers. Cette règle le fait, et
 * transforme l'oubli de recopier en échec de construction.
 *
 * Elle vérifie aussi que l'écran qui les affiche est déclaré au manifeste.
 * Une activité absente du manifeste compile, se lie, et ne lève son exception
 * qu'au moment où un utilisateur touche l'entrée : exactement le genre de
 * panne que ce dossier existe pour attraper plus tôt.
 */
class LegalDocumentPolicyTest {

    private val root = WorkflowFile.repositoryRoot
    private val raw = File(root, "app/android/src/main/res/raw")

    /** Document du dépôt, et sa copie embarquée. */
    private val copies = mapOf(
        "TERMS.md" to "terms.md",
        "PRIVACY.md" to "privacy.md",
        "LICENSE" to "license_agpl_3_0.txt",
        "native/achievements/LICENSE.rcheevos" to "license_mit_rcheevos.txt",
    )

    @Test
    fun `chaque document embarque est la copie exacte de celui du depot`() {
        for ((source, embedded) in copies) {
            val original = File(root, source)
            val shipped = File(raw, embedded)
            assertTrue(original.isFile, "Document absent du dépôt : $source")
            assertTrue(shipped.isFile, "Document absent de l'application : res/raw/$embedded")
            assertEquals(
                original.readText(),
                shipped.readText(),
                "res/raw/$embedded a dérivé de $source : l'application afficherait un texte périmé",
            )
        }
    }

    /**
     * La licence Apache n'a pas d'original dans le dépôt : elle couvre des
     * bibliothèques récupérées à la construction. On vérifie donc son
     * contenu plutôt que sa concordance.
     */
    @Test
    fun `la licence Apache embarquee est complete`() {
        val text = File(raw, "license_apache_2_0.txt").readText()
        assertTrue(text.startsWith("Apache License"), "En-tête de la licence Apache absent")
        assertTrue(text.contains("Version 2.0, January 2004"), "Version de la licence Apache absente")
        assertTrue(text.contains("9. Accepting Warranty"), "Article 9 absent : le texte est tronqué")
        assertTrue(text.contains("APPENDIX"), "Annexe absente : le texte est tronqué")
    }

    @Test
    fun `l'ecran des documents est declare au manifeste`() {
        val manifest = File(root, "app/android/src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(manifest)
        val activities = document.getElementsByTagName("activity")
        val names = (0 until activities.length)
            .map(activities::item)
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { (it as Element).getAttribute("android:name") }
        assertTrue(
            names.contains(".about.LegalDocumentActivity"),
            "LegalDocumentActivity absente du manifeste : l'entrée « À propos » lèverait une exception à l'ouverture",
        )
    }

    /**
     * Chaque ressource `R.raw` citée par le code doit exister. Une ressource
     * manquante est une erreur de compilation, mais une ressource *renommée*
     * d'un côté seulement laisse un document vide à l'écran.
     */
    @Test
    fun `chaque document cite par le code existe`() {
        val sources = File(root, "app/android/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val cited = Regex("""R\.raw\.([a-z0-9_]+)""").findAll(sources).map { it.groupValues[1] }.toSortedSet()
        assertTrue(cited.isNotEmpty(), "Aucune ressource brute citée : la règle ne vérifierait rien")
        val present = (raw.listFiles() ?: emptyArray()).map { it.name.substringBeforeLast('.') }.toSet()
        assertEquals(
            emptySet(),
            cited - present,
            "Ressources citées par le code et absentes de res/raw",
        )
    }
}
