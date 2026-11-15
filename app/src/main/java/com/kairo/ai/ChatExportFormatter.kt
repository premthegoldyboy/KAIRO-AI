package com.kairo.ai

/** Formats an explicit user-requested export; it does not write or share files itself. */
object ChatExportFormatter {
    fun format(entries: List<ConversationEntry>): String = buildString {
        append("KAIRO conversation export\n")
        append("Messages: ")
        append(entries.size)
        append("\n")
        append("This file may contain private information. Store and share it carefully.\n\n")
        entries.forEachIndexed { index, entry ->
            append(if (entry.fromKairo) "KAIRO" else "YOU")
            append("\n")
            append(entry.text)
            if (index != entries.lastIndex) append("\n\n")
        }
        if (entries.isNotEmpty()) append("\n")
    }
}
