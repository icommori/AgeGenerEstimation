package com.ml.innocomm.age_genderdetection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    modelNames: Array<String>,
    currentModelIndex: Int,
    onModelSelected: (Int) -> Unit,
    currentMode: InferenceMode,
    onModeSelected: (InferenceMode) -> Unit,
    onlyFrontFace: Boolean,
    onOnlyFrontFaceChanged: (Boolean) -> Unit
) {
    if (!showDialog) return

    // 🔹 建立暫存的設定（按 OK 才套用）
    var tempModelIndex by remember { mutableStateOf(currentModelIndex) }
    var tempMode by remember { mutableStateOf(currentMode) }
    var tempOnlyFrontFace by remember { mutableStateOf(onlyFrontFace) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings")
                Text(
                    text = " - v${BuildConfig.VERSION_NAME}",
                    //color = androidx.compose.ui.graphics.Color(0xFFFF9800)
                )
            }
        },
        text = {
            Column {
                // 模型選擇
                Text("Model Selection", fontWeight = FontWeight.Bold)
                DropdownMenuBox(
                    items = modelNames.toList(),
                    selectedIndex = tempModelIndex,
                    onSelect = { tempModelIndex = it }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Inference Mode", fontWeight = FontWeight.Bold)
                DropdownMenuBox(
                    items = InferenceMode.values().map { it.name },
                    selectedIndex = InferenceMode.values().indexOf(tempMode),
                    onSelect = { idx -> tempMode = InferenceMode.values()[idx] }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = tempOnlyFrontFace,
                        onCheckedChange = { tempOnlyFrontFace = it }
                    )
                    Text("Only front-facing faces")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // ✅ 按 OK 才套用更動
                    onModelSelected(tempModelIndex)
                    onModeSelected(tempMode)
                    onOnlyFrontFaceChanged(tempOnlyFrontFace)
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DropdownMenuBox(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = items[selectedIndex],
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { idx, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(idx)
                        expanded = false
                    }
                )
            }
        }
    }
}
