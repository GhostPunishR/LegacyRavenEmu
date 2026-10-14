package com.ravenemu.v3.shared

/**
 * Persistent RetroAchievements status while playing: active challenges,
 * measured progress and leaderboard trackers. Unlike popups, each one stays
 * until the SDK sends its HIDE event. Same rules as V2's indicator state.
 */
class RetroIndicatorState {
    private val active = linkedMapOf<String, String>()

    /** Lines to show, oldest first. */
    val labels: List<String> get() = active.values.toList()

    /** True for the event kinds this state handles. */
    fun handles(kind: String): Boolean = kind.substringBeforeLast('_') in FAMILIES &&
        (kind.endsWith("_show") || kind.endsWith("_hide"))

    fun update(kind: String, id: String, title: String, detail: String) {
        if (!handles(kind)) return
        val family = kind.substringBeforeLast('_')
        // rcheevos has one progress indicator, which can switch achievements;
        // its timer's HIDE carries no achievement identifier.
        val key = if (family == "progress") family else "$family:$id"
        if (kind.endsWith("_hide")) active.remove(key)
        else active[key] = listOf(title, detail).filter { it.isNotBlank() }.joinToString(" • ")
    }

    fun clear() = active.clear()

    private companion object {
        val FAMILIES = setOf("challenge", "progress", "tracker")
    }
}

/** One-off RetroAchievements messages for the popup, or null for other kinds. */
object RetroNoticeText {
    fun of(kind: String, title: String, detail: String, extra: String): String? {
        val headline = when (kind) {
            "achievement" -> "Succès débloqué · $title ($extra pts)"
            "leaderboard_started" -> "Classement commencé · $title"
            "leaderboard_failed" -> "Classement annulé · $title"
            "leaderboard_submitted" -> "Classement envoyé · $title"
            "leaderboard_scoreboard" -> "Classement terminé · $title"
            "notice", "offline", "online" -> title
            else -> return null
        }
        // An unlock's description is long; the popup keeps it to the title.
        val body = if (kind == "achievement" || kind.startsWith("leaderboard_")) "" else detail
        return listOf(headline, body).filter { it.isNotBlank() }.joinToString("\n")
    }
}
