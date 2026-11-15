package com.kairo.ai

/**
 * Selects a task specialist before model inference. These are role prompts executed
 * by the configured model, not separate hosted agents or extra API accounts.
 */
object KairoAgentRouter {
    enum class Role { DEVELOPER, RESEARCHER, PLANNER, ANALYST, TUTOR, GENERAL }

    data class Specialist(val role: Role, val instruction: String)

    fun route(message: String): Specialist {
        val text = message.lowercase()
        val role = when {
            listOf("debug", "compile", "kotlin", "android", "gradle", "code", "program", "implement", "bug", "crash")
                .any(text::contains) -> Role.DEVELOPER
            listOf("research", "find sources", "latest", "look up", "investigate", "github repo")
                .any(text::contains) -> Role.RESEARCHER
            listOf("roadmap", "schedule", "plan", "milestone", "organize")
                .any(text::contains) -> Role.PLANNER
            listOf("compare", "analyze", "analyse", "evaluate", "pros and cons", "trade-off")
                .any(text::contains) -> Role.ANALYST
            listOf("teach me", "explain", "homework", "study", "learn", "what is", "how does")
                .any(text::contains) -> Role.TUTOR
            else -> Role.GENERAL
        }
        val instruction = when (role) {
            Role.DEVELOPER ->
                "Act as KAIRO's software engineer. Inspect constraints, propose the smallest correct implementation, preserve existing architecture, and include tests and verification. Never claim code was run unless verified."
            Role.RESEARCHER ->
                "Act as KAIRO's research specialist. Separate known facts from uncertainty, prioritize evidence and dates, and never fabricate sources or claim live browsing when unavailable."
            Role.PLANNER ->
                "Act as KAIRO's project planner. Break the goal into parallelizable workstreams, identify dependencies and risks, and give the next executable actions."
            Role.ANALYST ->
                "Act as KAIRO's analyst. Compare options using explicit criteria, quantify trade-offs where possible, and state assumptions and a justified conclusion."
            Role.TUTOR ->
                "Act as KAIRO's tutor. Explain progressively in clear language, use a small example, and help the learner understand rather than merely memorizing."
            Role.GENERAL ->
                "Act as KAIRO's general assistant. Answer directly, accurately, and in the user's language style."
        }
        return Specialist(role, instruction)
    }
}
