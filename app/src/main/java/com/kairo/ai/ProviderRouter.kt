package com.kairo.ai

/**
 * In-process provider scheduler inspired by gateway quota-aware routing.
 * It rotates healthy providers and applies temporary cooldowns after failures.
 * Provider limits remain controlled by the upstream service.
 */
internal object ProviderRouter {
    private val cooldownUntil = mutableMapOf<String, Long>()
    private var nextStartIndex = 0

    @Synchronized
    fun order(providers: List<AIProviderConfig>, nowMillis: Long = System.currentTimeMillis()): List<AIProviderConfig> {
        if (providers.isEmpty()) return emptyList()
        val start = nextStartIndex % providers.size
        nextStartIndex = (nextStartIndex + 1) % providers.size
        val rotated = providers.indices.map { providers[(start + it) % providers.size] }
        val ready = rotated.filter { (cooldownUntil[key(it)] ?: 0L) <= nowMillis }
        if (ready.isNotEmpty()) return ready
        // If every provider is cooling down, probe only the one that recovers first.
        return listOf(rotated.minBy { cooldownUntil[key(it)] ?: 0L })
    }

    @Synchronized
    fun recordFailure(provider: AIProviderConfig, error: Throwable, nowMillis: Long = System.currentTimeMillis()) {
        val status = (error as? ProviderException)?.status ?: 0
        val cooldown = when (status) {
            401, 403, 402 -> 5 * 60_000L
            404 -> 2 * 60_000L
            408, 429 -> 60_000L
            in 500..599 -> 30_000L
            else -> 20_000L
        }
        cooldownUntil[key(provider)] = nowMillis + cooldown
    }

    @Synchronized
    fun recordSuccess(provider: AIProviderConfig) {
        cooldownUntil.remove(key(provider))
    }

    @Synchronized
    fun resetForTests() {
        cooldownUntil.clear()
        nextStartIndex = 0
    }

    private fun key(provider: AIProviderConfig): String =
        provider.baseUrl.trim().lowercase() + "|" + provider.model.trim().lowercase()
}
