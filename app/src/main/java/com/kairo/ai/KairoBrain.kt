package com.kairo.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Routes between local inference and configured cloud providers. */
class KairoBrain(
    private val localEngine: AIEngine,
    private val providers: List<AIProviderConfig>,
    private val preferLocal: Boolean,
    private val localReady: Boolean
) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String {
        val profile = KairoTaskPlanner.classify(message)
        val planned = KairoTaskPlanner.prepare(message, profile)
        val cloud = providers.takeIf { it.isNotEmpty() }?.let(::FailoverAIEngine)
        val localActive = preferLocal && localReady
        val cloudFirst = cloud != null && (!localActive || profile.preferCloud)

        if (cloudFirst) {
            try {
                return cloud!!.reply(planned, memoryContext)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cloudError: Exception) {
                if (!localReady) throw cloudError
                return try { localEngine.reply(planned, memoryContext) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { throw cloudError }
            }
        }
        if (localActive) {
            try {
                return localEngine.reply(planned, memoryContext)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (localError: Exception) {
                if (cloud == null) throw IllegalStateException(
                    "KAIRO's on-device brain couldn't answer: ${localError.message ?: "unknown error"}"
                )
                return cloud.reply(planned, memoryContext)
            }
        }
        return cloud?.reply(planned, memoryContext) ?: throw IllegalStateException("KAIRO has no active AI brain. Add a cloud provider in Settings or download and enable the on-device model.")
    }

    /**
     * Streams cloud tokens; local inference emits one complete chunk. Prior turns exclude the
     * current message. localContext can include recent turns for local models that lack chat APIs.
     */
    fun replyStream(
        message: String,
        history: List<ChatTurn>,
        memoryContext: String,
        localContext: String = memoryContext
    ): Flow<String> = flow {
        val profile = KairoTaskPlanner.classify(message)
        val planned = KairoTaskPlanner.prepare(message, profile)
        val turns = ChatTurn.normalize(history + ChatTurn("user", planned))
        val cloud = providers.takeIf { it.isNotEmpty() }?.let(::FailoverStreamingEngine)
        val localActive = preferLocal && localReady
        val cloudFirst = cloud != null && (!localActive || profile.preferCloud)

        suspend fun emitLocal() {
            val answer = localEngine.reply(planned, localContext)
            emit(answer)
        }

        if (cloudFirst) {
            var emitted = false
            try {
                cloud!!.stream(turns, memoryContext).collect { chunk ->
                    emitted = true
                    emit(chunk)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cloudError: Exception) {
                if (emitted || !localReady) throw cloudError
                emitLocal()
            }
        } else if (localActive) {
            try {
                emitLocal()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (localError: Exception) {
                if (cloud == null) throw IllegalStateException(
                    "KAIRO's on-device brain couldn't answer: ${localError.message ?: "unknown error"}"
                )
                cloud.stream(turns, memoryContext).collect { emit(it) }
            }
        } else if (cloud != null) {
            cloud.stream(turns, memoryContext).collect { emit(it) }
        } else {
            throw IllegalStateException("KAIRO has no active AI brain. Add a cloud provider in Settings or download and enable the on-device model.")
        }
    }
}
