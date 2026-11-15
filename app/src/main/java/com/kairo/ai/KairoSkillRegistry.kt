package com.kairo.ai

enum class KairoSkillType { OPEN_APP, REMEMBER, FORGET, RECALL, GENERAL }
data class KairoSkillRequest(val type: KairoSkillType, val argument: String = "")

/** Deterministic intent routing for supported commands in English and Telugu-English. */
object KairoSkillRegistry {
    private val openAppPattern = Regex("(?i)^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$")
    private val rememberPatterns = listOf(
        Regex("(?i)^(?:kairo[, ]*)?remember(?: that)?\\s+(.+)$"),
        Regex("(?i)^(?:please\\s+)?(?:gurthupettuko|gurtupettuko|gurthu pettuko)\\s*[:,]?\\s*(.+)$"),
        Regex("(?i)^idi\\s+(?:gurthupettuko|gurtupettuko)\\s*[:,]?\\s*(.+)$")
    )
    private val forgetPatterns = listOf(
        Regex("(?i)^(?:kairo[, ]*)?forget\\s+(.+)$"),
        Regex("(?i)^(?:please\\s+)?(?:marchipo|marichipo|marchipoo)\\s+(.+)$")
    )

    fun detect(message: String): KairoSkillRequest {
        val text = message.trim()
        if (text.isEmpty()) return KairoSkillRequest(KairoSkillType.GENERAL)
        openAppPattern.find(text)?.let {
            return KairoSkillRequest(KairoSkillType.OPEN_APP, it.groupValues[1].trim())
        }
        rememberPatterns.firstNotNullOfOrNull { it.find(text) }?.let {
            return KairoSkillRequest(KairoSkillType.REMEMBER, it.groupValues[1].trim())
        }
        val lower = text.lowercase()
        if (lower in setOf("forget all memories", "clear all memories", "delete all memories",
                "anni memories marchipo", "na memories anni marchipo")) {
            return KairoSkillRequest(KairoSkillType.FORGET, "*")
        }
        forgetPatterns.firstNotNullOfOrNull { it.find(text) }?.let {
            return KairoSkillRequest(KairoSkillType.FORGET, it.groupValues[1].trim())
        }
        if (lower.contains("what do you remember") ||
            lower in setOf("show memories", "list memories", "na gurinchi em gurthundi",
                "naaku em gurthundi", "em gurthupettukunnav")) {
            return KairoSkillRequest(KairoSkillType.RECALL)
        }
        return KairoSkillRequest(KairoSkillType.GENERAL)
    }
}
