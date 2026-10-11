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

    @Test
    fun `la barre suit les points et non le nombre de succès`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "20", total = "25", points = "20", totalPoints = "200"),
        )!!
        assertEquals(1000, progress.progressBasisPoints)
    }

    @Test
    fun `les petites progressions en points gardent leurs décimales`() {
        val first = AchievementGameProgress.fromSummary(
            summary(unlocked = "1", total = "197", points = "1", totalPoints = "2086"),
        )!!
        val second = AchievementGameProgress.fromSummary(
            summary(unlocked = "2", total = "197", points = "2", totalPoints = "2086"),
        )!!
        assertEquals(5, first.progressBasisPoints)
        assertEquals(10, second.progressBasisPoints)
    }

    @Test
    fun `les deux jeux de référence avancent selon les points`() {
        val emerald = AchievementGameProgress.fromSummary(
            summary(unlocked = "7", total = "197", points = "20", totalPoints = "2086"),
        )!!
        val ghostBabel = AchievementGameProgress.fromSummary(
            summary(title = "Metal Gear Solid: Ghost Babel", unlocked = "5", total = "106",
                points = "15", totalPoints = "1100"),
        )!!
        assertEquals(96, emerald.progressBasisPoints)
        assertEquals(136, ghostBabel.progressBasisPoints)
    }

    @Test
    fun `un jeu sans points suit quand même le nombre de succès`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "1", total = "4", points = "0", totalPoints = "0"),
        )!!
        assertEquals(2500, progress.progressBasisPoints)
    }

    @Test
    fun `un jeu sans succès ne montre pas de barre`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "0", total = "0", points = "0", totalPoints = "0"),
        )!!
        assertTrue(progress.empty)
        assertEquals(0, progress.progressBasisPoints)
    }

    @Test
    fun `un jeu terminé remplit la barre sans la dépasser`() {
        val done = AchievementGameProgress.fromSummary(
            summary(unlocked = "197", total = "197", points = "2086", totalPoints = "2086"),
        )!!
        assertEquals(10_000, done.progressBasisPoints)
        // Des compteurs incohérents ne doivent pas faire dépasser la barre.
        val overflow = AchievementGameProgress.fromSummary(
            summary(unlocked = "200", total = "197", points = "3000", totalPoints = "2086"),
        )!!
        assertEquals(10_000, overflow.progressBasisPoints)
        val negative = AchievementGameProgress.fromSummary(summary(points = "-1"))!!
        assertEquals(0, negative.progressBasisPoints)
    }

    @Test
    fun `des compteurs illisibles valent zéro plutôt qu'une exception`() {
        val progress = AchievementGameProgress.fromSummary(
            summary(unlocked = "", total = "non", points = "?", totalPoints = ""),
        )!!
        assertEquals(0, progress.unlocked)
        assertEquals(0, progress.progressBasisPoints)
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
