package com.kairo.ai

enum class KairoSkillType { OPEN_APP, REMEMBER, FORGET, RECALL, CALCULATE, CONVERT_UNITS, CURRENT_TIME, GENERAL }
data class KairoSkillRequest(val type: KairoSkillType, val argument: String = "")

/** Deterministic routing for supported commands in English and Telugu-English. */
object KairoSkillRegistry {
    private val openAppPattern = Regex("(?i)^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$")
    private val openAppTeluguPattern = Regex("(?i)^(?:please\\s+)?(.+?)(?:\\s+(?:ni|ki))?\\s+(?:open|launch)\\s+(?:chey|cheyyi|cheyandi|cheyyandi|chei)[.!]*$")
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
    private val conversionPattern = Regex("(?i)^(?:convert\\s+)?([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*([a-z°]+)\\s+(?:to|in)\\s+([a-z°]+)[?!.]*$")
    private val percentOfPattern = Regex("(?i)^(?:what is\\s+)?([0-9]+(?:\\.[0-9]+)?)\\s*%\\s+of\\s+([0-9]+(?:\\.[0-9]+)?)\\??$")
    private val vagueForgetTargets = setOf("it","that","this","about it","about that","nothing","everything","all","them","those","what i said","my last message")
    private val clearAllPhrases = setOf("forget all memories","clear all memories","delete all memories","anni memories marchipo","na memories anni marchipo")
    private val recallPhrases = setOf("show memories","list memories","na gurinchi em gurthundi","naaku em gurthundi","em gurthupettukunnav")
    private val timePhrases = setOf("what time is it","current time","time now","what's the time","whats the time","today's date","todays date",
        "what is today's date","what date is it","today date","what day is it","date and time","time entha","time enti",
        "ippudu time enti","ippudu time entha","ippudu enta time","ee roju date enti","ee roju date entha","today date enti","ee roju em date")

    private fun plausibleAppName(name: String) = name.isNotBlank() && '?' !in name && name.split(Regex("\\s+")).size <= 4

    fun detect(message: String): KairoSkillRequest {
        val text = message.trim()
        if (text.isEmpty()) return KairoSkillRequest(KairoSkillType.GENERAL)
        val lower = text.lowercase()
        val normalized = lower.trimEnd('?', '.', '!', ' ')

        openAppTeluguPattern.find(text)?.let {
            val name = it.groupValues[1].trim()
            if (plausibleAppName(name)) return KairoSkillRequest(KairoSkillType.OPEN_APP, name)
        }
        openAppPattern.find(text)?.let {
            val name = it.groupValues[1].trim().trimEnd('.', '!')
            if (plausibleAppName(name)) return KairoSkillRequest(KairoSkillType.OPEN_APP, name)
        }
        rememberPatterns.firstNotNullOfOrNull { it.find(text) }?.let {
            return KairoSkillRequest(KairoSkillType.REMEMBER, it.groupValues[1].trim())
        }
        if (normalized in clearAllPhrases) return KairoSkillRequest(KairoSkillType.FORGET, "*")
        forgetPatterns.firstNotNullOfOrNull { it.find(text) }?.let {
            val target = it.groupValues[1].trim().trimEnd('.', '!', '?')
            if (target.length >= 3 && target.lowercase() !in vagueForgetTargets) {
                return KairoSkillRequest(KairoSkillType.FORGET, target)
            }
            return KairoSkillRequest(KairoSkillType.GENERAL)
        }
        if (lower.contains("what do you remember") || normalized in recallPhrases) return KairoSkillRequest(KairoSkillType.RECALL)
        conversionPattern.matchEntire(text)?.let {
            return KairoSkillRequest(KairoSkillType.CONVERT_UNITS, "${it.groupValues[1]}|${it.groupValues[2]}|${it.groupValues[3]}")
        }
        percentOfPattern.matchEntire(text)?.let {
            val percent = it.groupValues[1]
            val amount = it.groupValues[2]
            return KairoSkillRequest(KairoSkillType.CALCULATE, "($percent/100)*$amount")
        }
        calculatePatterns.firstNotNullOfOrNull { it.find(text) }?.let {
            val expression = it.groupValues[1].trim().removeSuffix("?").trim()
            if (expression.isNotEmpty() && arithmeticOnly.matches(expression)) return KairoSkillRequest(KairoSkillType.CALCULATE, expression)
        }
        if (arithmeticOnly.matches(text) && text.any { it in "+-*/%" } && text.any(Char::isDigit)) {
            return KairoSkillRequest(KairoSkillType.CALCULATE, text)
        }
        if (normalized in timePhrases) return KairoSkillRequest(KairoSkillType.CURRENT_TIME)
        return KairoSkillRequest(KairoSkillType.GENERAL)
    }
}
