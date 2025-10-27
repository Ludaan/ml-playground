package com.example.ml_playground.visioncore

import android.Manifest
import androidx.compose.runtime.derivedStateOf
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import kotlin.collections.toTypedArray

@Composable
fun rememberCameraPermissionState(permissions: List<String>): State<Boolean> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(permissions.toTypedArray())
    }
    return remember(granted) { derivedStateOf { granted } }
}

