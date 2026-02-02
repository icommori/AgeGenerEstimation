package com.ml.innocomm.age_genderdetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.util.concurrent.atomic.AtomicBoolean

class FaceDetectionViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "FaceDetectionViewModel"
        private const val MIN_FACE_WIDTH = 150
        private const val FACE_LAST_SEENTIME = 2000L
        private const val REINFERENCE_TIME = 60_000L
        private const val FPS_WINDOW = 30
    }
    
    // UI State
    private val _trackedFaces = MutableStateFlow<List<TrackedFace>>(emptyList())
    val trackedFaces: StateFlow<List<TrackedFace>> = _trackedFaces.asStateFlow()
    
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()
    
    private val _faceFps = MutableStateFlow(0f)
    val faceFps: StateFlow<Float> = _faceFps.asStateFlow()
    
    private val _previewSize = MutableStateFlow(Pair(0, 0))
    val previewSize: StateFlow<Pair<Int, Int>> = _previewSize.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _modelsReady = MutableStateFlow(false)
    val modelsReady: StateFlow<Boolean> = _modelsReady.asStateFlow()

    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    private var lastPlaybackFace: TrackedFace? = null
    private val VIDEO_PLAY_DELAY = 1000L
    
    // Internal state
    private val trackedFacesList = mutableListOf<TrackedFace>()
    private var nextFaceId = 0
    private val frameTimestamps = mutableListOf<Long>()
    private val faceFrameTimestamps = mutableListOf<Long>()
    private val isProcessingFlag = AtomicBoolean(false)
    private val inferenceLock = Mutex()
    
    // ML Models
    private var ageEstimationModel: AgeEstimationModel? = null
    private var genderClassificationModel: GenderClassificationModel? = null
    private var faceDetector: FaceDetector? = null
    private var delegate: Delegate? = null
    
    // Configuration
    private var onlyFrontFace = true
    
    init {
        // Initialize face detector
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            //.setMinFaceSize(0.10f)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }
    
    /**
     * Initialize ML models
     */
    fun initializeModels(
        context: Context,
        modelFilenames: Array<String>,
        inferenceMode: InferenceMode,
        onlyFrontFaceEnabled: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.v(TAG, "Initialize ${modelFilenames.contentToString()} (${inferenceMode.name})")
                onlyFrontFace = onlyFrontFaceEnabled
                
                // Release old delegate if exists
                delegate?.let {
                    when (it) {
                        is GpuDelegate -> it.close()
                        is NnApiDelegate -> it.close()
                    }
                }
                
                // Create new delegate based on mode
                delegate = when (inferenceMode) {
                    InferenceMode.GPU -> {
                        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                            Log.v(TAG, "Using GPU delegate")
                            GpuDelegate()
                        } else {
                            Log.v(TAG, "GPU not supported, using CPU")
                            null
                        }
                    }
                    InferenceMode.NNAPI -> {
                        Log.v(TAG, "Using NNAPI delegate")
                        NnApiDelegate()
                    }
                    InferenceMode.CPU -> {
                        Log.v(TAG, "Using CPU (no delegate)")
                        null
                    }
                }
                
                // Initialize models
                val options = Interpreter.Options().apply {
                    delegate?.let { addDelegate(it) }
                    setNumThreads(4)
                }
                
                val ageModelBuffer = FileUtil.loadMappedFile(context, modelFilenames[0])
                val genderModelBuffer = FileUtil.loadMappedFile(context, modelFilenames[1])

                val ageInterpreter = Interpreter(ageModelBuffer, options)
                val genderInterpreter = Interpreter(genderModelBuffer, options)
                
                ageEstimationModel = AgeEstimationModel().apply { interpreter = ageInterpreter }
                genderClassificationModel = GenderClassificationModel().apply { interpreter = genderInterpreter }
                
                _modelsReady.value = true
                Log.v(TAG, "Models initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize models", e)
                _modelsReady.value = false
            }
        }
    }
    
    /**
     * Process a camera frame
     */
    fun processFrame(bitmap: Bitmap, rotation: Int) {
        // Update FPS immediately (Camera FPS)
        updateFps()

        if (isProcessingFlag.get() || !_modelsReady.value || inferenceLock.isLocked) {
            return
        }
        
        isProcessingFlag.set(true)
        _isProcessing.value = true
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val now = System.currentTimeMillis()
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                
                // Update preview size if changed
                if (bitmap.width != 0 && (_previewSize.value.first != bitmap.width || _previewSize.value.second != bitmap.height)) {
                    _previewSize.value = Pair(bitmap.width, bitmap.height)
                    Log.d(TAG, "Preview size updated: ${bitmap.width}x${bitmap.height}")
                }
                
                // Detect faces
                detectFaces(inputImage, bitmap, now)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                isProcessingFlag.set(false)
                _isProcessing.value = false
            }
        }
    }
    
    private suspend fun detectFaces(inputImage: InputImage, bitmap: Bitmap, now: Long) {
        try {
            val faces = withContext(Dispatchers.IO) {
                faceDetector?.process(inputImage)?.await()
            } ?: return
            
            updateFaceFps()
            
            if (faces.isEmpty()) {
                trackedFacesList.clear()
                _trackedFaces.value = emptyList()
                return
            }
            
            // 1. Update Tracking and Positions (FAST)
            for (face in faces) {
                updateFaceTracking(face, now)
            }
            
            // 2. Remove expired faces
            trackedFacesList.removeAll { now - it.lastSeen > FACE_LAST_SEENTIME }
            
            // 3. Dispatch Background Inferences (SLOW)
            val largestFace = faces.maxByOrNull { it.boundingBox.width() }
            for (face in faces) {
                val bbox = face.boundingBox
                val centerX = bbox.centerX()
                val centerY = bbox.centerY()
                
                // Find matching tracked face (already updated in step 1)
                val trackedFace = trackedFacesList.minByOrNull {
                    val dx = it.bbox.centerX() - centerX
                    val dy = it.bbox.centerY() - centerY
                    dx * dx + dy * dy
                } ?: continue

                val distanceThreshold = bbox.width() * bbox.width() * 1.5f
                if ((trackedFace.bbox.centerX() - centerX).let { dx -> (trackedFace.bbox.centerY() - centerY).let { dy -> dx * dx + dy * dy } } >= distanceThreshold) {
                    continue 
                }

                // If needs initial inference OR needs locking, and not currently busy
                val needsLock = bbox.width() >= MIN_FACE_WIDTH && !trackedFace.locked
                val needsInitial = trackedFace.age == 0f
                val isLargest = (face == largestFace)

                if ((needsInitial || (needsLock && isLargest)) && !trackedFace.isInferring) {
                    val isFront = checkFaceFront(face, bitmap)
                    val faceBitmap = bitmap.cropFaceForModelInput(bbox = bbox)
                    // Launch background inference with front-face status
                    launchInference(trackedFace, faceBitmap, needsLock && isLargest, isFront, now)
                }
            }
            
            // Update UI state
            _trackedFaces.value = trackedFacesList.toList()
            
            // 4. Update Video Playback Status
            updateVideoPlayback()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting faces", e)
        }
    }

    private fun updateVideoPlayback() {
        val now = System.currentTimeMillis()
        // 挑選目前畫面中「最大」且「最近 1 秒內有出現」且「已鎖定」的臉孔
        // 這樣當人臉更換時（原本的人消失或變小，新的人變大），videoInfo 才能即時更新
        val bestFace = trackedFacesList
            .filter { it.locked && it.thumb != null && (now - it.lastSeen) < 1000 && (now - it.firstSeen) >= VIDEO_PLAY_DELAY }
            .maxByOrNull { it.bbox.width() * it.bbox.height() }

        smartUpdate(lastPlaybackFace, bestFace) { newFace ->
            lastPlaybackFace = newFace
            if (newFace != null) {
                val isMale = newFace.gender[0] > newFace.gender[1]
                _videoInfo.value = VideoInfo(
                    isMale = isMale,
                    age = newFace.age,
                    Url = getAvatarFilename(isMale, newFace.age.toInt()),
                    thumb = newFace.thumb
                )
            } else {
                _videoInfo.value = null
            }
        }
    }

    private fun updateFaceTracking(face: Face, now: Long) {
        val bbox = face.boundingBox
        val centerX = bbox.centerX()
        val centerY = bbox.centerY()
        
        // Find matching tracked face
        val matched = trackedFacesList.minByOrNull {
            val dx = it.bbox.centerX() - centerX
            val dy = it.bbox.centerY() - centerY
            dx * dx + dy * dy
        }
        
        val distanceThreshold = bbox.width() * bbox.width() * 1.0f
        
        if (matched != null && (matched.bbox.centerX() - centerX).let { dx -> (matched.bbox.centerY() - centerY).let { dy -> dx * dx + dy * dy } } < distanceThreshold) {
            matched.bbox = bbox
            matched.lastSeen = now
        } else {
            trackedFacesList.add(TrackedFace(id = nextFaceId++, bbox = bbox, lastSeen = now, firstSeen = now))
        }
    }

    private fun launchInference(target: TrackedFace, faceBitmap: Bitmap, isLockTarget: Boolean, isFront: Boolean, now: Long) {
        target.isInferring = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Shared interpreter lock
                inferenceLock.withLock {
                    val finalAge = ageEstimationModel?.predictAge(faceBitmap) ?: 0f
                    val finalGender = genderClassificationModel?.predictGender(faceBitmap) ?: floatArrayOf(0f, 0f)
                    
                    val hasValidPrediction = finalAge > 0f && (finalGender[0] != 0f || finalGender[1] != 0f)
                    
                    withContext(Dispatchers.Main) {
                        if (hasValidPrediction) {
                            target.age = finalAge
                            target.gender = finalGender
                            target.lastInferenceTime = System.currentTimeMillis()
                            
                            // Handle Locking if it was a lock target
                            if (isLockTarget && (!onlyFrontFace || isFront)) {
                                target.locked = true
                                target.firstSeen = System.currentTimeMillis()
                                target.thumb = faceBitmap
                                target.appearProgress = 0f
                                Log.d(TAG, "Face ${target.id} ASYNC LOCKED. Quality improved.")
                            }else{

                            }
                        } else {
                            // If invalid (e.g. gender low confidence), it remains age=0 and will be retried
                            Log.v(TAG, "Face ${target.id} Inference invalid/low confidence, will retry.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference error for face ${target.id}", e)
            } finally {
                target.isInferring = false
                // Trigger an update to UI list to show results
                _trackedFaces.value = trackedFacesList.toList()
                updateVideoPlayback()
            }
        }
    }


    
    private fun updateFps() {
        synchronized(frameTimestamps) {
            val timestamp = System.nanoTime()
            frameTimestamps.add(timestamp)
            if (frameTimestamps.size > FPS_WINDOW) frameTimestamps.removeAt(0)
            
            if (frameTimestamps.size >= 2) {
                val deltaNs = frameTimestamps.last() - frameTimestamps.first()
                if (deltaNs > 0L) {
                    _fps.value = ((frameTimestamps.size - 1) * 1_000_000_000.0 / deltaNs).toFloat()
                }
            }
        }
    }
    
    private fun updateFaceFps() {
        faceFrameTimestamps.add(System.nanoTime())
        if (faceFrameTimestamps.size > FPS_WINDOW) faceFrameTimestamps.removeAt(0)
        
        if (faceFrameTimestamps.size >= 2) {
            val deltaNs = faceFrameTimestamps.last() - faceFrameTimestamps.first()
            if (deltaNs > 0L) {
                _faceFps.value = ((faceFrameTimestamps.size - 1) * 1_000_000_000.0 / deltaNs).toFloat()
            }
        }
    }
    
    private fun checkFaceFront(face: Face, bitmap: Bitmap): Boolean {
        val eulerY = face.headEulerAngleY
        val eulerZ = face.headEulerAngleZ
        return kotlin.math.abs(eulerY) < 15f && kotlin.math.abs(eulerZ) < 15f
    }
    
    private fun isFaceStable(prevBox: Rect?, currentBox: Rect): Boolean {
        if (prevBox == null) return false
        val dx = kotlin.math.abs(prevBox.centerX() - currentBox.centerX())
        val dy = kotlin.math.abs(prevBox.centerY() - currentBox.centerY())
        val threshold = currentBox.width() * 0.3f
        return dx < threshold && dy < threshold
    }
    
    /**
     * Clear all tracked faces
     */
    fun clearTrackedFaces() {
        trackedFacesList.clear()
        _trackedFaces.value = emptyList()
    }
    
    /**
     * Update configuration
     */
    fun updateOnlyFrontFace(enabled: Boolean) {
        onlyFrontFace = enabled
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Clean up resources
        faceDetector?.close()
        ageEstimationModel?.interpreter?.close()
        genderClassificationModel?.interpreter?.close()
        
        delegate?.let {
            when (it) {
                is GpuDelegate -> it.close()
                is NnApiDelegate -> it.close()
            }
        }
        
        Log.d(TAG, "ViewModel cleared and resources released")
    }
}

// Extension function to await Task
// Custom await extension removed to use library version
