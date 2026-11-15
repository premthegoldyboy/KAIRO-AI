package com.kairo.ai

import java.net.URI

/** Normalizes common provider base URLs without duplicating API path segments. */
object ProviderEndpoints {
    fun chatCompletions(baseUrl: String, anthropic: Boolean): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://", ignoreCase = true)) { "Provider URL must use HTTPS." }
        val uri = URI(trimmed)
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&\n            uri.port in -1..65535 && uri.port != 0 && uri.host.length <= 253 && trimmed.length <= 2048) {
            "Enter a valid provider base URL without credentials, query parameters, or fragments."
        }
        val path = uri.path.orEmpty().trimEnd('/')
        val suffix = if (anthropic) "/v1/messages" else "/chat/completions"
        val normalizedPath = when {
            path.endsWith(suffix) -> path
            anthropic && path.endsWith("/v1") -> path + "/messages"
            else -> path + suffix
        }
        return URI(uri.scheme, null, uri.host, uri.port, normalizedPath, null, null).toASCIIString()
    }

    fun providerLabel(baseUrl: String): String = runCatching {
        URI(baseUrl.trim()).host?.removePrefix("www.") ?: "AI provider"
    }.getOrDefault("AI provider")
}
