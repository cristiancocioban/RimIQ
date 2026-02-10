package com.rimiq.training.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.pose.PoseLandmark
import com.rimiq.training.camera.PoseAnalyzer
import com.rimiq.training.model.Defaults
import com.rimiq.training.model.PoseFrame
import com.rimiq.training.vm.TrainingViewModel
import java.util.concurrent.Executors

@Composable
fun TrainingRoute(viewModel: TrainingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingScreen(
        latestFrame = uiState.latestPoseFrame,
        repCount = uiState.metrics.repCount,
        timerText = "${uiState.metrics.elapsedMs / 1000}s",
        stanceCue = uiState.metrics.stanceCue ?: "Good Form",
        onPoseFrame = { frame -> viewModel.onPoseFrame(frame, null) }
    )
}

@Composable
fun TrainingScreen(
    latestFrame: PoseFrame?,
    repCount: Int,
    timerText: String,
    stanceCue: String,
    onPoseFrame: (PoseFrame) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    LaunchedEffect(previewView, hasCameraPermission) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect

        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val analyzer = PoseAnalyzer(isFrontCamera = true, onPoseFrame = onPoseFrame)

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageRotationEnabled(true)
            .build()
            .also { it.setAnalyzer(cameraExecutor, analyzer) }

        provider.unbindAll()
        provider.bindToLifecycle(
            context as androidx.lifecycle.LifecycleOwner,
            CameraSelector.Builder().requireLensFacing(Defaults.defaultLensFacing).build(),
            preview,
            analysis
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF111A2E), Color(0xFF090E1B))))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).also { pv ->
                    pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                    pv.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    previewView = pv
                }
            }
        )

        PoseOverlay(latestFrame = latestFrame)
        TopBar()
        MetricPills(repCount, timerText, stanceCue, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Menu, null, tint = Color.White)
        Text("TRACKER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Icon(Icons.Default.Person, null, tint = Color.White)
    }
}

@Composable
private fun MetricPills(repCount: Int, timerText: String, stanceCue: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pill("Reps", repCount.toString())
        Pill("Timer", timerText)
        Pill("Cue", stanceCue)
    }
}

@Composable
private fun Pill(title: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xAA1A2238)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = title, color = Color(0xFFFFA94D), fontSize = 11.sp)
            Text(text = value, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PoseOverlay(latestFrame: PoseFrame?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val frame = latestFrame ?: return@Canvas
        val pairs = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
        )

        fun transform(pointX: Float, pointY: Float): Offset {
            val xNorm = pointX / frame.width
            val yNorm = pointY / frame.height
            val (rx, ry) = when (frame.rotationDegrees) {
                90 -> yNorm to (1f - xNorm)
                180 -> (1f - xNorm) to (1f - yNorm)
                270 -> (1f - yNorm) to xNorm
                else -> xNorm to yNorm
            }
            val mirroredX = if (frame.isFrontCamera) 1f - rx else rx
            return Offset(mirroredX * size.width, ry * size.height)
        }

        pairs.forEach { (start, end) ->
            val a = frame.points[start] ?: return@forEach
            val b = frame.points[end] ?: return@forEach
            drawLine(Color(0xFFFF8B2C), transform(a.x, a.y), transform(b.x, b.y), strokeWidth = 4f)
        }

        frame.points.values.forEach { p ->
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = transform(p.x, p.y),
                style = Stroke(width = 3f)
            )
        }
    }
}
