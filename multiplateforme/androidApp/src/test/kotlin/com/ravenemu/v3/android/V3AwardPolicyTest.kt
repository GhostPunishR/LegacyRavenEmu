package com.ravenemu.v3.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Offline unlock journal rules, ported from V2 with the V2 test cases. */
class V3AwardPolicyTest {
    private val url = "https://retroachievements.org/dorequest.php"
    private val award = "r=awardachievement&u=test&t=secret&a=7&h=0&m=hash"

    @Test fun onlySdkAwardsSentToRetroAchievementsAreJournaled() {
        assertTrue(V3AwardPolicy.isSdkAward(url, award))
        assertFalse(V3AwardPolicy.isSdkAward(url, "r=login2&u=test"))
        assertFalse(V3AwardPolicy.isSdkAward("https://other.example/dorequest.php", award))
        assertFalse(V3AwardPolicy.isSdkAward("http://retroachievements.org/dorequest.php", award))
        assertFalse(V3AwardPolicy.isSdkAward(url, award + "&extra=" + "x".repeat(8192)))
    }

    @Test fun hardcoreAwardsWaitForRetroAchievementsApproval() {
        val hardcore = award.replace("h=0", "h=1")
        assertFalse(V3AwardPolicy.isSdkAward(url, hardcore, allowHardcore = false))
        // Once approved, the same journal also keeps Hardcore unlocks.
        assertTrue(V3AwardPolicy.isSdkAward(url, hardcore, allowHardcore = true))
    }

    @Test fun replayRefreshesOnlyTheTokenForTheSameAccount() {
        val post = "r=awardachievement&u=Player+One&t=revoked&a=7&h=0&m=hash&v=sdk-signature&o=5"
        val refreshed = V3AwardPolicy.authenticatedReplay(url, post, "player one", "new+& token")
        assertEquals(post.replace("t=revoked", "t=new%2B%26+token"), refreshed)
        assertNull(V3AwardPolicy.authenticatedReplay(url, post, "someone else", "new"))
        assertNull(V3AwardPolicy.authenticatedReplay(url, post + "&t=second", "Player One", "new"))
        assertNull(V3AwardPolicy.authenticatedReplay(url, post.replace("h=0", "h=1"), "Player One", "new"))
    }

    @Test fun signOutRevokesLateWritesButAllowsANewSession() {
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

    @Test fun clientsOfTheSameFileSerializeTheirTransactions() {
        val first = AwardJournalGuard.forFile("v3-transaction-test")
        val second = AwardJournalGuard.forFile("v3-transaction-test")
        assertSame(first, second)
        val leases = listOf(first.open(), second.open())
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        var value = 0
        try {
            val futures = leases.map { lease ->
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

    @Test fun failedAwardsRetryWithBackoffAndSentOnesAreNotRepeated() {
        val replay = AwardReplay(listOf("a", "b"))
        val attempts = mutableListOf<String>()
        assertEquals(5000L, replay.attempt({ true }) { attempts += it; it == "a" })
        assertEquals(10000L, replay.attempt({ true }) { attempts += it; false })
        assertNull(replay.attempt({ true }) { attempts += it; true })
        assertEquals(listOf("a", "b", "b", "b"), attempts)
        assertNull(replay.attempt({ true }) { fail("already acknowledged"); false })
    }

    @Test fun revokedReplayStopsAndTheDelayStaysBounded() {
        val replay = AwardReplay(listOf("a"))
        repeat(12) { assertTrue(replay.attempt({ true }) { false }!! <= 120000L) }
        assertNull(replay.attempt({ false }) { fail("signed out"); false })
    }
}
