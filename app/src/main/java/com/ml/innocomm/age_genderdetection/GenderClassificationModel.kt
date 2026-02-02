package com.ml.innocomm.age_genderdetection

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

// Helper class for gender classification model
class GenderClassificationModel {

    private val inputImageSize = 128
    private val inputImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputImageSize, inputImageSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    var inferenceTime: Long = 0
    var interpreter: Interpreter? = null

    // 可設定性別準確率門檻
    var confidenceThreshold: Float = MainActivity.DEFAULT_GENDER_THRESHOLD

    // 回傳 FloatArray?，不達門檻時回 null
    suspend fun predictGender(image: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val tensorInputImage = TensorImage.fromBitmap(image)
        val genderOutputArray = Array(1) { FloatArray(2) }
        val processedImageBuffer = inputImageProcessor.process(tensorInputImage).buffer
        interpreter?.run(processedImageBuffer, genderOutputArray)
        inferenceTime = System.currentTimeMillis() - start

        val output = genderOutputArray[0]
        val maxProb = output.maxOrNull() ?: 0f
        //if (maxProb >= confidenceThreshold) Log.v("innocomm", "predictGender "+output.joinToString("-"))
        return@withContext if (maxProb >= confidenceThreshold) output else null
    }
}
