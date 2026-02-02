package com.ml.innocomm.age_genderdetection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class HardwareState(
    val cpuTotal: Int = 0,
    val perCoreLoad: Map<Int, Int> = emptyMap(),
    val gpuLoad: Int = 0,
    val gpuFreq: Int = 0,
    val temperature: Double = 0.0
)

@Composable
fun HardwareMonitorScreen(monitor: HardwareMonitor) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(HardwareState()) }

    LaunchedEffect(Unit) {
        while (true) {
            val newState = withContext(Dispatchers.IO) {
                HardwareState(
                    cpuTotal = monitor.getCpuLoad(),
                    perCoreLoad = monitor.getPerCoreCpuLoad(),
                    gpuLoad = monitor.getGpuLoad(),
                    gpuFreq = monitor.getGpuFreqMHz(),
                    temperature = monitor.getCpuTemperature(context)
                )
            }
            state = newState
            delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // CPU 資訊
        val perCoreText = state.perCoreLoad
            .toSortedMap()
            .entries
            .joinToString(" ") { "${it.key}:${it.value.toString().padStart(2, ' ')}%" }

        HardwareInfoRow(
            label = "CPU",
            value = "${state.cpuTotal.toString().padStart(2, ' ')}% ($perCoreText)"
        )

        // GPU 資訊
        val gpuValue = if (state.gpuFreq > 0) {
            "${state.gpuLoad.toString().padStart(2, ' ')}% @ ${state.gpuFreq} MHz"
        } else {
            "${state.gpuLoad}%"
        }
        HardwareInfoRow(label = "GPU", value = gpuValue)

        // 溫度資訊
        HardwareInfoRow(label = "Temp", value = "%.1f °C".format(state.temperature))
    }
}

@Composable
fun HardwareInfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 固定標籤寬度，確保冒號後的內容垂直對齊
        Text(
            text = "$label:",
            color = Color(0xFFFF9800),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(45.dp) // 根據標籤長度調整寬度
        )
        Text(
            text = value,
            color = Color(0xFFFF9800),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, // 等寬字體避免數字跳動
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun HardwareInfoText(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color.White,
        fontSize = 16.sp
    )
}