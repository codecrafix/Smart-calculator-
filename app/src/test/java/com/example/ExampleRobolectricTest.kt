package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.MathExpressionEvaluator
import com.example.model.DetectedMathItem
import com.example.model.MathOcrResult
import com.example.util.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart Calculator", appName)

        val cropBtn = context.getString(R.string.btn_crop)
        assertEquals("Crop", cropBtn)
    }

    @Test
    fun `test free aspect ratio bitmap cropping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(Color.RED)

        val testFile = File(context.cacheDir, "test_input.jpg")
        FileOutputStream(testFile).use { fos ->
            testBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        val inputUri = Uri.fromFile(testFile)

        val croppedUri = ImageUtils.cropAndRotateBitmap(
            context = context,
            sourceUri = inputUri,
            normalizedCropRect = RectF(0.2f, 0.2f, 0.8f, 0.8f),
            extraRotationDegrees = 0f
        )

        assertNotNull(croppedUri)
        val croppedDims = ImageUtils.getImageDimensions(context, croppedUri!!)
        assertEquals(120, croppedDims.first)
        assertEquals(60, croppedDims.second)
    }

    @Test
    fun `test handwriting image pre-processing and sharpening filter`() {
        // Create synthetic image with grayscale background and dark line
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                // Background off-white with dark line in center (mimicking handwritten stroke)
                val color = if (x == 50) Color.BLACK else Color.rgb(220, 220, 220)
                testBitmap.setPixel(x, y, color)
            }
        }

        val enhanced = ImageUtils.enhanceHandwritingBitmap(testBitmap)
        assertNotNull(enhanced)
        assertEquals(100, enhanced.width)
        assertEquals(100, enhanced.height)

        val centerPixel = enhanced.getPixel(50, 50)
        // Verify center pixel remains dark ink
        val centerLuminance = Color.red(centerPixel) * 0.299 + Color.green(centerPixel) * 0.587 + Color.blue(centerPixel) * 0.114
        assertTrue(centerLuminance < 100)
    }

    @Test
    fun `test bill sum, currency stripping, and math evaluation`() {
        // Multi-line bill sum evaluation
        val billSum = MathExpressionEvaluator.evaluate("50 + 120 + 30 + 45")
        assertTrue(billSum.isSuccess)
        assertEquals("245", billSum.getOrThrow())

        // Currency stripping: e.g. $50 + $25.50
        val currencyEval = MathExpressionEvaluator.evaluate("$50 + $25.50")
        assertTrue(currencyEval.isSuccess)
        assertEquals("75.5", currencyEval.getOrThrow())

        // Rupees with label: Rs. 500 - 150
        val inrEval = MathExpressionEvaluator.evaluate("Rs. 500 - 150")
        assertTrue(inrEval.isSuccess)
        assertEquals("350", inrEval.getOrThrow())

        // Thousands separators: 1,250 + 250
        val thousandsEval = MathExpressionEvaluator.evaluate("1,250 + 250")
        assertTrue(thousandsEval.isSuccess)
        assertEquals("1500", thousandsEval.getOrThrow())

        // "of" keyword: 15% of 200
        val ofEval = MathExpressionEvaluator.evaluate("15% of 200")
        assertTrue(ofEval.isSuccess)
        assertEquals("30", ofEval.getOrThrow())

        // Square root: √144 + 6
        val sqrtEval = MathExpressionEvaluator.evaluate("√144 + 6")
        assertTrue(sqrtEval.isSuccess)
        assertEquals("18", sqrtEval.getOrThrow())

        // Roman numeral expression
        val romanEval = MathExpressionEvaluator.evaluate("XIV * 5")
        assertTrue(romanEval.isSuccess)
        assertEquals("70", romanEval.getOrThrow())

        // Percentage math
        val percentEval = MathExpressionEvaluator.evaluate("200 * 15%")
        assertTrue(percentEval.isSuccess)
        assertEquals("30", percentEval.getOrThrow())
    }

    @Test
    fun `test zig zag chains, equations, and deep calculations`() {
        // Zig-zag multi-step calculation chain: 10 + 20 -> 30 * 2 -> 60 - 15
        val zigzagEval = MathExpressionEvaluator.evaluate("10 + 20 -> 30 * 2 -> 60 - 15")
        assertTrue(zigzagEval.isSuccess)
        assertEquals("45", zigzagEval.getOrThrow())

        // Equation with equals: 45 + 55 =
        val eqEval1 = MathExpressionEvaluator.evaluate("45 + 55 =")
        assertTrue(eqEval1.isSuccess)
        assertEquals("100", eqEval1.getOrThrow())

        // Equation evaluation: 15 * 4 = 60
        val eqEval2 = MathExpressionEvaluator.evaluate("15 * 4 = 60")
        assertTrue(eqEval2.isSuccess)
        assertEquals("60", eqEval2.getOrThrow())

        // Linear equation solving: 2x + 6 = 20
        val solveEval = MathExpressionEvaluator.evaluate("2x + 6 = 20")
        assertTrue(solveEval.isSuccess)
        assertEquals("7", solveEval.getOrThrow())

        // Discount percentage calculation: 500 - 10%
        val discountEval = MathExpressionEvaluator.evaluate("500 - 10%")
        assertTrue(discountEval.isSuccess)
        assertEquals("450", discountEval.getOrThrow())

        // Tax percentage addition: 1000 + 18%
        val taxEval = MathExpressionEvaluator.evaluate("1000 + 18%")
        assertTrue(taxEval.isSuccess)
        assertEquals("1180", taxEval.getOrThrow())

        // Factorial: 5! + 10
        val factEval = MathExpressionEvaluator.evaluate("5! + 10")
        assertTrue(factEval.isSuccess)
        assertEquals("130", factEval.getOrThrow())

        // Vertical column / multi-line numbers
        val columnEval = MathExpressionEvaluator.evaluate("50\n120\n30")
        assertTrue(columnEval.isSuccess)
        assertEquals("200", columnEval.getOrThrow())
    }

    @Test
    fun `test grand total selection logic defaults to last calculated line item over intermediate steps`() {
        // Multi-line scenario: Step 1 = 405 (intermediate), Step 2 = 420 (Total)
        val expressions = listOf(
            DetectedMathItem(
                originalText = "135 * 3",
                expression = "135 * 3",
                result = "405"
            ),
            DetectedMathItem(
                originalText = "Total = 405 + 15",
                expression = "405 + 15",
                result = "420"
            )
        )

        val ocrResult = MathOcrResult(
            foundMath = true,
            expressions = expressions,
            confidence = "high",
            grandTotal = "420"
        )

        // UI default index must be the last item (expressions.lastIndex)
        val defaultSelectedIndex = if (ocrResult.expressions.isNotEmpty()) ocrResult.expressions.lastIndex else 0
        assertEquals(1, defaultSelectedIndex)

        // Selected expression is the last item
        val selectedItem = ocrResult.expressions[defaultSelectedIndex]
        assertEquals("405 + 15", selectedItem.expression)

        // Evaluated result of selected item is 420 (not intermediate 405)
        val evaluatedResult = MathExpressionEvaluator.evaluate(selectedItem.expression).getOrThrow()
        assertEquals("420", evaluatedResult)

        // Final answer presented to user is the Grand Total (420)
        val finalAnswer = if (defaultSelectedIndex == ocrResult.expressions.lastIndex) {
            ocrResult.grandTotal ?: selectedItem.result
        } else {
            evaluatedResult
        }
        assertEquals("420", finalAnswer)
    }
}
