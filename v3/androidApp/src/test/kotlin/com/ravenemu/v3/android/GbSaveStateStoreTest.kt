package com.ravenemu.v3.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class GbSaveStateStoreTest {
    private val store = GbSaveStateStore(File("test-states"))

    @Test
    fun sameRomUsesSameSlotAndDifferentRomsStaySeparate() {
        val first = GbBatteryStore.keyForRom(byteArrayOf(1, 2, 3))
        val other = GbBatteryStore.keyForRom(byteArrayOf(1, 2, 4))
        assertEquals(store.fileFor(first), store.fileFor(first))
        assertNotEquals(store.fileFor(first), store.fileFor(other))
        assertEquals("$first.slot0.state", store.fileFor(first).name)
    }

    @Test
    fun invalidRomKeysCannotEscapeSaveDirectory() {
        assertThrows(IllegalArgumentException::class.java) {
            store.fileFor("../escape")
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.fileFor("a".repeat(63) + "/")
        }
    }
}
