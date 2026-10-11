package com.ravenemu.app.achievements

import java.text.NumberFormat

/**
 * Le profil du joueur, tel que l'API client du SDK le donne.
 *
 * Ce que RetroAchievements montre sur son site et que l'on ne retrouvera pas
 * ici : le **rang**, la **date d'inscription** et la **dernière activité**. Ces
 * trois valeurs ne sont pas dans l'API client ; elles ne s'obtiennent que par
 * l'API Web, qui demande une clé de développeur distincte du compte joueur.
 * RavenEmu ne s'en sert pas. Elles sont donc absentes de la carte plutôt
 * qu'approchées, et le bouton qui ouvre le site mène à la page qui les porte.
 *
 * Aucune dépendance Android : la lecture se vérifie sur la JVM seule.
 */
internal data class AchievementProfile(
    val displayName: String,
    val username: String,
    val softcorePoints: Int,
    val hardcorePoints: Int,
    val avatarUrl: String?,
) {
    /** Première lettre affichée tant que le portrait n'est pas arrivé. */
    val initial: String get() = displayName.firstOrNull()?.uppercase() ?: "?"

    fun formattedSoftcorePoints(): String = POINTS.format(softcorePoints.toLong())

    fun formattedHardcorePoints(): String = POINTS.format(hardcorePoints.toLong())

    internal companion object {
        /** Les points se comptent par milliers : les séparer les rend lisibles. */
        private val POINTS: NumberFormat = NumberFormat.getIntegerInstance()

        /**
         * Lit les colonnes de `summary`. Le tableau vient du pont natif : il
         * peut être court quand la session n'est pas encore ouverte, et ses
         * nombres sont du texte. Une lecture qui échoue rend `null` plutôt que
         * de propager une exception dans un dialogue.
         */
        fun fromSummary(summary: List<String>?, fallbackName: String = "", fallbackUser: String = ""): AchievementProfile? {
            if (summary == null || summary.size < 11) return null
            val username = summary[9].ifBlank { fallbackUser }
            val displayName = summary[0].ifBlank { username.ifBlank { fallbackName } }
            if (displayName.isBlank()) return null
            return AchievementProfile(
                displayName = displayName,
                username = username.ifBlank { displayName },
                softcorePoints = summary[1].toIntOrNull() ?: 0,
                hardcorePoints = summary[2].toIntOrNull() ?: 0,
                avatarUrl = AchievementImageUrl.sanitize(summary[10]),
            )
        }
    }
}
