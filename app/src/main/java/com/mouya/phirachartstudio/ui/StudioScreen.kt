package com.mouya.phirachartstudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mouya.phirachartstudio.R
import com.mouya.phirachartstudio.StudioUiState
import com.mouya.phirachartstudio.StudioViewModel
import com.mouya.phirachartstudio.engine.ChartNote
import com.mouya.phirachartstudio.engine.DifficultyPreset
import com.mouya.phirachartstudio.engine.GeneratedChart
import com.mouya.phirachartstudio.engine.NoteKind
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import android.os.Build
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.Shader as PlatformShader
import kotlin.math.max
import kotlin.math.min

@Composable
fun StudioScreen(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importAudio)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshPlaybackState()
            delay(150)
        }
    }

    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            TopAppBar(
                title = when (page) { 1 -> "Preview"; 2 -> "Settings"; else -> "Phira Chart Studio" },
                largeTitle = when (page) { 1 -> "Chart preview"; 2 -> "Preferences"; else -> "Create charts" },
                subtitle = when (page) { 1 -> "Review rhythm and timing"; 2 -> "Local processing · Optional AI"; else -> "Offline-first rhythm chart generator" },
                defaultWindowInsetsPadding = false,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    1 -> PreviewPage(state, viewModel, onGoStudio = { page = 0 }) {
                        val base = state.trackName.substringBeforeLast('.', state.trackName).ifBlank { "PhiraChart" }
                        exportPicker.launch("${base.take(48)}-${state.selectedDifficulty.displayName}.pez")
                    }
                    2 -> SettingsPage(state, viewModel)
                    else -> StudioHome(state, viewModel,
                        onImport = { audioPicker.launch(arrayOf("audio/*")) },
                        onOpenPreview = { page = 1 },
                    )
                }
            }
            NavigationBar {
                NavigationBarItem(
                    selected = page == 0,
                    onClick = { page = 0 },
                    icon = ImageVector.vectorResource(R.drawable.ic_studio),
                    label = "Studio",
                )
                NavigationBarItem(
                    selected = page == 1,
                    onClick = { page = 1 },
                    icon = ImageVector.vectorResource(R.drawable.ic_preview),
                    label = "Preview",
                )
                NavigationBarItem(
                    selected = page == 2,
                    onClick = { page = 2 },
                    icon = ImageVector.vectorResource(R.drawable.ic_settings),
                    label = "Settings",
                )
            }
        }

        if (state.error != null) {
            MiuixCard(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                onClick = { viewModel.dismissError() },
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiuixText("Something needs attention", color = MiuixTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    MiuixText(state.error.orEmpty(), color = MiuixTheme.colorScheme.onSurface)
                    MiuixText("Tap to dismiss", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                }
            }
        }
        if (state.aboutVisible) AboutPanel { viewModel.showAbout(false) }
    }
}

@Composable
private fun StudioHome(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onImport: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
                GlassGlow(Modifier.matchParentSize())
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiuixText("PHIRA", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        MiuixText("/", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        MiuixText("CHART STUDIO", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        StatusPill("OFFLINE ENGINE", Color(0xFF1B9A72))
                    }
                    MiuixText("Turn music into\nplayable charts.", fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
                    MiuixText(
                        "Analyze rhythm on your device, shape three difficulty levels, then export a Phira chart package.",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                    MiuixButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        MiuixText(if (state.sourceUri == null) "Import audio" else "Choose another track", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    MiuixText("MP3 · OGG · WAV · FLAC · AAC", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        if (state.sourceUri == null) {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixText("A clean start", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    MiuixText("Choose an audio file with the Android file picker. The original file stays where it is; this app reads it through a temporary document permission.",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                }
            }
        } else {
            TrackCard(state, viewModel)
        }

        if (state.isAnalysing) {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixText("Analyzing audio", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        MiuixText("${(state.progress * 100).toInt()}%", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    ProgressTrack(state.progress)
                    MiuixText(state.status, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                    MiuixButton(onClick = viewModel::cancelAnalysis, modifier = Modifier.fillMaxWidth()) {
                        MiuixText("Cancel analysis", color = Color.White)
                    }
                }
            }
        } else if (state.analysis != null && state.charts.isNotEmpty()) {
            AnalysisCard(state, viewModel)
            MiuixText("Generated difficulties", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                state.charts.sortedBy { it.difficulty.ordinal }.forEach { chart ->
                    DifficultyCard(
                        chart = chart,
                        selected = chart.difficulty == state.selectedDifficulty,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectDifficulty(chart.difficulty) },
                    )
                }
            }
            MiuixButton(onClick = onOpenPreview, modifier = Modifier.fillMaxWidth()) {
                MiuixText("Open chart preview", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.warnings.isNotEmpty()) {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiuixText("Review notes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    state.warnings.distinct().take(5).forEach { warning ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            MiuixText("•", color = MiuixTheme.colorScheme.primary)
                            MiuixText(warning, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
        MiuixText("Audio analysis runs locally. AI is optional and receives derived timing features only.",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
private fun TrackCard(state: StudioUiState, viewModel: StudioViewModel) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiuixText("CURRENT TRACK", fontSize = 10.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold)
                    MiuixText(state.trackName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(if (state.analysis != null) "ANALYZED" else "IMPORTED", if (state.analysis != null) Color(0xFF1B9A72) else Color(0xFF5885D9))
            }
            if (state.analysis != null) Waveform(state, Modifier.fillMaxWidth().height(58.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixText(formatTime(state.playbackMs), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.weight(1f))
                MiuixText(formatTime(state.durationMs), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MiuixButton(onClick = viewModel::togglePlayback, modifier = Modifier.weight(1f)) {
                    MiuixText(if (state.isPlaying) "Pause" else "Listen", color = Color.White)
                }
                MiuixButton(onClick = viewModel::startAnalysis, modifier = Modifier.weight(1f)) {
                    MiuixText(if (state.analysis == null) "Analyze" else "Regenerate", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(state: StudioUiState, viewModel: StudioViewModel) {
    val analysis = state.analysis ?: return
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixText("Music analysis", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                StatusPill("${(analysis.confidence * 100).toInt()}% CONFIDENCE", if (analysis.confidence >= 0.25f) Color(0xFF1B9A72) else Color(0xFFBA8537))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("BPM", String.format(Locale.US, "%.1f", state.displayBpm), Modifier.weight(1f))
                MetricTile("ONSETS", analysis.onsetTimesSeconds.size.toString(), Modifier.weight(1f))
                MetricTile("LENGTH", formatTime((analysis.durationSeconds * 1000).toLong()), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixText("Tempo", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.weight(1f))
                MiuixText("−0.1", modifier = Modifier.clickable { viewModel.adjustBpm(-0.1f) }.padding(8.dp), color = MiuixTheme.colorScheme.primary)
                MiuixText(String.format(Locale.US, "%.1f BPM", state.displayBpm), fontWeight = FontWeight.SemiBold)
                MiuixText("+0.1", modifier = Modifier.clickable { viewModel.adjustBpm(0.1f) }.padding(8.dp), color = MiuixTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixText("Beat offset", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.weight(1f))
                MiuixText("−10 ms", modifier = Modifier.clickable { viewModel.adjustOffset(-0.01f) }.padding(8.dp), color = MiuixTheme.colorScheme.primary)
                MiuixText(String.format(Locale.US, "%+.0f ms", state.displayOffset * 1000f), fontWeight = FontWeight.SemiBold)
                MiuixText("+10 ms", modifier = Modifier.clickable { viewModel.adjustOffset(0.01f) }.padding(8.dp), color = MiuixTheme.colorScheme.primary)
            }
            MiuixText(state.aiStatus, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun DifficultyCard(chart: GeneratedChart, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    MiuixCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiuixText(chart.difficulty.displayName, color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
            MiuixText(String.format(Locale.US, "Lv. %.1f", chart.difficulty.rating), fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MiuixText("${chart.notes.size} notes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (selected) MiuixText("SELECTED", fontSize = 9.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewPage(state: StudioUiState, viewModel: StudioViewModel, onGoStudio: () -> Unit, onExport: () -> Unit) {
    val chart = state.selectedChart
    if (chart == null) {
        EmptyState("No chart yet", "Import an audio track and run the offline analysis to create a chart.", "Go to Studio", onGoStudio)
        return
    }
    var seekValue by remember(chart) { mutableFloatStateOf(state.playbackMs.toFloat()) }
    LaunchedEffect(state.playbackMs) { seekValue = state.playbackMs.toFloat() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MiuixText(chart.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MiuixText("${chart.difficulty.displayName} · ${chart.notes.size} notes · ${chart.bpm.toInt()} BPM",
                            fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    StatusPill("RPE", Color(0xFF517BC7))
                }
                ChartCanvas(chart, state.playbackMs / 1000f, Modifier.fillMaxWidth().height(360.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixText(formatTime(state.playbackMs), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.weight(1f))
                    MiuixText(formatTime(state.durationMs), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                MiuixSlider(
                    value = seekValue.coerceIn(0f, max(1f, state.durationMs.toFloat())),
                    onValueChange = { seekValue = it },
                    onValueChangeFinished = { viewModel.seekTo(seekValue.toLong()) },
                    valueRange = 0f..max(1f, state.durationMs.toFloat()),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MiuixButton(onClick = viewModel::togglePlayback, modifier = Modifier.weight(1f)) {
                        MiuixText(if (state.isPlaying) "Pause" else "Play music", color = Color.White)
                    }
                    MiuixButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        MiuixText("Export .pez", color = Color.White)
                    }
                }
            }
        }
        if (state.isExporting) {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixText("Creating Phira package", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        MiuixText("${(state.exportProgress * 100).toInt()}%", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    ProgressTrack(state.exportProgress)
                    MiuixText(state.exportStatus, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    MiuixButton(onClick = viewModel::cancelExport, modifier = Modifier.fillMaxWidth()) {
                        MiuixText("Cancel export", color = Color.White)
                    }
                }
            }
        } else if (state.exportStatus.isNotBlank()) {
            MiuixText(state.exportStatus, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }

        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixText("Switch difficulty", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.charts.sortedBy { it.difficulty.ordinal }.forEach { item ->
                        MiuixButton(onClick = { viewModel.selectDifficulty(item.difficulty) }, modifier = Modifier.weight(1f)) {
                            MiuixText("${item.difficulty.displayName} · ${item.notes.size}", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                MiuixText("The preview is a timing and lane review surface. Final judgement and device rendering are performed by Phira itself.",
                    fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun SettingsPage(state: StudioUiState, viewModel: StudioViewModel) {
    var baseUrl by remember(state.aiBaseUrl) { mutableStateOf(state.aiBaseUrl) }
    var model by remember(state.aiModel) { mutableStateOf(state.aiModel) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText("Appearance", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                MiuixText("Follow the system or choose a fixed appearance.", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        MiuixButton(onClick = { viewModel.setThemeMode(value) }, modifier = Modifier.weight(1f)) {
                            MiuixText(if (state.themeMode == value) "✓ $label" else label, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MiuixText("AI arrangement assist", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        MiuixText("Optional · Offline chart generation always remains available", fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    MiuixSwitch(checked = state.aiEnabled, onCheckedChange = { enabled ->
                        viewModel.saveAiSettings(enabled, baseUrl, model, apiKey)
                        apiKey = ""
                    })
                }
                InputField("API Base URL", baseUrl, { baseUrl = it }, "https://api.example.com/v1", KeyboardType.Uri)
                InputField("Model ID", model, { model = it }, "gpt-4o-mini")
                InputField("API Key", apiKey, { apiKey = it }, if (state.aiHasKey) "Saved securely · enter to replace" else "Enter your key",
                    KeyboardType.Password, secret = true)
                MiuixText(if (state.aiHasKey) "A key is stored in Android Keystore-backed encrypted storage." else "No API key stored on this device.",
                    fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixButton(onClick = {
                        viewModel.saveAiSettings(state.aiEnabled, baseUrl, model, apiKey)
                        apiKey = ""
                    }, modifier = Modifier.weight(1f)) { MiuixText("Save settings", color = Color.White, fontSize = 12.sp) }
                    MiuixButton(onClick = viewModel::removeAiKey, modifier = Modifier.weight(1f)) {
                        MiuixText("Remove key", color = Color.White, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixButton(onClick = {
                        viewModel.saveAiSettings(state.aiEnabled, baseUrl, model, apiKey) {
                            apiKey = ""
                            viewModel.testAiConnection()
                        }
                    }, enabled = !state.isAiBusy, modifier = Modifier.weight(1f)) {
                        MiuixText("Test connection", color = Color.White, fontSize = 11.sp)
                    }
                    MiuixButton(onClick = {
                        viewModel.saveAiSettings(state.aiEnabled, baseUrl, model, apiKey) {
                            apiKey = ""
                            viewModel.fetchAiModels()
                        }
                    }, enabled = !state.isAiBusy, modifier = Modifier.weight(1f)) {
                        MiuixText("Fetch models", color = Color.White, fontSize = 11.sp)
                    }
                }
                if (state.aiModels.isNotEmpty()) {
                    MiuixText("Available models", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    state.aiModels.take(12).forEach { id ->
                        MiuixText(id, modifier = Modifier.fillMaxWidth().clickable { model = id }.padding(vertical = 5.dp),
                            fontSize = 12.sp, color = if (model == id) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    }
                }
                MiuixText(state.aiStatus, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                MiuixText("AI receives BPM, onset times sampled from the analysis, duration and energy segments. Raw audio is never sent. Unavailable or invalid AI responses automatically fall back to the offline generator.",
                    fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }

        MiuixCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.showAbout(true) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MiuixText("About Phira Chart Studio", fontWeight = FontWeight.SemiBold)
                    MiuixText("Version 1.0.0 · Privacy · Licenses", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                MiuixText("›", fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MiuixText(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        MiuixCard(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
                singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) MiuixText(placeholder, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
@Suppress("NewApi")
private fun GlassGlow(modifier: Modifier = Modifier) {
    val blur = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PlatformRenderEffect.createBlurEffect(42f, 42f, PlatformShader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    Canvas(modifier.graphicsLayer { renderEffect = blur }) {
        drawCircle(Color(0x554D83E8), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.88f, size.height * 0.18f))
        drawCircle(Color(0x3325B9A0), radius = size.minDimension * 0.30f, center = Offset(size.width * 0.08f, size.height * 0.84f))
    }
}

@Composable
private fun Waveform(state: StudioUiState, modifier: Modifier = Modifier) {
    val samples = state.analysis?.energyEnvelope.orEmpty()
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.surfaceContainer)) {
        if (samples.isEmpty()) return@Canvas
        val centerY = size.height / 2f
        val mid = Path()
        val count = min(samples.size, size.width.toInt().coerceAtLeast(1))
        val stride = max(1, samples.size / count)
        for (i in 0 until count) {
            val p = samples[(i * stride).coerceAtMost(samples.lastIndex)]
            val x = i.toFloat() / max(1, count - 1) * size.width
            val amp = (0.08f + p.rms * 0.42f) * size.height
            if (i == 0) mid.moveTo(x, centerY - amp) else mid.lineTo(x, centerY - amp)
        }
        drawPath(mid, color = Color(0xFF6E9BEA), style = Stroke(width = 1.6.dp.toPx()))
        for (i in 0 until count step 2) {
            val p = samples[(i * stride).coerceAtMost(samples.lastIndex)]
            val x = i.toFloat() / max(1, count - 1) * size.width
            val amp = (0.08f + p.rms * 0.42f) * size.height
            drawLine(Color(0x5578A3EB), Offset(x, centerY - amp), Offset(x, centerY + amp), strokeWidth = 1.2.dp.toPx())
        }
        val progressX = (state.playbackMs.toFloat() / max(1L, state.durationMs)).coerceIn(0f, 1f) * size.width
        drawLine(Color(0xFFFFB15A), Offset(progressX, 0f), Offset(progressX, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun ChartCanvas(chart: GeneratedChart, playheadSeconds: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF101827))) {
        val width = size.width
        val height = size.height
        for (lane in 0..7) {
            val x = lane / 7f * width
            drawLine(Color(0x223E5A7A), Offset(x, 0f), Offset(x, height), strokeWidth = 1.dp.toPx())
        }
        for (row in 0..8) {
            val y = row / 8f * height
            drawLine(Color(0x1E8299B9), Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
        }
        val hitLine = height * 0.82f
        drawLine(Color(0xFF6FA1EB), Offset(0f, hitLine), Offset(width, hitLine), strokeWidth = 2.dp.toPx())
        val bpm = chart.bpm.coerceAtLeast(1f)
        val pixelsPerSecond = height * 0.28f
        val firstVisibleBeat = (((playheadSeconds - 1f - chart.offsetSeconds) * bpm / 60f).coerceAtLeast(0f)).toDouble()
        val lastVisibleBeat = (((playheadSeconds + 5f - chart.offsetSeconds) * bpm / 60f)).toDouble()
        // Notes are time-sorted. Binary-search the visible window so playback redraw cost
        // scales with the notes on screen instead of the full chart length.
        var low = 0
        var high = chart.notes.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (chart.notes[middle].beat.toBeats() < firstVisibleBeat) low = middle + 1 else high = middle
        }
        var noteIndex = low
        while (noteIndex < chart.notes.size) {
            val note = chart.notes[noteIndex]
            val beat = note.beat.toBeats()
            if (beat > lastVisibleBeat) break
            val noteTime = chart.offsetSeconds + beat.toFloat() * 60f / bpm
            val delta = noteTime - playheadSeconds
            val x = (0.5f + note.positionX / 1350f).coerceIn(0.03f, 0.97f) * width
            val y = hitLine - delta * pixelsPerSecond
            val color = when (note.kind) {
                NoteKind.TAP -> Color(0xFF77B4FF)
                NoteKind.HOLD -> Color(0xFFFFC56F)
                NoteKind.FLICK -> Color(0xFFC49BFF)
                NoteKind.DRAG -> Color(0xFF78D5B3)
            }
            if (note.kind == NoteKind.HOLD) {
                val length = ((note.endBeat.toBeats() - beat).toFloat() * 60f / bpm * pixelsPerSecond).coerceIn(12f, height)
                drawRoundRect(color.copy(alpha = 0.4f), Offset(x - 7.dp.toPx(), y - length), Size(14.dp.toPx(), length + 9.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
            }
            drawRoundRect(color, Offset(x - 10.dp.toPx(), y - 5.dp.toPx()), Size(20.dp.toPx(), 10.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            noteIndex++
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    MiuixCard(modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MiuixText(label, fontSize = 9.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold)
            MiuixText(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        MiuixText(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressTrack(progress: Float) {
    // Theme colours are read in the composable scope: the Canvas draw lambda is
    // not @Composable and cannot call MiuixTheme.colorScheme itself.
    val trackColor = MiuixTheme.colorScheme.surfaceVariant
    val barColor = MiuixTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp))) {
        drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        drawRoundRect(barColor, size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        MiuixText(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        MiuixText(subtitle, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(16.dp))
        MiuixButton(onClick = onClick) { MiuixText(action, color = Color.White) }
    }
}

@Composable
private fun AboutPanel(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        MiuixCard(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MiuixText("Phira Chart Studio", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                MiuixText("Version 1.0.0 · Android", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                MiuixText("An offline-first chart creation tool. Audio is decoded and analyzed on this device. AI is optional and only receives derived timing and energy features when enabled.", fontSize = 13.sp)
                MiuixText("Generated charts are drafts. Review BPM, offset, note placement, difficulty and rights before sharing or publishing.", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                MiuixText("Chart output: RPE JSON + info.yml in a .pez ZIP package. Preview is an editing aid, not a substitute for testing in Phira.", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                MiuixText("Open the project documentation for the build guide, privacy policy, AI data declaration and third-party license list.", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                MiuixButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { MiuixText("Close", color = Color.White) }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val total = (milliseconds.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}