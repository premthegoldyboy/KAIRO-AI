package com.kairo.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Routes to the explicitly selected engine. Selecting local AI is a privacy boundary:
 * no prompt is silently sent to a cloud provider, including after local inference fails.
 */
class KairoBrain(
    private val localEngine: AIEngine,
    private val providers: List<AIProviderConfig>,
    private val preferLocal: Boolean,
    private val localReady: Boolean
) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String {
        val profile = KairoTaskPlanner.classify(message)
        val planned = KairoTaskPlanner.prepare(message, profile)

        if (preferLocal) {
            check(localReady) {
                "Local AI is selected, but its model is not downloaded. Download the model in Settings or explicitly switch to cloud AI."
            }
            return try {
                localEngine.reply(planned, memoryContext)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Local AI failed. Your prompt was not sent to a cloud provider. " +
                        (error.message ?: "Try again or explicitly switch to cloud AI."),
                    error
                )
            }
        }

        val cloud = providers.takeIf { it.isNotEmpty() }?.let(::FailoverAIEngine)
        return cloud?.reply(planned, memoryContext)
            ?: throw IllegalStateException("KAIRO has no active AI brain. Select local AI or configure a cloud provider.")
    }

    /**
     * Streams from the explicitly selected engine. Local inference emits its collected
     * response as one chunk; cloud streaming remains available only when cloud is selected.
     */
    fun replyStream(
        message: String,
        history: List<ChatTurn>,
        memoryContext: String,
        localContext: String = memoryContext,
        liveDataAvailable: Boolean = false
    ): Flow<String> = flow {
        val profile = KairoTaskPlanner.classify(message)
        val planned = KairoTaskPlanner.prepare(message, profile, liveDataAvailable)

        if (preferLocal) {
            check(localReady) {
                "Local AI is selected, but its model is not downloaded. Download the model in Settings or explicitly switch to cloud AI."
            }
            val answer = try {
                localEngine.reply(planned, localContext)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Local AI failed. Your prompt was not sent to a cloud provider. " +
                        (error.message ?: "Try again or explicitly switch to cloud AI."),
                    error
                )
            }
            emit(answer)
            return@flow
        }

        val cloud = providers.takeIf { it.isNotEmpty() }?.let(::FailoverStreamingEngine)
            ?: throw IllegalStateException("No cloud AI provider is configured. Select local AI or add a provider in Settings.")
        val turns = ChatTurn.normalize(history + ChatTurn("user", planned))
        cloud.stream(turns, memoryContext).collect { emit(it) }
    }
}
