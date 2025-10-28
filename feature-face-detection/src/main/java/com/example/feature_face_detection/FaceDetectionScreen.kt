package com.example.feature_face_detection

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun FaceDetectionScreen() {
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Resultados
    var faceBoxes by remember { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }

    // Tamaño mostrado para mapear coords
    var renderedSize by remember { mutableStateOf(IntSize.Zero) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedUri = uri }

    // Cargar Bitmap cuando cambia el URI (respetando EXIF en API 28+)
    LaunchedEffect(selectedUri) {
        faceBoxes = emptyList()
        errorText = null
        bitmap = null
        val uri = selectedUri ?: return@LaunchedEffect
        try {
            bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (t: Throwable) {
            errorText = "Error cargando imagen: ${t.message}"
        }
    }

    // Correr ML Kit cuando hay bitmap
    LaunchedEffect(bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        isProcessing = true
        errorText = null
        faceBoxes = emptyList()

        try {
            val image = InputImage.fromBitmap(bmp, 0) // rotación ya viene aplicada por ImageDecoder
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build()
            val detector = FaceDetection.getClient(options)
            val faces = detector.process(image).await()
            faceBoxes = faces.map { it.boundingBox }
        } catch (t: Throwable) {
            errorText = "Error en detección: ${t.message}"
        } finally {
            isProcessing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) { Text("Elegir imagen") }

            if (isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Detectando rostros…")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Imagen + overlay
        val bmp = bitmap
        if (bmp != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width / bmp.height.toFloat())
                    .onSizeChanged { renderedSize = it },
                contentAlignment = Alignment.Center
            ) {
                // Imagen (mantén aspect ratio)
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Imagen seleccionada",
                    contentScale = ContentScale.Fit, // Fit para preservar escalado uniforme (con letterbox si hace falta)
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay con bounding boxes
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    if (renderedSize.width == 0 || renderedSize.height == 0) return@Canvas

                    // Calcula escalado y offsets para mapear coords de bitmap → canvas
                    val imgW = bmp.width.toFloat()
                    val imgH = bmp.height.toFloat()
                    val viewW = size.width
                    val viewH = size.height

                    // Escalado uniforme (fit), con barras si sobra en un eje
                    val scale = minOf(viewW / imgW, viewH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val offsetX = (viewW - drawW) / 2f
                    val offsetY = (viewH - drawH) / 2f

                    val stroke = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    faceBoxes.forEach { r ->
                        // r está en coords del bitmap
                        val left = offsetX + r.left * scale
                        val top = offsetY + r.top * scale
                        val right = offsetX + r.right * scale
                        val bottom = offsetY + r.bottom * scale

                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = stroke
                        )
                    }
                }
            }
        } else {
            Text(
                text = when {
                    errorText != null -> errorText!!
                    else -> "Selecciona una imagen con rostros para ver los recuadros."
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

/* ---- await() helper para Task<T> ---- */
suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
        addOnCanceledListener { cont.cancel() }
    }
