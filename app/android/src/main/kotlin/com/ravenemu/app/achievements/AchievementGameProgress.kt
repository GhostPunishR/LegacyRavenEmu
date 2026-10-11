package com.ravenemu.app.achievements

import java.text.NumberFormat

/**
 * Avancement du joueur dans le jeu identifié.
 *
 * Le pont natif rend ces cinq valeurs en texte, au milieu des colonnes du
 * compte. Les lire ici plutôt qu'au moment de les afficher permet de décider
 * une fois pour toutes ce que veut dire « pas de jeu » : un titre vide, ce que
 * le SDK rend tant qu'il n'a rien reconnu.
 *
 * Aucune dépendance Android : la lecture se vérifie sur la JVM seule.
 */
internal data class AchievementGameProgress(
    val title: String,
    val unlocked: Int,
    val total: Int,
    val points: Int,
    val totalPoints: Int,
) {
    /**
     * Avancement en centièmes, pour la barre.
     *
     * Compté en points et non en succès : vingt petits succès obtenus sur un
     * jeu qui en garde cinq énormes ne valent pas quatre-vingts pour cent du
     * chemin. Un jeu dont les succès ne valent aucun point retombe sur leur
     * nombre, faute de mieux.
     */
    val percent: Int
        get() {
            val (done, all) = if (totalPoints > 0) points to totalPoints else unlocked to total
            if (all <= 0) return 0
            return (done.coerceIn(0, all) * 100) / all
        }

    /** `true` quand le jeu n'expose aucun succès : la barre n'a rien à montrer. */
    val empty: Boolean get() = total <= 0

    fun achievementsLabel(): String = "${count(unlocked)} / ${count(total)}"

    fun pointsLabel(): String = "${count(points)} / ${count(totalPoints)}"

    internal companion object {
        private val NUMBERS: NumberFormat = NumberFormat.getIntegerInstance()
        private fun count(value: Int) = NUMBERS.format(value.toLong())

        /**
         * Lit les colonnes 3 et 5 à 8. Rend `null` tant qu'aucun jeu n'est
         * reconnu, cas où le titre est vide et où les compteurs ne veulent
         * rien dire.
         */
        fun fromSummary(summary: List<String>?): AchievementGameProgress? {
            if (summary == null || summary.size < 9) return null
            val title = summary[3].trim()
            if (title.isEmpty()) return null
            return AchievementGameProgress(
                title = title,
                unlocked = summary[5].toIntOrNull() ?: 0,
                total = summary[6].toIntOrNull() ?: 0,
                points = summary[7].toIntOrNull() ?: 0,
                totalPoints = summary[8].toIntOrNull() ?: 0,
            )
        }
    }
}
