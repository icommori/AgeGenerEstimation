package com.ml.innocomm.age_genderdetection

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ml.innocomm.age_genderdetection.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : ComponentActivity() {
    companion object {
        val MODEL_SELECTION = 1
        val DEFAULT_GENDER_THRESHOLD = 0.7f
        val targetPreviewSize = android.util.Size(640, 480)//(480, 640)//(720, 1280)/(1080, 1920)
        val VIDEO_PLAY_DELAY = 2000L
        val FACE_RECT_PADDING = 0.1f
        val FACE_RECT_PADDING_PREVIEW = 0f
        val DETECT_FACE_ONLY = false
    }


    private val TAG = "MainActivity"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

// Face detection logic moved to ViewModel

    // ML Model interpreters moved to ViewModel
    private val modelNames = arrayOf(
        "Age/Gender Detection Model ( Quantized ) ",
        "Age/Gender Detection Model ( Non-quantized )",
        "Age/Gender Detection Lite Model ( Quantized )",
        "Age/Gender Detection Lite Model ( Non-quantized )",
    )

    private val modelFilenames = arrayOf(
        arrayOf("model_age_q.tflite", "model_gender_q.tflite"),
        arrayOf("model_age_nonq.tflite", "model_gender_nonq.tflite"),
        arrayOf("model_lite_age_q.tflite", "model_lite_gender_q.tflite"),
        arrayOf("model_lite_age_nonq.tflite", "model_lite_gender_nonq.tflite"),
    )

    private var modelFilename = modelFilenames[MODEL_SELECTION]
    private lateinit var prefs: AppPreferences
    private var scaleFactor = 1f  // 當前縮放比例
    private val monitor = HardwareMonitor()

    // HDMI Presentation Logic
    private lateinit var displayManager: DisplayManager
    private var presentation: MainPresentation? = null
    private var isExternalDisplayAvailable = mutableStateOf(false)
    private var isPresentationModeActive = mutableStateOf(false)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = checkExternalDisplay()
        override fun onDisplayRemoved(displayId: Int) = checkExternalDisplay()
        override fun onDisplayChanged(displayId: Int) = checkExternalDisplay()
    }

    private fun checkExternalDisplay() {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        isExternalDisplayAvailable.value = displays.isNotEmpty()
        if (displays.isEmpty() && isPresentationModeActive.value) {
            stopPresentation()
        }
    }

    private fun togglePresentation() {
        if (isPresentationModeActive.value) {
            stopPresentation()
        } else {
            startPresentation()
        }
    }

    private fun startPresentation() {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val display = displays[0]
            val activity = this
            presentation = MainPresentation(this, display) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides activity,
                    LocalViewModelStoreOwner provides (activity as ViewModelStoreOwner),
                    LocalActivityResultRegistryOwner provides (activity as ActivityResultRegistryOwner),
                    LocalOnBackPressedDispatcherOwner provides (activity as OnBackPressedDispatcherOwner)
                ) {
                    CameraPreviewWithFaceDetection(isExternal = true)
                }
            }
            presentation?.show()
            isPresentationModeActive.value = true
        }
    }

    private fun stopPresentation() {
        presentation?.dismiss()
        presentation = null
        isPresentationModeActive.value = false
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        checkExternalDisplay()

        setContent {
            // 🔹偵測返回鍵
            BackHandler {
                finishAffinity()
            }
            HideStatusBarScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        presentation?.dismiss()
    }
// initModels logic moved to ViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HideStatusBarScreen() {
        val view = LocalView.current
        val window = (view.context as Activity).window

        DisposableEffect(window) {
            val insetsController = WindowCompat.getInsetsController(window, view)

            // 隐藏状态栏和导航栏（可选，只隐藏状态栏用 Type.statusBars()）
            insetsController.hide(WindowInsetsCompat.Type.systemBars())

            // 设置行为：当用户从屏幕边缘滑动时，系统栏会显示并自动隐藏
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            onDispose {
                // 在 Composable 退出时，重新显示系统栏
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        AppTheme {

            Scaffold { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    MainContent()
                }

            }
        }
    }

    @Composable
    fun MainContent() {
        CameraPreviewWithFaceDetection(isExternal = false)
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @Composable
    fun CameraPreviewWithFaceDetection(isExternal: Boolean = false) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // ViewModel Integration
        val viewModel: FaceDetectionViewModel = viewModel()

        // Collect ViewModel States
        val trackedFaces by viewModel.trackedFaces.collectAsState()
        val fps by viewModel.fps.collectAsState()
        val faceFps by viewModel.faceFps.collectAsState()
        val previewSizeState by viewModel.previewSize.collectAsState()
        val modelsReady by viewModel.modelsReady.collectAsState()
        // val isProcessing by viewModel.isProcessing.collectAsState()

        var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
        // Initialize initializing based on modelsReady immediately
        var initializing by remember { mutableStateOf(!modelsReady) }

        // Sync initializing state with modelsReady
        LaunchedEffect(modelsReady) {
            Log.d(TAG, "modelsReady changed: $modelsReady, isExternal: $isExternal")
            initializing = !modelsReady
        }

        val currentModelIndex by prefs.modelIndexFlow.collectAsState()
        val inferenceMode by prefs.inferenceModeFlow.collectAsState()
        val onlyFrontFace by prefs.onlyFrontFaceFlow.collectAsState()

        // Local UI State
        var showSettings by remember { mutableStateOf(false) }

        // Update ViewModel config
        LaunchedEffect(onlyFrontFace) {
            viewModel.updateOnlyFrontFace(onlyFrontFace)
        }

        var permissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        // Camera State
        var availableCameras by remember {
            mutableStateOf<List<CameraOption>>(
                listOf(
                    CameraOption(
                        "0",
                        "Back Camera",
                        CameraType.BUILTIN,
                        lensFacing = CameraSelector.LENS_FACING_BACK
                    ),
                    CameraOption(
                        "1",
                        "Front Camera",
                        CameraType.BUILTIN,
                        lensFacing = CameraSelector.LENS_FACING_FRONT
                    )
                )
            )
        }
        var selectedCamera by remember { mutableStateOf<CameraOption?>(availableCameras[0]) }

        var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
        var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }

        rawResource(R.raw.img20paoff)

        // 權限請求 Launcher
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(), onResult = { granted ->
                permissionGranted = granted
            })

        LaunchedEffect(Unit) {
            if (!permissionGranted) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        if (!permissionGranted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required", color = Color.White)
            }
            return
        }

        LaunchedEffect(currentModelIndex, inferenceMode) {
            Log.d(TAG, "Model config changed, re-initializing models. isExternal: $isExternal")
            // Only trigger re-init if not already ready or if specific settings changed
            viewModel.initializeModels(
                context,
                modelFilenames[currentModelIndex],
                InferenceMode.valueOf(inferenceMode),
                onlyFrontFace
            )
        }

        // Simplified ProcessFrame delegating to ViewModel
        fun processFrame(bitmap: Bitmap, rotation: Int) {
            currentBitmap = bitmap // Keep for UI scaling
            viewModel.processFrame(bitmap, rotation)
        }

        // UVC Helper Init
        val uvcListener = remember {
            object : UvcHelper.UvcListener {
                override fun onFrame(bitmap: Bitmap) {
                    processFrame(bitmap, 0)
                }

                override fun onDeviceAttach(device: UsbDevice) {
                    // Ignore "Wireless_Device" (dongles) if user only has one camera
                    if (device.productName == "Wireless_Device") return

                    val id = "USB_${device.deviceId}"
                    val name = device.productName ?: "USB Camera ${device.deviceId}"
                    val newCam = CameraOption(id, name, CameraType.USB, device = device)
                    if (availableCameras.none { it.id == id }) {
                        availableCameras = availableCameras + newCam
                    }
                    // Priority Initialization: Switch to new UVC device
                    if (selectedCamera?.id != id) {
                        selectedCamera = newCam
                        Toast.makeText(context, "Switched to $name", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onDeviceDetach(device: UsbDevice) {
                    val id = "USB_${device.deviceId}"
                    availableCameras = availableCameras.filter { it.id != id }
                    if (selectedCamera?.id == id) {
                        selectedCamera = availableCameras.firstOrNull()
                    }
                }

                override fun onError(e: Exception) {
                    Toast.makeText(context, "Camera Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Fallback to builtin if current USB fails
                    if (selectedCamera?.type == CameraType.USB) {
                        selectedCamera =
                            availableCameras.firstOrNull { it.type == CameraType.BUILTIN }
                    }
                }
            }
        }
        val uvcHelper = remember {
            UvcHelper(
                context,
                uvcListener,
                targetWidth = targetPreviewSize.width,
                targetHeight = targetPreviewSize.height
            )
        }

        if (!isExternal) {
            DisposableEffect(Unit) {
                onDispose { uvcHelper.release() }
            }
        }

        if (!isExternal) {
            // Initial Load of USB Devices
            LaunchedEffect(Unit) {
                val devices = uvcHelper.getDeviceList()
                devices.forEach { device ->
                    if (device.productName == "Wireless_Device") return@forEach

                    val id = "USB_${device.deviceId}"
                    val name = device.productName ?: "USB Camera ${device.deviceId}"
                    if (availableCameras.none { it.id == id }) {
                        availableCameras =
                            availableCameras + CameraOption(id, name, CameraType.USB, device = device)
                    }
                }
                // If any USB device exists, select first one (Priority)
                availableCameras.firstOrNull { it.type == CameraType.USB }?.let {
                    selectedCamera = it
                }
            }
        }

        if (!isExternal) {
            // Sync selected camera with UVC helper
            LaunchedEffect(selectedCamera) {
                currentBitmap = null // Reset bitmap to show loading
                if (selectedCamera?.type == CameraType.USB) {
                    selectedCamera?.device?.let { uvcHelper.selectDevice(it) }
                } else {
                    uvcHelper.stop()
                }
            }
        }

        // Settings Dialog
        if (!isExternal) {
            SettingsDialog(
                showDialog = showSettings,
                onDismiss = { showSettings = false },
                modelNames = modelNames,
                currentModelIndex = prefs.modelIndex,
                onModelSelected = { prefs.modelIndex = it },
                currentMode = InferenceMode.valueOf(prefs.inferenceMode),
                onModeSelected = { prefs.inferenceMode = it.name },
                onlyFrontFace = prefs.onlyFrontFace,
                onOnlyFrontFaceChanged = { prefs.onlyFrontFace = it }
            )
        }

        if (permissionGranted && modelsReady) {
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
            val videoInfo by viewModel.videoInfo.collectAsState()
            val isCasting = isPresentationModeActive.value
            
            // Define visibility based on display type and casting state
            // Goal: Phone shows Camera+Boxes. HDMI shows Snapshots+QR+VideoUI+Info.
            val showLeftPanel = if (isExternal) true else !isCasting
            val showVideoUI = if (isExternal) true else !isCasting
            val showCameraPreview = !isExternal
            val showOverlays = !isExternal
            val showStatusInfo = if (isExternal) true else !isCasting

            val alphaTarget = when {
                isExternal -> 0f  // Force hide camera on HDMI
                isCasting -> 1f   // Force show camera on Phone (override VideoUI)
                else -> if (videoInfo == null) 1f else 0f
            }
            val videoAlpha by animateFloatAsState(targetValue = alphaTarget)

            Row(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)) {
                
                // 1. Left Panel (Snapshots & QR)
                if (showLeftPanel) {
                    Column(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        
                        // Snapshot Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(GetDimenInDp(id = R.dimen.snapshot_padding))
                                .clip(RoundedCornerShape(GetDimenInDp(id = R.dimen.snapshot_corner_radius)))
                                .background(Color(0xFFFF9800))
                        ) {
                            if (videoInfo != null) {
                                DrawSnapshot(
                                    vinfo = videoInfo,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                Text(
                                    text = "Detecting...",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.White,
                                    fontSize = 28.sp
                                )
                            }
                            if (!isExternal) {
                                IconButton(
                                    modifier = Modifier.align(Alignment.TopStart),
                                    onClick = { showSettings = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                        
                        // QR Code Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(GetDimenInDp(id = R.dimen.snapshot_padding))
                                .clip(RoundedCornerShape(GetDimenInDp(id = R.dimen.snapshot_corner_radius)))
                                .background(Color.White), contentAlignment = Alignment.Center
                        ) {
                            QRCodeAndImageLayout(
                                qrContent = "https://www.innocomm.com/",
                                bottomImageResId = R.drawable.logo,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                }
                // 2. Main Content Area (Camera / Video / Overlay)
                Box(
                    modifier = Modifier
                        .weight(if (showLeftPanel) 2f else 1f)
                        .fillMaxHeight()
                        .padding(GetDimenInDp(id = R.dimen.snapshot_padding))
                        .clip(RoundedCornerShape(GetDimenInDp(id = R.dimen.snapshot_corner_radius)))
                        .background(Color.Black)
                        .then(if (isCasting && !isExternal) Modifier.clickable { togglePresentation() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (showVideoUI) VideoUI(videoInfo)
                    
                    if (showCameraPreview) {
                        CameraLayer(
                            selectedCamera, uvcHelper, cameraProviderFuture, lifecycleOwner,
                            videoAlpha, { control, info -> cameraControl = control; cameraInfo = info }
                        ) { bitmap, rot -> currentBitmap = bitmap; viewModel.processFrame(bitmap, rot) }
                    }

                    if (showOverlays) {
                        OverlayLayer(currentBitmap, trackedFaces, selectedCamera, isCasting || videoInfo == null)
                    }

                    if (showStatusInfo) {
                        PreviewInfo(
                            previewSizeState.first, previewSizeState.second, fps, faceFps,
                            Modifier.align(Alignment.TopStart), videoInfo, monitor
                        )
                    }

                    if (!isExternal) {
                        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                            CameraSwitcherIcon(availableCameras, isCasting) { selectedCamera = it }
                        }
                        ControlPanal(trackedFaces, cameraInfo, cameraControl, viewModel)
                    }
                    
                    if (currentBitmap == null && showCameraPreview) CircularProgressIndicator(color = Color(0xFFFF9800))
                }
            }
        }

        if (initializing && !isExternal) InitializeDialog()
    }

    @Composable
    fun PreviewInfo(
        previewWidth: Int,
        previewHeight: Int,
        fps: Float,
        faceFps: Float,
        modifier: Modifier = Modifier,
        vinfo: VideoInfo?,
        monitor: HardwareMonitor
    ) {
        val appendurl = if (vinfo != null) " | " + vinfo.Url.substringAfterLast("/") else ""
        val infoText = if (previewWidth > 0) "${previewWidth}x${previewHeight} | FPS: ${fps.toInt()}${appendurl}" else ""
        if (infoText.isNotEmpty()) {
            Text(
                text = infoText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = modifier.padding(16.dp)
            )
        }
    }
    @Composable
    private fun ColumnScope.SnapshotPanel(videoInfo: VideoInfo?, isExternal: Boolean, onSettingsClick: () -> Unit) {
        Box(
            modifier = Modifier
                .weight(1f).fillMaxSize()
                .padding(GetDimenInDp(id = R.dimen.snapshot_padding))
                .clip(RoundedCornerShape(GetDimenInDp(id = R.dimen.snapshot_corner_radius)))
                .background(Color(0xFFFF9800))
        ) {
            if (videoInfo != null) {
                DrawSnapshot(vinfo = videoInfo, modifier = Modifier.align(Alignment.Center))
            } else {
                Text("Detecting...", Modifier.align(Alignment.Center), Color.White, 28.sp)
            }
            if (!isExternal) {
                IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.Filled.MoreVert, "Menu", tint = Color.White)
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.QRPanel() {
        Box(
            modifier = Modifier
                .weight(1f).fillMaxWidth()
                .padding(GetDimenInDp(id = R.dimen.snapshot_padding))
                .clip(RoundedCornerShape(GetDimenInDp(id = R.dimen.snapshot_corner_radius)))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            QRCodeAndImageLayout(
                qrContent = "https://www.innocomm.com/",
                bottomImageResId = R.drawable.logo,
                modifier = Modifier.fillMaxHeight()
            )
        }
    }

    @Composable
    private fun CameraLayer(
        selectedCamera: CameraOption?,
        uvcHelper: UvcHelper,
        cameraProviderFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        videoAlpha: Float,
        onCameraBound: (CameraControl, CameraInfo) -> Unit,
        onFrame: (Bitmap, Int) -> Unit
    ) {
        if (selectedCamera?.type == CameraType.USB) {
            AndroidView(factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { uvcHelper.addSurface(android.view.Surface(s)) }
                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean { uvcHelper.removeSurface(android.view.Surface(s)); return true }
                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                    }
                }
            }, modifier = Modifier.fillMaxSize().alpha(videoAlpha))
        } else {
            key(selectedCamera?.id) {
                AndroidView(factory = { ctx ->
                    PreviewView(ctx).apply {
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().setTargetResolution(targetPreviewSize).build()
                            preview.setSurfaceProvider(surfaceProvider)
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(targetPreviewSize)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                proxy.image?.let { onFrame(it.toBitmap(proxy.imageInfo.rotationDegrees), proxy.imageInfo.rotationDegrees) }
                                proxy.close()
                            }
                            val selector = if (selectedCamera?.lensFacing == CameraSelector.LENS_FACING_FRONT) 
                                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                provider.unbindAll()
                                val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                                onCameraBound(cam.cameraControl, cam.cameraInfo)
                            } catch (e: Exception) { Log.e("CameraLayer", "Binding failed", e) }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }, modifier = Modifier.fillMaxSize().alpha(videoAlpha))
            }
        }
    }

    @Composable
    private fun OverlayLayer(
        bitmap: Bitmap?,
        faces: List<TrackedFace>,
        camera: CameraOption?,
        isVisible: Boolean
    ) {
        if (!isVisible || bitmap == null) return
        Canvas(Modifier.fillMaxSize()) {
            val isFront = camera?.lensFacing == CameraSelector.LENS_FACING_FRONT
            val isUvc = camera?.type == CameraType.USB
            faces.forEach { face ->
                val rect = mapRectToPreview(face.bbox, bitmap, size.width.toInt(), size.height.toInt(), isFront, isUvc)
                val isMale = face.gender[0] > face.gender[1]
                val color = if (face.age > 0) (if (isMale) Color(0xFF2196F3) else Color(0xFFFF69B4)) else Color.Gray
                drawRect(color, Offset(rect.left.toFloat(), rect.top.toFloat()), Size(rect.width().toFloat(), rect.height().toFloat()), style = Stroke(4f))
                if (face.age > 0) {
                    val fontSize = (rect.width() * 0.15f).coerceIn(20f, 45f)
                    val paint = Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        this.textSize = fontSize
                        this.isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("${face.age.toInt()}s / ${if (isMale) "M" else "F"}", rect.left.toFloat(), rect.top - fontSize * 0.25f, paint)
                }
            }
        }
    }

    @Composable
    private fun CameraSwitcherIcon(
        availableCameras: List<CameraOption>,
        isCasting: Boolean,
        onSelect: (CameraOption) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.Cameraswitch, "Switch", tint = Color.White)
            }
            if (isExternalDisplayAvailable.value) {
                IconButton(onClick = { togglePresentation() }) {
                    Icon(Icons.Filled.Tv, "Cast", tint = if (isCasting) Color(0xFFFF9800) else Color.White)
                }
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            availableCameras.forEach { opt ->
                DropdownMenuItem(text = { Text(opt.name) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }

    @Composable
    fun ControlPanal(
        trackedFaces: List<TrackedFace>,
        cameraInfo: CameraInfo?,
        cameraControl: CameraControl?,
        viewModel: FaceDetectionViewModel
    ) {
        // 右下角按鈕（半透明效果）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            val isEnabled = trackedFaces.isNotEmpty()

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Reset Faces button
                if (isEnabled) {
                    Button(
                        onClick = { viewModel.clearTrackedFaces() },
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800), // 只有启用时才渲染，所以直接使用启用颜色
                            contentColor = Color.White
                        )
                    ) {
                        Text("Reset")
                    }
                }
                /*Row {
                    // Zoom out button
                    Button(
                        onClick = {
                            cameraInfo?.zoomState?.value?.let { state ->
                                val newZoom = (state.linearZoom - 0.1f).coerceIn(0f, 1f)
                                cameraControl?.setLinearZoom(newZoom)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("−", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Zoom in button
                    Button(
                        onClick = {
                            cameraInfo?.zoomState?.value?.let { state ->
                                val newZoom = (state.linearZoom + 0.1f).coerceIn(0f, 1f)
                                cameraControl?.setLinearZoom(newZoom)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("+", fontSize = 20.sp)
                    }
                }
*/

        }
    }
}

    @Composable
    private fun VideoUI(vinfo: VideoInfo?) {
        if (vinfo == null) return
        val url = getAvatarFilename(vinfo.isMale, vinfo.age.toInt())
        FullScreenExoPlayerView(url = url, videoAlpha = 1f, modifier = Modifier.fillMaxSize())
    }

    @Composable
    private fun DrawSnapshot(vinfo: VideoInfo?, modifier: Modifier = Modifier) {
        if (vinfo == null || vinfo.thumb == null) return
        BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val thumbSize = maxWidth * 0.30f
            val textSize = (thumbSize.value * 0.18f).sp
            val imageBitmap = vinfo.thumb.asImageBitmap()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Age: ${vinfo.age.toInt()} / ${if (vinfo.isMale) "Male" else "Female"}", color = Color.White, fontSize = textSize, modifier = Modifier.padding(4.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.size(thumbSize).shadow(4.dp, RoundedCornerShape(12.dp)).background(Color.Black, RoundedCornerShape(12.dp)))
            }
        }
    }

    @Composable
    private fun InitializeDialog() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AlertDialog(onDismissRequest = {}, confirmButton = {},
                title = { Text("Initializing Model...", fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Please wait...", fontSize = 16.sp)
                    }
                })
        }
    }

    fun mapRectToPreview(
        bbox: Rect,
        bitmap: Bitmap,
        previewWidth: Int,
        previewHeight: Int,
        isFrontCamera: Boolean,
        isUvc: Boolean, // New param
        paddingRatio: Float = FACE_RECT_PADDING_PREVIEW
    ): Rect {
        // 計算預覽與輸入影像的縮放比例
        val scaleX = previewWidth.toFloat() / bitmap.width
        val scaleY = previewHeight.toFloat() / bitmap.height

        var scale = 1f
        var offsetX = 0f
        var offsetY = 0f

        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f

        if (isUvc) {
            // UVC usually stretches to fill TextureView by default (Non-uniform)
            left = bbox.left * scaleX
            top = bbox.top * scaleY
            right = bbox.right * scaleX
            bottom = bbox.bottom * scaleY
        } else {
            // CameraX uses FILL_CENTER (Center Crop)
            // We use MAX scale to ensure it fills the screen
            scale = max(scaleX, scaleY)
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale

            // Center Offset (can be negative if cropped)
            offsetX = (previewWidth - scaledWidth) / 2f
            offsetY = (previewHeight - scaledHeight) / 2f

            left = bbox.left * scale + offsetX
            top = bbox.top * scale + offsetY
            right = bbox.right * scale + offsetX
            bottom = bbox.bottom * scale + offsetY
        }

        // 加上 padding
        if (paddingRatio > 0f) {
            val w = right - left
            val h = bottom - top
            val padW = w * paddingRatio
            val padH = h * paddingRatio
            left -= padW
            top -= padH
            right += padW
            bottom += padH
        }

        // 前鏡頭需左右鏡像
        return if (isFrontCamera) {
            Rect(
                (previewWidth - right).toInt().coerceAtLeast(0),
                top.toInt().coerceAtLeast(0),
                (previewWidth - left).toInt().coerceAtMost(previewWidth),
                bottom.toInt().coerceAtMost(previewHeight)
            )
        } else {
            Rect(
                left.toInt().coerceAtLeast(0),
                top.toInt().coerceAtLeast(0),
                right.toInt().coerceAtMost(previewWidth),
                bottom.toInt().coerceAtMost(previewHeight)
            )
        }
    }

}
