package com.ml.innocomm.age_genderdetection

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun FullScreenExoPlayerView(
    url: String,
    videoAlpha: Float,
    isLooping: Boolean = true, // 循环播放
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. 创建和记住 ExoPlayer 实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            // 设置循环模式
            repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true // 自动播放
        }
    }

    // 2. 监听生命周期以控制播放/暂停 (重要优化)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 3. 在 Composable 退出时释放播放器资源
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // 4. 将 ExoPlayer 挂载到 PlayerView (SurfaceView/TextureView)
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .alpha(videoAlpha),
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false // 隐藏控制栏

                // --- 关键设置：实现全屏裁剪填充 (ContentScale.Crop) ---
                // RESIZE_MODE_ZOOM: 保持宽高比，但放大以填充整个 View，裁剪超出部分。
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    )
}