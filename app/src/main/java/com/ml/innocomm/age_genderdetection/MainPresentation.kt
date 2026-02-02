package com.ml.innocomm.age_genderdetection

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ml.innocomm.age_genderdetection.ui.theme.AppTheme

class MainPresentation(
    private val outerContext: Context,
    display: Display,
    private val content: @Composable () -> Unit
) : Presentation(outerContext, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Request no title before super.onCreate if possible, or just before setting content
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        
        // Ensure fullscreen on the external display
        window?.apply {
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(null) // Optional: remove background to ensure no extra borders
            
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(this, decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Allow the window to extend into display cutout areas (if any)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val composeView = ComposeView(context)

        // Set the owners required for Compose to work in a Presentation
        val owner = outerContext as? LifecycleOwner
        val viewModelStoreOwner = outerContext as? ViewModelStoreOwner
        val savedStateRegistryOwner = outerContext as? SavedStateRegistryOwner

        composeView.setViewTreeLifecycleOwner(owner)
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

        composeView.setContent {
            AppTheme {
                content()
            }
        }
        setContentView(composeView)
    }
}
