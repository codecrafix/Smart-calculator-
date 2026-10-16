package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiVisionService
import com.example.model.ActiveTab
import com.example.model.MathOcrResult
import com.example.model.ScanUiState
import com.example.model.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("smart_calc_prefs", Context.MODE_PRIVATE)
    private val visionService = GeminiVisionService(application)

    private val _themeMode = MutableStateFlow(loadSavedTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _activeTab = MutableStateFlow(ActiveTab.SCAN)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _calculatorExpression = MutableStateFlow("")
    val calculatorExpression: StateFlow<String> = _calculatorExpression.asStateFlow()

    private var activeAnalysisJob: Job? = null

    private fun loadSavedTheme(): ThemeMode {
        val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(saved)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun toggleTheme() {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        _themeMode.value = next
        prefs.edit().putString("theme_mode", next.name).apply()
    }

    fun setActiveTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun openCamera() {
        _scanState.value = ScanUiState.CameraPreview()
    }

    fun closeCamera() {
        _scanState.value = ScanUiState.Idle
    }

    fun onImageSelected(uri: Uri) {
        _scanState.value = ScanUiState.CropPreview(imageUri = uri.toString())
    }

    fun retakeScan() {
        activeAnalysisJob?.cancel()
        _scanState.value = ScanUiState.Idle
    }

    fun cancelProcessing(lastUri: Uri) {
        activeAnalysisJob?.cancel()
        _scanState.value = ScanUiState.CropPreview(imageUri = lastUri.toString())
    }

    fun calculateFromImage(uri: Uri) {
        _scanState.value = ScanUiState.Processing(imageUri = uri.toString())

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            val result = visionService.analyzeCalculationImage(uri)
            if (result.isSuccess) {
                val ocrResult = result.getOrThrow()
                _scanState.value = ScanUiState.Success(
                    imageUri = uri.toString(),
                    result = ocrResult
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "An unexpected error occurred."
                _scanState.value = ScanUiState.Error(
                    message = errorMsg,
                    canRetry = true,
                    imageUri = uri.toString()
                )
            }
        }
    }

    fun openInCalculator(expr: String) {
        _calculatorExpression.value = expr
        _activeTab.value = ActiveTab.CALCULATOR
    }

    fun setSampleInCalculator(expr: String) {
        _calculatorExpression.value = expr
        _activeTab.value = ActiveTab.CALCULATOR
    }

    fun updateCalculatorExpression(expr: String) {
        _calculatorExpression.value = expr
    }
}
