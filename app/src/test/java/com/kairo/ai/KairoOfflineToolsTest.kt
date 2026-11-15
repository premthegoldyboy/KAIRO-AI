package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoOfflineToolsTest {
    @Test
    fun detectsUnitConversionCommand() {
        val request = KairoSkillRegistry.detect("convert 2 km to m")
        assertEquals(KairoSkillType.CONVERT, request.type)
        assertEquals("2|km|m", request.argument)
    }

    @Test
    fun convertsUnitsWithoutNetworkOrAi() {
        val answer = KairoToolExecutor.execute(
            KairoSkillRegistry.detect("convert 2 km to m")
        )
        assertEquals("2.0 km = 2000.0 m", answer)
    }

    @Test
    fun analyzesDocumentOffline() {
        val answer = KairoToolExecutor.execute(
            KairoSkillRegistry.detect("analyze document: Hello world. తెలుగు వాక్యం!")
        )
        assertTrue(answer.orEmpty().contains("Words: 5"))
        assertTrue(answer.orEmpty().contains("Sentences: 2"))
        assertTrue(answer.orEmpty().contains("MIXED"))
    }

    @Test
    fun incompatibleUnitsReturnReadableError() {
        val answer = KairoToolExecutor.execute(
            KairoSkillRegistry.detect("convert 1 km to kg")
        )
        assertTrue(answer.orEmpty().contains("Incompatible unit dimensions"))
    }
}
