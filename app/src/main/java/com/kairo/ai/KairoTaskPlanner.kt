package com.kairo.ai

enum class TaskType { SIMPLE, REASONING, MATH, CODING, NEWS, RESEARCH, TRANSLATION, PLANNING, REQUIREMENTS, MEMORY, TOOL }

data class TaskProfile(
    val type: TaskType,
    val complexity: Int,
    val needsLiveData: Boolean,
    val language: LangStyle
) {
    // Model choice is explicit in Settings. Task complexity or language must never force a cloud request.
}

/** Fast keyword-based task routing; live-data requests are explicitly marked as unverified. */
object KairoTaskPlanner {
    private fun words(vararg terms: String) = Regex(
        "\\b(?:" + terms.joinToString("|") { Regex.escape(it) } + ")\\b",
        RegexOption.IGNORE_CASE
    )
    private val codingWords = words("code","coding","kotlin","java","python","javascript","typescript","bug","bugs","debug",
        "compile","compiler","exception","stacktrace","stack trace","function","api","sql","regex","gradle",
        "jetpack compose","algorithm","crash","crashes","implement","refactor","script","github","android studio")
    private val debugWords = words("debug","fix","error","crash","crashes","bug","bugs","exception","stacktrace","stack trace","not working")
    private val mathWords = words("calculate","solve","equation","integral","derivative","probability","percentage","percent",
        "sqrt","square root","simplify","factorial","average","median","lekka","lekkinchu")
    private val mathExpression = Regex("\\d\\s*[+*^×÷=]\\s*\\d|\\d\\s+[-/%]\\s+\\d")
    private val translationWords = words("translate","translation","anuvadam","anuvadinchu","how do you say","meaning of","ardham enti","ardam enti")
    private val translationPhrase = Regex("(?i)\\b(?:telugu|english|hindi)\\s+(?:lo|ki)\\s+(?:cheppu|cheppandi|translate|rayi|raayi)\\b")
    private val researchWords = words("research","sources","fact check","fact-check","is it true","investigate","deep dive")
    private val newsWords = words("news","headlines","breaking","vartalu","trending","election","who won","score","scores")
    private val liveWords = words("latest","right now","this week","this month","price of","prices","stock price","weather",
        "release date","new version","currently","current price","who is the","ceo of","president of","prime minister of","who won","score","scores")
    private val planningWords = words("plan","planning","roadmap","itinerary","schedule","timeline","strategy","checklist")
    private val requirementsWords = words("requirement","requirements","acceptance criteria","user stories","product requirements","functional requirements","non-functional requirements","prd","specification","specifications")
    private val reasoningWords = words("analyze","analyse","compare","versus","vs","pros and cons","trade-off","trade-offs",
        "tradeoff","tradeoffs","explain why","why does","why do","why is","architecture","design","evaluate","difference between")
    private val explainWords = words("explain","teach","how does","how do")

    fun classify(message: String): TaskProfile {
        val text = message.trim()
        val language = KairoLanguage.detect(text)
        val skill = KairoSkillRegistry.detect(text).type
        val type = when (skill) {
            KairoSkillType.REMEMBER, KairoSkillType.FORGET, KairoSkillType.RECALL -> TaskType.MEMORY
            KairoSkillType.OPEN_APP, KairoSkillType.CALCULATE, KairoSkillType.CONVERT_UNITS, KairoSkillType.CURRENT_TIME -> TaskType.TOOL
            KairoSkillType.GENERAL -> when {
                requirementsWords.containsMatchIn(text) -> TaskType.REQUIREMENTS
                codingWords.containsMatchIn(text) || text.contains("```") -> TaskType.CODING
                mathWords.containsMatchIn(text) || mathExpression.containsMatchIn(text) -> TaskType.MATH
                translationWords.containsMatchIn(text) || translationPhrase.containsMatchIn(text) -> TaskType.TRANSLATION
                researchWords.containsMatchIn(text) -> TaskType.RESEARCH
                newsWords.containsMatchIn(text) -> TaskType.NEWS
                planningWords.containsMatchIn(text) -> TaskType.PLANNING
                reasoningWords.containsMatchIn(text) -> TaskType.REASONING
                else -> TaskType.SIMPLE
            }
        }
        val handledLocally = type == TaskType.MEMORY || type == TaskType.TOOL
        val needsLive = !handledLocally && (type == TaskType.NEWS || type == TaskType.RESEARCH || liveWords.containsMatchIn(text))
        val complexity = when (type) {
            TaskType.SIMPLE, TaskType.MEMORY, TaskType.TOOL -> if (explainWords.containsMatchIn(text)) 1 else 0
            TaskType.TRANSLATION -> 2
            TaskType.MATH, TaskType.CODING, TaskType.PLANNING, TaskType.REASONING, TaskType.NEWS, TaskType.REQUIREMENTS -> 2
            TaskType.RESEARCH -> 3
        } + if (text.length > 420 || text.count { it == '?' } >= 2) 1 else 0
        return TaskProfile(type, complexity.coerceAtMost(3), needsLive, language)
    }

    fun complexity(message: String): Int = classify(message).complexity

    /** Local mode is strictly offline: never send even a search query to a remote service. */
    fun shouldFetchLiveData(profile: TaskProfile, localMode: Boolean, hasSearchKey: Boolean): Boolean =
        !localMode && profile.needsLiveData && hasSearchKey

    fun prepare(message: String, profile: TaskProfile = classify(message), liveDataAvailable: Boolean = false): String {
        val lines = mutableListOf<String>()
        approach(profile, message)?.let { lines += "Task approach: $it" }
        if (profile.needsLiveData && !liveDataAvailable) {
            lines += "Live data: you cannot browse or fetch current information in this build. Answer only from general knowledge, say plainly that it may be outdated, never invent headlines, prices, dates, scores or sources, and suggest where the user can check."
        }
        replyStyle(profile.language)?.let { lines += "Reply style: $it" }
        if (lines.isEmpty()) return message
        return lines.joinToString("\n") + "\n\nUser request: $message"
    }

    private fun approach(profile: TaskProfile, message: String): String? = when (profile.type) {
        TaskType.CODING -> if (debugWords.containsMatchIn(message))
            "Diagnose the likely cause first. Separate confirmed facts from hypotheses, then give the smallest safe fix and a verification step."
        else "Break the implementation into ordered steps. State assumptions, give a practical solution, and include how to verify it."
        TaskType.PLANNING -> "Create a realistic ordered plan. Identify dependencies, milestones, and the next concrete action."
        TaskType.REQUIREMENTS -> "Create a structured, testable requirements brief. Separate confirmed facts, assumptions, scope, acceptance criteria, risks, and open questions. Ask at most one essential clarification question."
        TaskType.REASONING -> "Compare or analyse against clear criteria, explain trade-offs, and give a reasoned conclusion."
        TaskType.MATH -> "Work step by step, show the calculation, and double-check the final number before answering."
        TaskType.TRANSLATION -> "Translate faithfully, keep the tone, and note ambiguous words. If the user wrote Telugu in English letters, give the translation in English letters too."
        TaskType.RESEARCH, TaskType.NEWS -> "Separate verified facts from speculation, mention how current the information is, and state uncertainty honestly."
        TaskType.SIMPLE -> if (explainWords.containsMatchIn(message))
            "Start with a plain-language explanation, then give a small example and clarify important limitations." else null
        TaskType.MEMORY, TaskType.TOOL -> null
    }

    private fun replyStyle(language: LangStyle): String? = when (language) {
        LangStyle.TELUGU_ENGLISH -> "The user writes Telugu in English letters mixed with English. Reply in the same natural Telugu-English mix, not formal textbook Telugu."
        LangStyle.TELUGU_SCRIPT -> "The user writes in Telugu script. Reply in Telugu script unless asked otherwise."
        LangStyle.ENGLISH -> null
    }
}
