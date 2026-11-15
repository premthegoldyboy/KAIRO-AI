package com.kairo.ai

/** Adds a lightweight task-specific workflow before the model handles harder requests. */
object KairoTaskPlanner {
    enum class Kind { BUILD, DEBUG, PLAN, COMPARE, EXPLAIN, GENERAL }

    fun complexity(message: String): Int {
        val text = message.lowercase()
        var score = 0
        if (listOf("step by step", "plan", "build", "create", "implement", "debug", "fix", "design")
                .any(text::contains)) score++
        if (listOf("compare", "analyse", "analyze", "research", "explain why", "architecture", "code", "algorithm")
                .any(text::contains)) score++
        if (text.length > 420 || text.count { it == '?' } >= 2) score++
        return score.coerceAtMost(3)
    }

    fun prepare(message: String): String {
        val text = message.lowercase()
        val kind = when {
            listOf("debug", "fix", "error", "crash", "bug").any(text::contains) -> Kind.DEBUG
            listOf("build", "create", "implement", "code", "develop").any(text::contains) -> Kind.BUILD
            listOf("plan", "roadmap", "steps").any(text::contains) -> Kind.PLAN
            listOf("compare", "versus", " vs ").any(text::contains) -> Kind.COMPARE
            listOf("explain", "teach", "how does", "what is").any(text::contains) -> Kind.EXPLAIN
            else -> Kind.GENERAL
        }
        val approach = when (kind) {
            Kind.BUILD -> "Break the implementation into ordered steps. State assumptions, give a practical solution, and include how to verify it."
            Kind.DEBUG -> "Diagnose the likely cause first. Separate confirmed facts from hypotheses, then give the smallest safe fix and a verification step."
            Kind.PLAN -> "Create a realistic ordered plan. Identify dependencies, milestones, and the next concrete action."
            Kind.COMPARE -> "Compare the options against the same criteria, explain trade-offs, and give a reasoned conclusion."
            Kind.EXPLAIN -> "Start with a plain-language explanation, then give a small example and clarify important limitations."
            Kind.GENERAL -> return message
        }
        return "Task approach: $approach\n\nUser request: $message"
    }
}
