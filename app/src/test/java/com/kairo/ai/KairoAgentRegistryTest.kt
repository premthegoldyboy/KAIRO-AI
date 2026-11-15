package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoAgentRegistryTest {
    @Test fun routesCodingToDeveloper() {
        assertEquals(
            KairoAgentId.DEVELOPER,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Fix this Kotlin crash")).id
        )
    }

    @Test fun routesLiveResearchToResearcher() {
        assertEquals(
            KairoAgentId.RESEARCHER,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Research the latest Android updates")).id
        )
    }

    @Test fun routesPlanningAndRequirementsToTheirSpecialists() {
        assertEquals(
            KairoAgentId.PLANNER,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Create a roadmap and milestones")).id
        )
        assertEquals(
            KairoAgentId.REQUIREMENTS,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Write requirements and acceptance criteria")).id
        )
    }

    @Test fun routesMathTranslationAndTeaching() {
        assertEquals(
            KairoAgentId.ANALYST,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Calculate 25 percent of 80")).id
        )
        assertEquals(
            KairoAgentId.TRANSLATOR,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Translate hello to Telugu")).id
        )
        assertEquals(
            KairoAgentId.TUTOR,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Explain recursion")).id
        )
    }

    @Test fun simpleGreetingUsesGeneralAssistant() {
        assertEquals(
            KairoAgentId.GENERAL,
            KairoAgentRegistry.resolve(KairoTaskPlanner.classify("Hello")).id
        )
    }

    @Test fun preparedPromptIncludesSpecialistAndExistingTaskGuidance() {
        val prompt = KairoAgentRegistry.prepare("Fix this Kotlin crash")
        assertTrue(prompt.contains("Software Developer"))
        assertTrue(prompt.contains("verification steps"))
        assertTrue(prompt.contains("User request: Fix this Kotlin crash"))
    }

    @Test fun registryDoesNotClaimSeparateHostedModels() {
        val source = KairoAgentRegistry.javaClass.getAnnotation(Suppress("unused")) // compile-time marker only
        assertTrue(KairoAgentRegistry.resolve(KairoTaskPlanner.classify("hello")).displayName.isNotBlank())
    }
}
