package com.phaseslauncher.data.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** A calculation shown at the top of drawer search: [expression] = [result]. */
data class Calculation(val expression: String, val result: String)

/**
 * Answers arithmetic ("12*(3+4)", "2^10", "15% of 80", "80 + 15%") and unit conversions
 * ("10 km to mi", "70 f in c", "5 kg to lb") typed into search. Returns null for anything that
 * isn't clearly a calculation, so plain app searches stay uncluttered.
 */
object Calculator {

    fun evaluate(query: String): Calculation? {
        val text = query.trim().lowercase(Locale.ROOT).replace(',', '.').replace('×', '*').replace('÷', '/')
        if (text.isEmpty()) return null
        return convert(text) ?: percentOf(text) ?: arithmetic(text)
    }

    // region Arithmetic

    private fun arithmetic(text: String): Calculation? {
        // Needs an operator between numbers; a lone number is just a search.
        if (!text.any { it in "+-*/^%" } || !text.any(Char::isDigit)) return null
        if (text.any { !(it.isDigit() || it in "+-*/^%(). ") }) return null
        val value = runCatching { Parser(text).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return Calculation(query(text), format(value))
    }

    /** "15% of 80" / "15 % of 80". */
    private fun percentOf(text: String): Calculation? {
        val match = PERCENT_OF.matchEntire(text) ?: return null
        val percent = match.groupValues[1].toDoubleOrNull() ?: return null
        val of = match.groupValues[2].toDoubleOrNull() ?: return null
        return Calculation("${format(percent)}% of ${format(of)}", format(percent / 100 * of))
    }

    /**
     * Recursive descent over: expr = term (('+'|'-') term)*, term = factor (('*'|'/') factor)*,
     * factor = unary ('^' factor)?, unary = '-' unary | postfix, postfix = primary '%'?.
     * "a + b%" means a plus b percent of a, like a phone calculator.
     */
    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            skip()
            require(i == s.length)
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                val op = s.getOrNull(i)
                if (op != '+' && op != '-') return v
                i++
                val (rhs, percent) = termWithPercent()
                val amount = if (percent) v * rhs / 100 else rhs
                v = if (op == '+') v + amount else v - amount
            }
        }

        /** A term, and whether it was a bare percentage ("15%") so + and - can apply it to the left side. */
        private fun termWithPercent(): Pair<Double, Boolean> {
            val start = i
            val v = term()
            val raw = s.substring(start, i).trim()
            return if (raw.endsWith("%") && raw.count { it in "*/^(" } == 0) (v * 100) to true else v to false
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skip()
                when (s.getOrNull(i)) {
                    '*' -> { i++; v *= factor() }
                    '/' -> { i++; val d = factor(); require(d != 0.0); v /= d }
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            val base = unary()
            skip()
            if (s.getOrNull(i) == '^') {
                i++
                return base.pow(factor())
            }
            return base
        }

        private fun unary(): Double {
            skip()
            if (s.getOrNull(i) == '-') { i++; return -unary() }
            if (s.getOrNull(i) == '+') { i++; return unary() }
            var v = primary()
            skip()
            if (s.getOrNull(i) == '%') { i++; v /= 100 }
            return v
        }

        private fun primary(): Double {
            skip()
            if (s.getOrNull(i) == '(') {
                i++
                val v = expr()
                skip()
                require(s.getOrNull(i) == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }
    }

    // endregion

    // region Units

    private enum class Dimension { Length, Mass, Volume, Temperature, Speed }

    /** [factor] converts to the dimension's base unit; temperatures use [toBase]/[fromBase]. */
    private class Unit(
        val symbol: String,
        val dimension: Dimension,
        val factor: Double = 1.0,
        val toBase: ((Double) -> Double)? = null,
        val fromBase: ((Double) -> Double)? = null,
    )

    private val units: Map<String, Unit> = buildMap {
        fun add(names: List<String>, unit: Unit) = names.forEach { put(it, unit) }
        add(listOf("mm", "millimeter", "millimeters"), Unit("mm", Dimension.Length, 0.001))
        add(listOf("cm", "centimeter", "centimeters"), Unit("cm", Dimension.Length, 0.01))
        add(listOf("m", "meter", "meters", "metre", "metres"), Unit("m", Dimension.Length, 1.0))
        add(listOf("km", "kilometer", "kilometers", "kilometre", "kilometres"), Unit("km", Dimension.Length, 1000.0))
        add(listOf("in", "inch", "inches"), Unit("in", Dimension.Length, 0.0254))
        add(listOf("ft", "foot", "feet"), Unit("ft", Dimension.Length, 0.3048))
        add(listOf("yd", "yard", "yards"), Unit("yd", Dimension.Length, 0.9144))
        add(listOf("mi", "mile", "miles"), Unit("mi", Dimension.Length, 1609.344))
        add(listOf("g", "gram", "grams"), Unit("g", Dimension.Mass, 0.001))
        add(listOf("kg", "kilo", "kilos", "kilogram", "kilograms"), Unit("kg", Dimension.Mass, 1.0))
        add(listOf("oz", "ounce", "ounces"), Unit("oz", Dimension.Mass, 0.028349523125))
        add(listOf("lb", "lbs", "pound", "pounds"), Unit("lb", Dimension.Mass, 0.45359237))
        add(listOf("ml", "milliliter", "milliliters"), Unit("ml", Dimension.Volume, 0.001))
        add(listOf("l", "liter", "liters", "litre", "litres"), Unit("l", Dimension.Volume, 1.0))
        add(listOf("gal", "gallon", "gallons"), Unit("gal", Dimension.Volume, 3.785411784))
        add(listOf("cup", "cups"), Unit("cup", Dimension.Volume, 0.2365882365))
        add(listOf("kmh", "km/h", "kph"), Unit("km/h", Dimension.Speed, 1 / 3.6))
        add(listOf("mph"), Unit("mph", Dimension.Speed, 0.44704))
        add(listOf("m/s", "mps"), Unit("m/s", Dimension.Speed, 1.0))
        add(listOf("c", "°c", "celsius"), Unit("°C", Dimension.Temperature, toBase = { it }, fromBase = { it }))
        add(listOf("f", "°f", "fahrenheit"), Unit("°F", Dimension.Temperature, toBase = { (it - 32) * 5 / 9 }, fromBase = { it * 9 / 5 + 32 }))
        add(listOf("k", "kelvin"), Unit("K", Dimension.Temperature, toBase = { it - 273.15 }, fromBase = { it + 273.15 }))
    }

    /** "10 km to mi", "10km in miles", "70 f to c". */
    private fun convert(text: String): Calculation? {
        val match = CONVERSION.matchEntire(text) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val from = units[match.groupValues[2]] ?: return null
        val to = units[match.groupValues[3]] ?: return null
        if (from.dimension != to.dimension || from === to) return null
        val result = if (from.dimension == Dimension.Temperature) {
            to.fromBase!!(from.toBase!!(amount))
        } else {
            amount * from.factor / to.factor
        }
        return Calculation("${format(amount)} ${from.symbol} in ${to.symbol}", "${format(result)} ${to.symbol}")
    }

    // endregion

    private fun query(text: String) = text.replace(" ", "")

    /** Up to 10 significant digits, no trailing zeros, no scientific notation for everyday sizes. */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        if (abs(value) >= 1e15 || abs(value) < 1e-9) return "%.6e".format(Locale.ROOT, value)
        val rounded = BigDecimal(value).round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        return DecimalFormat("#,##0.##########", DecimalFormatSymbols(Locale.getDefault())).format(rounded)
    }

    private val PERCENT_OF = Regex("""(-?[\d.]+)\s*%\s*of\s*(-?[\d.]+)""")
    private val CONVERSION = Regex("""(-?[\d.]+)\s*([a-z°/]+)\s+(?:to|in|=)\s+([a-z°/]+)""")
}
