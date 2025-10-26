package com.example.feature_camerax_hello

import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ml_playground.visioncore.createPreviewView
import com.example.ml_playground.visioncore.rememberCameraPermissionState

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasPermission by rememberCameraPermissionState()
    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Se requiere permiso de cámara")
        }
        return
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS
            )
        }
    }

    DisposableEffect(Unit) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> createPreviewView(ctx).apply { this.controller = controller } },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Button(
            onClick = {
                val resolver = context.contentResolver
                val name = "IMG_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraXHello")
                }
                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                ) ?: return@Button

                val output = ImageCapture.OutputFileOptions
                    .Builder(resolver, uri, contentValues)
                    .build()

                controller.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Toast.makeText(context, "Error: ${exc.message}", Toast.LENGTH_SHORT).show()
                        }
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Toast.makeText(context, "Foto guardada ✅", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("Capturar") }
    }
}