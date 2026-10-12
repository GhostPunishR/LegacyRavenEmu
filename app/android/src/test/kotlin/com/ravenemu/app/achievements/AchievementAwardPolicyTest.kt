package com.ravenemu.app.achievements

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementAwardPolicyTest {
    private val url = "https://retroachievements.org/dorequest.php"
    private val award = "r=awardachievement&u=test&t=secret&a=7&h=0&m=hash"

    @Test fun `only sdk generated casual awards are eligible for durable retry`() {
        assertTrue(AchievementAwardPolicy.isSdkAward(url, award))
        assertFalse(AchievementAwardPolicy.isSdkAward(url, award.replace("h=0", "h=1")))
        assertFalse(AchievementAwardPolicy.isSdkAward(url, "r=login2&u=test"))
        assertFalse(AchievementAwardPolicy.isSdkAward("https://other.example/dorequest.php", award))
        assertFalse(AchievementAwardPolicy.isSdkAward("http://retroachievements.org/dorequest.php", award))
        assertFalse(AchievementAwardPolicy.isSdkAward(url, award + "&extra=" + "x".repeat(8192)))
    }

    @Test fun `unapproved Hardcore cannot start or restore state`() {
        assertFalse(HardcoreAccess.mayEnterHardcore(
            approved = true, freshGame = true, protectionsReady = true))
        assertFalse(HardcoreAccess.mayLoadState(hardcore = true))
        assertTrue(HardcoreAccess.mayLoadState(hardcore = false))
        assertFalse(HardcoreAccess.mayUseDiagnostics(hardcore = true))
    }
}
