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
