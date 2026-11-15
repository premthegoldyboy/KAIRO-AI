package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderEndpointsTest {
    @Test fun deepSeekRootGetsChatEndpoint() = assertEquals(
        "https://api.deepseek.com/chat/completions",
        ProviderEndpoints.chatCompletions("https://api.deepseek.com", false)
    )

    @Test fun openAiCompatibleV1PathIsPreserved() = assertEquals(
        "https://api.x.ai/v1/chat/completions",
        ProviderEndpoints.chatCompletions("https://api.x.ai/v1/", false)
    )

    @Test fun fullEndpointIsNotDuplicated() = assertEquals(
        "https://api.deepseek.com/chat/completions",
        ProviderEndpoints.chatCompletions("https://api.deepseek.com/chat/completions", false)
    )

    @Test fun anthropicV1PathIsPreserved() = assertEquals(
        "https://api.anthropic.com/v1/messages",
        ProviderEndpoints.chatCompletions("https://api.anthropic.com/v1", true)
    )
}
