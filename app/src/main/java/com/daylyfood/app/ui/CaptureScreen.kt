package com.daylyfood.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor

/**
 * One capture surface for both sources: the viewfinder takes the photo, and the album tile in the
 * corner picks an existing image — the way chat apps do it, instead of two buttons in the diary.
 */
@Composable
fun CaptureScreen(
    newPhotoFile: () -> File?,
    onCaptured: (Uri) -> Unit,
    onPicked: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }

    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                        }.onFailure { error = "相机打不开，去相册选一张吧" }
                        imageCapture.value = capture
                    }, executor)
                    previewView
                },
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("需要用相机才能拍照", color = Color.White)
            }
        }

        // Top bar: close on the left, so the viewfinder keeps the middle.
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("关闭", color = Color.White) }
            Spacer(Modifier.weight(1f))
            error?.let {
                Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Bottom: album tile on the left, shutter in the middle, empty on the right.
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                ) { Text("相册", color = Color.White) }

                Spacer(Modifier.weight(1f))

                Surface(
                    onClick = {
                        val capture = imageCapture.value ?: return@Surface
                        val file = newPhotoFile() ?: run { error = "创建照片文件失败"; return@Surface }
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        capture.takePicture(
                            options,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    val uri = output.savedUri
                                        ?: runCatching {
                                            androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file,
                                            )
                                        }.getOrNull()
                                    if (uri == null) error = "保存照片失败" else onCaptured(uri)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    error = "拍照失败，再试一次"
                                }
                            },
                        )
                    },
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(72.dp),
                ) {}
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(60.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "拍菜单或包装；左下角可换成本地图",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }
}
