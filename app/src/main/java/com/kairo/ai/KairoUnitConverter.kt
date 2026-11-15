package com.kairo.ai

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Small, deterministic unit converter. It performs no network requests. */
object KairoUnitConverter {
    private data class Unit(
        val label: String,
        val dimension: String,
        val factorToBase: BigDecimal,
        val offsetToBase: BigDecimal = BigDecimal.ZERO
    ) {
        fun toBase(value: BigDecimal): BigDecimal =
            value.add(offsetToBase, MathContext.DECIMAL128).multiply(factorToBase, MathContext.DECIMAL128)

        fun fromBase(value: BigDecimal): BigDecimal =
            value.divide(factorToBase, MathContext.DECIMAL128).subtract(offsetToBase, MathContext.DECIMAL128)
    }

    private fun unit(label: String, dimension: String, factor: String, offset: String = "0") =
        Unit(label, dimension, factor.toBigDecimal(), offset.toBigDecimal())

    private val units = buildMap {
        fun add(unit: Unit, vararg aliases: String) {
            (listOf(unit.label) + aliases).forEach { put(normalize(it), unit) }
        }
        add(unit("km", "length", "1000"), "kilometer", "kilometers", "kilometre", "kilometres")
        add(unit("m", "length", "1"), "meter", "meters", "metre", "metres")
        add(unit("cm", "length", "0.01"), "centimeter", "centimeters", "centimetre", "centimetres")
        add(unit("mm", "length", "0.001"), "millimeter", "millimeters", "millimetre", "millimetres")
        add(unit("mi", "length", "1609.344"), "mile", "miles")
        add(unit("ft", "length", "0.3048"), "foot", "feet")
        add(unit("in", "length", "0.0254"), "inch", "inches")
        add(unit("yd", "length", "0.9144"), "yard", "yards")
        add(unit("m2", "area", "1"), "sqm", "squaremeter", "squaremeters")
        add(unit("km2", "area", "1000000"), "sqkm", "squarekilometer", "squarekilometers")
        add(unit("cm2", "area", "0.0001"), "sqcm", "squarecentimeter", "squarecentimeters")
        add(unit("ft2", "area", "0.09290304"), "sqft", "squarefoot", "squarefeet")
        add(unit("ha", "area", "10000"), "hectare", "hectares")
        add(unit("acre", "area", "4046.8564224"), "acres")

        add(unit("kg", "mass", "1"), "kilogram", "kilograms", "kilo", "kilos")
        add(unit("g", "mass", "0.001"), "gram", "grams")
        add(unit("mg", "mass", "0.000001"), "milligram", "milligrams")
        add(unit("lb", "mass", "0.45359237"), "lbs", "pound", "pounds")
        add(unit("oz", "mass", "0.028349523125"), "ounce", "ounces")

        add(unit("l", "volume", "1"), "liter", "liters", "litre", "litres")
        add(unit("ml", "volume", "0.001"), "milliliter", "milliliters", "millilitre", "millilitres")
        add(unit("gal", "volume", "3.785411784"), "gallon", "gallons", "usgal")

        add(unit("m/s", "speed", "1"), "mps", "meterspersecond", "metrespersecond")
        add(unit("km/h", "speed", "0.2777777777777777777777777777777778"), "kph", "kmh", "kilometersperhour", "kilometresperhour")
        add(unit("mph", "speed", "0.44704"), "mileperhour", "milesperhour")

        add(unit("B", "data", "1"), "byte", "bytes")
        add(unit("KB", "data", "1000"), "kilobyte", "kilobytes")
        add(unit("MB", "data", "1000000"), "megabyte", "megabytes")
        add(unit("GB", "data", "1000000000"), "gigabyte", "gigabytes")
        add(unit("TB", "data", "1000000000000"), "terabyte", "terabytes")
        add(unit("KiB", "data", "1024"), "kibibyte", "kibibytes")
        add(unit("MiB", "data", "1048576"), "mebibyte", "mebibytes")
        add(unit("GiB", "data", "1073741824"), "gibibyte", "gibibytes")

        add(unit("°C", "temperature", "1", "273.15"), "c", "celsius", "centigrade")
        add(unit("°F", "temperature", "0.5555555555555555555555555555555556", "459.67"), "f", "fahrenheit")
        add(unit("k", "temperature", "1"), "kelvin", "kelvins")
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(" ", "")

    fun convert(valueText: String, fromText: String, toText: String): String {
        require(valueText.length <= 40) { "number is too long" }
        val value = valueText.toBigDecimalOrNull() ?: throw IllegalArgumentException("enter a valid number")
        val from = units[normalize(fromText)] ?: throw IllegalArgumentException("unsupported source unit: $fromText")
        val to = units[normalize(toText)] ?: throw IllegalArgumentException("unsupported target unit: $toText")
        require(from.dimension == to.dimension) { "those units measure different things" }
        val base = from.toBase(value)
        if (from.dimension == "temperature") {
            require(base >= BigDecimal.ZERO) { "temperature is below absolute zero" }
        }
        val converted = to.fromBase(base).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return "$valueText $fromText = ${converted.toPlainString()} ${to.label}"
    }
}
