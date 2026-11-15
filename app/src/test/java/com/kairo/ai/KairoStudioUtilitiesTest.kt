package com.kairo.ai

import com.kairo.ai.tools.KairoStudioUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KairoStudioUtilitiesTest {
    @Test fun convertsMetricAndImperialUnits() {
        assertEquals(1000.0, KairoStudioUtilities.convert(1.0, "km", "m"), 0.000001)
        assertEquals(1.0, KairoStudioUtilities.convert(1000.0, "g", "kg"), 0.000001)
        assertEquals(60.0, KairoStudioUtilities.convert(1.0, "min", "s"), 0.000001)
        assertEquals(1000.0, KairoStudioUtilities.convert(1.0, "kb", "b"), 0.000001)
    }

    @Test fun rejectsIncompatibleAndUnknownUnits() {
        assertThrows(IllegalArgumentException::class.java) {
            KairoStudioUtilities.convert(1.0, "km", "kg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            KairoStudioUtilities.convert(1.0, "parsec", "m")
        }
    }

    @Test fun detectsTeluguAndMixedText() {
        assertEquals(KairoStudioUtilities.Language.TELUGU, KairoStudioUtilities.detectLanguage("తెలుగు"))
        assertEquals(KairoStudioUtilities.Language.MIXED, KairoStudioUtilities.detectLanguage("Hello తెలుగు"))
    }

    @Test fun analyzesDocumentWithoutAi() {
        val metrics = KairoStudioUtilities.analyzeDocument("Hello world. తెలుగు వాక్యం!")
        assertEquals(5, metrics.words)
        assertEquals(2, metrics.sentences)
        assertEquals(KairoStudioUtilities.Language.MIXED, metrics.language)
    }
}
