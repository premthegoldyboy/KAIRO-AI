package com.kairo.ai

enum class KairoAgentId {
    GENERAL, DEVELOPER, RESEARCHER, PLANNER, ANALYST, TUTOR, TRANSLATOR, REQUIREMENTS
}

data class KairoAgentProfile(
    val id: KairoAgentId,
    val displayName: String,
    val instruction: String
)

/**
 * Registry of specialist roles used by KAIRO's coordinator.
 * These profiles run through the user's explicitly selected AI engine; they are not
 * separate hosted models or independent autonomous services.
 */
object KairoAgentRegistry {
    private val profiles = mapOf(
        KairoAgentId.GENERAL to KairoAgentProfile(
            KairoAgentId.GENERAL,
            "General Assistant",
            "Answer the user's request directly. Be accurate, concise, and transparent about uncertainty."
        ),
        KairoAgentId.DEVELOPER to KairoAgentProfile(
            KairoAgentId.DEVELOPER,
            "Software Developer",
            "Act as a careful software engineer. Understand the existing constraints, explain the root cause before proposing changes, prefer small maintainable solutions, and include concrete verification steps. Never claim code was run unless it was actually executed."
        ),
        KairoAgentId.RESEARCHER to KairoAgentProfile(
            KairoAgentId.RESEARCHER,
            "Research Analyst",
            "Separate verified evidence from inference. Use only research results and source material actually supplied in context; never invent citations, dates, statistics, or live facts. State when evidence is missing or outdated."
        ),
        KairoAgentId.PLANNER to KairoAgentProfile(
            KairoAgentId.PLANNER,
            "Planning Specialist",
            "Turn the goal into an ordered, realistic plan with dependencies, milestones, risks, and measurable completion criteria. Identify the next actionable step."
        ),
        KairoAgentId.ANALYST to KairoAgentProfile(
            KairoAgentId.ANALYST,
            "Analysis Specialist",
            "Check assumptions, work carefully through the evidence or calculation, compare relevant alternatives fairly, and show the reasoning needed to verify the conclusion."
        ),
        KairoAgentId.TUTOR to KairoAgentProfile(
            KairoAgentId.TUTOR,
            "Learning Tutor",
            "Teach the underlying idea in clear language, build from simple to complex, and use a small example. Help the learner understand rather than merely memorise."
        ),
        KairoAgentId.TRANSLATOR to KairoAgentProfile(
            KairoAgentId.TRANSLATOR,
            "Language Specialist",
            "Preserve the original meaning, tone, and register. Do not add facts. Keep names and technical terms accurate, and explain ambiguity only when it matters."
        ),
        KairoAgentId.REQUIREMENTS to KairoAgentProfile(
            KairoAgentId.REQUIREMENTS,
            "Requirements Engineer",
            "Produce testable requirements and acceptance criteria. Separate confirmed facts, assumptions, scope, dependencies, risks, and unresolved questions."
        )
    )

    fun resolve(profile: TaskProfile): KairoAgentProfile {
        val id = when (profile.type) {
            TaskType.CODING -> KairoAgentId.DEVELOPER
            TaskType.RESEARCH, TaskType.NEWS -> KairoAgentId.RESEARCHER
            TaskType.PLANNING -> KairoAgentId.PLANNER
            TaskType.MATH, TaskType.REASONING -> KairoAgentId.ANALYST
            TaskType.TRANSLATION -> KairoAgentId.TRANSLATOR
            TaskType.REQUIREMENTS -> KairoAgentId.REQUIREMENTS
            TaskType.SIMPLE -> if (profile.complexity > 0) KairoAgentId.TUTOR else KairoAgentId.GENERAL
            TaskType.MEMORY, TaskType.TOOL -> KairoAgentId.GENERAL
        }
        return profiles.getValue(id)
    }

    fun prepare(
        message: String,
        profile: TaskProfile = KairoTaskPlanner.classify(message),
        liveDataAvailable: Boolean = false
    ): String {
        val agent = resolve(profile)
        return "KAIRO specialist: ${agent.displayName}.\n${agent.instruction}\n\n" +
            KairoTaskPlanner.prepare(message, profile, liveDataAvailable)
    }
}
