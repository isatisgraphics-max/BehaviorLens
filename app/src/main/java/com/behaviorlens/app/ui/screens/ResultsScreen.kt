package com.behaviorlens.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.behaviorlens.app.data.models.Emotion
import com.behaviorlens.app.data.models.FusedResult
import com.behaviorlens.app.viewmodel.AnalysisViewModel
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(onBack: () -> Unit, vm: AnalysisViewModel = viewModel()) {
    val currentResult by vm.currentResult.collectAsState()
    val fused = currentResult?.fusedResult

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        if (fused == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No analysis data available")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Emotion Distribution", style = MaterialTheme.typography.titleMedium)
            EmotionRadarChart(emotions = fused.emotions, modifier = Modifier.fillMaxWidth().height(260.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GaugeChart(
                    label = "Honesty",
                    value = fused.honestyIndex,
                    ci = fused.honestyCI,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f).height(160.dp)
                )
                GaugeChart(
                    label = "Anxiety",
                    value = fused.anxietyLevel,
                    ci = fused.anxietyCI,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f).height(160.dp)
                )
            }
            GaugeChart(
                label = "Deception Risk",
                value = fused.deceptionRisk,
                ci = 5f,
                color = Color(0xFFF44336),
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Score Summary", style = MaterialTheme.typography.titleSmall)
                    SummaryRow("Engagement", fused.engagementScore.toInt())
                    SummaryRow("Honesty", fused.honestyIndex.toInt())
                    SummaryRow("Anxiety", fused.anxietyLevel.toInt())
                    SummaryRow("Deception Risk", fused.deceptionRisk.toInt())
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value%", style = MaterialTheme.typography.bodyMedium)
    }
    LinearProgressIndicator(
        progress = value / 100f,
        modifier = Modifier.fillMaxWidth().height(4.dp)
    )
}

@Composable
fun EmotionRadarChart(emotions: Map<Emotion, Float>, modifier: Modifier = Modifier) {
    val emotionList = Emotion.values().toList()
    val sides = emotionList.size
    val radarColor = Color(0xFF6650A4)
    val gridColor = Color.Gray.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.75f
        val angleStep = (2 * PI / sides).toFloat()

        for (ring in 1..4) {
            val ringR = r * ring / 4f
            val pts = (0 until sides).map { i ->
                val angle = i * angleStep - PI.toFloat() / 2
                Offset(cx + ringR * cos(angle), cy + ringR * sin(angle))
            }
            for (i in pts.indices) {
                drawLine(gridColor, pts[i], pts[(i + 1) % sides], strokeWidth = 1f)
            }
        }

        val axisPts = (0 until sides).map { i ->
            val angle = i * angleStep - PI.toFloat() / 2
            Offset(cx + r * cos(angle), cy + r * sin(angle))
        }
        axisPts.forEach { drawLine(gridColor, Offset(cx, cy), it, strokeWidth = 1f) }

        val dataPts = emotionList.mapIndexed { i, emotion ->
            val value = emotions[emotion] ?: 0f
            val angle = i * angleStep - PI.toFloat() / 2
            Offset(cx + r * value * cos(angle), cy + r * value * sin(angle))
        }
        for (i in dataPts.indices) {
            drawLine(radarColor, dataPts[i], dataPts[(i + 1) % sides], strokeWidth = 2.5f)
        }
        dataPts.forEach { drawCircle(radarColor, radius = 5f, center = it) }
    }
}

@Composable
fun GaugeChart(
    label: String,
    value: Float,
    ci: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = Color.Gray.copy(alpha = 0.2f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.65f
            val r = minOf(cx, cy) * 0.85f
            val startAngle = 180f
            val sweepMax = 180f

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepMax,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepMax * (value / 100f),
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )
            val ciSweep = sweepMax * (ci / 100f)
            val midSweep = sweepMax * (value / 100f)
            drawArc(
                color = color.copy(alpha = 0.3f),
                startAngle = startAngle + midSweep - ciSweep / 2f,
                sweepAngle = ciSweep,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 18f)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Text("${value.toInt()}%", style = MaterialTheme.typography.headlineMedium, color = color)
            Text("±${ci.toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
