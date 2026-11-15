package com.kairo.ai

import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.kairo.ai.tools.KairoStudioUtilities

/** Executes small, deterministic, offline tools. No code evaluation or shell access. */
object KairoToolExecutor {
    fun execute(request: KairoSkillRequest): String? = when (request.type) {
        KairoSkillType.CALCULATE -> runCatching {
            val value = ArithmeticParser(request.argument).parse()
            "The answer is ${BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()}."
        }.getOrElse { "I couldn't calculate that: ${it.message ?: "invalid expression"}." }
        KairoSkillType.CONVERT -> runCatching {
            val parts = request.argument.split('|')
            require(parts.size == 3) { "Use: convert 5 km to m." }
            val value = parts[0].toDouble()
            val converted = KairoStudioUtilities.roundTo(
                KairoStudioUtilities.convert(value, parts[1], parts[2]), 6
            )
            "$" + "value $" + "{parts[1]} = $" + "converted $" + "{parts[2]}"
        }.getOrElse { "I couldn't convert those units: $" + "{it.message ?: \"invalid conversion\"}." }
        KairoSkillType.ANALYZE_DOCUMENT -> runCatching {
            val metrics = KairoStudioUtilities.analyzeDocument(request.argument)
            "Document analysis\\n" +
                "Characters: $" + "{metrics.characters}\\n" +
                "Words: $" + "{metrics.words}\\n" +
                "Sentences: $" + "{metrics.sentences}\\n" +
                "Estimated tokens: $" + "{metrics.estimatedTokens}\\n" +
                "Script: $" + "{metrics.language.name.lowercase().replace('_', ' ')}"
        }.getOrElse { "I couldn't analyze that text: $" + "{it.message ?: \"unknown error\"}." }
        KairoSkillType.CURRENT_TIME -> {
            val now = ZonedDateTime.now()
            "It's " + now.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.getDefault())) +
                " on " + now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())) + "."
        }
        else -> null
    }

    private class ArithmeticParser(private val source: String) {
        private var index = 0

        fun parse(): Double {
            require(source.isNotBlank() && source.length <= 100) { "enter a short arithmetic expression" }
            val result = expression()
            skipSpaces()
            require(index == source.length) { "unexpected character at position ${index + 1}" }
            require(result.isFinite()) { "result is outside the supported range" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * factor()
                    consume('/') -> {
                        val divisor = factor()
                        require(divisor != 0.0) { "division by zero is not allowed" }
                        value / divisor
                    }
                    consume('%') -> {
                        val divisor = factor()
                        require(divisor != 0.0) { "division by zero is not allowed" }
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            skipSpaces()
            if (consume('+')) return factor()
            if (consume('-')) return -factor()
            if (consume('(')) {
                val nested = expression()
                skipSpaces()
                require(consume(')')) { "missing closing parenthesis" }
                return nested
            }
            val start = index
            var dots = 0
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                if (source[index] == '.') dots++
                index++
            }
            require(index > start && dots <= 1) { "expected a number" }
            return source.substring(start, index).toDoubleOrNull()
                ?: throw IllegalArgumentException("invalid number")
        }

        private fun consume(char: Char): Boolean {
            if (index < source.length && source[index] == char) {
                index++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (index < source.length && source[index].isWhitespace()) index++
        }
    }
}
