package com.example.model

data class DetectedMathItem(
    val originalText: String,
    val expression: String,
    val result: String,
    val romanConversions: List<Pair<String, Int>> = emptyList()
)

data class MathOcrResult(
    val foundMath: Boolean,
    val expressions: List<DetectedMathItem> = emptyList(),
    val confidence: String = "medium",
    val note: String = "",
    val grandTotal: String? = null
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ActiveTab {
    SCAN,
    CALCULATOR
}

sealed class ScanUiState {
    object Idle : ScanUiState()
    data class CameraPreview(val flashEnabled: Boolean = false) : ScanUiState()
    data class CropPreview(val imageUri: String, val rotationDegrees: Int = 0) : ScanUiState()
    data class Processing(val imageUri: String) : ScanUiState()
    data class Success(
        val imageUri: String,
        val result: MathOcrResult,
        val activeExpressionIndex: Int = 0,
        val editedExpressions: Map<Int, String> = emptyMap(),
        val evaluatedResults: Map<Int, String> = emptyMap()
    ) : ScanUiState()
    data class Error(val message: String, val canRetry: Boolean = true, val imageUri: String? = null) : ScanUiState()
}
