package com.kairo.ai.tools

import kotlin.math.abs
import kotlin.math.round

/**
 * Small, deterministic utilities ported from the Google AI Studio KAIRO prototype.
 * These are offline tools, not an AI reasoning substitute.
 */
object KairoStudioUtilities {
    enum class Language { TELUGU, ENGLISH, MIXED, UNKNOWN }

    data class DocumentMetrics(
        val characters: Int,
        val words: Int,
        val sentences: Int,
        val estimatedTokens: Int,
        val language: Language
    )

    private data class UnitDef(val dimension: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

    private val units = mapOf(
        "m" to UnitDef("length", { it }, { it }),
        "km" to UnitDef("length", { it * 1000.0 }, { it / 1000.0 }),
        "cm" to UnitDef("length", { it / 100.0 }, { it * 100.0 }),
        "mm" to UnitDef("length", { it / 1000.0 }, { it * 1000.0 }),
        "mi" to UnitDef("length", { it * 1609.344 }, { it / 1609.344 }),
        "ft" to UnitDef("length", { it * 0.3048 }, { it / 0.3048 }),
        "in" to UnitDef("length", { it * 0.0254 }, { it / 0.0254 }),
        "kg" to UnitDef("mass", { it }, { it }),
        "g" to UnitDef("mass", { it / 1000.0 }, { it * 1000.0 }),
        "lb" to UnitDef("mass", { it * 0.45359237 }, { it / 0.45359237 }),
        "oz" to UnitDef("mass", { it * 0.028349523125 }, { it / 0.028349523125 }),
        "s" to UnitDef("time", { it }, { it }),
        "min" to UnitDef("time", { it * 60.0 }, { it / 60.0 }),
        "h" to UnitDef("time", { it * 3600.0 }, { it / 3600.0 }),
        "day" to UnitDef("time", { it * 86400.0 }, { it / 86400.0 }),
        "b" to UnitDef("data", { it }, { it }),
        "kb" to UnitDef("data", { it * 1000.0 }, { it / 1000.0 }),
        "mb" to UnitDef("data", { it * 1_000_000.0 }, { it / 1_000_000.0 }),
        "gb" to UnitDef("data", { it * 1_000_000_000.0 }, { it / 1_000_000_000.0 })
    )

    fun detectLanguage(text: String): Language {
        var telugu = 0
        var latin = 0
        text.forEach { ch ->
            when {
                ch in '\u0C00'..'\u0C7F' -> telugu++
                ch in 'A'..'Z' || ch in 'a'..'z' -> latin++
            }
        }
        return when {
            telugu > 0 && latin > 0 -> Language.MIXED
            telugu > 0 -> Language.TELUGU
            latin > 0 -> Language.ENGLISH
            else -> Language.UNKNOWN
        }
    }

    fun analyzeDocument(text: String): DocumentMetrics {
        val words = Regex("\\S+").findAll(text).count()
        val sentences = if (text.isBlank()) 0 else
            Regex("[.!?।]+|\\n+").findAll(text).count { match ->
                text.substring(0, match.range.first).trim().isNotEmpty()
            } + if (text.trim().lastOrNull() in listOf('.', '!', '?', '।')) 0 else 1
        return DocumentMetrics(
            characters = text.length,
            words = words,
            sentences = sentences,
            estimatedTokens = (text.length + 3) / 4,
            language = detectLanguage(text)
        )
    }

    /** Converts supported metric/imperial length, mass, time and decimal data units. */
    fun convert(value: Double, from: String, to: String): Double {
        require(value.isFinite()) { "Value must be finite." }
        val source = units[from.trim().lowercase()]
            ?: throw IllegalArgumentException("Unsupported source unit: $from")
        val target = units[to.trim().lowercase()]
            ?: throw IllegalArgumentException("Unsupported target unit: $to")
        require(source.dimension == target.dimension) { "Incompatible unit dimensions." }
        val result = target.fromBase(source.toBase(value))
        require(result.isFinite()) { "Conversion result is outside the supported range." }
        return result
    }

    fun roundTo(value: Double, decimals: Int = 4): Double {
        require(decimals in 0..8) { "Decimals must be between 0 and 8." }
        val scale = Math.pow(10.0, decimals.toDouble())
        return round(value * scale) / scale
    }
}
