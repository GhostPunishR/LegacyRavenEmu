package com.ravenemu.app.achievements

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

/** Lecture de l'avancement affiché au-dessus de la liste des succès. */
class AchievementGameProgressTest {

    private fun summary(
        title: String = "Pokémon Emerald Version",
        unlocked: String = "4",
        total: String = "197",
        points: String = "7",
        totalPoints: String = "2086",
    ) = listOf("GhostPunishR", "485", "0", title, "1", unlocked, total, points, totalPoints)

    @Test
    fun `l'avancement se lit colonne par colonne`() {
        val progress = AchievementGameProgress.fromSummary(summary())!!
        assertEquals("Pokémon Emerald Version", progress.title)
        assertEquals(4, progress.unlocked)
        assertEquals(197, progress.total)
        assertEquals(7, progress.points)
        assertEquals(2086, progress.totalPoints)
        assertFalse(progress.empty)
    }

    @Test
    fun `sans jeu reconnu il n'y a pas d'avancement`() {
        assertNull(AchievementGameProgress.fromSummary(null))
        assertNull(AchievementGameProgress.fromSummary(emptyList()))
        assertNull(AchievementGameProgress.fromSummary(summary(title = "")))
        assertNull(AchievementGameProgress.fromSummary(summary(title = "   ")))
        assertNull(AchievementGameProgress.fromSummary(summary().take(8)))
    }

    /**
     * La barre compte en points, pas en succès. Vingt petits succès obtenus
     * sur un jeu qui en garde cinq énormes ne sont pas quatre-vingts pour cent
     * du chemin, et une barre qui le dirait mentirait sur ce qui reste.
     */
    @Test
    fun `la barre suit les points et non le nombre de succès`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "20", total = "25", points = "20", totalPoints = "200"),
        )!!
        assertEquals(10, progress.percent)
    }

    @Test
    fun `un jeu sans points retombe sur le nombre de succès`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "1", total = "4", points = "0", totalPoints = "0"),
        )!!
        assertEquals(25, progress.percent)
    }

    @Test
    fun `un jeu sans succès ne montre pas de barre`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "0", total = "0", points = "0", totalPoints = "0"),
        )!!
        assertTrue(progress.empty)
        assertEquals(0, progress.percent)
    }

    @Test
    fun `un jeu terminé remplit la barre sans la dépasser`() {
        val done = AchievementGameProgress.fromSummary(
            summary(unlocked = "197", total = "197", points = "2086", totalPoints = "2086"),
        )!!
        assertEquals(100, done.percent)
        // Le serveur peut compter des points d'un lot que le total ignore :
        // la barre se borne au lieu de déborder.
        val overflow = AchievementGameProgress.fromSummary(
            summary(points = "3000", totalPoints = "2086"),
        )!!
        assertEquals(100, overflow.percent)
    }

    @Test
    fun `des compteurs illisibles valent zéro plutôt qu'une exception`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "", total = "non", points = "?", totalPoints = ""),
        )!!
        assertEquals(0, progress.unlocked)
        assertEquals(0, progress.percent)
        assertTrue(progress.empty)
    }

    @Test
    fun `les grands nombres portent un séparateur de milliers`() {
        val progress = AchievementGameProgress.fromSummary(summary())!!
        assertEquals("4 / 197", progress.achievementsLabel())
        // Le séparateur dépend de la langue : on vérifie les chiffres et sa
        // présence, pas lequel c'est.
        val points = progress.pointsLabel()
        assertEquals("72086", points.filter { it.isDigit() })
        assertTrue(points.length > "7 / 2086".length)
    }
}
