package com.ravenemu.app.achievements

/** One transaction lock per journal file, including explicit sign-out. */
internal class AwardJournalGuard {
    private var generation = 0L

    @Synchronized fun open(): Lease = Lease(generation)

    @Synchronized fun clear(delete: () -> Unit) {
        ++generation // Revoke every old client, including its late HTTP callbacks.
        delete()
    }

    inner class Lease internal constructor(private val openedAt: Long) {
        fun <T> access(fallback: T, action: () -> T): T = synchronized(this@AwardJournalGuard) {
            if (openedAt != generation) fallback else action()
        }
    }

    companion object {
        private val guards = mutableMapOf<String, AwardJournalGuard>()
        @Synchronized fun forFile(path: String): AwardJournalGuard =
            guards.getOrPut(path) { AwardJournalGuard() }
    }
}
