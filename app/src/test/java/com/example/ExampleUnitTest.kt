package com.example

import com.example.data.MathExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testStandardBodmasEvaluation() {
        val res = MathExpressionEvaluator.evaluate("125 + 48 × 2")
        assertTrue(res.isSuccess)
        assertEquals("221", res.getOrThrow())
    }

    @Test
    fun testParenthesesEvaluation() {
        val res = MathExpressionEvaluator.evaluate("(18 + 12) ÷ 6")
        assertTrue(res.isSuccess)
        assertEquals("5", res.getOrThrow())
    }

    @Test
    fun testRomanNumeralsEvaluation() {
        val (converted, romanList) = MathExpressionEvaluator.convertRomanNumerals("XIV × 5")
        assertEquals("14 × 5", converted)
        assertEquals(1, romanList.size)
        assertEquals("XIV", romanList[0].first)
        assertEquals(14, romanList[0].second)

        val res = MathExpressionEvaluator.evaluate("XIV × 5")
        assertTrue(res.isSuccess)
        assertEquals("70", res.getOrThrow())
    }

    @Test
    fun testDivideByZero() {
        val res = MathExpressionEvaluator.evaluate("10 ÷ 0")
        assertTrue(res.isFailure)
        assertEquals("Cannot divide by 0", res.exceptionOrNull()?.message)
    }

    @Test
    fun testDecimalsAndPercentages() {
        val res = MathExpressionEvaluator.evaluate("450.50 + 120.25")
        assertTrue(res.isSuccess)
        assertEquals("570.75", res.getOrThrow())
    }
}
