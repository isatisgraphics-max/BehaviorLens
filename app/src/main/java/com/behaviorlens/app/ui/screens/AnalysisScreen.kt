package com.behaviorlens.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.behaviorlens.app.data.models.Emotion
import com.behaviorlens.app.data.models.FusedResult
import com.behaviorlens.app.viewmodel.AnalysisViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    mode: String,
    onNavigateToResults: () -> Unit,
    onBack: () -> Unit,
    vm: AnalysisViewModel = viewModel()
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val isAnalyzing by vm.isAnalyzing.collectAsState()
    val currentResult by vm.currentResult.collectAsState()

    LaunchedEffect(Unit) {
        vm.initialize()
        if (mode == "camera") {
            cameraPermission.launchPermissionRequest()
            audioPermission.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mode.replaceFirstChar { it.uppercase() } + " Analysis") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isAnalyzing) vm.stopLiveAnalysis()
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    if (isAnalyzing) {
                        IconButton(onClick = {
                            vm.stopLiveAnalysis()
                            onNavigateToResults()
                        }) { Icon(Icons.Default.Stop, null, tint = Color.Red) }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mode == "camera" && cameraPermission.hasPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrameReady = { bitmap ->
                        if (!isAnalyzing) vm.startLiveAnalysis()
                        vm.analyzeFrame(bitmap)
                    }
                )
            }
            currentResult?.fusedResult?.let { result ->
                LiveScoreOverlay(
                    result = result,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onFrameReady: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val bitmap = imageProxy.toBitmap()
                            onFrameReady(bitmap)
                            imageProxy.close()
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview, imageAnalysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

private fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

@Composable
private fun LiveScoreOverlay(result: FusedResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScorePill("Honesty", result.honestyIndex.toInt(), Color(0xFF4CAF50))
                ScorePill("Anxiety", result.anxietyLevel.toInt(), Color(0xFFFF9800))
                ScorePill("Deception Risk", result.deceptionRisk.toInt(), Color(0xFFF44336))
            }
            val dominant = result.emotions.maxByOrNull { it.value }
            dominant?.let {
                Text(
                    "Dominant: ${it.key.name} (${(it.value * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value%",
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
