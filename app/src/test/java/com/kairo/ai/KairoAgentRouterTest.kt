package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class KairoAgentRouterTest {
    @Test fun routesAndroidImplementationToDeveloper() {
        assertEquals(KairoAgentRouter.Role.DEVELOPER, KairoAgentRouter.route("Fix this Kotlin Android crash").role)
    }

    @Test fun routesResearchToResearcher() {
        assertEquals(KairoAgentRouter.Role.RESEARCHER, KairoAgentRouter.route("Research current GitHub repos").role)
    }

    @Test fun routesPlanningAndTeaching() {
        assertEquals(KairoAgentRouter.Role.PLANNER, KairoAgentRouter.route("Create a roadmap and milestones").role)
        assertEquals(KairoAgentRouter.Role.TUTOR, KairoAgentRouter.route("Teach me what is recursion").role)
    }
}
