package com.example.ui.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MathExpressionEvaluator
import com.example.ui.theme.DarkKeypadFunc
import com.example.ui.theme.DarkKeypadNum
import com.example.ui.theme.DarkKeypadOp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LightKeypadFunc
import com.example.ui.theme.LightKeypadNum
import com.example.ui.theme.LightKeypadOp

private val KEYPAD_ROWS = listOf(
    listOf("C", "()", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "-"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "DEL", "=")
)

@Composable
fun ManualCalculatorScreen(
    initialExpression: String = "",
    onExpressionChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var expression by rememberSaveable {
        mutableStateOf(if (initialExpression.isNotBlank()) initialExpression else "")
    }
    var finalResult by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var justEvaluated by rememberSaveable { mutableStateOf(false) }

    // Sync if a new expression is passed from outside (e.g. Scan result or quick sample)
    LaunchedEffect(initialExpression) {
        if (initialExpression.isNotBlank() && initialExpression != expression) {
            expression = initialExpression
            finalResult = ""
            errorMessage = null
            justEvaluated = false
        }
    }

    // Auto-scroll expression to end when typing
    val scrollState = rememberScrollState()
    LaunchedEffect(expression) {
        onExpressionChange(expression)
        if (expression.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Live preview: only evaluate when expression ends with a digit, closing paren, percentage, or factorial
    val livePreview = remember(expression) {
        val trimmed = expression.trimEnd()
        if (trimmed.isBlank()) ""
        else {
            val lastChar = trimmed.last()
            if (lastChar.isDigit() || lastChar == ')' || lastChar == '%' || lastChar == '!') {
                val eval = MathExpressionEvaluator.evaluate(trimmed)
                if (eval.isSuccess) {
                    "= ${eval.getOrThrow()}"
                } else ""
            } else ""
        }
    }

    fun triggerHaptic() {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Exception) {}
    }

    fun onButtonClick(key: String) {
        triggerHaptic()
        errorMessage = null

        when (key) {
            "C" -> {
                expression = ""
                finalResult = ""
                errorMessage = null
                justEvaluated = false
            }
            "DEL" -> {
                justEvaluated = false
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                }
            }
            "=" -> {
                if (expression.isNotBlank()) {
                    val eval = MathExpressionEvaluator.evaluate(expression)
                    if (eval.isSuccess) {
                        finalResult = eval.getOrThrow()
                        expression = finalResult
                        justEvaluated = true
                    } else {
                        errorMessage = eval.exceptionOrNull()?.message ?: "Syntax Error"
                    }
                }
            }
            "()" -> {
                justEvaluated = false
                val openCount = expression.count { it == '(' }
                val closeCount = expression.count { it == ')' }
                if (openCount > closeCount && expression.lastOrNull() !in listOf('(', '+', '-', '×', '÷', '*', '/')) {
                    expression += ")"
                } else {
                    expression += "("
                }
            }
            "+", "-", "×", "÷", "%" -> {
                justEvaluated = false
                if (expression.isEmpty()) {
                    if (key == "-") {
                        expression = "-"
                    }
                } else {
                    val lastChar = expression.last()
                    if (lastChar in listOf('+', '-', '×', '÷', '%', '*')) {
                        expression = expression.dropLast(1) + key
                    } else {
                        expression += key
                    }
                }
            }
            "." -> {
                if (justEvaluated) {
                    expression = "0."
                    justEvaluated = false
                } else {
                    val parts = expression.split(Regex("[+\\-×÷%*()]"))
                    val lastPart = parts.lastOrNull() ?: ""
                    if (!lastPart.contains(".")) {
                        expression += if (lastPart.isEmpty()) "0." else "."
                    }
                }
            }
            else -> {
                // Digits
                if (justEvaluated) {
                    expression = key
                    justEvaluated = false
                } else {
                    expression += key
                }
            }
        }
    }

    fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Calculator Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("manual_calculator_screen"),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper Half: Display Area Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("calculator_display_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    // Top: Mode info, copy action, error indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Standard BODMAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            if (expression.isNotEmpty() || finalResult.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val textToCopy = if (finalResult.isNotEmpty()) finalResult else expression
                                        copyToClipboard(textToCopy)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy result",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Middle: Expression Display with smooth horizontal scrolling
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (expression.isEmpty()) "0" else expression,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = when {
                                        expression.length > 20 -> 26.sp
                                        expression.length > 12 -> 34.sp
                                        else -> 42.sp
                                    },
                                    fontFamily = FontFamily.SansSerif
                                ),
                                color = if (expression.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier.testTag("calculator_expression_display")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom: Live preview result (instant, smooth)
                        Text(
                            text = if (errorMessage != null) "" else livePreview,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ),
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("calculator_live_preview")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lower Half: 4-Column Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (row in KEYPAD_ROWS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (key in row) {
                            CalculatorKey(
                                key = key,
                                modifier = Modifier.weight(1f),
                                onClick = { onButtonClick(key) },
                                onLongClick = if (key == "DEL") {
                                    {
                                        triggerHaptic()
                                        expression = ""
                                        finalResult = ""
                                        errorMessage = null
                                        justEvaluated = false
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorKey(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.value == com.example.ui.theme.DarkBackground.value

    val isOperator = key in listOf("÷", "×", "-", "+", "%")
    val isAction = key == "="
    val isFunc = key in listOf("C", "DEL", "()")

    val containerColor = when {
        isAction -> ElectricBlue
        isOperator -> if (isDark) DarkKeypadOp else LightKeypadOp
        isFunc -> if (isDark) DarkKeypadFunc else LightKeypadFunc
        else -> if (isDark) DarkKeypadNum else LightKeypadNum
    }

    val contentColor = when {
        isAction -> Color.White
        isOperator -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
        isFunc -> if (key == "C") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .heightIn(min = 52.dp, max = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .testTag("calc_key_$key"),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = if (isAction) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (key == "DEL") {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = if (isAction || isOperator || isFunc) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = when (key) {
                            "()" -> 19.sp
                            "C" -> 22.sp
                            "=" -> 28.sp
                            else -> 24.sp
                        }
                    ),
                    color = contentColor
                )
            }
        }
    }
}
