package com.lagersystem.interactivesystem.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.draw.clipToBounds

@OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR-Code scannen") },
        text = {
            Box(modifier = Modifier.size(250.dp).clipToBounds()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val executor = ContextCompat.getMainExecutor(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val scanner = BarcodeScanning.getClient()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                barcode.rawValue?.let { 
                                                    onCodeScanned(it)
                                                    imageAnalysis.clearAnalyzer()
                                                    onDismiss()
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Scanner Overlay
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = 0.99f)
                ) {
                    val boxSize = size.width * 0.7f
                    val offset = (size.width - boxSize) / 2
                    
                    // Dark background
                    drawRect(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                    
                    // Transparent Hole
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                        size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                    
                    // Border
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                        size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
