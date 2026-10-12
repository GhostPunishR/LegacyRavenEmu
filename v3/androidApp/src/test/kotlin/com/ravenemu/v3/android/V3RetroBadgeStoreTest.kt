package com.ravenemu.v3.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V3RetroBadgeStoreTest {
    @Test fun allowsOnlyRetroAchievementsHttpsImages() {
        val url = "https://media.retroachievements.org/Badge/123456.png"
        assertEquals(url, V3RetroBadgeStore.sanitize(url))
        assertEquals("https://retroachievements.org/avatar.png",
            V3RetroBadgeStore.sanitize("https://retroachievements.org/avatar.png"))
    }

    @Test fun blocksUntrustedUrlsAndCredentials() {
        for (url in listOf(
            "http://media.retroachievements.org/Badge/a.png",
            "https://retroachievements.org.evil.com/Badge/a.png",
            "https://evil.com/user/secret",
            "https://username:password@media.retroachievements.org/a.png",
            "https://media.retroachievements.org/a.png?token=secret",
            "https://media.retroachievements.org/a.png#credentials",
            "https://media.retroachievements.org:8443/a.png",
            "https://media.retroachievements.org/../../x.png",
        )) assertNull(V3RetroBadgeStore.sanitize(url))
    }
}
