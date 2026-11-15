package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderRouterTest {
    private val alpha = AIProviderConfig("https://alpha.example/v1", "alpha-model", "key-a")
    private val beta = AIProviderConfig("https://beta.example/v1", "beta-model", "key-b")
    private val gamma = AIProviderConfig("https://gamma.example/v1", "gamma-model", "key-c")

    @Before fun resetRouter() = ProviderRouter.resetForTests()

    @Test fun rotatesTheFirstProviderBetweenRequests() {
        assertEquals(listOf(alpha, beta, gamma), ProviderRouter.order(listOf(alpha, beta, gamma), 1L))
        assertEquals(listOf(beta, gamma, alpha), ProviderRouter.order(listOf(alpha, beta, gamma), 1L))
        assertEquals(listOf(gamma, alpha, beta), ProviderRouter.order(listOf(alpha, beta, gamma), 1L))
    }

    @Test fun rateLimitedProviderIsSkippedDuringCooldown() {
        ProviderRouter.recordFailure(alpha, ProviderException("quota", 429), 10_000L)
        assertEquals(listOf(beta), ProviderRouter.order(listOf(alpha, beta), 10_001L))
    }

    @Test fun providerReturnsAfterCooldownExpires() {
        ProviderRouter.recordFailure(alpha, ProviderException("quota", 429), 10_000L)
        assertTrue(ProviderRouter.order(listOf(alpha, beta), 70_001L).contains(alpha))
    }

    @Test fun invalidCredentialsReceiveLongerCooldown() {
        ProviderRouter.recordFailure(alpha, ProviderException("unauthorized", 401), 10_000L)
        assertEquals(listOf(beta), ProviderRouter.order(listOf(alpha, beta), 200_000L))
        assertTrue(ProviderRouter.order(listOf(alpha, beta), 310_001L).contains(alpha))
    }

    @Test fun successfulProviderIsRemovedFromCooldown() {
        ProviderRouter.recordFailure(alpha, ProviderException("quota", 429), 10_000L)
        ProviderRouter.recordSuccess(alpha)
        assertTrue(ProviderRouter.order(listOf(alpha, beta), 10_001L).contains(alpha))
    }

    @Test fun allCoolingProvidersProbeOnlyEarliestRecovery() {
        ProviderRouter.recordFailure(alpha, ProviderException("server", 503), 10_000L)
        ProviderRouter.recordFailure(beta, ProviderException("quota", 429), 10_000L)
        assertEquals(listOf(alpha), ProviderRouter.order(listOf(alpha, beta), 10_001L))
    }
}
