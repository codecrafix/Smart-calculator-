package com.example.ui.components

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlueLight

private enum class DragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT_EDGE,
    RIGHT_EDGE,
    TOP_EDGE,
    BOTTOM_EDGE,
    CENTER_MOVE
}

/**
 * Interactive free-aspect-ratio cropping overlay.
 * Uses rememberUpdatedState to ensure continuous 60fps drag gestures
 * without interrupting recomposition cycles.
 */
@Composable
fun InteractiveCropOverlay(
    imageBounds: Rect,
    normalizedCrop: RectF,
    onCropChange: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageBounds.width <= 0f || imageBounds.height <= 0f) {
        return
    }

    val currentCrop by rememberUpdatedState(normalizedCrop)
    val currentOnCropChange by rememberUpdatedState(onCropChange)
    val currentBounds by rememberUpdatedState(imageBounds)

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val hitTargetPx = 54.dp.toPx()

                detectDragGestures(
                    onDragStart = { downOffset ->
                        val b = currentBounds
                        val c = currentCrop
                        val cropPxLeft = b.left + c.left * b.width
                        val cropPxTop = b.top + c.top * b.height
                        val cropPxRight = b.left + c.right * b.width
                        val cropPxBottom = b.top + c.bottom * b.height

                        // Check corners first (highest priority)
                        val dTL = (downOffset - Offset(cropPxLeft, cropPxTop)).getDistance()
                        val dTR = (downOffset - Offset(cropPxRight, cropPxTop)).getDistance()
                        val dBL = (downOffset - Offset(cropPxLeft, cropPxBottom)).getDistance()
                        val dBR = (downOffset - Offset(cropPxRight, cropPxBottom)).getDistance()

                        activeHandle = when {
                            dTL <= hitTargetPx -> DragHandle.TOP_LEFT
                            dTR <= hitTargetPx -> DragHandle.TOP_RIGHT
                            dBL <= hitTargetPx -> DragHandle.BOTTOM_LEFT
                            dBR <= hitTargetPx -> DragHandle.BOTTOM_RIGHT
                            // Check edges
                            kotlin.math.abs(downOffset.x - cropPxLeft) <= hitTargetPx &&
                                    downOffset.y in (cropPxTop - hitTargetPx)..(cropPxBottom + hitTargetPx) -> DragHandle.LEFT_EDGE
                            kotlin.math.abs(downOffset.x - cropPxRight) <= hitTargetPx &&
                                    downOffset.y in (cropPxTop - hitTargetPx)..(cropPxBottom + hitTargetPx) -> DragHandle.RIGHT_EDGE
                            kotlin.math.abs(downOffset.y - cropPxTop) <= hitTargetPx &&
                                    downOffset.x in (cropPxLeft - hitTargetPx)..(cropPxRight + hitTargetPx) -> DragHandle.TOP_EDGE
                            kotlin.math.abs(downOffset.y - cropPxBottom) <= hitTargetPx &&
                                    downOffset.x in (cropPxLeft - hitTargetPx)..(cropPxRight + hitTargetPx) -> DragHandle.BOTTOM_EDGE
                            // Check center (pan/move whole crop frame)
                            downOffset.x in cropPxLeft..cropPxRight && downOffset.y in cropPxTop..cropPxBottom -> DragHandle.CENTER_MOVE
                            else -> DragHandle.NONE
                        }
                    },
                    onDragEnd = {
                        activeHandle = DragHandle.NONE
                    },
                    onDragCancel = {
                        activeHandle = DragHandle.NONE
                    },
                    onDrag = { _, dragAmount ->
                        if (activeHandle == DragHandle.NONE) return@detectDragGestures

                        val b = currentBounds
                        if (b.width <= 0f || b.height <= 0f) return@detectDragGestures

                        val deltaNormX = dragAmount.x / b.width
                        val deltaNormY = dragAmount.y / b.height

                        val minSize = 0.05f // minimum 5% dimension

                        val prev = currentCrop
                        var newLeft = prev.left
                        var newTop = prev.top
                        var newRight = prev.right
                        var newBottom = prev.bottom

                        when (activeHandle) {
                            DragHandle.TOP_LEFT -> {
                                newLeft = (newLeft + deltaNormX).coerceIn(0f, newRight - minSize)
                                newTop = (newTop + deltaNormY).coerceIn(0f, newBottom - minSize)
                            }
                            DragHandle.TOP_RIGHT -> {
                                newRight = (newRight + deltaNormX).coerceIn(newLeft + minSize, 1f)
                                newTop = (newTop + deltaNormY).coerceIn(0f, newBottom - minSize)
                            }
                            DragHandle.BOTTOM_LEFT -> {
                                newLeft = (newLeft + deltaNormX).coerceIn(0f, newRight - minSize)
                                newBottom = (newBottom + deltaNormY).coerceIn(newTop + minSize, 1f)
                            }
                            DragHandle.BOTTOM_RIGHT -> {
                                newRight = (newRight + deltaNormX).coerceIn(newLeft + minSize, 1f)
                                newBottom = (newBottom + deltaNormY).coerceIn(newTop + minSize, 1f)
                            }
                            DragHandle.LEFT_EDGE -> {
                                newLeft = (newLeft + deltaNormX).coerceIn(0f, newRight - minSize)
                            }
                            DragHandle.RIGHT_EDGE -> {
                                newRight = (newRight + deltaNormX).coerceIn(newLeft + minSize, 1f)
                            }
                            DragHandle.TOP_EDGE -> {
                                newTop = (newTop + deltaNormY).coerceIn(0f, newBottom - minSize)
                            }
                            DragHandle.BOTTOM_EDGE -> {
                                newBottom = (newBottom + deltaNormY).coerceIn(newTop + minSize, 1f)
                            }
                            DragHandle.CENTER_MOVE -> {
                                val width = newRight - newLeft
                                val height = newBottom - newTop
                                var shiftedLeft = newLeft + deltaNormX
                                var shiftedTop = newTop + deltaNormY

                                if (shiftedLeft < 0f) shiftedLeft = 0f
                                if (shiftedLeft + width > 1f) shiftedLeft = 1f - width

                                if (shiftedTop < 0f) shiftedTop = 0f
                                if (shiftedTop + height > 1f) shiftedTop = 1f - height

                                newLeft = shiftedLeft
                                newRight = shiftedLeft + width
                                newTop = shiftedTop
                                newBottom = shiftedTop + height
                            }
                            DragHandle.NONE -> Unit
                        }

                        currentOnCropChange(RectF(newLeft, newTop, newRight, newBottom))
                    }
                )
            }
    ) {
        val cropLeftPx = imageBounds.left + normalizedCrop.left * imageBounds.width
        val cropTopPx = imageBounds.top + normalizedCrop.top * imageBounds.height
        val cropRightPx = imageBounds.left + normalizedCrop.right * imageBounds.width
        val cropBottomPx = imageBounds.top + normalizedCrop.bottom * imageBounds.height
        val cropWidthPx = (cropRightPx - cropLeftPx).coerceAtLeast(1f)
        val cropHeightPx = (cropBottomPx - cropTopPx).coerceAtLeast(1f)

        // 1. Draw scrim outside the crop rectangle within the entire screen
        val fullRect = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val cropPath = Path().apply {
            addRect(Rect(cropLeftPx, cropTopPx, cropRightPx, cropBottomPx))
        }
        val scrimPath = Path.combine(PathOperation.Difference, fullRect, cropPath)
        drawPath(scrimPath, color = Color.Black.copy(alpha = 0.68f))

        // 2. Draw border around the crop area
        drawRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = Offset(cropLeftPx, cropTopPx),
            size = Size(cropWidthPx, cropHeightPx),
            style = Stroke(width = 1.8.dp.toPx())
        )

        // 3. Draw Rule of Thirds grid lines
        val oneThirdW = cropWidthPx / 3f
        val oneThirdH = cropHeightPx / 3f
        val gridColor = Color.White.copy(alpha = 0.35f)
        val gridStroke = Stroke(width = 1.dp.toPx())

        // Vertical grid lines
        drawLine(gridColor, Offset(cropLeftPx + oneThirdW, cropTopPx), Offset(cropLeftPx + oneThirdW, cropBottomPx), gridStroke.width)
        drawLine(gridColor, Offset(cropLeftPx + 2f * oneThirdW, cropTopPx), Offset(cropLeftPx + 2f * oneThirdW, cropBottomPx), gridStroke.width)

        // Horizontal grid lines
        drawLine(gridColor, Offset(cropLeftPx, cropTopPx + oneThirdH), Offset(cropRightPx, cropTopPx + oneThirdH), gridStroke.width)
        drawLine(gridColor, Offset(cropLeftPx, cropTopPx + 2f * oneThirdH), Offset(cropRightPx, cropTopPx + 2f * oneThirdH), gridStroke.width)

        // 4. Draw prominent corner handles
        val cornerLen = 28.dp.toPx()
        val cornerStroke = 4.5.dp.toPx()
        val cornerColor = CyanAccent

        // Top-Left
        drawLine(cornerColor, Offset(cropLeftPx - 1, cropTopPx), Offset(cropLeftPx + cornerLen, cropTopPx), cornerStroke, StrokeCap.Square)
        drawLine(cornerColor, Offset(cropLeftPx, cropTopPx - 1), Offset(cropLeftPx, cropTopPx + cornerLen), cornerStroke, StrokeCap.Square)

        // Top-Right
        drawLine(cornerColor, Offset(cropRightPx - cornerLen, cropTopPx), Offset(cropRightPx + 1, cropTopPx), cornerStroke, StrokeCap.Square)
        drawLine(cornerColor, Offset(cropRightPx, cropTopPx - 1), Offset(cropRightPx, cropTopPx + cornerLen), cornerStroke, StrokeCap.Square)

        // Bottom-Left
        drawLine(cornerColor, Offset(cropLeftPx - 1, cropBottomPx), Offset(cropLeftPx + cornerLen, cropBottomPx), cornerStroke, StrokeCap.Square)
        drawLine(cornerColor, Offset(cropLeftPx, cropBottomPx - cornerLen), Offset(cropLeftPx, cropBottomPx + 1), cornerStroke, StrokeCap.Square)

        // Bottom-Right
        drawLine(cornerColor, Offset(cropRightPx - cornerLen, cropBottomPx), Offset(cropRightPx + 1, cropBottomPx), cornerStroke, StrokeCap.Square)
        drawLine(cornerColor, Offset(cropRightPx, cropBottomPx - cornerLen), Offset(cropRightPx, cropBottomPx + 1), cornerStroke, StrokeCap.Square)

        // 5. Draw edge handles (mid-edge indicator pills)
        val edgeHandleLen = 22.dp.toPx()
        val edgeHandleStroke = 3.5.dp.toPx()
        val edgeColor = ElectricBlueLight

        val midX = cropLeftPx + cropWidthPx / 2f
        val midY = cropTopPx + cropHeightPx / 2f

        // Top edge center
        drawLine(edgeColor, Offset(midX - edgeHandleLen / 2f, cropTopPx), Offset(midX + edgeHandleLen / 2f, cropTopPx), edgeHandleStroke, StrokeCap.Round)
        // Bottom edge center
        drawLine(edgeColor, Offset(midX - edgeHandleLen / 2f, cropBottomPx), Offset(midX + edgeHandleLen / 2f, cropBottomPx), edgeHandleStroke, StrokeCap.Round)
        // Left edge center
        drawLine(edgeColor, Offset(cropLeftPx, midY - edgeHandleLen / 2f), Offset(cropLeftPx, midY + edgeHandleLen / 2f), edgeHandleStroke, StrokeCap.Round)
        // Right edge center
        drawLine(edgeColor, Offset(cropRightPx, midY - edgeHandleLen / 2f), Offset(cropRightPx, midY + edgeHandleLen / 2f), edgeHandleStroke, StrokeCap.Round)
    }
}
