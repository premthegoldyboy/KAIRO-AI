package com.kairo.ai

/**
 * KAIRO's provider-independent brain coordinator.
 *
 * The app talks to this class instead of directly choosing a model/provider.
 * KAIRO can change its underlying model without changing the chat UI or skills.
 */
class KairoBrain(
    private val localEngine: AIEngine,
    private val providers: List<AIProviderConfig>,
    private val preferLocal: Boolean,
    private val localReady: Boolean
) : AIEngine {

    override suspend fun reply(message: String, memoryContext: String): String {
        val cloudEngine = providers.takeIf { it.isNotEmpty() }?.let(::FailoverAIEngine)

        if (preferLocal && localReady) {
            try {
                return localEngine.reply(message, memoryContext)
            } catch (localError: Exception) {
                if (cloudEngine == null) {
                    throw IllegalStateException(
                        "KAIRO's on-device brain couldn't answer: ${localError.message ?: "unknown error"}"
                    )
                }
                // Keep the assistant available if the on-device model fails.
                return cloudEngine.reply(message, memoryContext)
            }
        }

        if (cloudEngine != null) {
            return try {
                cloudEngine.reply(message, memoryContext)
            } catch (cloudError: Exception) {
                if (!localReady) throw cloudError
                // A downloaded local model is the offline safety net.
                runCatching { localEngine.reply(message, memoryContext) }.getOrElse { throw cloudError }
            }
        }

        return DemoAIEngine().reply(message, memoryContext)
    }
}
