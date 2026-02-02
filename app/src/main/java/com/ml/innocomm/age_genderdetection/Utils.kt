package com.ml.innocomm.age_genderdetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.google.mlkit.vision.face.Face
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.ml.innocomm.age_genderdetection.MainActivity.Companion.FACE_RECT_PADDING
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt


// --- Extensions ---
fun Image.toBitmap(rotation: Int): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    yBuffer.remaining()
    
    // NV21 size is width * height * 1.5
    // NV21 size is width * height * 1.5
    val nv21 = ByteArray(width * height * 3 / 2)

    // Copy Y channel
    val yRowStride = planes[0].rowStride
    val yPixelStride = planes[0].pixelStride
    
    if (yPixelStride == 1 && yRowStride == width) {
        yBuffer.get(nv21, 0, width * height)
    } else {
        // Handle stride > width or pixelStride > 1 (though Y usually has pixelStride 1)
        // We clone the buffer to safely modify position
        val yBuf = yBuffer.duplicate()
        for (row in 0 until height) {
            yBuf.position(row * yRowStride)
            // If pixelStride > 1, this simple get() is still wrong for Y, but it handles rowStride
            yBuf.get(nv21, row * width, width)
        }
    }

    // Copy V and U channels (interleaved for NV21: V, U, V, U...)
    val uRowStride = planes[1].rowStride
    val vRowStride = planes[2].rowStride
    val uPixelStride = planes[1].pixelStride
    val vPixelStride = planes[2].pixelStride

    var pos = width * height // Start after tight-packed Y
    val chromaHeight = height / 2
    val chromaWidth = width / 2

    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val vIndex = row * vRowStride + col * vPixelStride
            val uIndex = row * uRowStride + col * uPixelStride

            // Buffer.get(index) performs absolute positioning
            // NV21 expects V first, then U
            nv21[pos++] = vBuffer.get(vIndex)
            nv21[pos++] = uBuffer.get(uIndex)
        }
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val yuv = ByteArrayOutputStream().use { out ->
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        out.toByteArray()
    }
    var bmp = BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    if (rotation != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
    return bmp
}


//加邊界 padding：裁切時多加 10~20% 的邊界，避免臉部被裁掉額外特徵。
fun Bitmap.cropToBBox(bbox: Rect, paddingRatio: Float = FACE_RECT_PADDING): Bitmap {
    val padX = (bbox.width() * paddingRatio).toInt()
    val padY = (bbox.height() * paddingRatio).toInt()
    val left = (bbox.left - padX).coerceAtLeast(0)
    val top = (bbox.top - padY).coerceAtLeast(0)
    val right = (bbox.right + padX).coerceAtMost(width)
    val bottom = (bbox.bottom + padY).coerceAtMost(height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

fun Bitmap.cropFaceForModelInput(
    bbox: Rect,
    paddingRatio: Float = FACE_RECT_PADDING
): Bitmap {

    var padW = (bbox.width() * paddingRatio).toInt()
    var padH = (bbox.height() * paddingRatio).toInt()

    var left   = bbox.left - padW
    var top    = bbox.top - padH
    var right  = bbox.right + padW
    var bottom = bbox.bottom + padH

    val w = right - left
    val h = bottom - top
    val size = max(w, h)

    val cx = (left + right) / 2
    val cy = (top + bottom) / 2
    left   = cx - size / 2
    top    = cy - size / 2
    right  = left + size
    bottom = top + size

    left = left.coerceAtLeast(0)
    top = top.coerceAtLeast(0)
    right = right.coerceAtMost(this.width)
    bottom = bottom.coerceAtMost(this.height)

    val finalW = right - left
    val finalH = bottom - top

    return Bitmap.createBitmap(this, left, top, finalW, finalH) // ❗ 不 resize
}



fun Bitmap.scaleToMaxSize(maxSize: Int): Bitmap {
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int

    if (width >= height) {
        newWidth = maxSize
        newHeight = (maxSize / ratio).toInt()
    } else {
        newHeight = maxSize
        newWidth = (maxSize * ratio).toInt()
    }

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

// simple linear interpolation helpers (放在 file/class scope)
fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
fun rawResource(id: Int): ImageBitmap {
    val context = LocalContext.current
    return remember(id) {
        // 1. 获取 Resources 对象
        val resources = context.resources

        // 2. 通过 openRawResource 获取 InputStream
        val inputStream = resources.openRawResource(id)

        // 3. 将 InputStream 解码为 Android Bitmap
        val bitmap = BitmapFactory.decodeStream(inputStream)

        // 4. 转换为 Compose 的 ImageBitmap
        bitmap.asImageBitmap()
    }
}

fun checkFaceFront(face: Face, bitmap: Bitmap): Boolean {
    val bbox = face.boundingBox
    val passRoll = false
    // 1. 角度檢查
    android.util.Log.d("Utils", "Face Angles - Yaw: ${face.headEulerAngleY}, Pitch: ${face.headEulerAngleX}, Roll: ${face.headEulerAngleZ}")
    val yawOk   = abs(face.headEulerAngleY) < 10
    val pitchOk = abs(face.headEulerAngleX) < 10
    val rollOk  = abs(face.headEulerAngleZ) < 10

    // 2. 臉的比例
    val aspect = bbox.width().toFloat() / bbox.height()
    val ratioOk = aspect in 0.7f..1.35f

    // 3. 臉位置
    val cxNorm = bbox.centerX().toFloat() / bitmap.width
    val cyNorm = bbox.centerY().toFloat() / bitmap.height
    val positionOk = (cxNorm in 0.2f..0.8f) && (cyNorm in 0.2f..0.8f)

    // 4. 過曝檢查
    val overExposed = isFaceOverExposed(bitmap, bbox)

    return yawOk && pitchOk && (rollOk ||passRoll) && ratioOk && positionOk && !overExposed
}
fun isFaceOverExposed(bitmap: Bitmap, rect: Rect): Boolean {
    val left = rect.left.coerceAtLeast(0)
    val top = rect.top.coerceAtLeast(0)
    val right = rect.right.coerceAtMost(bitmap.width)
    val bottom = rect.bottom.coerceAtMost(bitmap.height)

    val w = right - left
    val h = bottom - top

    if (w <= 0 || h <= 0) return false

    // 為了速度，取樣降到 4x4 的像素格
    val stepX = max(1, w / 4)
    val stepY = max(1, h / 4)

    var brightCount = 0
    var total = 0

    for (y in top until bottom step stepY) {
        for (x in left until right step stepX) {
            val pixel = bitmap.getPixel(x, y)

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = (pixel) and 0xFF

            // 簡單亮度：RGB 平均
            val brightness = (r + g + b) / 3

            if (brightness > 240) { // 高亮區
                brightCount++
            }

            total++
        }
    }

    // 若高亮像素超過 35%，視為過曝
    val ratio = brightCount.toFloat() / total
    return ratio > 0.35f
}


fun getAvatarFilename(isMale: Boolean, age: Int): String {

    // 预设的文件名前缀，请替换为你的实际文件名
    val FilenamesElderly = arrayOf(
        arrayOf("glasses.mp4","vitamin.mp4","sweater.mp4"),
        arrayOf("nursing_house.mp4","dentisit.mp4","health_food.mp4"),
    )

    val FilenamesMiddleAge = arrayOf(
        arrayOf("high-end_watch.mp4","3c.mp4","cars.mp4"),
        arrayOf("luxury_bag.mp4","plastic_surgery.mp4","jewelry.mp4"),
    )

    val FilenamesTeenAger = arrayOf(
        arrayOf("esports.mp4","nba.mp4","marvel.mp4"),
        arrayOf("labubu.mp4","kpop.mp4","cosmetic.mp4"),
    )

    // 确保年龄不会是负数
    val safeAge = age.coerceAtLeast(0)

    // 根据年龄区间返回对应的文件名索引
    val myClass = when (safeAge) {
        in 0..18 -> FilenamesTeenAger
        in 19..49 -> FilenamesMiddleAge
        else -> FilenamesElderly
    }

    // 根据性别返回最终的文件名
    val filename =  if (isMale) {
        myClass[0].random()
    } else {
        myClass[1].random()
    }

    return "asset:///video/$filename"
}
fun smartUpdate(current: TrackedFace?, new: TrackedFace?, onUpdate: (TrackedFace?) -> Unit) {
    // 1. 检查 'new' 和 'current' 是否完全相同（包括 null vs. null）
    //    如果两者都为 null，则不触发更新。
    if (current == new) return

    // 2. 如果两者之一是 null (current != new)，则视为状态变化，触发更新。
    if (current == null || new == null) {
        onUpdate(new)
        return
    }

    // 3. 只有当两者都有值时，才比较它们的 ID。
    //    如果 ID 不同，则触发更新。
    if (current.id != new.id) {
        onUpdate(new)
    }
}

// Shared models moved to AppModels.kt
fun PathShape(block:  Path.(w: Float, h: Float) -> Unit): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply { block(size.width, size.height) }
        return Outline.Generic(path)
    }
}

@Composable
fun Dp.inPx(): Float {
    val density = LocalDensity.current
    return with(density) {
        this@inPx.toPx()
    }
}

@Composable
fun GetDimenInDp(id: Int): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 1. 获取原始像素值 (Float)
    val pxValue = context.resources.getDimension(id)

    // 2. 在 Density 上下文中，将像素值转换为 Dp
    return with(density) {
        max(pxValue.toDp(), 0.dp)
    }
}
/**
 * 產生大小自適應 QR Code 的 Composable 函數。
 *
 * QR Code 將會填充由 [modifier] 決定的可用空間，並以正方形顯示。
 *
 * @param content 要編碼成 QR Code 的字串內容。
 * @param modifier 用於設定 QR Code 佔用空間的 Modifier (例如 Modifier.fillMaxWidth().aspectRatio(1f))。
 */
@Composable
fun AdaptiveZxingQRCodeImage(
    content: String,
    modifier: Modifier = Modifier,
) {
    // 1. 使用 BoxWithConstraints 獲取 Composable 可用的最大尺寸 (DP)
    BoxWithConstraints(modifier = modifier) {

        // QR Code 必須是正方形，所以取可用空間的最小邊長
        val sizeDp = minOf(maxWidth, maxHeight)

        // 2. 獲取當前的像素密度 (Density)
        val density = LocalDensity.current

        // 3. 將 DP 尺寸轉換為需要的像素 (px) 尺寸
        val sizePx = remember(sizeDp, density) {
            with(density) {
                // 將 Dp 轉換為 Float，然後四捨五入為 Int 像素值
                sizeDp.toPx().roundToInt()
            }
        }

        // 4. 使用轉換後的像素尺寸來生成 Bitmap
        val bitmap = remember(content, sizePx) {
            generateQRCodeBitmap(content, sizePx)
        }

        // 5. 顯示生成的 Bitmap
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "自適應 QR Code for $content",
                // 讓 Image 使用計算出來的 DP 尺寸
                modifier = Modifier.size(sizeDp),
                // 確保圖片在容器內正確縮放
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun generateQRCodeBitmap(content: String, size: Int): Bitmap? {
    // 設置邊界值。標準是 4，設置為 1 或 0 可以大幅減少留白。
    // 警告：設置為 0 可能會導致部分掃描器無法識別！建議嘗試 1。
    val quietZoneSize = 1 // 模塊數 (Module)

    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        // 關鍵修改：設置 MARGIN 提示來控制靜區大小
        EncodeHintType.MARGIN to quietZoneSize
    )
    val writer = QRCodeWriter()

    return try {
        // 1. 編碼字串並生成 BitMatrix
        val bitMatrix: BitMatrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            size, // 寬度 (px)
            size, // 高度 (px)
            hints
        )

        // 2. 創建一個空的 Bitmap
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        // 3. 遍歷 BitMatrix 設置 Bitmap 像素的顏色
        for (x in 0 until size) {
            for (y in 0 until size) {
                val color = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                bitmap.setPixel(x, y, color)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun QRCodeAndImageLayout(
    qrContent: String,
    bottomImageResId: Int,
    modifier: Modifier = Modifier.fillMaxSize() // 確保它佔滿父容器
) {
    Column(
        modifier = modifier
            .padding(5.dp) // 外邊距
    ) {
        Box(
            modifier = Modifier
                .weight(3f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // QR Code 需要保持正方形並最大化其在 Box 內的空間
            AdaptiveZxingQRCodeImage(
                content = qrContent,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )
        }

        // --- 3. 下方 Image 區域 (佔總高度的 1/3) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = bottomImageResId),
                contentDescription = "Bottom Promotional Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

fun isFaceStable(prev: Rect?, curr: Rect, threshold: Float = 0.06f): Boolean {
    if (prev == null) return false

    val dx = (prev.centerX() - curr.centerX()).toFloat()
    val dy = (prev.centerY() - curr.centerY()).toFloat()

    val dist = kotlin.math.sqrt(dx*dx + dy*dy)

    // 以 bbox 寬度當作比例基準
    val allowMove = curr.width() * threshold

    return dist < allowMove
}

fun Bitmap.isBlurred(threshold: Double = 60.0): Boolean {
    val width = this.width
    val height = this.height

    // 轉成灰階（快速版）
    val gray = IntArray(width * height)
    this.getPixels(gray, 0, width, 0, 0, width, height)

    val laplacian = DoubleArray(width * height)

    var sum = 0.0
    var sumSq = 0.0

    // Laplacian kernel:
    // [ 0, -1,  0 ]
    // [ -1, 4, -1 ]
    // [ 0, -1,  0 ]

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x

            val p = fun(px: Int): Double {
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = (px) and 0xFF
                return (0.299*r + 0.587*g + 0.114*b) // 灰階
            }

            val center = p(gray[idx])
            val top = p(gray[idx - width])
            val bottom = p(gray[idx + width])
            val left = p(gray[idx - 1])
            val right = p(gray[idx + 1])

            val value = 4 * center - top - bottom - left - right
            laplacian[idx] = value

            sum += value
            sumSq += value * value
        }
    }

    val mean = sum / laplacian.size
    val variance = sumSq / laplacian.size - mean * mean

    // ------------ 判斷模糊 -------------
    // variance 越小 -> 越模糊
    return variance < threshold
}

