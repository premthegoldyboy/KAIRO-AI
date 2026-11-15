package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.collect

class KairoRoutingTest {
    @Test fun providerRequestRejectsOversizedPromptAndContext() {
        try {
            validateProviderRequestSize("x".repeat(32_001), "")
            throw AssertionError("Expected oversized prompt to be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            validateProviderRequestSize("hello", "x".repeat(16_001))
            throw AssertionError("Expected oversized context to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun romanizedTeluguIsDetected() = assertEquals(LangStyle.TELUGU_ENGLISH, KairoLanguage.detect("Nuvvu ela unnav ippudu?"))
    @Test fun plainEnglishStaysEnglish() = assertEquals(LangStyle.ENGLISH, KairoLanguage.detect("How are you doing today"))
    @Test fun teluguScriptIsDetected() = assertEquals(LangStyle.TELUGU_SCRIPT, KairoLanguage.detect("నమస్కారం ఎలా ఉన్నారు"))

    @Test fun codingIsCloudWork() {
        val p = KairoTaskPlanner.classify("fix this kotlin crash")
        assertEquals(TaskType.CODING, p.type)
        assertFalse(p.needsLiveData)
    }
    @Test fun prefixIsNotTreatedAsFix() {
        val p = KairoTaskPlanner.classify("what is a prefix tree")
        assertEquals(TaskType.SIMPLE, p.type)
        assertFalse(p.needsLiveData)
    }
    @Test fun newsNeedsLiveData() {
        val p = KairoTaskPlanner.classify("latest AI news today")
        assertEquals(TaskType.NEWS, p.type)
        assertTrue(p.needsLiveData)
    }
    @Test fun translationDoesNotRequireLiveData() = assertFalse(KairoTaskPlanner.classify("translate good morning to telugu").needsLiveData)
    @Test fun wordProblemIsMath() = assertEquals(TaskType.MATH, KairoTaskPlanner.classify("solve 2x+3=7").type)
    @Test fun planningIsDetected() = assertEquals(TaskType.PLANNING, KairoTaskPlanner.classify("plan my week").type)
    @Test fun requirementsRequestsUseDedicatedAgentRoute() {
        val profile = KairoTaskPlanner.classify("write requirements and acceptance criteria for a study app")
        assertEquals(TaskType.REQUIREMENTS, profile.type)
        assertFalse(profile.needsLiveData)
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
        assertFalse(p.needsLiveData)
    }
    @Test fun teluguEnglishAddsStyleHintWithoutRequiringCloud() {
        val p = KairoTaskPlanner.classify("ela unnav")
        assertFalse(p.needsLiveData)
        assertTrue(KairoTaskPlanner.prepare("ela unnav", p).contains("Telugu"))
    }
    @Test fun liveQuestionWarnsModelItHasNoBrowsing() = assertTrue(KairoTaskPlanner.prepare("latest AI news today").contains("cannot browse"))
    @Test fun localModeNeverSendsLiveSearchRequests() {
        val profile = KairoTaskPlanner.classify("latest AI news today")
        assertFalse(KairoTaskPlanner.shouldFetchLiveData(profile, localMode = true, hasSearchKey = true))
    }
    @Test fun cloudModeCanUseLiveSearchWhenConfigured() {
        val profile = KairoTaskPlanner.classify("latest AI news today")
        assertTrue(KairoTaskPlanner.shouldFetchLiveData(profile, localMode = false, hasSearchKey = true))
    }
    @Test fun missingSearchKeyNeverStartsLiveSearch() {
        val profile = KairoTaskPlanner.classify("latest AI news today")
        assertFalse(KairoTaskPlanner.shouldFetchLiveData(profile, localMode = false, hasSearchKey = false))
    }
    @Test fun blankForgetQueryPreservesEveryMemory() {
        val saved = listOf("Name: Kai", "Preference: concise answers")
        assertEquals(saved, memoriesAfterForget(saved, "   "))
    }
    @Test fun forgetQueryRemovesOnlyMatchingMemories() {
        assertEquals(listOf("Preference: concise answers"),
            memoriesAfterForget(listOf("Name: Kai", "Preference: concise answers"), "name"))
    }

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


class ResearchPresentationTest {
    @Test fun rejectsNonHttpsAndCredentialUrls() {
        assertEquals(null, ResearchPresentation.safeHttpsUrl("http://example.com"))
        assertEquals(null, ResearchPresentation.safeHttpsUrl("https://user:pass@example.com/path"))
        assertEquals(null, ResearchPresentation.safeHttpsUrl("javascript:alert(1)"))
        assertEquals(null, ResearchPresentation.safeHttpsUrl("https://example.com:70000/path"))
        assertEquals(null, ResearchPresentation.safeHttpsUrl("https://" + "a".repeat(2_050) + ".com"))
    }

    @Test fun acceptsNormalHttpsSource() {
        assertEquals("https://example.com/news?id=2", ResearchPresentation.safeHttpsUrl("https://example.com/news?id=2"))
    }

    @Test fun sourceListIsAppendedDeterministically() {
        val answer = ResearchPresentation.appendSources(
            "A short answer.",
            listOf(ResearchResult("Example report", "https://example.com/report", "Summary"))
        )
        assertTrue(answer.startsWith("A short answer."))
        assertTrue(answer.contains("Sources checked by KAIRO"))
        assertTrue(answer.contains("[1] Example report"))
        assertTrue(answer.contains("https://example.com/report"))
    }

    @Test fun noSourcesDoesNotChangeAnswer() {
        assertEquals("Answer", ResearchPresentation.appendSources("Answer", emptyList()))
    }
}


class KairoBrainLocalFirstTest {
    private class FakeEngine(
        private val answer: String = "local answer",
        private val failure: Exception? = null
    ) : AIEngine {
        var calls = 0
        override suspend fun reply(message: String, memoryContext: String): String {
            calls++
            failure?.let { throw it }
            return answer
        }
    }

    private val deliberatelyInvalidCloud = listOf(
        AIProviderConfig("http://must-not-be-contacted.invalid", "test-model", "test-key")
    )

    @Test fun selectedLocalEngineHandlesComplexCodingWithoutCloudRouting() {
        val local = FakeEngine()
        val brain = KairoBrain(local, deliberatelyInvalidCloud, preferLocal = true, localReady = true)
        kotlinx.coroutines.runBlocking {
            assertEquals("local answer", brain.reply("fix this kotlin crash", ""))
        }
        assertEquals(1, local.calls)
    }

    @Test fun localFailureDoesNotSilentlyFallBackToCloud() {
        val local = FakeEngine(failure = IllegalStateException("local runtime unavailable"))
        val brain = KairoBrain(local, deliberatelyInvalidCloud, preferLocal = true, localReady = true)
        kotlinx.coroutines.runBlocking {
            try {
                brain.reply("write a research plan", "")
                throw AssertionError("Expected local inference failure")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("not sent to a cloud provider"))
                assertTrue(error.cause?.message.orEmpty().contains("local runtime unavailable"))
            }
        }
        assertEquals(1, local.calls)
    }

    @Test fun selectedLocalModeDoesNotUseCloudWhenModelIsMissing() {
        val local = FakeEngine()
        val brain = KairoBrain(local, deliberatelyInvalidCloud, preferLocal = true, localReady = false)
        kotlinx.coroutines.runBlocking {
            try {
                brain.reply("hello", "")
                throw AssertionError("Expected missing local model error")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("model is not downloaded"))
            }
        }
        assertEquals(0, local.calls)
    }

    @Test fun localStreamingAlsoStaysOfflineFromCloudProviders() {
        val local = FakeEngine()
        val brain = KairoBrain(local, deliberatelyInvalidCloud, preferLocal = true, localReady = true)
        kotlinx.coroutines.runBlocking {
            val chunks = mutableListOf<String>()
            brain.replyStream(
                message = "latest AI news today",
                history = emptyList(),
                memoryContext = "",
                localContext = "offline context",
                liveDataAvailable = true
            ).collect { chunks += it }
            assertEquals(listOf("local answer"), chunks)
        }
        assertEquals(1, local.calls)
    }
}
