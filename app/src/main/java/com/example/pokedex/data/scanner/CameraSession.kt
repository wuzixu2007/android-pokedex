/* CameraX lifecycle, preview surface, and JPEG capture adapter. / CameraX 生命周期、预览表面与 JPEG 拍照适配层。 */
package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import java.io.File

class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val preview = Preview.Builder().build()
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(CAPTURE_WIDTH, CAPTURE_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                )
                .build(),
        )
        .build()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var bindingRequested = false
    private var bindingGeneration = 0
    private var captureInProgress = false

    var surfaceRequest by mutableStateOf<SurfaceRequest?>(null)
        private set

    var isReady by mutableStateOf(false)
        private set

    fun bind(onError: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            isReady = false
            onError("相机权限未授予")
            return
        }
        bindingRequested = true
        isReady = false
        val generation = ++bindingGeneration
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    if (!bindingRequested || generation != bindingGeneration) return@runCatching
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val activityRotation = (lifecycleOwner as? Activity)?.let { activity ->
                        @Suppress("DEPRECATION")
                        activity.windowManager.defaultDisplay.rotation
                    }
                    val rotation = activityRotation
                        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.display?.rotation ?: Surface.ROTATION_0
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
                        }
                    preview.targetRotation = rotation
                    imageCapture.targetRotation = rotation
                    preview.setSurfaceProvider { request ->
                        surfaceRequest = request
                    }
                    provider.unbindAll()
                    val selector = when {
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> throw IllegalStateException("设备没有可用相机")
                    }
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                    isReady = true
                }.onFailure { error ->
                    if (generation != bindingGeneration) return@onFailure
                    isReady = false
                    onError("相机启动失败：${error.message ?: "未知错误"}")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun zoomBy(scaleFactor: Float) {
        if (!isReady || !scaleFactor.isFinite() || scaleFactor <= 0f) return
        val activeCamera = camera ?: return
        val zoomState = activeCamera.cameraInfo.zoomState.value ?: return
        val target = (zoomState.zoomRatio * scaleFactor)
            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        activeCamera.cameraControl.setZoomRatio(target)
    }

    fun capture(
        onCaptured: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isReady || camera == null) {
            onError("相机仍在启动，请稍候")
            return
        }
        if (captureInProgress) return

        captureInProgress = true
        val captureDirectory = File(context.cacheDir, "captures").apply { mkdirs() }
        val captureFile = File(captureDirectory, "capture-${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(captureFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureInProgress = false
                    onCaptured(captureFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    captureFile.delete()
                    isReady = false
                    onError("拍照失败：${exception.message ?: "未知错误"}")
                }
            },
        )
    }

    fun unbind() {
        bindingRequested = false
        bindingGeneration++
        captureInProgress = false
        runCatching { cameraProvider?.unbind(preview, imageCapture) }
        camera = null
        isReady = false
        surfaceRequest = null
    }
}

private const val CAPTURE_WIDTH = 1280
private const val CAPTURE_HEIGHT = 960

@Composable
fun rememberCameraSession(
    enabled: Boolean,
    onError: (String) -> Unit,
): CameraSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    val session = remember(context, lifecycleOwner) {
        CameraSession(context, lifecycleOwner)
    }

    DisposableEffect(session, enabled) {
        if (enabled) {
            session.bind(currentOnError)
        }
        onDispose {
            session.unbind()
        }
    }

    return session
}
