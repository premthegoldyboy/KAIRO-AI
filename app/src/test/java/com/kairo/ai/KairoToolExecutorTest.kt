package com.kairo.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KairoToolExecutorTest {
    private fun calculate(expression: String): String =
        KairoToolExecutor.execute(KairoSkillRequest(KairoSkillType.CALCULATE, expression)).orEmpty()

    @Test fun decimalArithmeticAvoidsBinaryFloatingPointArtifacts() {
        assertEquals("The answer is 0.3.", calculate("0.1+0.2"))
    }

    @Test fun parenthesesAndOperatorPrecedenceWork() {
        assertEquals("The answer is 14.", calculate("2*(3+4)"))
    }

    @Test fun divisionByZeroIsHandledSafely() {
        assertTrue(calculate("5/0").contains("division by zero"))
    }

    @Test fun percentageOfIsRecognized() {
        val request = KairoSkillRegistry.detect("What is 25% of 200?")
        assertEquals(KairoSkillType.CALCULATE, request.type)
        assertEquals("The answer is 50.", calculate(request.argument))
    }

    @Test fun malformedInputReturnsHelpfulError() {
        assertTrue(calculate("2+*3").startsWith("I couldn't calculate that:"))
    }
    @Test fun commonLengthConversionWorksOffline() {
        val request = KairoSkillRegistry.detect("convert 5 km to miles")
        assertEquals(KairoSkillType.CONVERT_UNITS, request.type)
        assertEquals("5 km = 3.106856 mi", KairoToolExecutor.execute(request))
    }

    @Test fun temperatureConversionUsesCorrectOffset() {
        val result = KairoUnitConverter.convert("32", "F", "C")
        assertEquals("32 F = 0 °C", result)
    }

    @Test fun incompatibleDimensionsAreRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            KairoUnitConverter.convert("5", "km", "kg")
        }
    }

    @Test fun belowAbsoluteZeroIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            KairoUnitConverter.convert("-300", "C", "K")
        }
    }

    @Test fun unsupportedUnitsAreRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            KairoUnitConverter.convert("5", "parsec", "km")
        }
    }

}
