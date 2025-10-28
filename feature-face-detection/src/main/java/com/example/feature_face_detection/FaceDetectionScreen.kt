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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceDetectionScreen() {
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Resultados
    var faceBoxes by remember { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }

    // UI controls
    var showBoxes by remember { mutableStateOf(true) }
    var strokeDp by remember { mutableStateOf(3f) }
    val colorOptions = listOf(
        "Rojo" to Color.Red,
        "Verde" to Color(0xFF00C853),
        "Azul" to Color(0xFF2962FF),
        "Amarillo" to Color(0xFFFFD600),
    )
    var selectedColorIdx by remember { mutableStateOf(0) }
    var colorMenuExpanded by remember { mutableStateOf(false) }

    // Tamaño mostrado para mapear coords
    var renderedSize by remember { mutableStateOf(IntSize.Zero) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedUri = uri }

    // Cargar Bitmap cuando cambia el URI (respeta EXIF en API 28+)
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
            val image = InputImage.fromBitmap(bmp, 0)
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
        // Top bar de acciones
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
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Detectando…")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Controles de overlay
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Switch mostrar cajas
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showBoxes, onCheckedChange = { showBoxes = it })
                Spacer(Modifier.width(6.dp))
                Text("Mostrar boxes")
            }

            // Grosor
            Text("Grosor", modifier = Modifier.padding(start = 4.dp))
            Slider(
                value = strokeDp,
                onValueChange = { strokeDp = it },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.weight(1f)
            )

            // Color (dropdown)
            ExposedDropdownMenuBox(
                expanded = colorMenuExpanded,
                onExpandedChange = { colorMenuExpanded = it }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = colorOptions[selectedColorIdx].first,
                    onValueChange = {},
                    label = { Text("Color") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorMenuExpanded) },
                    modifier = Modifier.menuAnchor().widthIn(min = 140.dp)
                )
                ExposedDropdownMenu(
                    expanded = colorMenuExpanded,
                    onDismissRequest = { colorMenuExpanded = false }
                ) {
                    colorOptions.forEachIndexed { index, (name, _) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedColorIdx = index
                                colorMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay con bounding boxes (si está activado)
                if (showBoxes && faceBoxes.isNotEmpty()) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        if (renderedSize.width == 0 || renderedSize.height == 0) return@Canvas

                        val viewW = size.width
                        val viewH = size.height
                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()

                        // Escalado uniforme FIT (letterbox si sobra)
                        val scale = minOf(viewW / imgW, viewH / imgH)
                        val drawW = imgW * scale
                        val drawH = imgH * scale
                        val offsetX = (viewW - drawW) / 2f
                        val offsetY = (viewH - drawH) / 2f

                        val stroke = Stroke(
                            width = strokeDp.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                        )
                        val color = colorOptions[selectedColorIdx].second

                        faceBoxes.forEach { r ->
                            val left = offsetX + r.left * scale
                            val top = offsetY + r.top * scale
                            val right = offsetX + r.right * scale
                            val bottom = offsetY + r.bottom * scale

                            drawRect(
                                color = color,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = stroke
                            )
                        }
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
