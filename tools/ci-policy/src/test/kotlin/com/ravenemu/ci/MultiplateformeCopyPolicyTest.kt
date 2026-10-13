package com.ravenemu.ci

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Copies de la V2 (0.8.1) embarquées dans la version multiplateforme.
 *
 * La version multiplateforme (`multiplateforme/`) est un projet Gradle
 * séparé, qui ne peut pas dépendre des modules de la V2 : quelques fichiers y
 * sont donc recopiés, et leur en-tête l'annonce
 * (« Port of V2 0.8.1… identical »). Une copie dérive en silence. Une
 * correction apportée d'un seul côté laisse l'autre avec l'ancienne faute, et
 * rien ne le signale, ni à la compilation ni dans les tests.
 *
 * Cette règle fait de l'affirmation de l'en-tête une vérification. Chaque
 * copie doit rester identique à son original, aux seules différences qu'un
 * port impose : le paquet Kotlin, et pour le pont natif le nom des fonctions
 * JNI et la mention de portage. Elle échoue dès qu'un côté change seul, et
 * elle échouera aussi le jour où `main`, plus avancé, sera fusionné dans une
 * branche plus ancienne : c'est voulu, ce jour-là les copies sont en retard.
 *
 * Sans dossier `multiplateforme/`, il n'y a rien à vérifier.
 */
class MultiplateformeCopyPolicyTest {

    private val root = WorkflowFile.repositoryRoot
    private val multiplateforme = File(root, "multiplateforme")

    private val audio = "engine/audio/src/main/kotlin/com/ravenemu/emulation/api/audio"
    private val copies = "multiplateforme/androidApp/src/main/kotlin/com/ravenemu/v3/android"

    /** Le fichier sans sa déclaration de paquet, seule différence d'une copie Kotlin. */
    private fun withoutPackage(text: String): String =
        text.lines().filterNot { it.startsWith("package ") }.joinToString("\n")

    @Test
    fun `les copies Kotlin de l'audio restent identiques à la V2`() {
        if (!multiplateforme.isDirectory) return
        for (name in listOf("AudioBufferPrimer", "AudioClockGovernor", "BandLimitedResampler")) {
            val original = File(root, "$audio/$name.kt")
            val copy = File(root, "$copies/$name.kt")
            assertTrue(copy.isFile, "Copie multiplateforme introuvable : ${copy.relativeTo(root)}. Renommée ? Mettez la règle à jour.")
            assertEquals(
                withoutPackage(original.readText()),
                withoutPackage(copy.readText()),
                "$name.kt a divergé entre la V2 et la version multiplateforme : reportez la modification des deux côtés",
            )
        }
    }

    @Test
    fun `le pont natif des succès reste identique à la V2`() {
        if (!multiplateforme.isDirectory) return
        val original = File(root, "native/jni/src/achievements_bridge.cpp").readText()
        val copyFile = File(root, "multiplateforme/native/android/achievements_bridge.cpp")
        assertTrue(copyFile.isFile, "Pont multiplateforme introuvable. Renommé ? Mettez la règle à jour.")
        val copy = copyFile.readText()
        // Les deux seules différences d'un port : la mention qui l'annonce, et
        // le paquet Java des fonctions JNI.
        val expected = PORT_HEADER + original.replace(
            "Java_com_ravenemu_nativebridge_NativeAchievementsBridge_",
            "Java_com_ravenemu_v3_android_NativeAchievementsBridge_",
        )
        assertEquals(
            expected,
            copy,
            "achievements_bridge.cpp a divergé entre la V2 et la version multiplateforme : reportez la modification des deux côtés",
        )
    }

    private companion object {
        const val PORT_HEADER =
            "// Port of V2's official rcheevos adapter (native/jni/src/achievements_bridge.cpp).\n" +
                "// Only the JNI package changes for V3; everything else stays identical.\n"
    }
}
