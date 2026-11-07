package com.mouya.phirachartstudio

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.mouya.phirachartstudio.ai.AiApiClient
import com.mouya.phirachartstudio.audio.MediaCodecAudioAnalyzer
import com.mouya.phirachartstudio.data.AiConfigStore
import com.mouya.phirachartstudio.data.PhiraExporter
import com.mouya.phirachartstudio.data.ProjectStore
import com.mouya.phirachartstudio.engine.AnalysisResult
import com.mouya.phirachartstudio.engine.ArrangementHints
import com.mouya.phirachartstudio.engine.ChartGenerator
import com.mouya.phirachartstudio.engine.DifficultyPreset
import com.mouya.phirachartstudio.engine.GeneratedChart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** UI state is immutable so progress and errors remain predictable across configuration changes. */
data class StudioUiState(
    val sourceUri: Uri? = null,
    val trackName: String = "",
    val durationMs: Long = 0L,
    val playbackMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isAnalysing: Boolean = false,
    val progress: Float = 0f,
    val status: String = "导入一首音乐，开始制作谱面",
    val analysis: AnalysisResult? = null,
    val charts: List<GeneratedChart> = emptyList(),
    val selectedDifficulty: DifficultyPreset = DifficultyPreset.EASY,
    val bpmAdjustment: Float = 0f,
    val offsetAdjustment: Float = 0f,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-mini",
    val aiHasKey: Boolean = false,
    val aiStatus: String = "AI 未启用，离线生成可独立工作",
    val aiModels: List<String> = emptyList(),
    val isAiBusy: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportStatus: String = "",
    val themeMode: String = "system",
    val aboutVisible: Boolean = false,
) {
    val selectedChart: GeneratedChart? get() = charts.firstOrNull { it.difficulty == selectedDifficulty }
    val displayBpm: Float get() = (analysis?.bpm ?: 0f) + bpmAdjustment
    val displayOffset: Float get() = (analysis?.beatOffsetSeconds ?: 0f) + offsetAdjustment
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = false
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(error = "音频播放失败：${error.errorCodeName}")
            }
        })
    }

    private val audioAnalyzer = MediaCodecAudioAnalyzer(application)
    private val chartGenerator = ChartGenerator()
    private val aiClient = AiApiClient()
    private val aiStore = AiConfigStore(application)
    private val projectStore = ProjectStore(application)
    private val exporter = PhiraExporter(application)
    private var analysisJob: Job? = null
    private var currentHints = ArrangementHints()
    private var exportJob: Job? = null
    private var regenerationJob: Job? = null

    init {
        _state.value = _state.value.copy(
            themeMode = getApplication<Application>().getSharedPreferences("app_settings", 0)
                .getString("theme", "system") ?: "system",
        )
        // Restoring a project may parse a large feature envelope; never do that on the UI thread.
        viewModelScope.launch {
            try {
                val (config, restored) = withContext(Dispatchers.IO) { aiStore.load() to projectStore.restore() }
                _state.value = _state.value.copy(
                    aiEnabled = config.enabled,
                    aiBaseUrl = config.baseUrl,
                    aiModel = config.model,
                    aiHasKey = config.apiKey.isNotBlank(),
                )
                if (restored != null) {
                    val uri = runCatching { Uri.parse(restored.sourceUri) }.getOrNull()
                    _state.value = _state.value.copy(
                        sourceUri = uri,
                        trackName = restored.title,
                        durationMs = (restored.analysis.durationSeconds * 1000).toLong(),
                        analysis = restored.analysis,
                        charts = restored.charts,
                        selectedDifficulty = restored.selectedDifficulty,
                        bpmAdjustment = restored.bpmAdjustment,
                        offsetAdjustment = restored.offsetAdjustment,
                        status = "已恢复上次项目 · ${restored.title}",
                        warnings = (restored.analysis.warnings + restored.charts.flatMap { it.warnings }).distinct(),
                    )
                    if (uri != null) runCatching {
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(aiStatus = "配置读取失败 · ${friendlyError(error)}")
            }
        }
    }

    fun importAudio(uri: Uri) {
        analysisJob?.cancel()
        regenerationJob?.cancel()
        exportJob?.cancel()
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        _state.value = _state.value.copy(
            sourceUri = uri, trackName = "Reading audio…", durationMs = 0L, playbackMs = 0L,
            isPlaying = false, isAnalysing = false, progress = 0f,
            status = "已导入 · 正在读取音频信息", analysis = null, charts = emptyList(),
            error = null, warnings = emptyList(), bpmAdjustment = 0f, offsetAdjustment = 0f,
            isExporting = false, exportProgress = 0f, exportStatus = "",
        )
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { queryName(uri) ?: "Imported audio" }
                if (_state.value.sourceUri != uri) return@launch
                _state.value = _state.value.copy(trackName = name)
                startAnalysis()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(error = friendlyError(error), status = "无法读取音频信息")
            }
        }
    }

    fun startAnalysis() {
        val uri = _state.value.sourceUri ?: run {
            _state.value = _state.value.copy(error = "请先导入音乐文件")
            return
        }
        if (analysisJob?.isActive == true) return
        analysisJob = viewModelScope.launch {
            _state.value = _state.value.copy(isAnalysing = true, progress = 0f, error = null, status = "正在启动离线分析")
            try {
                val analysis = audioAnalyzer.analyze(uri) { progress ->
                    _state.value = _state.value.copy(progress = progress.fraction, status = progress.stage)
                }
                val config = aiStore.load()
                var hints = ArrangementHints()
                var aiMessage = "已使用本地分析特征生成"
                if (config.enabled && config.apiKey.isNotBlank()) {
                    _state.value = _state.value.copy(status = "离线分析完成 · 正在请求 AI 编排建议")
                    try {
                        hints = withContext(Dispatchers.IO) {
                            aiClient.suggestArrangement(AiApiClient.Settings(config.baseUrl, config.model, config.apiKey), analysis)
                        }
                        aiMessage = "AI 建议已通过约束校验 · 音频未上传"
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        aiMessage = "AI 不可用，已自动回退离线生成：${error.message?.take(100) ?: "连接失败"}"
                    }
                } else if (config.enabled) {
                    aiMessage = "AI 已启用但尚未配置 Key，已回退离线生成"
                }
                currentHints = hints
                val title = nameWithoutExtension(_state.value.trackName)
                val charts = withContext(Dispatchers.Default) {
                    DifficultyPreset.entries.map { difficulty -> chartGenerator.generate(title, analysis, difficulty, hints) }
                }
                _state.value = _state.value.copy(
                    isAnalysing = false,
                    progress = 1f,
                    status = "谱面已生成 · ${charts.sumOf { it.notes.size }} 个音符版本",
                    analysis = analysis,
                    charts = charts,
                    selectedDifficulty = DifficultyPreset.EASY,
                    bpmAdjustment = 0f,
                    offsetAdjustment = 0f,
                    warnings = (analysis.warnings + charts.flatMap { it.warnings }).distinct(),
                    aiStatus = aiMessage,
                    error = null,
                    durationMs = (analysis.durationSeconds * 1000).toLong(),
                )
                persistProject()
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isAnalysing = false, status = "分析已取消 · 可重新开始", progress = 0f)
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(isAnalysing = false, error = friendlyError(error), status = "分析未完成", progress = 0f)
            }
        }
    }

    fun cancelAnalysis() { analysisJob?.cancel() }

    fun selectDifficulty(difficulty: DifficultyPreset) {
        _state.value = _state.value.copy(selectedDifficulty = difficulty)
        persistProject()
    }

    fun adjustBpm(delta: Float) {
        val analysis = _state.value.analysis ?: return
        val next = (_state.value.bpmAdjustment + delta).coerceIn(-20f, 20f)
        regenerate(analysis.copy(bpm = (analysis.bpm + next).coerceIn(55f, 200f)), next, _state.value.offsetAdjustment)
    }

    fun adjustOffset(deltaSeconds: Float) {
        val analysis = _state.value.analysis ?: return
        val next = (_state.value.offsetAdjustment + deltaSeconds).coerceIn(-0.5f, 0.5f)
        regenerate(analysis.copy(beatOffsetSeconds = (analysis.beatOffsetSeconds + next).coerceIn(-2f, 2f)), _state.value.bpmAdjustment, next)
    }

    private fun regenerate(analysisForGeneration: AnalysisResult, bpmAdjust: Float, offsetAdjust: Float) {
        // Reflect the adjustment immediately; only the newest completed generation is committed.
        _state.value = _state.value.copy(bpmAdjustment = bpmAdjust, offsetAdjustment = offsetAdjust)
        regenerationJob?.cancel()
        regenerationJob = viewModelScope.launch(Dispatchers.Default) {
            val snapshot = _state.value
            val base = snapshot.analysis ?: return@launch
            val title = nameWithoutExtension(snapshot.trackName)
            val charts = DifficultyPreset.entries.map { chartGenerator.generate(title, analysisForGeneration, it, currentHints) }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            _state.value = _state.value.copy(charts = charts,
                warnings = (base.warnings + charts.flatMap { it.warnings }).distinct(),
                status = "参数已更新 · ${(base.bpm + bpmAdjust).toInt()} BPM")
            persistProject()
        }
    }

    fun togglePlayback() {
        if (_state.value.sourceUri == null) return
        if (player.isPlaying) player.pause() else player.play()
        _state.value = _state.value.copy(isPlaying = player.isPlaying)
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(playbackMs = player.currentPosition)
    }

    fun refreshPlaybackState() {
        val current = _state.value
        val duration = player.duration.takeIf { it > 0L } ?: current.durationMs
        val position = if (player.isPlaying) player.currentPosition.coerceAtLeast(0L) else current.playbackMs
        val playing = player.isPlaying
        if (position != current.playbackMs || duration != current.durationMs || playing != current.isPlaying) {
            _state.value = current.copy(playbackMs = position, durationMs = duration.coerceAtLeast(0L), isPlaying = playing)
        }
    }

    fun saveAiSettings(enabled: Boolean, baseUrl: String, model: String, enteredKey: String, onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isAiBusy = true, aiStatus = "正在安全保存设置", error = null)
            try {
                val config = withContext(Dispatchers.IO) {
                    aiStore.save(enabled, baseUrl, model, enteredKey.ifBlank { null })
                    aiStore.load()
                }
                _state.value = _state.value.copy(
                    aiEnabled = enabled,
                    aiBaseUrl = config.baseUrl,
                    aiModel = config.model,
                    aiHasKey = config.apiKey.isNotBlank(),
                    aiStatus = "AI 设置已安全保存",
                    isAiBusy = false,
                )
                onSaved?.invoke()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(isAiBusy = false, error = friendlyError(error), aiStatus = "设置保存失败")
            }
        }
    }

    fun removeAiKey() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { aiStore.clearKey() }
                _state.value = _state.value.copy(aiHasKey = false, aiStatus = "已清除本机保存的 API Key")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(error = friendlyError(error))
            }
        }
    }

    fun testAiConnection() {
        runAiTask("正在测试 AI 服务") {
            val config = aiStore.load()
            require(config.apiKey.isNotBlank()) { "请先输入并保存 API Key" }
            val message = aiClient.testConnection(AiApiClient.Settings(config.baseUrl, config.model, config.apiKey))
            _state.value = _state.value.copy(aiStatus = message)
        }
    }

    fun fetchAiModels() {
        runAiTask("正在获取模型列表") {
            val config = aiStore.load()
            require(config.apiKey.isNotBlank()) { "请先输入并保存 API Key" }
            val models = aiClient.fetchModels(AiApiClient.Settings(config.baseUrl, config.model, config.apiKey)).models
            _state.value = _state.value.copy(aiModels = models, aiStatus = "读取到 ${models.size} 个模型")
        }
    }

    private fun runAiTask(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isAiBusy = true, aiStatus = label, error = null)
            try { withContext(Dispatchers.IO) { block() } }
            catch (error: Throwable) { if (error is CancellationException) throw error
                _state.value = _state.value.copy(aiStatus = "请求失败 · ${friendlyError(error)}") }
            finally { _state.value = _state.value.copy(isAiBusy = false) }
        }
    }

    fun export(target: Uri) {
        if (exportJob?.isActive == true) return
        val chart = _state.value.selectedChart ?: run {
            _state.value = _state.value.copy(error = "请先生成谱面")
            return
        }
        val source = _state.value.sourceUri ?: return
        exportJob = viewModelScope.launch {
            _state.value = _state.value.copy(status = "正在检查谱面并打包", isExporting = true,
                exportProgress = 0f, exportStatus = "正在进行兼容性检查", error = null)
            try {
                val report = withContext(Dispatchers.IO) {
                    exporter.export(chart, source, target) { progress, message ->
                        _state.value = _state.value.copy(exportProgress = progress.coerceIn(0f, 1f), exportStatus = message)
                    }
                }
                _state.value = _state.value.copy(status = "导出完成 · ${report.noteCount} 个音符 · ${report.entries.size} 个文件",
                    isExporting = false, exportProgress = 1f, exportStatus = "已创建 .pez · ${report.entries.joinToString()}",
                    warnings = (_state.value.warnings + report.validationWarnings).distinct(), error = null)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(status = "导出已取消", isExporting = false, exportStatus = "导出已取消；未保留不完整谱包")
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(status = "导出失败", isExporting = false, exportStatus = "", error = friendlyError(error))
            }
        }
    }

    fun cancelExport() { exportJob?.cancel() }

    fun setThemeMode(mode: String) {
        if (mode !in setOf("system", "light", "dark")) return
        getApplication<Application>().getSharedPreferences("app_settings", 0).edit().putString("theme", mode).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun showAbout(show: Boolean) { _state.value = _state.value.copy(aboutVisible = show) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }

    private fun persistProject() {
        val current = _state.value
        val analysis = current.analysis ?: return
        if (current.charts.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                projectStore.save(ProjectStore.Snapshot(current.sourceUri?.toString().orEmpty(), current.trackName,
                    analysis, current.charts, current.selectedDifficulty, current.bpmAdjustment, current.offsetAdjustment))
            }
        }
    }

    private fun queryName(uri: Uri): String? = getApplication<Application>().contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun nameWithoutExtension(value: String): String = value.substringBeforeLast('.', value).ifBlank { "Untitled" }.take(80)

    private fun friendlyError(error: Throwable): String = when {
        error.message.isNullOrBlank() -> "发生未知错误，请重新尝试"
        error.message!!.contains("permission", true) || error.message!!.contains("denied", true) -> "文件访问权限已失效，请重新导入音频"
        else -> error.message!!.take(240)
    }

    override fun onCleared() {
        analysisJob?.cancel()
        exportJob?.cancel()
        regenerationJob?.cancel()
        player.release()
        super.onCleared()
    }
}
