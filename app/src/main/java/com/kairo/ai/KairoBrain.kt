package com.kairo.ai

/** Adaptive, provider-independent coordinator for KAIRO's local and cloud brains. */
class KairoBrain(
    private val localEngine: AIEngine,
    private val providers: List<AIProviderConfig>,
    private val preferLocal: Boolean,
    private val localReady: Boolean
) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String {
        val cloudEngine = providers.takeIf { it.isNotEmpty() }?.let(::FailoverAIEngine)
        val plannedMessage = KairoTaskPlanner.prepare(message)
        val complexity = KairoTaskPlanner.complexity(message)
        // Use cloud for demanding multi-step work; keep simple requests local when selected.
        val cloudFirst = cloudEngine != null && (!preferLocal || complexity >= 2)

        if (cloudFirst) {
            return try {
                cloudEngine.reply(plannedMessage, memoryContext)
            } catch (cloudError: Exception) {
                if (!localReady) throw cloudError
                runCatching { localEngine.reply(plannedMessage, memoryContext) }.getOrElse { throw cloudError }
            }
        }
        if (preferLocal && localReady) {
            return try {
                localEngine.reply(plannedMessage, memoryContext)
            } catch (localError: Exception) {
                if (cloudEngine == null) throw IllegalStateException(
                    "KAIRO's on-device brain couldn't answer: ${localError.message ?: "unknown error"}"
                )
                cloudEngine.reply(plannedMessage, memoryContext)
            }
        }
        if (cloudEngine != null) {
            return try {
                cloudEngine.reply(plannedMessage, memoryContext)
            } catch (cloudError: Exception) {
                if (!localReady) throw cloudError
                runCatching { localEngine.reply(plannedMessage, memoryContext) }.getOrElse { throw cloudError }
            }
        }
        return DemoAIEngine().reply(plannedMessage, memoryContext)
    }

    private fun estimateComplexity(message: String): Int {
        val text = message.lowercase()
        var score = 0
        if (listOf("step by step", "plan", "build", "create", "implement", "debug", "fix", "design")
                .any(text::contains)) score++
        if (listOf("compare", "analyse", "analyze", "research", "explain why", "architecture", "code", "algorithm")
                .any(text::contains)) score++
        if (text.length > 420 || text.count { it == '?' } >= 2) score++
        return score.coerceAtMost(3)
    }
}
