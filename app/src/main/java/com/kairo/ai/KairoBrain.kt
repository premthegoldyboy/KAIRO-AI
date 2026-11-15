package com.kairo.ai

/** Adaptive, provider-independent coordinator for KAIRO's local and cloud brains. */
class KairoBrain(
    private val localEngine: AIEngine,
    private val providers: List<AIProviderConfig>,
    private val preferLocal: Boolean,
    private val localReady: Boolean
) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String {
        val specialist = KairoAgentRouter.route(message)
        val plannedMessage = specialist.instruction + "\n\n" + KairoTaskPlanner.prepare(message)

        // LOCAL is a strict privacy boundary: never silently send its prompt to a cloud provider.
        if (preferLocal) {
            check(localReady) {
                "Local AI is selected, but its model is not downloaded. Download it in Settings or explicitly switch to cloud AI."
            }
            return try {
                localEngine.reply(plannedMessage, memoryContext)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Local AI failed. Your prompt was not sent to a cloud provider. " +
                        (error.message ?: "Try again or explicitly switch to cloud AI."),
                    error
                )
            }
        }

        val cloudEngine = providers.takeIf { it.isNotEmpty() }?.let(::FailoverAIEngine)
            ?: throw IllegalStateException(
                "No AI engine is configured. Add a cloud provider in Settings or select and download the on-device model."
            )
        return cloudEngine.reply(plannedMessage, memoryContext)
    }

}
