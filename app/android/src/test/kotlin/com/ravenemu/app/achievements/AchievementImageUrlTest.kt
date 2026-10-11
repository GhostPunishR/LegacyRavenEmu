package com.ravenemu.app.achievements

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Le filtre décide de l'hôte que l'application ira contacter pour une image.
 * Les adresses refusées ci-dessous viennent toutes d'une réponse serveur :
 * c'est le seul chemin par lequel une adresse arbitraire peut entrer.
 */
class AchievementImageUrlTest {

    private val badge = "https://media.retroachievements.org/Badge/00001.png"

    @Test
    fun `un badge et un portrait du domaine sont acceptés`() {
        assertEquals(badge, AchievementImageUrl.sanitize(badge))
        assertEquals(
            "https://media.retroachievements.org/UserPic/GhostPunishR.png",
            AchievementImageUrl.sanitize("https://media.retroachievements.org/UserPic/GhostPunishR.png"),
        )
        // Le domaine nu et ses sous-domaines servent aussi des images.
        assertEquals("https://retroachievements.org/Images/1.png",
            AchievementImageUrl.sanitize("https://retroachievements.org/Images/1.png"))
    }

    @Test
    fun `une adresse vide ou absente ne donne pas d'image`() {
        assertNull(AchievementImageUrl.sanitize(null))
        assertNull(AchievementImageUrl.sanitize(""))
        assertNull(AchievementImageUrl.sanitize("   "))
        assertNull(AchievementImageUrl.sanitize("pas une adresse"))
    }

    @Test
    fun `tout ce qui sort du domaine est refusé`() {
        assertNull(AchievementImageUrl.sanitize("https://exemple.invalid/Badge/00001.png"))
        // Le piège classique : le domaine attendu en préfixe d'un autre.
        assertNull(AchievementImageUrl.sanitize("https://retroachievements.org.exemple.invalid/a.png"))
        // Et en suffixe, sans le point qui sépare le sous-domaine.
        assertNull(AchievementImageUrl.sanitize("https://notretroachievements.org/a.png"))
    }

    @Test
    fun `le transport en clair est refusé`() {
        assertNull(AchievementImageUrl.sanitize("http://media.retroachievements.org/Badge/00001.png"))
    }

    @Test
    fun `identifiants, port, paramètres et fragment sont refusés`() {
        assertNull(AchievementImageUrl.sanitize("https://user:secret@media.retroachievements.org/Badge/1.png"))
        assertNull(AchievementImageUrl.sanitize("https://media.retroachievements.org:8443/Badge/1.png"))
        assertNull(AchievementImageUrl.sanitize("https://media.retroachievements.org/Badge/1.png?jeton=abc"))
        assertNull(AchievementImageUrl.sanitize("https://media.retroachievements.org/Badge/1.png#abc"))
        // Le port explicite par défaut reste légitime.
        assertEquals("https://media.retroachievements.org:443/Badge/1.png",
            AchievementImageUrl.sanitize("https://media.retroachievements.org:443/Badge/1.png"))
    }

    @Test
    fun `la casse de l'hôte ne contourne pas le filtre`() {
        assertEquals("https://MEDIA.RetroAchievements.ORG/Badge/1.png",
            AchievementImageUrl.sanitize("https://MEDIA.RetroAchievements.ORG/Badge/1.png"))
        assertNull(AchievementImageUrl.sanitize("HTTP://media.retroachievements.org/Badge/1.png"))
    }

    @Test
    fun `une adresse démesurée est refusée avant d'être analysée`() {
        assertNull(AchievementImageUrl.sanitize("https://media.retroachievements.org/Badge/" + "a".repeat(600) + ".png"))
    }

    @Test
    fun `la clé de cache est une empreinte stable et sans séparateur`() {
        val key = AchievementImageUrl.cacheKey(badge)
        assertEquals(64, key.length)
        assertEquals(key, AchievementImageUrl.cacheKey(badge))
        assertEquals(true, key.all { it in '0'..'9' || it in 'a'..'f' })
        // Deux adresses voisines ne doivent pas partager un fichier de cache :
        // le badge verrouillé et le badge obtenu sont deux images distinctes.
        assertEquals(false, key == AchievementImageUrl.cacheKey(
            "https://media.retroachievements.org/Badge/00001_lock.png"))
    }
}
