package com.example.data

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Stack
import java.util.regex.Pattern

object MathExpressionEvaluator {

    private val ROMAN_REGEX = Pattern.compile("\\b(?=[MDCLXVI])M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})\\b", Pattern.CASE_INSENSITIVE)

    /**
     * Converts Roman numerals in expression to Arabic numbers and tracks what was converted.
     */
    fun convertRomanNumerals(input: String): Pair<String, List<Pair<String, Int>>> {
        val conversions = mutableListOf<Pair<String, Int>>()
        val matcher = ROMAN_REGEX.matcher(input)
        val sb = StringBuffer()

        while (matcher.find()) {
            val roman = matcher.group()
            if (roman.isNotBlank()) {
                val arabic = parseRoman(roman.uppercase())
                if (arabic > 0) {
                    conversions.add(Pair(roman.uppercase(), arabic))
                    matcher.appendReplacement(sb, arabic.toString())
                } else {
                    matcher.appendReplacement(sb, roman)
                }
            }
        }
        matcher.appendTail(sb)
        return Pair(sb.toString(), conversions)
    }

    private fun parseRoman(s: String): Int {
        val map = mapOf(
            'I' to 1,
            'V' to 5,
            'X' to 10,
            'L' to 50,
            'C' to 100,
            'D' to 500,
            'M' to 1000
        )
        var total = 0
        var prevValue = 0
        for (i in s.length - 1 downTo 0) {
            val char = s[i]
            val value = map[char] ?: return 0
            if (value < prevValue) {
                total -= value
            } else {
                total += value
                prevValue = value
            }
        }
        return total
    }

    /**
     * Pre-cleans expressions by stripping currency markers, thousands commas,
     * normalizing mathematical keywords ("of", "sqrt", "√"), percentage contexts, factorials, and symbols.
     */
    fun sanitizeExpression(input: String): String {
        var expr = input
            // Strip currency symbols and common labels ($50 -> 50, Rs. 100 -> 100)
            .replace("(?i)(\\b(rs\\.?|inr|usd|eur|gbp|aud|cad)\\s*|[$€£¥₹])".toRegex(), "")
            // Replace multi-line / vertical column numbers with addition
            .replace("(?<=[0-9])\\s*\\n\\s*(?=[0-9])".toRegex(), " + ")
            // Replace "of" keyword with multiplication (e.g., "15% of 200" -> "15% * 200")
            .replace("(?i)\\s+of\\s+".toRegex(), "*")
            // Contextual percentages in additions and discounts:
            // "500 - 10%" -> "500 - (500 * 10 / 100)"
            // "1000 + 18%" -> "1000 + (1000 * 18 / 100)"
            .replace("([0-9.]+)\\s*([+\\-])\\s*([0-9.]+)%".toRegex(), "$1 $2 ($1 * $3 / 100)")
            // Standalone or multiplier percentages: "20%" -> "(20/100)"
            .replace("([0-9.]+)%".toRegex(), "($1/100)")
            // Factorials: "5!" -> 120
            .let { replaceFactorials(it) }
            // Handle square root symbols: √9 -> (9^0.5), sqrt(9) -> (9^0.5)
            .replace("√\\s*([0-9.]+)".toRegex(), "($1^0.5)")
            .replace("(?i)sqrt\\s*\\(\\s*([0-9.]+)\\s*\\)".toRegex(), "($1^0.5)")
            // Strip thousands separators: e.g. 1,500.00 -> 1500.00
            .replace("(?<=\\d),(?=\\d{3}\\b)".toRegex(), "")
            // Normalize arithmetic operators
            .replace("×", "*")
            .replace("x", "*")
            .replace("X", "*")
            .replace("·", "*")
            .replace("÷", "/")
            .replace(":", "/")
            .replace("−", "-")
            .replace("–", "-")
            .replace("—", "-")
            .replace("\\s+".toRegex(), "")

        // Handle implicit multiplication: e.g. 2(3) -> 2*(3), (2)(3) -> (2)*(3), )2 -> )*2
        expr = expr.replace("(\\d)\\(", "$1*(")
        expr = expr.replace("\\)(\\d)", ")*$1")
        expr = expr.replace("\\)\\(", ")*(")

        return expr
    }

    private fun replaceFactorials(input: String): String {
        val regex = "(\\d+)!".toRegex()
        return regex.replace(input) { match ->
            val n = match.groupValues[1].toLongOrNull() ?: 1L
            if (n in 0..15) {
                var fact = 1L
                for (j in 2..n) fact *= j
                fact.toString()
            } else {
                match.value
            }
        }
    }

    /**
     * Evaluates a mathematical expression following standard BODMAS/PEMDAS order.
     * Supports zig-zag calculation chains (A -> B -> C), equations (A = B), and deep calculations.
     */
    fun evaluate(rawExpression: String): Result<String> {
        return try {
            if (rawExpression.isBlank()) {
                return Result.failure(IllegalArgumentException("Expression is empty"))
            }

            // 1. Check for zig-zag chains: e.g. "10 + 20 -> 30 * 2 -> 60" or with "=>" / "→"
            if (rawExpression.contains("->") || rawExpression.contains("=>") || rawExpression.contains("→")) {
                val steps = rawExpression.split(Regex("->|=>|→")).map { it.trim() }.filter { it.isNotEmpty() }
                if (steps.isNotEmpty()) {
                    var lastResult = ""
                    for (step in steps) {
                        val eval = evaluateSingleExpression(step)
                        if (eval.isSuccess) {
                            lastResult = eval.getOrThrow()
                        }
                    }
                    if (lastResult.isNotEmpty()) {
                        return Result.success(lastResult)
                    }
                }
            }

            // 2. Check for simple linear algebraic equations like "2x + 6 = 20" or "x - 5 = 10"
            val eqSolved = trySolveLinearEquation(rawExpression)
            if (eqSolved != null) {
                return Result.success(eqSolved)
            }

            // 3. Handle '=' sign: e.g. "45 + 55 =" or "45 + 55 = 100"
            val sanitizedEq = if (rawExpression.contains("=")) {
                val sides = rawExpression.split("=").map { it.trim() }
                if (sides.size >= 2) {
                    val left = sides[0]
                    val right = sides[1]
                    if (right.isEmpty() || right == "?" || right.matches(Regex("(?i)[a-z_?]+"))) {
                        left
                    } else if (left.isEmpty()) {
                        right
                    } else {
                        val leftEval = evaluateSingleExpression(left)
                        if (leftEval.isSuccess) {
                            leftEval.getOrThrow()
                        } else {
                            right
                        }
                    }
                } else {
                    rawExpression.replace("=", "")
                }
            } else {
                rawExpression
            }

            evaluateSingleExpression(sanitizedEq)
        } catch (e: ArithmeticException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "Evaluation error"))
        }
    }

    private fun evaluateSingleExpression(rawInput: String): Result<String> {
        return try {
            if (rawInput.isBlank()) {
                return Result.failure(IllegalArgumentException("Empty input"))
            }
            // First convert any Roman numerals
            val (sanitizedInput, _) = convertRomanNumerals(rawInput)

            // Sanitize currencies, thousands commas, keywords, and arithmetic operators
            val expr = sanitizeExpression(sanitizedInput)

            val tokens = tokenize(expr)
            if (tokens.isEmpty()) {
                return Result.failure(IllegalArgumentException("Invalid expression"))
            }

            val rpn = shuntingYard(tokens)
            val result = evaluateRpn(rpn)

            // Format nicely
            val formatted = formatNumber(result)
            Result.success(formatted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Solves simple single-variable linear equations like "2x + 6 = 20" -> "7"
     */
    private fun trySolveLinearEquation(raw: String): String? {
        if (!raw.contains("=")) return null
        val sides = raw.split("=").map { it.trim() }
        if (sides.size != 2) return null
        val left = sides[0]
        val right = sides[1]

        // Pattern matching e.g. "2x + 6 = 20" or "x + 5 = 12" or "3x = 15"
        val regex = Regex("(?i)^([+-]?\\s*\\d*\\.?\\d*)\\s*([a-z])\\s*([+-]\\s*\\d+\\.?\\d*)?$")
        val match = regex.find(left) ?: return null
        val rightVal = right.toDoubleOrNull() ?: return null

        var aStr = match.groupValues[1].replace("\\s".toRegex(), "")
        val a = when {
            aStr.isEmpty() || aStr == "+" -> 1.0
            aStr == "-" -> -1.0
            else -> aStr.toDoubleOrNull() ?: 1.0
        }
        if (a == 0.0) return null

        val bStr = match.groupValues[3].replace("\\s".toRegex(), "")
        val b = if (bStr.isNotEmpty()) bStr.toDoubleOrNull() ?: 0.0 else 0.0

        val solution = (rightVal - b) / a
        val bd = BigDecimal(solution).setScale(4, RoundingMode.HALF_UP)
        return formatNumber(bd)
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        var expectUnary = true

        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                        sb.append(expr[i])
                        i++
                    }
                    tokens.add(sb.toString())
                    expectUnary = false
                    continue
                }
                c == '+' || c == '-' -> {
                    if (expectUnary) {
                        // Unary operator
                        if (c == '-') {
                            // Collect negative number or unary operator
                            if (i + 1 < expr.length && (expr[i + 1].isDigit() || expr[i + 1] == '.')) {
                                i++
                                val sb = StringBuilder("-")
                                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                                    sb.append(expr[i])
                                    i++
                                }
                                tokens.add(sb.toString())
                                expectUnary = false
                                continue
                            } else {
                                tokens.add("neg")
                            }
                        }
                    } else {
                        tokens.add(c.toString())
                        expectUnary = true
                    }
                }
                c == '*' || c == '/' || c == '^' || c == '%' -> {
                    tokens.add(c.toString())
                    expectUnary = true
                }
                c == '(' -> {
                    tokens.add("(")
                    expectUnary = true
                }
                c == ')' -> {
                    tokens.add(")")
                    expectUnary = false
                }
            }
            i++
        }
        return tokens
    }

    private fun precedence(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/", "%" -> 2
        "neg" -> 3
        "^" -> 4
        else -> 0
    }

    private fun isRightAssociative(op: String): Boolean = op == "^" || op == "neg"

    private fun shuntingYard(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val ops = Stack<String>()

        for (token in tokens) {
            if (isNumber(token)) {
                output.add(token)
            } else if (token == "(") {
                ops.push(token)
            } else if (token == ")") {
                while (ops.isNotEmpty() && ops.peek() != "(") {
                    output.add(ops.pop())
                }
                if (ops.isNotEmpty() && ops.peek() == "(") {
                    ops.pop()
                }
            } else {
                // Operator
                while (ops.isNotEmpty() && ops.peek() != "(" &&
                    (precedence(ops.peek()) > precedence(token) ||
                            (precedence(ops.peek()) == precedence(token) && !isRightAssociative(token)))
                ) {
                    output.add(ops.pop())
                }
                ops.push(token)
            }
        }

        while (ops.isNotEmpty()) {
            val top = ops.pop()
            if (top != "(" && top != ")") {
                output.add(top)
            }
        }

        return output
    }

    private fun isNumber(token: String): Boolean {
        return token.toDoubleOrNull() != null
    }

    private fun evaluateRpn(rpn: List<String>): BigDecimal {
        val stack = Stack<BigDecimal>()
        val mc = MathContext(16, RoundingMode.HALF_UP)

        for (token in rpn) {
            if (isNumber(token)) {
                stack.push(BigDecimal(token))
            } else if (token == "neg") {
                if (stack.isEmpty()) throw IllegalArgumentException("Malformed expression")
                stack.push(stack.pop().negate())
            } else if (token == "%") {
                if (stack.isEmpty()) throw IllegalArgumentException("Malformed expression")
                val a = stack.pop()
                stack.push(a.divide(BigDecimal("100"), mc))
            } else {
                if (stack.size < 2) throw IllegalArgumentException("Malformed expression")
                val b = stack.pop()
                val a = stack.pop()
                val res = when (token) {
                    "+" -> a.add(b, mc)
                    "-" -> a.subtract(b, mc)
                    "*" -> a.multiply(b, mc)
                    "/" -> {
                        if (b.compareTo(BigDecimal.ZERO) == 0) {
                            throw ArithmeticException("Cannot divide by 0")
                        }
                        a.divide(b, mc)
                    }
                    "^" -> {
                        try {
                            val intB = b.intValueExact()
                            if (intB >= 0) a.pow(intB, mc)
                            else BigDecimal(Math.pow(a.toDouble(), b.toDouble()), mc)
                        } catch (e: Exception) {
                            BigDecimal(Math.pow(a.toDouble(), b.toDouble()), mc)
                        }
                    }
                    else -> throw IllegalArgumentException("Unknown operator: $token")
                }
                stack.push(res)
            }
        }

        if (stack.size != 1) {
            throw IllegalArgumentException("Malformed expression")
        }
        return stack.pop()
    }

    private fun formatNumber(bd: BigDecimal): String {
        // Strip trailing zeroes
        var stripped = bd.stripTrailingZeros()
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0)
        }
        val plain = stripped.toPlainString()
        return if (plain == "-0") "0" else plain
    }
}
