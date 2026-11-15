package com.kairo.ai

import java.math.BigDecimal
import java.math.MathContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Executes small, deterministic, offline tools. No code evaluation or shell access. */
object KairoToolExecutor {
    fun execute(request: KairoSkillRequest): String? = when (request.type) {
        KairoSkillType.CALCULATE -> runCatching {
            val value = ArithmeticParser(request.argument).parse()
            "The answer is ${value.stripTrailingZeros().toPlainString()}."
        }.getOrElse { "I couldn't calculate that: ${it.message ?: "invalid expression"}." }
        KairoSkillType.CONVERT_UNITS -> runCatching {
            val parts = request.argument.split("|")
            require(parts.size == 3) { "use a format such as: convert 5 km to miles" }
            KairoUnitConverter.convert(parts[0], parts[1], parts[2])
        }.getOrElse { "I couldn't convert that: ${it.message ?: "unsupported conversion"}." }
        KairoSkillType.CURRENT_TIME -> {
            val now = ZonedDateTime.now()
            "It's " + now.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.getDefault())) +
                " on " + now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())) + "."
        }
        else -> null
    }

    private class ArithmeticParser(private val source: String) {
        private var index = 0

        fun parse(): BigDecimal {
            require(source.isNotBlank() && source.length <= 100) { "enter a short arithmetic expression" }
            val result = expression()
            skipSpaces()
            require(index == source.length) { "unexpected character at position ${index + 1}" }
            require(result.precision() <= 128 && result.scale() <= 128) { "result is outside the supported range" }
            return result
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value.add(term(), MathContext.DECIMAL128)
                    consume('-') -> value.subtract(term(), MathContext.DECIMAL128)
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = unary()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value.multiply(unary(), MathContext.DECIMAL128)
                    consume('/') -> {
                        val divisor = unary()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "division by zero is not allowed" }
                        value.divide(divisor, MathContext.DECIMAL128)
                    }
                    consume('%') -> {
                        val divisor = unary()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "division by zero is not allowed" }
                        value.remainder(divisor, MathContext.DECIMAL128)
                    }
                    else -> return value
                }
            }
        }

        private fun unary(): BigDecimal {
            skipSpaces()
            if (consume('+')) return unary()
            if (consume('-')) return unary().negate()
            return power()
        }

        private fun power(): BigDecimal {
            var value = primary()
            skipSpaces()
            if (consume('^')) {
                val exponent = unary().stripTrailingZeros()
                require(exponent.scale() <= 0) { "exponent must be a whole number" }
                val power = exponent.intValueExact()
                require(power in -100..100) { "exponent is outside the supported range" }
                if (power < 0) {
                    require(value.compareTo(BigDecimal.ZERO) != 0) { "zero cannot have a negative exponent" }
                    value = BigDecimal.ONE.divide(value.pow(-power, MathContext.DECIMAL128), MathContext.DECIMAL128)
                } else {
                    value = value.pow(power, MathContext.DECIMAL128)
                }
            }
            return value
        }

        private fun primary(): BigDecimal {
            skipSpaces()
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
            return source.substring(start, index).toBigDecimalOrNull()
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
