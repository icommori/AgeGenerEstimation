package com.ml.innocomm.age_genderdetection

import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.usb.UsbDevice

enum class InferenceMode {
    CPU, NNAPI, GPU
}

enum class CameraType { BUILTIN, USB }

data class CameraOption(
    val id: String,
    val name: String,
    val type: CameraType,
    val device: UsbDevice? = null,
    val lensFacing: Int? = null 
)

data class TrackedFace(
    val id: Int,
    var bbox: Rect,
    var age: Float = 0f,
    var gender: FloatArray = floatArrayOf(0f, 0f),
    var locked: Boolean = false,
    var lastSeen: Long,
    var firstSeen: Long = System.currentTimeMillis(),
    var thumb: Bitmap? = null,
    var lastInferenceTime: Long = 0L,
    var appearProgress: Float = 0f,
    var isInferring: Boolean = false
)

data class VideoInfo(
    val isMale: Boolean,
    val age: Float,
    val Url: String,
    val thumb: Bitmap?
)
