package com.ml.innocomm.age_genderdetection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview


@Composable
fun SplitBackgroundLayout() {

    val cutAngle = 22f   // 左區斜度角度（可調）

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(2f / 3f)   // 佔寬度的 2/3
                .align(Alignment.CenterEnd)  // 靠右對齊
                .background(Color.Gray)
        ){
            Image(
                bitmap = rawResource( R.raw.img20paoff),
                contentDescription = "Background Image",
                modifier = Modifier
                    .matchParentSize(),
                        contentScale = ContentScale.FillBounds
            )
        }

        // 左邊不規則斜邊區域（用 Path 裁出）
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height

            val leftWidth = w * 0.60f        // 左區寬度比例
            val slope = kotlin.math.tan(Math.toRadians(cutAngle.toDouble())).toFloat()

            // 左側多邊形 Path
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(leftWidth, 0f)
                lineTo(leftWidth - h * slope, h)
                lineTo(0f, h)
                close()
            }

            drawPath(path, Color.White)  // 左側顏色
        }

    }
}



@Preview(showBackground = true, widthDp = 700, heightDp = 400)
@Composable
fun PreviewZxingQRCodeImage() {
    // 預覽中，QR Code 會自動調整大小以適應 Box 的 250dp 空間
    QRCodeAndImageLayout(
        qrContent = "https://www.innocomm.com/",
        bottomImageResId = R.drawable.logo,
        modifier = Modifier.fillMaxHeight()
    )
}