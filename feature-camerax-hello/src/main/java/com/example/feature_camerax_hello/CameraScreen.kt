package com.example.feature_camerax_hello

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
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
import androidx.core.view.doOnLayout
import com.example.ml_playground.visioncore.createPreviewView
import com.example.ml_playground.visioncore.rememberCameraPermissionState

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permisos según API
    val permissionsToRequest = if (Build.VERSION.SDK_INT <= 28) {
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    } else {
        listOf(Manifest.permission.CAMERA)
    }

    val hasPermission by rememberCameraPermissionState(permissionsToRequest)
    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Se requiere permiso de cámara")
        }
        return
    }

    // Controller de CameraX
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS
            )
            imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        }
    }

    // Bind/unbind al ciclo de vida
    DisposableEffect(Unit) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                createPreviewView(ctx).also { preview ->
                    preview.controller = controller
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Button(
            onClick = {
                // ContentValues para MediaStore
                val name = "IMG_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraXHello")
                    }
                }

                // ✅ Deja que CameraX inserte en MediaStore (no insertes tú el Uri antes)
                val output = ImageCapture.OutputFileOptions
                    .Builder(
                        context.contentResolver,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    .build()

                controller.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Toast.makeText(
                                context,
                                "Error(${exc.imageCaptureError}): ${exc.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            exc.printStackTrace()
                        }
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            Toast.makeText(context, "Foto guardada ✅", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) { Text("Capturar") }
    }
}
