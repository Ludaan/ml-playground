package com.example.feature_ocr_static

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
/* ---------- Helpers ---------- */

// Coroutines bridge para Task<T>.  Puedes mover esto a una util si quieres.
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun OcrStaticScreen() {
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reprocessingTrigger by remember { mutableStateOf(0) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedUri = uri
    }

    // Corre OCR cuando cambia la imagen seleccionada
    LaunchedEffect(selectedUri,reprocessingTrigger) {
        val uri = selectedUri ?: return@LaunchedEffect
        isProcessing = true
        errorMsg = null
        ocrText = null

        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await() // extensión await abajo
            ocrText = result.text.ifBlank { "(sin texto detectado)" }
        } catch (t: Throwable) {
            errorMsg = t.message ?: t.toString()
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) { Text("Elegir imagen") }

            if (selectedUri != null) {
                Button(onClick = {
                    // Reforzar re-proceso
                    reprocessingTrigger++
                }) { Text("Reprocesar") }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            isProcessing -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text("Procesando OCR…")
                }
            }

            errorMsg != null -> {
                Text("Error: $errorMsg")
            }

            ocrText != null -> {
                Text(
                    text = ocrText!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }

            else -> {
                Text("Selecciona una imagen para extraer texto.")
            }
        }
    }
}

suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
        addOnCanceledListener { cont.cancel() }
    }
