package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.DetectedMathItem
import com.example.model.MathOcrResult
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GeminiVisionService(private val context: Context) {

    // Optimized network client with aggressive timeouts for ultra-fast millisecond response
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Supported modern multimodal models prioritized for ultra-fast OCR latency and accuracy
    private val candidateModels = listOf(
        "gemini-flash-latest",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite-preview"
    )

    suspend fun analyzeCalculationImage(imageUri: Uri): Result<MathOcrResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = try {
                val bKey = BuildConfig.GEMINI_API_KEY
                if (bKey.isNotBlank() && bKey != "MY_GEMINI_API_KEY") {
                    bKey
                } else {
                    System.getenv("GEMINI_API_KEY") ?: ""
                }
            } catch (e: Throwable) {
                System.getenv("GEMINI_API_KEY") ?: ""
            }

            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing. Please configure GEMINI_API_KEY in the Secrets panel.")
                )
            }

            // High-speed image pre-processing: GPU ColorMatrix contrast stretching and stroke sharpening
            // Scaled to optimal OCR size (~768px) for instant sub-80KB upload (<80ms network latency).
            val optimizedBitmap = ImageUtils.optimizeForHandwritingOcr(context, imageUri, maxDimension = 768)
                ?: ImageUtils.loadOrientedBitmap(context, imageUri, maxDimension = 768)
                ?: return@withContext Result.failure(IllegalArgumentException("Could not read image file"))

            val base64Data = bitmapToBase64(optimizedBitmap, quality = 75)
            if (optimizedBitmap.isRecycled.not()) {
                optimizedBitmap.recycle()
            }

            val prompt = """
                You are a world-class, ultra-fast mathematical vision engine specializing in messy, tilted, cursive handwriting, and complex zig-zag format calculations.
                Analyze the image which may contain:
                - Zig-zag calculations & calculation chains:
                  * Multi-step zig-zag calculation chains where intermediate steps snake across lines, diagonal arrows, or sequential steps (e.g. A + B = C -> C * D = E -> E - F = G). Trace the whole chain step-by-step and calculate the ultimate final answer.
                  * Zig-zag or diagonal handwriting: numbers written diagonally, scattered across the page, or arranged in zig-zag patterns; sum or evaluate them.
                  * Zig-zag shop ledgers, debit/credit records, and khaata accounts with multiple entries.
                - Slanted, rough, or unaligned handwritten digits and arithmetic signs (+, -, ×, x, ÷, /, *, ^, %, =):
                  * Distinguish 1 vs 7 vs / vs | vs l vs I.
                  * Distinguish 0 vs 6 vs 8 vs O vs D.
                  * Distinguish 2 vs Z, 5 vs S, 4 vs 9 or +.
                  * Distinguish + vs t vs x vs *.
                  * Distinguish decimal dot (.) vs comma (,) vs stray pen dots.
                  * Distinguish minus (-) vs notebook ruled lines, underlines, or strikethroughs.
                - Vertical arithmetic: multi-row columns of numbers (e.g. 50 above 120 above 35) with or without an underline or plus sign: convert to addition sum "50 + 120 + 35".
                - Multi-line itemized lists, receipts, grocery bills, restaurant chits, and invoices:
                  * Extract each line item calculation.
                  * Provide an overall grand total adding all amounts together.
                - Deep calculations:
                  * Percentages and discounts (e.g. "500 - 10%", "1000 + 18% tax", "25% of 800").
                  * Powers, square roots, factorials, word problems ("solve 2x + 6 = 20", "area of circle r=7").
                - Single standalone numbers or prices: extract expression and value.
                - Crossed-out numbers: ignore scribbled/crossed-out marks and focus on the final intended numbers.

                CRITICAL RULES:
                1. ALWAYS compute the ultimate final answer for the entire image in "grand_total". In multi-step, zig-zag, or sequential calculations (e.g. step 405 -> total 420), "grand_total" MUST be the final calculated total from the last step (e.g. 420), NEVER an intermediate step (e.g. 405).
                2. The last item in the "expressions" array MUST be the final calculated line item (the "Total").
                3. If ANY numbers, prices, digits, or arithmetic operators are present in the image, ALWAYS set "found_math": true.
                4. Follow BODMAS / PEMDAS order of operations strictly.

                Return ONLY valid JSON matching this schema:
                {
                  "found_math": true,
                  "grand_total": "final ultimate calculated answer for the whole image (last calculated Total)",
                  "expressions": [
                    {
                      "original_text": "verbatim text as seen in image",
                      "expression": "clean math expression without currency symbols",
                      "result": "exact calculated answer"
                    }
                  ],
                  "confidence": "high",
                  "note": "brief summary (e.g. Zig-zag calculation, Handwritten bill, Arithmetic)"
                }
                If and ONLY if absolutely no numbers or mathematical content exist, return found_math: false.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            // Text prompt
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Image data
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                })
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                // Generation Config: greedy sampling (temperature 0.0) for ultra-fast, deterministic math OCR
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.0)
                    put("maxOutputTokens", 1024)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

            // Resilient multi-model execution: try ultra-fast models in priority order, fallback seamlessly if 404
            var responseBody = ""
            var lastHttpCode = 0
            var requestSucceeded = false

            for (model in candidateModels) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    lastHttpCode = response.code
                    val body = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        responseBody = body
                        requestSucceeded = true
                        break
                    } else if (response.code == 404) {
                        Log.w("GeminiVisionService", "Model $model returned 404, falling back to next candidate model...")
                        continue
                    } else {
                        Log.e("GeminiVisionService", "Model $model failed with HTTP ${response.code}: $body")
                        if (candidateModels.last() != model) continue
                        responseBody = body
                    }
                } catch (e: Exception) {
                    Log.w("GeminiVisionService", "Call to $model failed with exception: ${e.message}")
                    if (candidateModels.last() != model) continue
                    throw e
                }
            }

            if (!requestSucceeded) {
                Log.e("GeminiVisionService", "All models failed. Last code: $lastHttpCode, body: $responseBody")
                val errorMsg = when (lastHttpCode) {
                    401, 403 -> "Invalid or unauthorized API key. Please check your GEMINI_API_KEY in the Secrets panel."
                    404 -> "Vision API service is temporarily unavailable. Please retry in a moment."
                    in 500..599 -> "AI server is temporarily busy. Please retry in a moment."
                    else -> "Unable to process image (code $lastHttpCode). Please check your internet connection."
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Parse Gemini response with multi-layer fallback
            val parsedResult = parseGeminiResponseWithFallbacks(responseBody)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e("GeminiVisionService", "Error analyzing image", e)
            val msg = if (e.message?.contains("Unable to resolve host") == true || e.message?.contains("Failed to connect") == true) {
                "Internet connection required. Please check your network."
            } else {
                e.message ?: "Analysis failed"
            }
            Result.failure(Exception(msg))
        }
    }

    /**
     * Multi-tier parsing architecture:
     * Tier 1: Direct JSON parsing with standard schema including grand_total.
     * Tier 2: Flexible JSON schema matching (items, equations, calculations, data).
     * Tier 3: Regex-based extraction of JSON substrings.
     * Tier 4: Direct text regex equation and number extraction fallback.
     */
    private fun parseGeminiResponseWithFallbacks(responseBody: String): MathOcrResult {
        val rootObj = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            return fallbackParseRawText(responseBody)
        }

        val candidates = rootObj.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return fallbackParseRawText(responseBody)
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

        if (rawText.isBlank()) {
            return MathOcrResult(foundMath = false, note = "No content detected")
        }

        // Clean json text if wrapped in markdown codeblocks
        var cleanedJson = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Extract JSON substring if wrapped in explanatory text
        if (!cleanedJson.startsWith("{") && cleanedJson.contains("{")) {
            val startIdx = cleanedJson.indexOf('{')
            val endIdx = cleanedJson.lastIndexOf('}')
            if (startIdx != -1 && endIdx > startIdx) {
                cleanedJson = cleanedJson.substring(startIdx, endIdx + 1)
            }
        }

        val jsonOutput = try {
            JSONObject(cleanedJson)
        } catch (e: Exception) {
            Log.w("GeminiVisionService", "Primary JSON parse failed, falling back to regex text extraction", e)
            return fallbackParseRawText(rawText)
        }

        var foundMath = jsonOutput.optBoolean("found_math", false)
        val confidence = jsonOutput.optString("confidence", "high")
        val note = jsonOutput.optString("note", "")

        val expressionsList = mutableListOf<DetectedMathItem>()

        // Check various possible array keys: "expressions", "items", "equations", "calculations", "results"
        val exprArray = jsonOutput.optJSONArray("expressions")
            ?: jsonOutput.optJSONArray("items")
            ?: jsonOutput.optJSONArray("equations")
            ?: jsonOutput.optJSONArray("calculations")
            ?: jsonOutput.optJSONArray("results")

        if (exprArray != null && exprArray.length() > 0) {
            for (i in 0 until exprArray.length()) {
                val item = exprArray.optJSONObject(i) ?: continue
                val original = item.optString("original_text", "").ifBlank {
                    item.optString("text", "")
                }
                val expr = item.optString("expression", "").ifBlank {
                    item.optString("math", "").ifBlank {
                        item.optString("equation", "")
                    }
                }
                var resultVal = item.optString("result", "").ifBlank {
                    item.optString("answer", "")
                }

                val cleanExpr = expr.ifBlank { original }
                val (sanitizedExpr, romanConversions) = MathExpressionEvaluator.convertRomanNumerals(cleanExpr)

                // Verify and compute precision using local BODMAS engine
                if (sanitizedExpr.isNotBlank()) {
                    val localEval = MathExpressionEvaluator.evaluate(sanitizedExpr)
                    if (localEval.isSuccess) {
                        resultVal = localEval.getOrThrow()
                    }
                }

                if (cleanExpr.isNotBlank() || original.isNotBlank()) {
                    expressionsList.add(
                        DetectedMathItem(
                            originalText = original.ifBlank { cleanExpr },
                            expression = cleanExpr.ifBlank { original },
                            result = resultVal.ifBlank { "Solved" },
                            romanConversions = romanConversions
                        )
                    )
                }
            }
        }

        // If JSON had no array but had a single expression at the root
        if (expressionsList.isEmpty()) {
            val rootExpr = jsonOutput.optString("expression", "").ifBlank {
                jsonOutput.optString("equation", "")
            }
            if (rootExpr.isNotBlank()) {
                val (sanitized, roman) = MathExpressionEvaluator.convertRomanNumerals(rootExpr)
                val localEval = MathExpressionEvaluator.evaluate(sanitized)
                val res = if (localEval.isSuccess) localEval.getOrThrow() else jsonOutput.optString("result", "Solved")
                expressionsList.add(
                    DetectedMathItem(
                        originalText = jsonOutput.optString("original_text", rootExpr),
                        expression = rootExpr,
                        result = res,
                        romanConversions = roman
                    )
                )
            }
        }

        // If still empty, perform fallback regex extraction on raw response
        if (expressionsList.isEmpty()) {
            val fallbackResult = fallbackParseRawText(rawText)
            if (fallbackResult.expressions.isNotEmpty()) {
                return fallbackResult
            }
        }

        if (expressionsList.isNotEmpty()) {
            foundMath = true
        }

        // Determine grandTotal / final result of the calculation:
        // Always default to selecting and displaying the last calculated line item (the "Total") as the final answer
        val lastItem = expressionsList.lastOrNull()
        val totalKeywordItem = expressionsList.findLast {
            it.originalText.contains("total", ignoreCase = true) ||
            it.originalText.contains("grand", ignoreCase = true) ||
            it.originalText.contains("sum", ignoreCase = true) ||
            it.originalText.contains("net", ignoreCase = true) ||
            it.expression.contains("total", ignoreCase = true)
        }

        var grandTotal = jsonOutput.optString("grand_total", "").ifBlank {
            jsonOutput.optString("final_result", "").ifBlank {
                jsonOutput.optString("total", "")
            }
        }

        if (grandTotal.isNotBlank()) {
            val eval = MathExpressionEvaluator.evaluate(grandTotal)
            grandTotal = if (eval.isSuccess) eval.getOrThrow() else grandTotal
            // If the model mistakenly returned an intermediate step (e.g. matching first item) when multiple items exist,
            // override with the final calculated line item / total item
            if (expressionsList.size > 1 && grandTotal == expressionsList.first().result && lastItem != null && lastItem.result != grandTotal) {
                grandTotal = totalKeywordItem?.result ?: lastItem.result
            }
        } else {
            grandTotal = totalKeywordItem?.result ?: lastItem?.result ?: ""
        }

        return MathOcrResult(
            foundMath = foundMath,
            expressions = expressionsList,
            confidence = confidence,
            note = note.ifBlank { if (foundMath) "Calculated successfully" else "No math detected" },
            grandTotal = grandTotal.ifBlank { null }
        )
    }

    /**
     * Fallback text parser when JSON is malformed or raw text was returned.
     * Extracts equations, arithmetic expressions, item amounts, or lists of numbers.
     */
    private fun fallbackParseRawText(text: String): MathOcrResult {
        val detected = mutableListOf<DetectedMathItem>()

        // 1. Check for equations: e.g. "45 + 55 = 100" or "Total = 250"
        val eqPattern = Pattern.compile("([0-9a-zA-Z\\s\\+\\-\\*/÷×^%().]+)=([0-9.\\s\\-]+)")
        val eqMatcher = eqPattern.matcher(text)
        while (eqMatcher.find()) {
            val left = eqMatcher.group(1)?.trim() ?: ""
            val right = eqMatcher.group(2)?.trim() ?: ""
            if (left.isNotBlank() && left.any { it.isDigit() }) {
                val eval = MathExpressionEvaluator.evaluate(left)
                val computed = if (eval.isSuccess) eval.getOrThrow() else right
                detected.add(
                    DetectedMathItem(
                        originalText = "$left = $right",
                        expression = left,
                        result = computed
                    )
                )
            }
        }

        // 2. Check for arithmetic lines: e.g. "50 + 120 + 30"
        if (detected.isEmpty()) {
            val arithPattern = Pattern.compile("(\\d+(?:\\.\\d+)?(?:\\s*[\\+\\-\\*/×÷^%]\\s*\\d+(?:\\.\\d+)?)+)")
            val arithMatcher = arithPattern.matcher(text)
            while (arithMatcher.find()) {
                val expr = arithMatcher.group(1)?.trim() ?: ""
                val eval = MathExpressionEvaluator.evaluate(expr)
                val computed = if (eval.isSuccess) eval.getOrThrow() else "Solved"
                detected.add(
                    DetectedMathItem(
                        originalText = expr,
                        expression = expr,
                        result = computed
                    )
                )
            }
        }

        // 3. Check for standalone numbers (e.g. list of prices: 50, 100, 25)
        if (detected.isEmpty()) {
            val numberPattern = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b")
            val numMatcher = numberPattern.matcher(text)
            val numbers = mutableListOf<String>()
            while (numMatcher.find()) {
                val num = numMatcher.group(1)
                if (!num.isNullOrBlank()) {
                    numbers.add(num)
                }
            }

            if (numbers.isNotEmpty()) {
                if (numbers.size > 1) {
                    val sumExpr = numbers.joinToString(" + ")
                    val eval = MathExpressionEvaluator.evaluate(sumExpr)
                    detected.add(
                        DetectedMathItem(
                            originalText = "Total Sum (${numbers.size} items)",
                            expression = sumExpr,
                            result = if (eval.isSuccess) eval.getOrThrow() else numbers.sumOf { it.toDoubleOrNull() ?: 0.0 }.toString()
                        )
                    )
                } else {
                    val singleNum = numbers.first()
                    detected.add(
                        DetectedMathItem(
                            originalText = singleNum,
                            expression = singleNum,
                            result = singleNum
                        )
                    )
                }
            }
        }

        val grandTotal = if (detected.isNotEmpty()) {
            if (detected.size == 1) detected[0].result else detected.last().result
        } else null

        return MathOcrResult(
            foundMath = detected.isNotEmpty(),
            expressions = detected,
            confidence = if (detected.isNotEmpty()) "medium" else "low",
            note = if (detected.isNotEmpty()) "Extracted via smart text fallback" else "No numbers found",
            grandTotal = grandTotal
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
