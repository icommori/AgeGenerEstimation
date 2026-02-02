package com.ml.innocomm.age_genderdetection

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _modelIndex = MutableStateFlow(prefs.getInt("model_index", 0))
    val modelIndexFlow = _modelIndex.asStateFlow()

    private val _inferenceMode =
        MutableStateFlow(prefs.getString("inference_mode", "CPU") ?: "CPU")
    val inferenceModeFlow = _inferenceMode.asStateFlow()

    private val _onlyFrontFace = MutableStateFlow(prefs.getBoolean("only_front_face", true))
    val onlyFrontFaceFlow = _onlyFrontFace.asStateFlow()

    var modelIndex: Int
        get() = _modelIndex.value
        set(value) {
            prefs.edit().putInt("model_index", value).apply()
            _modelIndex.value = value
        }

    var inferenceMode: String
        get() = _inferenceMode.value
        set(value) {
            prefs.edit().putString("inference_mode", value).apply()
            _inferenceMode.value = value
        }

    var onlyFrontFace: Boolean
        get() = _onlyFrontFace.value
        set(value) {
            prefs.edit().putBoolean("only_front_face", value).apply()
            _onlyFrontFace.value = value
        }
}
