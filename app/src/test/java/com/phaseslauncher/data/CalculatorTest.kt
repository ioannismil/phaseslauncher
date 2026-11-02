package com.phaseslauncher.data

import com.phaseslauncher.data.search.Calculator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CalculatorTest {

    @Before
    fun englishNumbers() {
        Locale.setDefault(Locale.US)
    }

    private fun result(query: String) = Calculator.evaluate(query)?.result

    @Test
    fun arithmeticFollowsPrecedence() {
        assertEquals("14", result("2+3*4"))
        assertEquals("20", result("(2+3)*4"))
        assertEquals("1,024", result("2^10"))
        assertEquals("-6", result("-2*3"))
        assertEquals("0.3333333333", result("1/3"))
    }

    @Test
    fun percentages() {
        assertEquals("12", result("15% of 80"))
        assertEquals("92", result("80 + 15%"))
        assertEquals("68", result("80 - 15%"))
        assertEquals("0.15", result("15%"))
        assertEquals("12", result("80*15%"))
    }

    @Test
    fun unitConversions() {
        assertEquals("6.213711922 mi", result("10 km to mi"))
        assertEquals("21.11111111 °C", result("70 f to c"))
        assertEquals("11.02311311 lb", result("5 kg in lb"))
        assertEquals("100 cm", result("1 m to cm"))
    }

    @Test
    fun plainSearchesAreNotCalculations() {
        assertNull(result("chrome"))
        assertNull(result("42"))
        assertNull(result("2048 game"))
        assertNull(result("10 km to kg"))
        assertNull(result("1/0"))
        assertNull(result("3 +"))
    }

    @Test
    fun theExpressionIsEchoed() {
        assertEquals("2+3*4", Calculator.evaluate("2 + 3 * 4")?.expression)
        assertEquals("10 km in mi", Calculator.evaluate("10 km to mi")?.expression)
    }
}
