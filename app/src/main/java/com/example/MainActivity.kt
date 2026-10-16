package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ActiveTab
import com.example.model.ScanUiState
import com.example.ui.MainViewModel
import com.example.ui.calculator.ManualCalculatorScreen
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.AppHeader
import com.example.ui.components.PermissionDialog
import com.example.ui.components.PermissionType
import com.example.ui.scan.CameraCaptureScreen
import com.example.ui.scan.CropPreviewScreen
import com.example.ui.scan.ProcessingScreen
import com.example.ui.scan.ResultScreen
import com.example.ui.scan.ScanHomeScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.SmartCalculatorTheme

enum class PendingSettingsAction {
    CAMERA,
    GALLERY
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install androidx.core splashscreen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            SmartCalculatorTheme(themeMode = themeMode) {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = { showSplash = false }
                    )
                } else {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("smart_calc_perms", Context.MODE_PRIVATE) }

    val currentTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val calcExpression by viewModel.calculatorExpression.collectAsStateWithLifecycle()

    // Permission popup states
    var showPermissionDialog by remember { mutableStateOf(false) }
    var activePermissionType by remember { mutableStateOf(PermissionType.CAMERA) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    var pendingActionAfterSettings by remember { mutableStateOf<PendingSettingsAction?>(null) }

    // Fallback ACTION_GET_CONTENT launcher for files/photos
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    // Modern Android Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    // Helper to open gallery with fallback to ACTION_GET_CONTENT
    fun openGallery() {
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                getContentLauncher.launch("image/*")
            }
        } catch (e: Exception) {
            // Robust fallback if Photo Picker throws on older/modified emulator
            getContentLauncher.launch("image/*")
        }
    }

    // Storage permission launcher (for older Android < 33 if needed)
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            val activity = context as? Activity
            val shouldShow = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_EXTERNAL_STORAGE)
            } ?: false
            isPermanentlyDenied = !shouldShow
            activePermissionType = PermissionType.GALLERY
            showPermissionDialog = true
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.openCamera()
        } else {
            val activity = context as? Activity
            val shouldShow = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            isPermanentlyDenied = !shouldShow
            activePermissionType = PermissionType.CAMERA
            showPermissionDialog = true
        }
    }

    fun requestCameraAccess() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.openCamera()
        } else {
            val activity = context as? Activity
            val hasRequestedBefore = prefs.getBoolean("has_requested_camera", false)
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false

            val permanent = hasRequestedBefore && !shouldShowRationale
            activePermissionType = PermissionType.CAMERA
            isPermanentlyDenied = permanent
            showPermissionDialog = true
        }
    }

    fun requestGalleryAccess() {
        openGallery()
    }

    // Auto-check permissions upon returning from App Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when (pendingActionAfterSettings) {
                    PendingSettingsAction.CAMERA -> {
                        val hasCamera = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamera) {
                            pendingActionAfterSettings = null
                            showPermissionDialog = false
                            viewModel.openCamera()
                        }
                    }
                    PendingSettingsAction.GALLERY -> {
                        val hasGallery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) true
                        else ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasGallery) {
                            pendingActionAfterSettings = null
                            showPermissionDialog = false
                            openGallery()
                        }
                    }
                    null -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission popup dialog
    if (showPermissionDialog) {
        PermissionDialog(
            type = activePermissionType,
            isPermanentlyDenied = isPermanentlyDenied,
            onEnablePermission = {
                showPermissionDialog = false
                when (activePermissionType) {
                    PermissionType.CAMERA -> {
                        prefs.edit().putBoolean("has_requested_camera", true).apply()
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    PermissionType.GALLERY -> {
                        prefs.edit().putBoolean("has_requested_storage", true).apply()
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        } else {
                            openGallery()
                        }
                    }
                }
            },
            onOpenSettings = {
                showPermissionDialog = false
                pendingActionAfterSettings = when (activePermissionType) {
                    PermissionType.CAMERA -> PendingSettingsAction.CAMERA
                    PermissionType.GALLERY -> PendingSettingsAction.GALLERY
                }
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onDismiss = {
                showPermissionDialog = false
                pendingActionAfterSettings = null
            }
        )
    }

    // Hide top and bottom bars during full-screen camera preview or active processing
    val isFullScreenScan = scanState is ScanUiState.CameraPreview ||
            scanState is ScanUiState.CropPreview ||
            scanState is ScanUiState.Processing

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(
                visible = !isFullScreenScan,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AppHeader(
                    currentTheme = currentTheme,
                    onToggleTheme = { viewModel.toggleTheme() }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isFullScreenScan,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AppBottomNavigation(
                    currentTab = activeTab,
                    onTabSelected = { tab -> viewModel.setActiveTab(tab) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreenScan) PaddingValues() else innerPadding)
        ) {
            Crossfade(
                targetState = activeTab,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    ActiveTab.SCAN -> {
                        ScanTabContent(
                            scanState = scanState,
                            onLaunchCamera = { requestCameraAccess() },
                            onLaunchGallery = { requestGalleryAccess() },
                            onSampleClick = { sampleExpr ->
                                viewModel.setSampleInCalculator(sampleExpr)
                            },
                            onImageCaptured = { uri -> viewModel.onImageSelected(uri) },
                            onCloseCamera = { viewModel.closeCamera() },
                            onRetake = { viewModel.retakeScan() },
                            onConfirmCalculate = { uri -> viewModel.calculateFromImage(uri) },
                            onCancelProcessing = { uri -> viewModel.cancelProcessing(uri) },
                            onScanAgain = { viewModel.retakeScan() },
                            onOpenInCalculator = { expr -> viewModel.openInCalculator(expr) }
                        )
                    }
                    ActiveTab.CALCULATOR -> {
                        BackHandler {
                            viewModel.setActiveTab(ActiveTab.SCAN)
                        }
                        ManualCalculatorScreen(
                            initialExpression = calcExpression,
                            onExpressionChange = { expr ->
                                viewModel.updateCalculatorExpression(expr)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanTabContent(
    scanState: ScanUiState,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onSampleClick: (String) -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onCloseCamera: () -> Unit,
    onRetake: () -> Unit,
    onConfirmCalculate: (Uri) -> Unit,
    onCancelProcessing: (Uri) -> Unit,
    onScanAgain: () -> Unit,
    onOpenInCalculator: (String) -> Unit
) {
    when (scanState) {
        is ScanUiState.Idle -> {
            ScanHomeScreen(
                onLaunchCamera = onLaunchCamera,
                onLaunchGallery = onLaunchGallery,
                onSampleClick = onSampleClick
            )
        }
        is ScanUiState.CameraPreview -> {
            CameraCaptureScreen(
                onImageCaptured = onImageCaptured,
                onGalleryClicked = onLaunchGallery,
                onClose = onCloseCamera
            )
        }
        is ScanUiState.CropPreview -> {
            CropPreviewScreen(
                imageUri = Uri.parse(scanState.imageUri),
                onRetake = onRetake,
                onConfirmCalculate = onConfirmCalculate
            )
        }
        is ScanUiState.Processing -> {
            ProcessingScreen(
                imageUri = Uri.parse(scanState.imageUri),
                onCancel = { onCancelProcessing(Uri.parse(scanState.imageUri)) }
            )
        }
        is ScanUiState.Success -> {
            ResultScreen(
                imageUri = Uri.parse(scanState.imageUri),
                ocrResult = scanState.result,
                onScanAgain = onScanAgain,
                onOpenInCalculator = onOpenInCalculator
            )
        }
        is ScanUiState.Error -> {
            val uri = scanState.imageUri?.let { Uri.parse(it) }
            ResultScreen(
                imageUri = uri ?: Uri.EMPTY,
                ocrResult = com.example.model.MathOcrResult(
                    foundMath = false,
                    note = scanState.message
                ),
                onScanAgain = onScanAgain,
                onOpenInCalculator = onOpenInCalculator
            )
        }
    }
}
