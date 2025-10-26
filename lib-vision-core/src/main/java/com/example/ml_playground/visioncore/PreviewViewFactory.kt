package com.example.ml_playground.visioncore

import android.content.Context
import androidx.camera.view.PreviewView

fun createPreviewView(context: Context): PreviewView =
    PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
