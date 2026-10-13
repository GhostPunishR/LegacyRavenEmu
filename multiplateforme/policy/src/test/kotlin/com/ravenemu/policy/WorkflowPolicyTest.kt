package com.ravenemu.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Règles des workflows GitHub Actions du dépôt.
 *
 * Le projet tourne autour de la version multiplateforme : le code situé hors
 * de `multiplateforme/` (V2 0.8.1) est conservé en héritage et n'est plus
 * construit par la CI. Ces règles le garantissent, et gardent les workflows
 * durcis : actions épinglées, identifiants git non conservés, secrets hors des
 * journaux, permissions déclarées.
 */
class WorkflowPolicyTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, ".github/workflows").isDirectory }
        ?: error("Racine du dépôt introuvable depuis ${File("").absolutePath}")

    private val workflows: List<File> = File(root, ".github/workflows")
        .listFiles { file -> file.extension == "yml" || file.extension == "yaml" }
        .orEmpty().sortedBy { it.name }

    private fun lines(file: File) = file.readLines().withIndex()

    @Test
    fun `les workflows sont lus, dont celui de la version multiplateforme`() {
        assertTrue(
            workflows.any { it.name == "multiplateforme.yml" },
            "Workflows lus : ${workflows.map { it.name }}",
        )
    }

    @Test
    fun `la CI ne construit que la version multiplateforme`() {
        // Gradle et CMake sont les deux portes d'entrée d'une construction.
        // Chaque appel doit viser `multiplateforme/` ; le code hérité de la
        // V2 ne doit pas revenir dans la CI par un nouveau workflow.
        val fautes = workflows.flatMap { file ->
            lines(file).filter { (_, line) ->
                val code = line.substringBefore(" #").trim()
                !code.startsWith("#") &&
                    ((Regex("""(^|[\s;&|])(\./)?gradlew(\.bat)?\s""").containsMatchIn(code) &&
                        "-p multiplateforme" !in code) ||
                        (Regex("""\bcmake\b""").containsMatchIn(code) && "multiplateforme" !in code))
            }.map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList(), fautes,
            "Construction hors de multiplateforme/ : le code hérité n'est plus construit par la CI")
    }

    @Test
    fun `les actions tierces sont épinglées par SHA`() {
        val uses = Regex("""^\s*-?\s*uses:\s*([^\s#]+)""")
        val fautes = workflows.flatMap { file ->
            lines(file).mapNotNull { (index, line) ->
                val reference = uses.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                if (reference.startsWith("./")) return@mapNotNull null
                val version = reference.substringAfter('@', "")
                if (Regex("^[0-9a-f]{40}$").matches(version)) null
                else "${file.name}:${index + 1}: $reference"
            }
        }
        assertEquals(emptyList(), fautes,
            "Une étiquette (v4, main…) peut être redirigée vers un autre code : épinglez le SHA complet")
    }

    @Test
    fun `le checkout ne conserve pas les identifiants git`() {
        // Bloc d'options du checkout : les lignes plus indentées qui suivent,
        // jusqu'à l'étape suivante (ligne commençant par « - »).
        val checkout = Regex(
            """uses: actions/checkout@\S+[^\n]*\n((?:[ \t]++(?!-)[^\n]*\n)*)""",
        )
        val fautes = workflows.flatMap { file ->
            checkout.findAll(file.readText())
                .filter { "persist-credentials: false" !in it.groupValues[1] }
                .map { "${file.name}: ${it.value.lineSequence().first().trim()}" }
                .toList()
        }
        assertEquals(emptyList(), fautes,
            "Chaque checkout doit déclarer persist-credentials: false")
    }

    @Test
    fun `aucun secret n'est écrit dans un journal`() {
        val fautes = workflows.flatMap { file ->
            lines(file).filter { (_, line) ->
                Regex("""\b(echo|printf)\b""").containsMatchIn(line) && "secrets." in line
            }.map { (index, _) -> "${file.name}:${index + 1}" }
        }
        assertEquals(emptyList(), fautes, "Un secret ne doit jamais passer par echo/printf")
    }

    @Test
    fun `chaque workflow déclare ses permissions`() {
        // Sans déclaration, le jeton reçoit les permissions par défaut du
        // dépôt, souvent en écriture.
        val fautes = workflows.filter { file ->
            file.readLines().none { it == "permissions:" || it.startsWith("permissions: ") }
        }.map { it.name }
        assertEquals(emptyList(), fautes, "Déclarez un bloc permissions: au niveau du workflow")
    }

    @Test
    fun `la distribution Gradle est épinglée par empreinte`() {
        // Le workflow compare ensuite cette empreinte à celle publiée par Gradle.
        val properties = File(root, "gradle/wrapper/gradle-wrapper.properties").readText()
        assertTrue(
            Regex("""(?m)^distributionSha256Sum=[0-9a-fA-F]{64}$""").containsMatchIn(properties),
            "distributionSha256Sum absent de gradle-wrapper.properties",
        )
    }
}
