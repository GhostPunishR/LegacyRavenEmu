package com.ravenemu.app.achievements

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/** Lecture des colonnes du pont natif vers la carte de profil. */
class AchievementProfileTest {

    private fun summary(
        display: String = "GhostPunishR",
        softcore: String = "483",
        hardcore: String = "0",
        username: String = "GhostPunishR",
        avatar: String = "https://media.retroachievements.org/UserPic/GhostPunishR.png",
    ) = listOf(display, softcore, hardcore, "", "0", "0", "0", "0", "0", username, avatar)

    @Test
    fun `un profil complet est lu colonne par colonne`() {
        val profile = AchievementProfile.fromSummary(summary())!!
        assertEquals("GhostPunishR", profile.displayName)
        assertEquals("GhostPunishR", profile.username)
        assertEquals(483, profile.softcorePoints)
        assertEquals(0, profile.hardcorePoints)
        assertEquals("https://media.retroachievements.org/UserPic/GhostPunishR.png", profile.avatarUrl)
        assertEquals("G", profile.initial)
    }

    @Test
    fun `un tableau trop court ne donne pas de profil`() {
        assertNull(AchievementProfile.fromSummary(null))
        assertNull(AchievementProfile.fromSummary(emptyList()))
        // Les neuf colonnes d'avant l'ajout du compte et du portrait.
        assertNull(AchievementProfile.fromSummary(summary().take(9)))
    }

    @Test
    fun `un nom affiché absent retombe sur le nom de compte`() {
        val profile = AchievementProfile.fromSummary(summary(display = ""))!!
        assertEquals("GhostPunishR", profile.displayName)
    }

    @Test
    fun `sans aucun nom il n'y a pas de profil à montrer`() {
        assertNull(AchievementProfile.fromSummary(summary(display = "", username = "")))
        // Sauf si l'appelant en connaît un, mémorisé lors d'une connexion.
        val profile = AchievementProfile.fromSummary(summary(display = "", username = ""), fallbackUser = "Mémorisé")!!
        assertEquals("Mémorisé", profile.displayName)
        assertEquals("Mémorisé", profile.username)
    }

    @Test
    fun `des points illisibles valent zéro plutôt qu'une exception`() {
        val profile = AchievementProfile.fromSummary(summary(softcore = "", hardcore = "non"))!!
        assertEquals(0, profile.softcorePoints)
        assertEquals(0, profile.hardcorePoints)
    }

    @Test
    fun `un portrait hors domaine est écarté sans emporter le profil`() {
        val profile = AchievementProfile.fromSummary(summary(avatar = "https://exemple.invalid/a.png"))!!
        assertNull(profile.avatarUrl)
        assertEquals("GhostPunishR", profile.displayName)
    }

    @Test
    fun `les points se lisent avec un séparateur de milliers`() {
        val profile = AchievementProfile.fromSummary(summary(softcore = "121381"))!!
        // Le séparateur dépend de la langue de l'appareil : on vérifie qu'il y
        // en a un et que les chiffres sont intacts, pas lequel c'est.
        val formatted = profile.formattedSoftcorePoints()
        assertEquals("121381", formatted.filter { it.isDigit() })
        assertEquals(true, formatted.length > 6)
    }
}

/**
 * Le lecteur décide *quand* le profil est lisible.
 *
 * Le pont natif répond à chaque tour de boucle, trente fois par seconde, bien
 * avant que le serveur ait répondu. Lire trop tôt affiche zéro point sans plus
 * jamais le corriger ; relire sans fin fait clignoter le portrait.
 */
class AchievementProfileReaderTest {

    private fun ready(points: String = "483") = listOf(
        "GhostPunishR", points, "0", "", "0", "0", "0", "0", "0", "GhostPunishR",
        "https://media.retroachievements.org/UserPic/GhostPunishR.png",
    )

    /** Ce que rend le pont tant que la connexion n'a pas abouti. */
    private val pending = List(11) { "" }

    @Test
    fun `rien tant que la session n'est pas ouverte`() {
        val reader = AchievementProfileReader()
        assertNull(reader.read(null))
        assertNull(reader.read(emptyList()))
        assertNull(reader.read(pending))
        // Les neuf colonnes d'avant l'ajout du compte et du portrait.
        assertNull(reader.read(ready().take(9)))
    }

    @Test
    fun `le profil est rendu une seule fois`() {
        val reader = AchievementProfileReader()
        assertNull(reader.read(pending))
        val profile = reader.read(ready())!!
        assertEquals(483, profile.softcorePoints)
        // Les tours suivants ne rendent plus rien, même si les points changent.
        assertNull(reader.read(ready()))
        assertNull(reader.read(ready(points = "999")))
    }

    @Test
    fun `une attente prolongée ne consomme pas la lecture`() {
        val reader = AchievementProfileReader()
        repeat(100) { assertNull(reader.read(pending)) }
        assertEquals(483, reader.read(ready())!!.softcorePoints)
    }

    @Test
    fun `l'identité mémorisée sert de repli quand le serveur n'en donne pas`() {
        val reader = AchievementProfileReader("Nom affiché", "compte")
        // Le nom de compte est la colonne qui ouvre la lecture : sans lui,
        // aucun repli ne s'applique, car la réponse n'est pas arrivée.
        assertNull(reader.read(pending))
        val summary = ready().toMutableList().apply { this[0] = "" }
        assertEquals("GhostPunishR", reader.read(summary)!!.displayName)
    }
}
