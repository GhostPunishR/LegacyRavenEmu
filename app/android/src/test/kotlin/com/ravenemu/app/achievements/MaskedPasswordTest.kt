package com.ravenemu.app.achievements

import org.junit.Test
import org.junit.Assert.assertEquals

class MaskedPasswordTest {
    @Test
    fun allCharactersStayHiddenWhileTypingAndDeleting() {
        val input = StringBuilder()
        val display = MaskedPassword(input)
        for (character in "aB9!é🔒") {
            input.append(character)
            assertEquals("•".repeat(input.length), display.toString())
            assertEquals('•', display[display.length - 1])
            assertEquals("•".repeat(input.length), display.subSequence(0, display.length))
        }
        input.delete(0, input.length)
        assertEquals(0, display.length)
        assertEquals("", display.toString())
    }
}
