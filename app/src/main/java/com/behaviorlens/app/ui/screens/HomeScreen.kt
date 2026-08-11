package com.behaviorlens.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.behaviorlens.app.data.models.AnalysisContext
import com.behaviorlens.app.data.models.SessionEntity
import com.behaviorlens.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartCamera: () -> Unit,
    onStartVideo: () -> Unit,
    onStartImage: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val sessions by vm.sessions.collectAsState()
    var selectedContext by remember { mutableStateOf(AnalysisContext.GENERAL) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BehaviorLens") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Context", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalysisContext.values().forEach { ctx ->
                        FilterChip(
                            selected = selectedContext == ctx,
                            onClick = { selectedContext = ctx },
                            label = { Text(ctx.name) }
                        )
                    }
                }
            }
            item {
                Text("Input Source", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputButton(
                        icon = { Icon(Icons.Default.CameraAlt, null) },
                        label = "Camera",
                        onClick = onStartCamera,
                        modifier = Modifier.weight(1f)
                    )
                    InputButton(
                        icon = { Icon(Icons.Default.VideoFile, null) },
                        label = "Video",
                        onClick = onStartVideo,
                        modifier = Modifier.weight(1f)
                    )
                    InputButton(
                        icon = { Icon(Icons.Default.Image, null) },
                        label = "Image",
                        onClick = onStartImage,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (sessions.isNotEmpty()) {
                item { Text("Recent Sessions", style = MaterialTheme.typography.titleMedium) }
                items(sessions) { session ->
                    SessionCard(session = session, onDelete = { vm.deleteSession(session.id) })
                }
            }
        }
    }
}

@Composable
private fun InputButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(80.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SessionCard(session: SessionEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(session.context, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Honesty: ${session.avgHonestyIndex.toInt()}% · Anxiety: ${session.avgAnxietyLevel.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${session.frameCount} frames · ${session.dominantEmotion}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
