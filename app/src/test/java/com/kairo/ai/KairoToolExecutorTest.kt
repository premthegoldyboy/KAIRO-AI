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

    @Test fun exponentiationHasHigherPrecedenceThanMultiplication() {
        assertEquals("The answer is 512.", calculate("2^3^2"))
        assertEquals("The answer is 32.", calculate("2*2^3"))
    }

    @Test fun exponentiationSupportsNegativeExponentsAndUnaryMinus() {
        assertEquals("The answer is 0.25.", calculate("2^-2"))
        assertEquals("The answer is -4.", calculate("-2^2"))
    }

    @Test fun exponentiationIsRecognizedInNaturalLanguageAndBareExpressions() {
        val natural = KairoSkillRegistry.detect("what is 2^3?")
        assertEquals(KairoSkillType.CALCULATE, natural.type)
        assertEquals("The answer is 8.", calculate(natural.argument))
        val bare = KairoSkillRegistry.detect("2^3")
        assertEquals(KairoSkillType.CALCULATE, bare.type)
        assertEquals("The answer is 8.", calculate(bare.argument))
    }

    @Test fun exponentiationRejectsFractionalAndHugeExponents() {
        assertTrue(calculate("2^1.5").contains("exponent must be a whole number"))
        assertTrue(calculate("2^101").contains("exponent is outside the supported range"))
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

    @Test fun speedConversionAcceptsSlashUnits() {
        assertEquals("100 km/h = 27.777778 m/s", KairoUnitConverter.convert("100", "km/h", "m/s"))
    }

    @Test fun areaConversionWorks() {
        assertEquals("1 km2 = 1000000 m2", KairoUnitConverter.convert("1", "km2", "m2"))
    }

    @Test fun decimalAndBinaryDataUnitsRemainDistinct() {
        assertEquals("1 MB = 0.953674 MiB", KairoUnitConverter.convert("1", "MB", "MiB"))
    }

    @Test fun speedCannotBeConvertedToLength() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            KairoUnitConverter.convert("60", "mph", "km")
        }
    }

    @Test fun conversionAcceptsIntoWording() {
        val request = KairoSkillRegistry.detect("5 km into miles")
        assertEquals(KairoSkillType.CONVERT_UNITS, request.type)
        assertEquals("5 km = 3.106856 mi", KairoToolExecutor.execute(request))
    }

    @Test fun conversionAcceptsQuestionForm() {
        val request = KairoSkillRegistry.detect("how many miles is 5 km?")
        assertEquals(KairoSkillType.CONVERT_UNITS, request.type)
        assertEquals("5 km = 3.106856 mi", KairoToolExecutor.execute(request))
    }

    @Test fun conversionAcceptsTeluguEnglishCommand() {
        val request = KairoSkillRegistry.detect("5 km ni miles loki convert chey")
        assertEquals(KairoSkillType.CONVERT_UNITS, request.type)
        assertEquals("5 km = 3.106856 mi", KairoToolExecutor.execute(request))
    }

}
