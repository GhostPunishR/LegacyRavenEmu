package com.ravenemu.app.achievements

/** Only awards inherited from an earlier session; live awards remain owned by rcheevos. */
internal class AwardReplay<T>(entries: List<T>) {
    private val pending = entries.toMutableList()
    private var delay = 5_000L

    /** Called by one scheduled worker. A failed attempt remains pending with bounded backoff. */
    fun attempt(active: () -> Boolean, sendAndAcknowledge: (T) -> Boolean): Long? {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            if (!active()) return null
            if (sendAndAcknowledge(iterator.next())) iterator.remove()
        }
        if (pending.isEmpty() || !active()) return null
        return delay.also { delay = (delay * 2).coerceAtMost(120_000L) }
    }
}
