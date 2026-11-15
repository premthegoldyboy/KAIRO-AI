package com.kairo.ai

import java.net.URI

/** Keeps live-search references validated and visible instead of relying on model-generated citations. */
object ResearchPresentation {
    fun safeHttpsUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        uri.toASCIIString()
    }.getOrNull()

    fun appendSources(answer: String, results: List<ResearchResult>): String {
        val valid = results.mapNotNull { result ->
            safeHttpsUrl(result.url)?.let { result.copy(url = it) }
        }
        if (valid.isEmpty()) return answer
        return buildString {
            append(answer.trimEnd())
            append("\n\nSources checked by KAIRO:\n")
            valid.forEachIndexed { index, result ->
                append("[")
                append(index + 1)
                append("] ")
                append(result.title.ifBlank { "Source" })
                append("\n")
                append(result.url)
                append("\n")
            }
        }
    }
}
