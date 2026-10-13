package com.ravenemu.ci

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Garde-fous des workflows : permissions, épinglage, secrets.
 *
 * Les règles d'épinglage, de secrets et de checkout valent pour **tous** les
 * workflows du dépôt. Elles ne lisaient que `android.yml`, si bien qu'un
 * nouveau workflow y échappait sans que rien ne le signale : c'est arrivé avec
 * celui de la V3, qui référençait ses actions par étiquette mobile. Une
 * étiquette comme `@v4` peut être redirigée vers un autre code par celui qui
 * la publie ; un commit ne le peut pas.
 *
 * Les permissions en lecture seule et l'absence d'APK Debug restent propres
 * au workflow Android : celui du wiki doit écrire pour publier, et celui de
 * la V3 diffuse volontairement un APK de préversion à tester.
 */
class WorkflowHardeningTest {

    private val workflow = WorkflowFile.androidWorkflow()

    private val allWorkflows: List<WorkflowFile> =
        (File(WorkflowFile.repositoryRoot, ".github/workflows").listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension in setOf("yml", "yaml") }
            .sortedBy { it.name }
            .map(::WorkflowFile)

    @Test
    fun `tous les workflows sont lus`() {
        assertTrue(
            allWorkflows.any { it.path.name == "android.yml" },
            "Le workflow Android doit figurer parmi les workflows vérifiés : lecture du dossier cassée",
        )
    }

    @Test
    fun `les permissions par défaut sont en lecture seule`() {
        val header = workflow.text.substringBefore("jobs:")
        assertTrue(
            Regex("^permissions:\\s*\\n\\s+contents: read\\s*$", RegexOption.MULTILINE)
                .containsMatchIn(header),
            "Le workflow doit démarrer avec « permissions: contents: read »",
        )
    }

    @Test
    fun `seul le job de publication Test obtient l'écriture`() {
        for ((name, block) in workflow.jobs) {
            val declaration = block.joinToString("\n")
            if ("contents: write" !in declaration) continue
            assertEquals(
                "publish-test",
                name,
                "Seule la publication de la préversion Test justifie « contents: write »",
            )
        }
    }

    @Test
    fun `les actions tierces sont épinglées par SHA`() {
        for (current in allWorkflows) {
            val references = current.actionReferences
            assertTrue(references.isNotEmpty(), "Aucune action détectée dans ${current.path.name} : lecture cassée")
            for (reference in references) {
                if (reference.startsWith("./")) continue
                val version = reference.substringAfterLast('@', missingDelimiterValue = "")
                if (!Regex("^[0-9a-f]{40}$").matches(version)) {
                    fail("Action non épinglée par SHA dans ${current.path.name} : $reference")
                }
            }
        }
    }

    @Test
    fun `aucun secret n'est écrit dans un journal`() {
        // Un secret ne doit apparaître qu'en affectation de variable
        // d'environnement, jamais dans un `echo` ou une interpolation shell.
        val fautes = allWorkflows.flatMap { current ->
            current.text.lines().withIndex().filter { (_, line) ->
                val trimmed = line.trim()
                "secrets." in trimmed &&
                    (trimmed.startsWith("echo") || trimmed.startsWith("printf") ||
                        trimmed.startsWith("run:"))
            }.map { "${current.path.name}, ligne ${it.index + 1} : ${it.value.trim()}" }
        }
        assertEquals(
            emptyList(),
            fautes,
            "Un secret ne doit jamais être passé à une commande d'affichage",
        )
    }

    @Test
    fun `le checkout ne conserve pas les identifiants git`() {
        for (current in allWorkflows) {
            // Toute référence au checkout est comptée, épinglée ou non : un
            // checkout par étiquette ne doit pas échapper à cette règle-ci au
            // motif qu'il échoue déjà à celle de l'épinglage.
            //
            // Le bloc d'options s'arrête à l'étape suivante, reconnue à son
            // tiret. Sans cette borne, toutes les lignes indentées qui suivent
            // en faisaient partie, et un `persist-credentials` situé plus bas
            // dans le fichier suffisait à faire passer un checkout qui n'en a
            // pas. Le quantificateur possessif empêche de reculer d'un espace
            // pour contourner le tiret.
            val checkouts = Regex("uses: actions/checkout@[^\\s]+[^\\n]*\\n((?:[ \\t]++(?!-)[^\\n]*\\n)*)")
                .findAll(current.text)
                .map { it.groupValues[1] }
                .toList()
            for (options in checkouts) {
                assertTrue(
                    "persist-credentials: false" in options,
                    "Chaque checkout de ${current.path.name} doit désactiver la persistance des identifiants",
                )
            }
        }
    }

    @Test
    fun `aucun APK Debug n'est publié`() {
        val uploads = workflow.text.substringAfter("jobs:")
        assertTrue(
            "apk/debug" !in uploads,
            "La CI ne publie pas d'APK Debug : seul l'APK Test optimisé est diffusé",
        )
    }
}
