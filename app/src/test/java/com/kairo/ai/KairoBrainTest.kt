package com.kairo.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoBrainTest {
    private class FakeEngine(private val answer: String) : AIEngine {
        var calls = 0
        override suspend fun reply(message: String, memoryContext: String): String {
            calls++
            return answer
        }
    }

    @Test fun usesSelectedLocalEngine() = runBlocking {
        val local = FakeEngine("real local response")
        val brain = KairoBrain(local, emptyList(), preferLocal = true, localReady = true)

        assertEquals("real local response", brain.reply("hello", ""))
        assertEquals(1, local.calls)
    }

    @Test fun usesAvailableLocalEngineWhenNoCloudProviderExists() = runBlocking {
        val local = FakeEngine("local only response")
        val brain = KairoBrain(local, emptyList(), preferLocal = false, localReady = true)

        assertEquals("local only response", brain.reply("hello", ""))
        assertEquals(1, local.calls)
    }

    @Test fun refusesToInventDemoAnswerWhenNoEngineIsConfigured() = runBlocking {
        val local = FakeEngine("should not run")
        val brain = KairoBrain(local, emptyList(), preferLocal = false, localReady = false)

        val error = runCatching { brain.reply("hello", "") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("No AI engine is configured"))
        assertEquals(0, local.calls)
    }
    @Test fun selectedLocalModeNeverCallsConfiguredCloudProvider() = runBlocking {
        val local = FakeEngine("private local answer")
        val deliberatelyInvalidCloud = listOf(
            AIProviderConfig("http://must-not-be-contacted.invalid", "test-model", "test-key")
        )
        val brain = KairoBrain(local, deliberatelyInvalidCloud, preferLocal = true, localReady = true)

        assertEquals("private local answer", brain.reply("build a complex multi-step coding agent", ""))
        assertEquals(1, local.calls)
    }

    @Test fun selectedLocalFailureNeverFallsBackToCloud() = runBlocking {
        val local = FakeEngine("unused")
        val failingLocal = object : AIEngine {
            override suspend fun reply(message: String, memoryContext: String): String {
                throw IllegalStateException("local runtime unavailable")
            }
        }
        val deliberatelyInvalidCloud = listOf(
            AIProviderConfig("http://must-not-be-contacted.invalid", "test-model", "test-key")
        )
        val brain = KairoBrain(failingLocal, deliberatelyInvalidCloud, preferLocal = true, localReady = true)

        val error = runCatching { brain.reply("research and build a plan", "") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("not sent to a cloud provider"))
        assertEquals(0, local.calls)
    }

}
