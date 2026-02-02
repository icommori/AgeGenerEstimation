package com.ml.innocomm.age_genderdetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class UvcHelper(
    private val context: Context, 
    private val listener: UvcListener,
    private val targetWidth: Int = 640,
    private val targetHeight: Int = 480
) {
    private var mCameraHelper: ICameraHelper? = null
    private val dummyTexture = SurfaceTexture(1).apply {
        setDefaultBufferSize(targetWidth, targetHeight)
    }
    private var preferredSize = Size(1, targetWidth, targetHeight, 30, listOf(30))
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var isResolutionCorrected = false

    interface UvcListener {
        fun onFrame(bitmap: Bitmap)
        fun onDeviceAttach(device: UsbDevice)
        fun onDeviceDetach(device: UsbDevice)
        fun onError(e: Exception)
    }

    private var externalSurface: Any? = null

    init {
        mCameraHelper = CameraHelper()
        
        mCameraHelper?.setStateCallback(object : ICameraHelper.StateCallback {
            override fun onAttach(device: UsbDevice?) {
                Log.d("UvcHelper", "Device attached: ${device?.productName}")
                device?.let { listener.onDeviceAttach(it) }
            }

            override fun onDeviceOpen(device: UsbDevice?, isFirstOpen: Boolean) {
                Log.d("UvcHelper", "onDeviceOpen: ${device?.productName}, isFirstOpen=$isFirstOpen")
                isResolutionCorrected = false
                
                var retryCount = 0
                val maxRetries = 15
                
                fun checkAndOpen() {
                    val rawSizes = mCameraHelper?.supportedSizeList.orEmpty()
                    if (rawSizes.isEmpty() && retryCount < maxRetries) {
                        if (mCameraHelper == null) return 
                        retryCount++
                        mainHandler.postDelayed({ checkAndOpen() }, 200)
                        return
                    }
                    
                    val merged = mergeWithCommonSizes(rawSizes)
                    val targetSize = pickBestSize(merged, preferredSize.width, preferredSize.height)
                    Log.d("UvcHelper", "Initial open request: ${targetSize.width}x${targetSize.height}")
                    
                    if (externalSurface != null && externalSurface is SurfaceTexture) {
                         (externalSurface as SurfaceTexture).setDefaultBufferSize(targetSize.width, targetSize.height)
                    }

                    try {
                        mCameraHelper?.openCamera(targetSize)
                    } catch (e: Exception) {
                        Log.e("UvcHelper", "Open camera failed", e)
                        listener.onError(e)
                    }
                }
                checkAndOpen()
            }

            override fun onCameraOpen(device: UsbDevice?) {
                val actualSize = mCameraHelper?.previewSize
                Log.d("UvcHelper", "onCameraOpen: Camera opened at ${actualSize?.width}x${actualSize?.height}")
                
                // Force target resolution if incorrect and not already corrected
                if (actualSize != null && (actualSize.width != targetWidth || actualSize.height != targetHeight) && !isResolutionCorrected) {
                    Log.w("UvcHelper", "Incorrect resolution. Forcing ${targetWidth}x${targetHeight}...")
                    isResolutionCorrected = true
                    mainHandler.postDelayed({
                        setPreviewSize(targetWidth, targetHeight)
                    }, 500)
                    // Removed return to allow preview to start at current resolution first
                }

                val format = if (actualSize?.type == 4) UVCCamera.FRAME_FORMAT_MJPEG else UVCCamera.FRAME_FORMAT_YUYV
                setupFrameCallback(format)
                
                // Add surfaces
                if (externalSurface != null) {
                    Log.d("UvcHelper", "Adding external surface")
                    mCameraHelper?.addSurface(externalSurface, false)
                } else {
                     // Fallback to dummy only if no external surface provided (though we can add both)
                    Log.d("UvcHelper", "Adding dummy surface")
                    mCameraHelper?.addSurface(dummyTexture, false)
                }

                mCameraHelper?.startPreview()
            }
// ... (rest of callbacks)

            override fun onCameraClose(device: UsbDevice?) {
                Log.d("UvcHelper", "Camera closed")
                frameCount = 0
            }

            override fun onDeviceClose(device: UsbDevice?) {
                Log.d("UvcHelper", "Device closed")
            }

            override fun onDetach(device: UsbDevice?) {
                Log.d("UvcHelper", "Device detached")
                device?.let { listener.onDeviceDetach(it) }
            }

            override fun onCancel(device: UsbDevice?) {
                Log.d("UvcHelper", "Operation cancelled")
            }

            override fun onError(device: UsbDevice?, e: CameraException) {
                Log.e("UvcHelper", "Camera Error: ${e.message} (Code: ${e.code})")
                listener.onError(e)
            }
        })

        // setupFrameCallback() // Remove from here, wait for onCameraOpen
        
        // 3. 最後註冊到 USB 服務
        try {
            (mCameraHelper as? CameraHelper)?.registerCallback()
            // 延遲一下再檢查設備數量，因為 onAttach 是異步的
            mainHandler.postDelayed({
                val deviceCount = getDeviceList().size
                Log.d("UvcHelper", "USB service registered, devices detected: $deviceCount")
            }, 500)
        } catch (e: Exception) {
            Log.e("UvcHelper", "Failed to register: ${e.message}")
        }
    }

    private var lastFrameTime = 0L
    private val frameInterval = 50L // 20 FPS polling interval

    private var argbBuffer: IntArray? = null

    private fun setupFrameCallback(format: Int = UVCCamera.FRAME_FORMAT_YUYV) {
        val formatName = if (format == UVCCamera.FRAME_FORMAT_YUYV) "YUYV" else "MJPEG"
        Log.d("UvcHelper", "Setting up frame callback for $formatName format")
        
        mCameraHelper?.setFrameCallback({ byteBuffer ->
            if (byteBuffer != null) {
                val now = System.currentTimeMillis()
                if (now - lastFrameTime < frameInterval) {
                    return@setFrameCallback
                }
                lastFrameTime = now
                
                frameCount++
                
                val previewSize = mCameraHelper?.previewSize ?: return@setFrameCallback
                val width = previewSize.width
                val height = previewSize.height
                
                byteBuffer.rewind()
                val bitmap = if (format == UVCCamera.FRAME_FORMAT_YUYV) {
                     fastYuyvToBitmap(byteBuffer, width, height)
                } else {
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                
                if (bitmap != null) {
                    listener.onFrame(bitmap)
                }
            }
        }, format)
    }

    private fun fastYuyvToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val size = width * height
        if (argbBuffer == null || argbBuffer!!.size != size) {
            argbBuffer = IntArray(size)
        }
        val argb = argbBuffer!!
        
        val yuyv = ByteArray(buffer.remaining())
        buffer.get(yuyv)
        
        var y0: Int
        var y1: Int
        var u: Int
        var v: Int
        var r: Int
        var g: Int
        var b: Int
        
        for (i in 0 until size / 2) {
            y0 = yuyv[i * 4].toInt() and 0xFF
            u = yuyv[i * 4 + 1].toInt() and 0xFF
            y1 = yuyv[i * 4 + 2].toInt() and 0xFF
            v = yuyv[i * 4 + 3].toInt() and 0xFF
            
            u -= 128
            v -= 128
            
            // YUV to RGB conversion
            r = (y0 + 1.402f * v).toInt().coerceIn(0, 255)
            g = (y0 - 0.34414f * u - 0.71414f * v).toInt().coerceIn(0, 255)
            b = (y0 + 1.772f * u).toInt().coerceIn(0, 255)
            argb[i * 2] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            
            r = (y1 + 1.402f * v).toInt().coerceIn(0, 255)
            g = (y1 - 0.34414f * u - 0.71414f * v).toInt().coerceIn(0, 255)
            b = (y1 + 1.772f * u).toInt().coerceIn(0, 255)
            argb[i * 2 + 1] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun yuvToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
        try {
            val remaining = buffer.remaining()
            val expectedSize = width * height * 2
            
            if (remaining < expectedSize) {
                return null
            }

            val bytes = ByteArray(remaining)
            buffer.get(bytes)
            
            val yuvImage = YuvImage(bytes, ImageFormat.YUY2, width, height, null)
            val out = ByteArrayOutputStream()
            if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)) {
                return null
            }
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("UvcHelper", "Conversion error: ${e.message}")
            return null
        }
    }

    private var currentDevice: UsbDevice? = null

    fun selectDevice(device: UsbDevice) {
        if (currentDevice == device) {
            Log.d("UvcHelper", "selectDevice ignored: Device ${device.productName} is already selected")
            return
        }
        Log.d("UvcHelper", "selectDevice called for: ${device.productName}")
        currentDevice = device
        // 只停止當前相機，不要 releaseAll() 以保留設備列表
        mCameraHelper?.stopPreview()
        mCameraHelper?.closeCamera()
        mCameraHelper?.selectDevice(device)
    }

    fun stop() {
        mCameraHelper?.stopPreview()
        mCameraHelper?.removeSurface(dummyTexture)
        mCameraHelper?.closeCamera()
        currentDevice = null
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        mCameraHelper?.release()
        mCameraHelper = null
    }

    fun getDeviceList(): List<UsbDevice> {
        return mCameraHelper?.deviceList ?: emptyList()
    }
    
    fun getSupportedSizes(): List<Size> {
        val raw = mCameraHelper?.supportedSizeList ?: emptyList()
        return mergeWithCommonSizes(raw)
    }
    
    fun setPreviewSize(width: Int, height: Int) {
        Log.d("UvcHelper", "setPreviewSize called: ${width}x${height}")
        val raw = mCameraHelper?.supportedSizeList ?: emptyList()
        val supported = mergeWithCommonSizes(raw)
        Log.d("UvcHelper", "Supported sizes: ${supported.map { "${it.width}x${it.height}" }}")
        val matchedSize = supported.find { it.width == width && it.height == height } 
            ?: supported.minByOrNull { Math.abs(it.width - width) + Math.abs(it.height - height) }
            
        if (matchedSize != null) {
            Log.d("UvcHelper", "Matched size: ${matchedSize.width}x${matchedSize.height}, stopping preview...")
            mCameraHelper?.stopPreview()
            mCameraHelper?.previewSize = matchedSize
            Log.d("UvcHelper", "Starting preview with new size...")
            mCameraHelper?.startPreview()
            Log.d("UvcHelper", "Preview restarted")
        } else {
            Log.w("UvcHelper", "No matching size found for ${width}x${height}")
        }
    }

    fun setPreferredSize(width: Int, height: Int) {
        preferredSize = Size(1, width, height, 30, listOf(30))
    }
    private fun mergeWithCommonSizes(supportedRaw: List<Size>): List<Size> {
        val merged = supportedRaw.toMutableList()
        // If empty, we might be in trouble, but adding common sizes blindly can crash.
        // Better to just return empty and let retry logic handle it.
        return merged
    }

    private fun pickBestSize(sizes: List<Size>, targetWidth: Int, targetHeight: Int): Size {
        // 優先順序 1: 目標解析度 YUYV (Type 1)
        sizes.find { it.width == targetWidth && it.height == targetHeight && it.type == 1 }?.let { return it }
        // 優先順序 2: 目標解析度 MJPEG (Type 4)
        sizes.find { it.width == targetWidth && it.height == targetHeight && it.type == 4 }?.let { return it }
        // 優先順序 3: 其他同解析度
        sizes.find { it.width == targetWidth && it.height == targetHeight }?.let { return it }
        // 優先順序 4: 最接近目標的解析度
        return sizes.minByOrNull { Math.abs(it.width - targetWidth) + Math.abs(it.height - targetHeight) } 
            ?: Size(1, targetWidth, targetHeight, 30, listOf(30))
    }
    fun addSurface(surface: Any) {
        externalSurface = surface
        mCameraHelper?.addSurface(surface, false)
    }

    fun removeSurface(surface: Any) {
        if (externalSurface == surface) externalSurface = null
        mCameraHelper?.removeSurface(surface)
    }
}
