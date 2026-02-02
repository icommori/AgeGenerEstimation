/*
adb root
adb shell setenforce 0
adb shell chmod 644 /sys/kernel/ged/hal/gpu_utilization
 */

package com.ml.innocomm.age_genderdetection

import android.content.Context
import android.os.HardwarePropertiesManager
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile

data class CpuCoreStat(
    var lastTotal: Long = 0,
    var lastIdle: Long = 0
)
class HardwareMonitor {

    private var lastTotal: Long = 0
    private var lastIdle: Long = 0
    private val coreStats = mutableMapOf<Int, CpuCoreStat>()

    /**
     * 回傳：
     * key = core index (0,1,2...)
     * value = CPU loading (%) for that core
     */
    fun getPerCoreCpuLoad(): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()

        try {
            val reader = BufferedReader(InputStreamReader(File("/proc/stat").inputStream()))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break

                // 只處理 cpu0, cpu1, ...
                if (!l.startsWith("cpu") || l.startsWith("cpu ")) continue

                val parts = l.split("\\s+".toRegex())
                val coreIndex = parts[0].removePrefix("cpu").toIntOrNull() ?: continue

                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val system = parts[3].toLong()
                val idle = parts[4].toLong()
                val iowait = parts[5].toLong()
                val irq = parts[6].toLong()
                val softirq = parts[7].toLong()

                val total = user + nice + system + idle + iowait + irq + softirq

                val stat = coreStats.getOrPut(coreIndex) { CpuCoreStat() }

                val diffTotal = total - stat.lastTotal
                val diffIdle = idle - stat.lastIdle

                if (diffTotal > 0) {
                    val usage = ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
                    result[coreIndex] = usage.coerceIn(0, 100)
                } else {
                    result[coreIndex] = 0
                }

                stat.lastTotal = total
                stat.lastIdle = idle
            }

            reader.close()
        } catch (e: Exception) {
            Log.e("HardwareMonitor", "getPerCoreCpuLoad failed", e)
        }

        return result
    }

    // --- CPU 負載 (需要讀取 /proc/stat) ---
    // 注意：Android 8.0+ 的第三方 App 可能會讀到空值，建議用於系統開發
    fun getCpuLoad(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split("\\s+".toRegex())

            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val ioWait = parts[5].toLong()
            val irq = parts[6].toLong()
            val softIrq = parts[7].toLong()

            val total = user + nice + system + idle + ioWait + irq + softIrq
            val diffTotal = total - lastTotal
            val diffIdle = idle - lastIdle

            lastTotal = total
            lastIdle = idle

            if (diffTotal == 0L) 0 else ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
        } catch (e: Exception) { 0 }
    }

    fun getGpuLoad(): Int {

        val gpuPaths = arrayOf(
            // 1. MTK Dimensity 常用路徑 (GED 驅動)
            "/sys/kernel/ged/hal/gpu_utilization"
        )

        for (path in gpuPaths) {
            try {
                val file = File(path)
                //Log.v("innocomm", "getGpuLoad: exists:"+file.exists()+",canRead:"+file.canRead())
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    //Log.v("innocomm", "$path content: $content")

                    // 處理 "0 0 100" 或 "45%" 或 "45"
                    // 1. 先拿掉百分比符號
                    // 2. 依照空格拆分，取第一個元素
                    val firstPart = content.replace("%", "")
                        .split("\\s+".toRegex())
                        .firstOrNull()

                    return firstPart?.toIntOrNull() ?: 0
                }
            } catch (e: Exception) {
                Log.e("innocomm", "Error reading $path: ${e.message}")
            }
        }
        return -1 // 返回 -1 代表所有已知路徑皆失敗
    }

    fun getGpuFreqMHz(): Int {
        val devfreqRoot = File("/sys/class/devfreq")
        val gpuDirs = devfreqRoot.listFiles() ?: return -1

        for (dir in gpuDirs) {
            if (!dir.name.contains("mali", ignoreCase = true)) continue

            val curFreq = File(dir, "cur_freq")
            if (curFreq.exists() && curFreq.canRead()) {
                val hz = curFreq.readText().trim().toLongOrNull()
                if (hz != null && hz > 0) {
                    return (hz / 1_000_000).toInt()
                }
            }
        }
        return -1
    }

    fun getCpuTemperature(context:Context): Double {
        try {
            val hwMgr = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as HardwarePropertiesManager

            // 獲取所有 CPU 感測器的當前溫度
            val temps = hwMgr.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT
            )

            if (temps.isEmpty()) {
                // 如果回傳空，代表權限不足或硬體不支持此 API
                Log.w("innocomm", "無法獲取溫度：temps 為空 (可能是權限問題)")
                return 0.0
            }

            // 有些設備會回傳多個核心的溫度，這裡取平均值
            return temps.average()

        } catch (e: SecurityException) {
            Log.e("innocomm", "權限不足：無法調用 HardwarePropertiesManager", e)
            return -1.0
        } catch (e: Exception) {
            Log.e("innocomm", "獲取溫度失敗", e)
            return 0.0
        }
    }

}