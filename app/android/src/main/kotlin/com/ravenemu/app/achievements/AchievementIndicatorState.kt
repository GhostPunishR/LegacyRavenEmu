package com.ravenemu.app.achievements

internal class AchievementIndicatorState {
    private val active = linkedMapOf<String, String>()
    val labels: List<String> get() = active.values.toList()

    fun update(kind: String, id: String, title: String, detail: String) {
        val family = kind.substringBeforeLast('_')
        // rcheevos has one progress tracker, which can switch achievements.
        // Its timer's HIDE carries no achievement identifier.
        val key = if (family == "progress") family else "$family:$id"
        if (kind.endsWith("_hide")) active.remove(key)
        else active[key] = listOf(title, detail).filter { it.isNotBlank() }.joinToString(" • ")
    }

    fun clear() = active.clear()
}
