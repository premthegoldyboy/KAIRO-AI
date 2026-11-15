package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryErasePolicyTest {
    @Test fun blankForgetQueryDoesNotDeleteAnything() {
        val current = listOf("Name: Alex", "Preference: dark mode")
        assertEquals(current, memoriesAfterForget(current, "   "))
    }

    @Test fun forgetMatchingRemovesOnlyMatchingEntries() {
        val current = listOf("Name: Alex", "Preference: dark mode", "Preference: Telugu")
        assertEquals(
            listOf("Name: Alex", "Preference: Telugu"),
            memoriesAfterForget(current, "dark mode")
        )
    }

    @Test fun matchingIsCaseInsensitive() {
        assertEquals(
            listOf("Name: Alex"),
            memoriesAfterForget(listOf("Name: Alex", "Preference: DARK MODE"), "dark mode")
        )
    }
}
