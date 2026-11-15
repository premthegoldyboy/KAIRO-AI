package com.kairo.ai

/**
 * Provider boundary for KAIRO's intelligence layer.
 * The first milestone uses a local demo implementation; cloud and offline
 * providers can be added without coupling the UI to a specific vendor.
 */
interface AIEngine {
    fun reply(message: String): String
}

class DemoAIEngine : AIEngine {
    override fun reply(message: String): String {
        val text = message.trim()
        if (text.isBlank()) return "I'm listening. What would you like to do?"
        return when {
            text.contains("hello", ignoreCase = true) ||
                text.contains("hi", ignoreCase = true) ->
                "Hello! KAIRO systems are online. How can I help?"
            text.contains("namaste", ignoreCase = true) ->
                "Namaste! Ela unnaru? I'm ready to help."
            else ->
                "I received: “$text”. My chat interface is online. A real AI provider will be connected in the next development phase."
        }
    }
}
