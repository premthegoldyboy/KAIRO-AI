package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoRoutingTest {
    @Test fun romanizedTeluguIsDetected() = assertEquals(LangStyle.TELUGU_ENGLISH, KairoLanguage.detect("Nuvvu ela unnav ippudu?"))
    @Test fun plainEnglishStaysEnglish() = assertEquals(LangStyle.ENGLISH, KairoLanguage.detect("How are you doing today"))
    @Test fun teluguScriptIsDetected() = assertEquals(LangStyle.TELUGU_SCRIPT, KairoLanguage.detect("నమస్కారం ఎలా ఉన్నారు"))

    @Test fun codingIsCloudWork() {
        val p = KairoTaskPlanner.classify("fix this kotlin crash")
        assertEquals(TaskType.CODING, p.type)
        assertTrue(p.preferCloud)
    }
    @Test fun prefixIsNotTreatedAsFix() {
        val p = KairoTaskPlanner.classify("what is a prefix tree")
        assertEquals(TaskType.SIMPLE, p.type)
        assertFalse(p.preferCloud)
    }
    @Test fun newsNeedsLiveData() {
        val p = KairoTaskPlanner.classify("latest AI news today")
        assertEquals(TaskType.NEWS, p.type)
        assertTrue(p.needsLiveData)
    }
    @Test fun translationUsesCloud() = assertTrue(KairoTaskPlanner.classify("translate good morning to telugu").preferCloud)
    @Test fun wordProblemIsMath() = assertEquals(TaskType.MATH, KairoTaskPlanner.classify("solve 2x+3=7").type)
    @Test fun planningIsDetected() = assertEquals(TaskType.PLANNING, KairoTaskPlanner.classify("plan my week").type)
    @Test fun requirementsRequestsUseDedicatedAgentRoute() {
        val profile = KairoTaskPlanner.classify("write requirements and acceptance criteria for a study app")
        assertEquals(TaskType.REQUIREMENTS, profile.type)
        assertTrue(profile.preferCloud)
    }
    @Test fun requirementsAgentAddsStructuredAndTestableInstructions() {
        val prompt = KairoRequirementsAgent.enhance("Build a study planner")
        assertTrue(prompt.contains("FR-01"))
        assertTrue(prompt.contains("Given / When / Then"))
        assertTrue(prompt.contains("Build a study planner"))
        assertTrue(prompt.contains("single most important"))
    }
    @Test fun greetingStaysLocal() {
        val p = KairoTaskPlanner.classify("hello")
        assertEquals(TaskType.SIMPLE, p.type)
        assertEquals(0, p.complexity)
        assertFalse(p.preferCloud)
    }
    @Test fun teluguEnglishPrefersCloudAndAddsStyleHint() {
        val p = KairoTaskPlanner.classify("ela unnav")
        assertTrue(p.preferCloud)
        assertTrue(KairoTaskPlanner.prepare("ela unnav", p).contains("Telugu"))
    }
    @Test fun liveQuestionWarnsModelItHasNoBrowsing() = assertTrue(KairoTaskPlanner.prepare("latest AI news today").contains("cannot browse"))
    @Test fun simpleChatIsNotWrapped() = assertEquals("hello", KairoTaskPlanner.prepare("hello"))

    @Test fun timeQuestionWithQuestionMark() = assertEquals(KairoSkillType.CURRENT_TIME, KairoSkillRegistry.detect("What time is it?").type)
    @Test fun teluguTimeQuestion() = assertEquals(KairoSkillType.CURRENT_TIME, KairoSkillRegistry.detect("ippudu time enti?").type)
    @Test fun forgetItDoesNotWipeMemories() = assertEquals(KairoSkillType.GENERAL, KairoSkillRegistry.detect("forget it").type)
    @Test fun forgetSpecificMemory() {
        val r = KairoSkillRegistry.detect("forget my old address")
        assertEquals(KairoSkillType.FORGET, r.type)
        assertEquals("my old address", r.argument)
    }
    @Test fun questionStartingWithOpenIsNotAppLaunch() =
        assertEquals(KairoSkillType.GENERAL, KairoSkillRegistry.detect("open source licenses, what do they mean?").type)
    @Test fun englishOpenApp() {
        val r = KairoSkillRegistry.detect("open whatsapp")
        assertEquals(KairoSkillType.OPEN_APP, r.type)
        assertEquals("whatsapp", r.argument)
    }
    @Test fun teluguOpenApp() {
        val r = KairoSkillRegistry.detect("WhatsApp open chey")
        assertEquals(KairoSkillType.OPEN_APP, r.type)
        assertEquals("WhatsApp", r.argument)
    }
    @Test fun calculatorStillWorks() {
        val r = KairoSkillRegistry.detect("What is 2+3?")
        assertEquals(KairoSkillType.CALCULATE, r.type)
        assertEquals("2+3", r.argument)
    }
}


class KairoBrainConfigurationTest {
    private class NeverUsedLocalEngine : AIEngine {
        override suspend fun reply(message: String, memoryContext: String): String =
            throw AssertionError("Local engine must not be called when it is not ready")
    }

    @Test fun missingProvidersDoesNotInventOrEchoAnAnswer() {
        val brain = KairoBrain(NeverUsedLocalEngine(), emptyList(), false, false)
        kotlinx.coroutines.runBlocking {
            try {
                brain.reply("Hello KAIRO", "")
                throw AssertionError("Expected missing-brain configuration error")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("no active AI brain", ignoreCase = true))
                assertFalse(error.message.orEmpty().contains("Demo mode"))
            }
        }
    }
}
