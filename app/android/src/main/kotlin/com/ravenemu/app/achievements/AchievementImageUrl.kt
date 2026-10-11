package com.ravenemu.app.achievements

import java.net.URI

/**
 * Filtre des adresses d'images RetroAchievements.
 *
 * Les badges et les portraits arrivent par deux chemins : le SDK les compose
 * lui-même à partir d'un nom, ou le serveur les nomme dans sa réponse. Le
 * second chemin est une adresse venue du réseau, et c'est elle qui décide de
 * l'hôte que l'application ira contacter. Sans filtre, une réponse altérée
 * suffirait à faire émettre une requête ailleurs, portrait de compte à l'appui.
 *
 * Le filtre tient en quatre refus : tout ce qui n'est pas HTTPS, tout hôte
 * étranger à `retroachievements.org`, toute adresse portant un identifiant
 * (`user:mot-de-passe@`) et tout port inhabituel. Ni paramètre ni fragment ne
 * sont conservés : une image n'en a pas besoin, et les laisser passer ferait de
 * l'adresse un canal.
 *
 * Aucune dépendance Android : cette décision se vérifie sur la JVM seule.
 */
internal object AchievementImageUrl {
    private const val DOMAIN = "retroachievements.org"

    /** L'adresse telle qu'elle sera demandée, ou `null` si elle est refusée. */
    fun sanitize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.isAbsolute || uri.scheme?.lowercase() != "https") return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port != -1 && uri.port != 443) return null
        val host = uri.host?.lowercase() ?: return null
        if (host != DOMAIN && !host.endsWith(".$DOMAIN")) return null
        val path = uri.rawPath.orEmpty()
        if (!path.startsWith("/") || path.contains("..")) return null
        return uri.toASCIIString()
    }

    /**
     * Clé de cache : une empreinte de l'adresse, et non l'adresse elle-même.
     * Un nom de fichier dérivé d'une adresse se heurte aux séparateurs, aux
     * majuscules que certains systèmes ignorent et à la longueur maximale ; une
     * empreinte n'a aucun de ces défauts et ne peut pas sortir du dossier.
     */
    fun cacheKey(url: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private const val MAX_LENGTH = 512
}
