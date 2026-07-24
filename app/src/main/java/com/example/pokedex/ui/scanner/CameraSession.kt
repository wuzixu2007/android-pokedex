/* CameraX lifecycle, preview surface, and JPEG capture adapter. / CameraX 生命周期、预览表面与 JPEG 拍照适配层。 */
package com.example.pokedex.ui.scanner

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
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
        .build()

    private var cameraProvider: ProcessCameraProvider? = null
    private var bindingRequested = false

    var surfaceRequest by mutableStateOf<SurfaceRequest?>(null)
        private set

    var isReady by mutableStateOf(false)
        private set

    fun bind(onError: (String) -> Unit) {
        bindingRequested = true
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    if (!bindingRequested) return@runCatching
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
                    provider.unbind(preview, imageCapture)
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                    isReady = true
                }.onFailure { error ->
                    isReady = false
                    onError("相机启动失败：${error.message ?: "未知错误"}")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun capture(
        onCaptured: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isReady) {
            onError("相机仍在启动，请稍候")
            return
        }

        val captureDirectory = File(context.cacheDir, "captures").apply { mkdirs() }
        val captureFile = File(captureDirectory, "capture-${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(captureFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onCaptured(captureFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    captureFile.delete()
                    onError("拍照失败：${exception.message ?: "未知错误"}")
                }
            },
        )
    }

    fun unbind() {
        bindingRequested = false
        cameraProvider?.unbind(preview, imageCapture)
        isReady = false
        surfaceRequest = null
    }
}

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
