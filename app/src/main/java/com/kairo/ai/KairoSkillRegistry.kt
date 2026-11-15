package com.kairo.ai

enum class KairoSkillType { OPEN_APP, REMEMBER, FORGET, RECALL, CALCULATE, CURRENT_TIME, GENERAL }
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
    private val calculatePatterns = listOf(
        Regex("(?i)^(?:calculate|compute|solve)\\s+(.+)$"),
        Regex("(?i)^what is\\s+([0-9().+*/%\\-\\s]+)\\??$")
    )
    private val arithmeticOnly = Regex("^[0-9().+*/%\\-\\s]+$")

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
        calculatePatterns.firstNotNullOfOrNull { it.find(text) }?.let { match ->
            val expression = match.groupValues[1].trim().removeSuffix("?").trim()
            if (expression.isNotEmpty() && arithmeticOnly.matches(expression)) {
                return KairoSkillRequest(KairoSkillType.CALCULATE, expression)
            }
        }
        if (arithmeticOnly.matches(text) && text.any { it in "+-*/%" } && text.any(Char::isDigit)) {
            return KairoSkillRequest(KairoSkillType.CALCULATE, text)
        }
        if (lower in setOf("what time is it", "current time", "time now", "what's the time",
                "today's date", "what is today's date", "what date is it", "today date",
                "what day is it", "date and time")) {
            return KairoSkillRequest(KairoSkillType.CURRENT_TIME)
        }
        return KairoSkillRequest(KairoSkillType.GENERAL)
    }
}
