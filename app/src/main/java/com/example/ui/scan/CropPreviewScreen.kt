package com.example.ui.scan

import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.components.InteractiveCropOverlay
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AspectPreset {
    FREE,
    SQUARE,
    RATIO_4_3,
    RATIO_16_9,
    FULL
}

@Composable
fun CropPreviewScreen(
    imageUri: Uri,
    onRetake: () -> Unit,
    onConfirmCalculate: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var originalImageUri by remember { mutableStateOf(imageUri) }
    var currentImageUri by remember { mutableStateOf(imageUri) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    var isCroppingActive by remember { mutableStateOf(false) }
    var normalizedCrop by remember { mutableStateOf(RectF(0.05f, 0.05f, 0.95f, 0.95f)) }
    var selectedPreset by remember { mutableStateOf(AspectPreset.FREE) }
    var isProcessingCrop by remember { mutableStateOf(false) }

    var intrinsicDrawableSize by remember { mutableStateOf<Size?>(null) }

    // Intercept back button: exit crop mode if active, else retake
    BackHandler {
        if (isCroppingActive) {
            isCroppingActive = false
        } else {
            onRetake()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("crop_preview_screen")
    ) {
        // Main Image Display Container with Free Aspect Ratio (ContentScale.Fit)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = if (isCroppingActive) 150.dp else 120.dp),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            // Calculate image dimensions from loaded drawable or file
            val (rawW, rawH) = remember(currentImageUri, intrinsicDrawableSize) {
                if (intrinsicDrawableSize != null && intrinsicDrawableSize!!.width > 0f) {
                    Pair(intrinsicDrawableSize!!.width, intrinsicDrawableSize!!.height)
                } else {
                    val d = ImageUtils.getImageDimensions(context, currentImageUri)
                    Pair(d.first.toFloat().coerceAtLeast(1f), d.second.toFloat().coerceAtLeast(1f))
                }
            }

            val isRotated90 = (rotationDegrees.toInt() / 90) % 2 != 0
            val effectiveW = if (isRotated90) rawH else rawW
            val effectiveH = if (isRotated90) rawW else rawH

            val scale = minOf(containerWidth / effectiveW, containerHeight / effectiveH)
            val displayedWidth = effectiveW * scale
            val displayedHeight = effectiveH * scale
            val offsetX = (containerWidth - displayedWidth) / 2f
            val offsetY = (containerHeight - displayedHeight) / 2f

            val imageBounds = Rect(
                left = offsetX,
                top = offsetY,
                right = offsetX + displayedWidth,
                bottom = offsetY + displayedHeight
            )

            // Render full image with original aspect ratio intact
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Calculation image preview",
                contentScale = ContentScale.Fit,
                onSuccess = { successResult ->
                    val drawable = successResult.result.drawable
                    intrinsicDrawableSize = Size(
                        drawable.intrinsicWidth.toFloat().coerceAtLeast(1f),
                        drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationDegrees)
                    .testTag("preview_image_display")
            )

            // Interactive Manual Crop Overlay (Visible only when user toggles Crop tool)
            if (isCroppingActive) {
                InteractiveCropOverlay(
                    imageBounds = imageBounds,
                    normalizedCrop = normalizedCrop,
                    onCropChange = { newRect ->
                        selectedPreset = AspectPreset.FREE
                        normalizedCrop = newRect
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Bar: Back button, Free Aspect Ratio badge, Rotate & Reset
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Retake Button
                IconButton(
                    onClick = {
                        if (isCroppingActive) isCroppingActive = false else onRetake()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("preview_back_button")
                ) {
                    Icon(
                        imageVector = if (isCroppingActive) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isCroppingActive) "Cancel Crop" else "Back",
                        tint = Color.White
                    )
                }

                // Title / Badge indicating mode
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCroppingActive) Icons.Default.Crop else Icons.Default.CropFree,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isCroppingActive) {
                                stringResource(R.string.crop_mode_title)
                            } else {
                                stringResource(R.string.free_aspect_ratio_badge)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Action icons: Rotate and Reset
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Reset to original if image was cropped
                    if (currentImageUri != originalImageUri && !isCroppingActive) {
                        IconButton(
                            onClick = {
                                currentImageUri = originalImageUri
                                rotationDegrees = 0f
                                normalizedCrop = RectF(0.05f, 0.05f, 0.95f, 0.95f)
                                Toast.makeText(context, "Restored original image", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .testTag("preview_reset_original_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset to Original",
                                tint = Color.White
                            )
                        }
                    }

                    // Rotate 90°
                    IconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90f) % 360f
                        },
                        enabled = !isCroppingActive,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .testTag("preview_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate Image",
                            tint = if (isCroppingActive) Color.Gray else Color.White
                        )
                    }
                }
            }
        }

        // Bottom Action Bar: Dynamic based on regular preview vs. manual crop mode
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xFF0F172A).copy(alpha = 0.96f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isCroppingActive) {
                // Preset Ratio Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedPreset == AspectPreset.FREE,
                        onClick = { selectedPreset = AspectPreset.FREE },
                        label = { Text("Free", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedPreset == AspectPreset.FULL,
                        onClick = {
                            selectedPreset = AspectPreset.FULL
                            normalizedCrop = RectF(0f, 0f, 1f, 1f)
                        },
                        label = { Text("Full", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedPreset == AspectPreset.SQUARE,
                        onClick = {
                            selectedPreset = AspectPreset.SQUARE
                            normalizedCrop = RectF(0.15f, 0.15f, 0.85f, 0.85f)
                        },
                        label = { Text("1:1 Square", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedPreset == AspectPreset.RATIO_4_3,
                        onClick = {
                            selectedPreset = AspectPreset.RATIO_4_3
                            normalizedCrop = RectF(0.1f, 0.2f, 0.9f, 0.8f)
                        },
                        label = { Text("4:3", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedPreset == AspectPreset.RATIO_16_9,
                        onClick = {
                            selectedPreset = AspectPreset.RATIO_16_9
                            normalizedCrop = RectF(0.05f, 0.3f, 0.95f, 0.7f)
                        },
                        label = { Text("16:9", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Crop Mode Actions: Cancel, Reset to Full, Apply Crop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Crop
                    OutlinedButton(
                        onClick = { isCroppingActive = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("crop_cancel_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = stringResource(R.string.btn_cancel),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Reset crop box to full bounds
                    FilledTonalButton(
                        onClick = {
                            selectedPreset = AspectPreset.FULL
                            normalizedCrop = RectF(0f, 0f, 1f, 1f)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("crop_reset_full_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_reset_crop),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Apply Crop
                    Button(
                        onClick = {
                            isProcessingCrop = true
                            coroutineScope.launch {
                                val croppedUri = withContext(Dispatchers.IO) {
                                    ImageUtils.cropAndRotateBitmap(
                                        context = context,
                                        sourceUri = currentImageUri,
                                        normalizedCropRect = normalizedCrop,
                                        extraRotationDegrees = rotationDegrees
                                    )
                                }
                                isProcessingCrop = false
                                if (croppedUri != null) {
                                    currentImageUri = croppedUri
                                    rotationDegrees = 0f // baked into cropped image
                                    isCroppingActive = false
                                    Toast.makeText(context, "Cropped successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Crop failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isProcessingCrop,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("crop_apply_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        )
                    ) {
                        if (isProcessingCrop) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_apply_crop),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            } else {
                // Standard Preview Actions: Retake, Manual Crop button, Calculate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retake Button
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(52.dp)
                            .testTag("preview_retake_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.btn_retake),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // Optional Manual Crop Tool Button
                    FilledTonalButton(
                        onClick = {
                            if (rotationDegrees % 360f != 0f) {
                                coroutineScope.launch {
                                    val bakedUri = withContext(Dispatchers.IO) {
                                        ImageUtils.rotateBitmapAndSave(context, currentImageUri, rotationDegrees)
                                    }
                                    if (bakedUri != null) {
                                        currentImageUri = bakedUri
                                        rotationDegrees = 0f
                                    }
                                    normalizedCrop = RectF(0.05f, 0.05f, 0.95f, 0.95f)
                                    selectedPreset = AspectPreset.FREE
                                    isCroppingActive = true
                                }
                            } else {
                                normalizedCrop = RectF(0.05f, 0.05f, 0.95f, 0.95f)
                                selectedPreset = AspectPreset.FREE
                                isCroppingActive = true
                            }
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .height(52.dp)
                            .testTag("preview_manual_crop_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.btn_crop),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // Primary Calculate / Confirm Button
                    Button(
                        onClick = {
                            if (rotationDegrees % 360f != 0f) {
                                coroutineScope.launch {
                                    val bakedUri = withContext(Dispatchers.IO) {
                                        ImageUtils.rotateBitmapAndSave(context, currentImageUri, rotationDegrees)
                                    }
                                    onConfirmCalculate(bakedUri ?: currentImageUri)
                                }
                            } else {
                                onConfirmCalculate(currentImageUri)
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("preview_calculate_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.btn_calculate),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
