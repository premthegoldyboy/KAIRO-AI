package com.kairo.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FailoverAIEngineTest {
    private val alpha = AIProviderConfig("https://alpha.example/v1", "alpha-model", "key-a")
    private val beta = AIProviderConfig("https://beta.example/v1", "beta-model", "key-b")

    @Before fun resetRouter() = ProviderRouter.resetForTests()

    @Test fun fallsBackAfterRateLimitAndReturnsSecondProviderAnswer() = runBlocking {
        val calls = mutableListOf<String>()
        val engine = FailoverAIEngine(listOf(alpha, beta)) { provider ->
            object : AIEngine {
                override suspend fun reply(message: String, memoryContext: String): String {
                    calls += provider.model
                    if (provider == alpha) throw ProviderException("quota reached", 429)
                    return "answer from beta"
                }
            }
        }

        assertEquals("answer from beta", engine.reply("hello"))
        assertEquals(listOf("alpha-model", "beta-model"), calls)
    }

    @Test fun successfulProviderIsRememberedAndRotationContinues() = runBlocking {
        val calls = mutableListOf<String>()
        val engine = FailoverAIEngine(listOf(alpha, beta)) { provider ->
            object : AIEngine {
                override suspend fun reply(message: String, memoryContext: String): String {
                    calls += provider.model
                    return provider.model
                }
            }
        }

        assertEquals("alpha-model", engine.reply("first"))
        assertEquals("beta-model", engine.reply("second"))
        assertEquals(listOf("alpha-model", "beta-model"), calls)
    }

    @Test fun allFailuresAreReportedWithProviderContext() = runBlocking {
        val engine = FailoverAIEngine(listOf(alpha, beta)) { provider ->
            object : AIEngine {
                override suspend fun reply(message: String, memoryContext: String): String {
                    throw ProviderException("quota reached", 429)
                }
            }
        }

        val error = runCatching { engine.reply("hello") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("alpha-model"))
        assertTrue(error?.message.orEmpty().contains("beta-model"))
    }

    @Test fun coroutineCancellationIsNeverConvertedToProviderFailure() = runBlocking {
        val engine = FailoverAIEngine(listOf(alpha, beta)) {
            object : AIEngine {
                override suspend fun reply(message: String, memoryContext: String): String {
                    throw CancellationException("cancelled")
                }
            }
        }

        val error = runCatching { engine.reply("hello") }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
