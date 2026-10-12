package com.ravenemu.app.achievements

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AchievementReliabilityTest {
    @Test fun `sign out revokes late writes and acknowledgements but allows a new session`() {
        val guard = AwardJournalGuard()
        val oldClient = guard.open()
        var journal = listOf("first", "second")
        val lateAcknowledgement = journal - "first"
        guard.clear { journal = emptyList() }
        oldClient.access(Unit) { journal = lateAcknowledgement }
        oldClient.access(Unit) { journal = journal + "late unlock" }
        assertTrue(journal.isEmpty())
        guard.open().access(Unit) { journal = journal + "new session" }
        assertEquals(listOf("new session"), journal)
    }

    @Test fun `instances for the same file serialize read modify write transactions`() {
        val first = AwardJournalGuard.forFile("transaction-test")
        val second = AwardJournalGuard.forFile("transaction-test")
        assertSame(first, second)
        val one = first.open()
        val two = second.open()
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        var value = 0
        try {
            val futures = listOf(one, two).map { lease ->
                workers.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(1000) { lease.access(Unit) { val read = value; Thread.yield(); value = read + 1 } }
                }
            }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(2000, value)
        } finally { workers.shutdownNow() }
    }

    @Test fun `replay refreshes only the token for the authenticated account`() {
        val url = "https://retroachievements.org/dorequest.php"
        val post = "r=awardachievement&u=Player+One&t=revoked&a=7&h=0&m=hash&v=sdk-signature&o=5"
        val refreshed = AchievementAwardPolicy.authenticatedReplay(url, post, "player one", "new+& token")
        assertEquals(post.replace("t=revoked", "t=new%2B%26+token"), refreshed)
        assertNull(AchievementAwardPolicy.authenticatedReplay(url, post, "someone else", "new"))
        assertNull(AchievementAwardPolicy.authenticatedReplay(url, post + "&t=second", "Player One", "new"))
        assertNull(AchievementAwardPolicy.authenticatedReplay(url, post.replace("h=0", "h=1"), "Player One", "new"))
    }

    @Test fun `failed inherited awards retry without login and successful awards are not repeated`() {
        val replay = AwardReplay(listOf("a", "b"))
        val attempts = mutableListOf<String>()
        assertEquals(5000L, replay.attempt({ true }) { attempts += it; it == "a" })
        assertEquals(10000L, replay.attempt({ true }) { attempts += it; false })
        assertNull(replay.attempt({ true }) { attempts += it; true })
        assertEquals(listOf("a", "b", "b", "b"), attempts)
        assertNull(replay.attempt({ true }) { fail("already acknowledged"); false })
    }

    @Test fun `revoked replay stops and retry delay stays bounded`() {
        val replay = AwardReplay(listOf("a"))
        repeat(12) { assertTrue(replay.attempt({ true }) { false }!! <= 120000L) }
        assertNull(replay.attempt({ false }) { fail("signed out"); false })
    }

    @Test fun `global progress hide clears switched progress without clearing challenges`() {
        val state = AchievementIndicatorState()
        state.update("challenge_show", "1", "Challenge", "")
        state.update("progress_show", "2", "Old progress", "1/10")
        state.update("progress_show", "3", "New progress", "2/10")
        assertEquals(listOf("Challenge", "New progress • 2/10"), state.labels)
        state.update("progress_hide", "", "", "")
        assertEquals(listOf("Challenge"), state.labels)
        state.update("challenge_hide", "1", "", "")
        assertTrue(state.labels.isEmpty())
    }
}
