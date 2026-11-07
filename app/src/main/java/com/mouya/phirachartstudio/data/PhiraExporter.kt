package com.mouya.phirachartstudio.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.provider.OpenableColumns
import com.mouya.phirachartstudio.engine.ChartValidator
import com.mouya.phirachartstudio.engine.GeneratedChart
import com.mouya.phirachartstudio.engine.RpeSerializer
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PhiraExporter(private val context: Context) {
    data class ExportReport(val uri: Uri, val entries: List<String>, val noteCount: Int, val validationWarnings: List<String>)

    suspend fun export(
        chart: GeneratedChart,
        sourceAudio: Uri,
        target: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): ExportReport {
        val resolver = context.contentResolver
        val sourceName = queryDisplayName(resolver, sourceAudio) ?: "song.mp3"
        val extension = sourceName.substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_AUDIO) { "Phira 不支持该音频格式（.$extension）。请先转换为 MP3、OGG、WAV、FLAC 或 AAC。" }
        val audioName = "song.$extension"
        val backgroundName = "background.png"
        val json = RpeSerializer.chartJson(chart, audioName, backgroundName)
        val yaml = RpeSerializer.infoYaml(chart, audioName, backgroundName, previewStart = 0f)
        val validation = ChartValidator.validate(chart, json, yaml, audioName)
        check(validation.isValid) { validation.errors.joinToString("\n") }
        val background = createBackground(chart)

        try {
            val totalBytes = querySize(resolver, sourceAudio)
            val output = resolver.openOutputStream(target, "w") ?: error("无法创建导出文件")
            ZipOutputStream(output.buffered(64 * 1024)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                writeEntry(zip, "info.yml", yaml.toByteArray(Charsets.UTF_8))
                onProgress(0.04f, "正在写入谱面元数据")
                currentCoroutineContext().ensureActive()
                writeEntry(zip, "chart.json", json.toByteArray(Charsets.UTF_8))
                onProgress(0.08f, "正在写入 RPE 谱面")
                currentCoroutineContext().ensureActive()
                writeEntry(zip, backgroundName, background)
                zip.putNextEntry(ZipEntry(audioName).apply { time = 0L })
                val input = resolver.openInputStream(sourceAudio) ?: error("无法重新读取原始音频")
                input.buffered(64 * 1024).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        copied += count
                        if (totalBytes > 0L) {
                            onProgress(0.10f + (copied.toFloat() / totalBytes).coerceIn(0f, 1f) * 0.88f,
                                "正在复制音乐资源 · ${copied / (1024 * 1024)} MB")
                        } else if (copied % (1024L * 1024L) < buffer.size) {
                            onProgress(0.10f, "正在复制音乐资源 · ${copied / (1024 * 1024)} MB")
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                zip.closeEntry()
                zip.finish()
            }
            onProgress(0.99f, "正在重新打开谱包并验证 ZIP 完整性")
            verifyPackage(resolver, target, chart, audioName)
            currentCoroutineContext().ensureActive()
            onProgress(1f, "谱包创建完成并通过内部校验")
        } catch (error: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            throw error
        }
        return ExportReport(target, listOf("info.yml", "chart.json", backgroundName, audioName), chart.notes.size, validation.warnings)
    }

    private suspend fun verifyPackage(
        resolver: ContentResolver,
        target: Uri,
        chart: GeneratedChart,
        audioName: String,
    ) {
        val required = setOf("info.yml", "chart.json", "background.png", audioName)
        val names = LinkedHashSet<String>()
        var infoText: ByteArray? = null
        var chartText: ByteArray? = null
        var audioBytes = 0L
        var backgroundBytes = 0L
        val input = resolver.openInputStream(target) ?: error("无法重新打开导出的谱包")
        ZipInputStream(BufferedInputStream(input, 64 * 1024)).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name !in setOf(".", "..") &&
                    !entry.name.contains('/') && !entry.name.contains('\\')) { "谱包包含非法路径" }
                require(names.add(entry.name)) { "谱包包含重复文件：${entry.name}" }
                when (entry.name) {
                    "info.yml" -> infoText = readEntryLimited(zip, 256 * 1024)
                    "chart.json" -> chartText = readEntryLimited(zip, 32 * 1024 * 1024)
                    "background.png" -> {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = zip.read(buffer)
                            if (count < 0) break
                            backgroundBytes += count
                        }
                    }
                    audioName -> {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = zip.read(buffer)
                            if (count < 0) break
                            audioBytes += count
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(names == required) { "谱包文件结构不完整：${names.joinToString()}" }
        require(audioBytes > 0L) { "谱包中的音乐文件为空" }
        require(backgroundBytes > 0L) { "谱包中的背景图片为空" }
        val validation = ChartValidator.validate(
            chart,
            chartText?.toString(Charsets.UTF_8) ?: error("谱包缺少 chart.json"),
            infoText?.toString(Charsets.UTF_8) ?: error("谱包缺少 info.yml"),
            audioName,
        )
        check(validation.isValid) { "导出后的谱包校验失败：${validation.errors.joinToString("；")}" }
    }

    private suspend fun readEntryLimited(zip: ZipInputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = zip.read(buffer)
            if (count < 0) break
            require(output.size() + count <= maximumBytes) { "谱包元数据超过安全大小限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).coerceAtLeast(0L) else 0L
        } ?: 0L

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.let { raw ->
            raw.substringAfterLast('/').substringAfterLast('\\').filter { it.isLetterOrDigit() || it in "._- " }.take(120)
        }

    private fun createBackground(chart: GeneratedChart): ByteArray {
        val width = 1200
        val height = 675
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF101D35.toInt(), 0xFF203B65.toInt(), 0xFF13243B.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x334E86E8; style = Paint.Style.FILL }
        canvas.drawCircle(1010f, 90f, 290f, glow)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x558EBAFF; strokeWidth = 2f }
        for (i in 0..12) {
            val x = i * 100f
            canvas.drawLine(x, 0f, x - 180f, height.toFloat(), line)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F8FF.toInt(); textSize = 58f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB8C8E2.toInt(); textSize = 28f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8CB4FF.toInt(); textSize = 24f }
        val safeTitle = chart.title.take(30)
        canvas.drawRoundRect(RectF(76f, 88f, 276f, 100f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF75A5FF.toInt() })
        canvas.drawText("PHIRA CHART STUDIO", 76f, 165f, accentPaint)
        canvas.drawText(safeTitle, 76f, 292f, titlePaint)
        canvas.drawText("${chart.difficulty.displayName}  ·  ${chart.difficulty.rating}     ${chart.bpm.toInt()} BPM", 78f, 356f, subPaint)
        canvas.drawText("${chart.notes.size} NOTES     ${"%.1f".format(java.util.Locale.US, chart.durationSeconds)} SEC", 78f, 410f, subPaint)
        canvas.drawText("Generated locally · Review before publishing", 78f, 590f, accentPaint)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    companion object {
        private val SUPPORTED_AUDIO = setOf("mp3", "ogg", "wav", "flac", "aac")
    }
}