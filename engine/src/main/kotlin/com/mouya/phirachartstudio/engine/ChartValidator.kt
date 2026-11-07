package com.mouya.phirachartstudio.engine

import kotlin.math.roundToInt

/** Validates the supported RPE subset and the matching Phira info.yml manifest. */
object ChartValidator {
    private val supportedAudio = setOf("mp3", "ogg", "wav", "flac", "aac")
    data class Report(val errors: List<String>, val warnings: List<String>) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(chart: GeneratedChart, chartJson: String, infoYaml: String, musicFile: String): Report {
        val errors = mutableListOf<String>()
        val warnings = chart.warnings.toMutableList()

        if (chart.title.isBlank()) errors += "谱面名称为空"
        if (chart.bpm !in 55f..200f || !chart.bpm.isFinite()) errors += "BPM 超出允许范围"
        if (chart.durationSeconds <= 0f || !chart.durationSeconds.isFinite()) errors += "音频时长无效"
        if (chart.notes.isEmpty()) errors += "谱面没有音符"
        if (chart.notes.size > 100_000) errors += "音符数量超过安全上限"
        if (musicFile.substringAfterLast('.', "").lowercase() !in supportedAudio) {
            errors += "音频文件格式不在 Phira 支持范围内"
        }

        var previous = -1.0
        chart.notes.forEachIndexed { index, note ->
            val beat = note.beat.toBeats()
            val end = note.endBeat.toBeats()
            if (!beat.isFinite() || beat < 0.0) errors += "第 ${index + 1} 个音符起始时间无效"
            if (beat < previous) errors += "谱面音符没有按时间排序"
            if (end < beat) errors += "第 ${index + 1} 个音符结束时间早于开始时间"
            if (note.kind == NoteKind.HOLD && end <= beat) errors += "第 ${index + 1} 个 Hold 没有有效长度"
            if (note.positionX !in -675f..675f || !note.positionX.isFinite()) errors += "第 ${index + 1} 个音符横坐标无效"
            if (!note.size.isFinite() || note.size !in 0.1f..4f) errors += "第 ${index + 1} 个音符尺寸无效"
            if (!note.visibleTimeSeconds.isFinite() || note.visibleTimeSeconds !in 0f..20f) errors += "第 ${index + 1} 个音符显示时间无效"
            previous = beat
        }

        val root = try {
            StrictJson.parse(chartJson) as? StrictJson.Value.ObjectValue
                ?: error("RPE 根节点必须是对象")
        } catch (error: Throwable) {
            errors += "RPE JSON 解析失败：${error.message?.take(120) ?: "结构无效"}"
            null
        }

        if (root != null) {
            validateRpeRoot(root, chart, errors)
        }

        if (!infoYaml.contains("format: rpe") ||
            !infoYaml.contains("chart: \"chart.json\"") ||
            !infoYaml.contains("music: \"$musicFile\"") ||
            !infoYaml.contains("illustration: \"background.png\"")) {
            errors += "info.yml 与谱面资源路径不一致"
        }
        if (!infoYaml.contains("name: \"${escapeYaml(chart.title)}\"")) {
            errors += "info.yml 谱面名称与生成结果不一致"
        }
        if (!infoYaml.contains("difficulty: ${number(chart.difficulty.rating)}")) {
            errors += "info.yml 难度数值与生成结果不一致"
        }

        if (chart.estimatedDensity > 7f) warnings += "音符密度超过 7 notes/s，实际可玩性需要人工复核"
        if (chart.notes.size < 12) warnings += "谱面偏短或音符较少，建议人工补充"
        return Report(errors.distinct(), warnings.distinct())
    }

    private fun validateRpeRoot(
        root: StrictJson.Value.ObjectValue,
        chart: GeneratedChart,
        errors: MutableList<String>,
    ) {
        val meta = root.objectAt("META")
        if (meta == null) {
            errors += "RPE 缺少 META 对象"
        } else {
            if (meta.stringAt("name") != chart.title) errors += "RPE META.name 与谱面标题不一致"
            if (meta.intAt("RPEVersion") !in 100..999) errors += "RPEVersion 缺失或无效"
            if (meta.intAt("offset") != (chart.offsetSeconds * 1000f).roundToInt()) errors += "RPE META.offset 与谱面校准值不一致"
            if (meta.stringAt("song").isNullOrBlank()) errors += "RPE META.song 未指定音频"
            if (meta.stringAt("background").isNullOrBlank()) errors += "RPE META.background 未指定背景"
        }

        val bpmList = root.arrayAt("BPMList")
        if (bpmList.isNullOrEmpty()) {
            errors += "RPE BPMList 为空"
        } else {
            bpmList.forEachIndexed { index, itemValue ->
                val item = itemValue as? StrictJson.Value.ObjectValue
                if (item == null) {
                    errors += "BPMList[$index] 必须是对象"
                } else {
                    val bpm = item.numberAt("bpm")
                    if (bpm == null || bpm !in 1.0..1000.0) errors += "BPMList[$index].bpm 无效"
                    if (!validTriple(item.arrayAt("startTime"))) errors += "BPMList[$index].startTime 无效"
                }
            }
            val firstBpm = (bpmList.firstOrNull() as? StrictJson.Value.ObjectValue)?.numberAt("bpm")
            if (firstBpm != null && kotlin.math.abs(firstBpm - chart.bpm) > 0.02) errors += "RPE BPMList 与谱面 BPM 不一致"
        }

        val lines = root.arrayAt("judgeLineList")
        if (lines.isNullOrEmpty()) {
            errors += "RPE judgeLineList 为空"
            return
        }
        var parsedNoteCount = 0
        lines.forEachIndexed lineLoop@ { lineIndex, lineValue ->
            val line = lineValue as? StrictJson.Value.ObjectValue
            if (line == null) {
                errors += "judgeLineList[$lineIndex] 必须是对象"
                return@lineLoop
            }
            if (line.stringAt("Name").isNullOrBlank()) errors += "judgeLineList[$lineIndex].Name 缺失"
            if (line.stringAt("Texture").isNullOrBlank()) errors += "judgeLineList[$lineIndex].Texture 缺失"
            if (line.arrayAt("eventLayers") == null) errors += "judgeLineList[$lineIndex].eventLayers 缺失"
            val notes = line.arrayAt("notes")
            if (notes == null) {
                errors += "judgeLineList[$lineIndex].notes 缺失"
                return@lineLoop
            }
            notes.forEachIndexed noteLoop@ { noteIndex, noteValue ->
                val note = noteValue as? StrictJson.Value.ObjectValue
                if (note == null) {
                    errors += "第 $lineIndex 条判定线的第 $noteIndex 个音符必须是对象"
                    return@noteLoop
                }
                parsedNoteCount++
                val kind = note.intAt("type")
                if (kind !in 1..4) errors += "RPE 音符类型无效"
                if (note.intAt("above") !in 0..1) errors += "RPE 音符 above 字段无效"
                if (!validTriple(note.arrayAt("startTime"))) errors += "RPE 音符 startTime 无效"
                if (!validTriple(note.arrayAt("endTime"))) errors += "RPE 音符 endTime 无效"
                val x = note.numberAt("positionX")
                if (x == null || x !in -675.0..675.0) errors += "RPE 音符 positionX 无效"
                val speed = note.numberAt("speed")
                if (speed == null || speed <= 0.0 || speed > 20.0) errors += "RPE 音符 speed 无效"
                val size = note.numberAt("size")
                if (size == null || size !in 0.1..4.0) errors += "RPE 音符 size 无效"
                val alpha = note.intAt("alpha")
                if (alpha !in 0..65_535) errors += "RPE 音符 alpha 无效"
                val visibleTime = note.numberAt("visibleTime")
                if (visibleTime == null || visibleTime !in 0.0..120.0) errors += "RPE 音符 visibleTime 无效"
            }
        }
        if (parsedNoteCount != chart.notes.size) errors += "RPE 音符数量与生成数据不一致"
    }

    private fun validTriple(value: List<StrictJson.Value>?): Boolean {
        if (value == null || value.size != 3) return false
        val whole = (value[0] as? StrictJson.Value.NumberValue)?.intOrNull() ?: return false
        val numerator = (value[1] as? StrictJson.Value.NumberValue)?.intOrNull() ?: return false
        val denominator = (value[2] as? StrictJson.Value.NumberValue)?.intOrNull() ?: return false
        return whole >= 0 && denominator > 0 && numerator in 0 until denominator
    }

    private fun StrictJson.Value.ObjectValue.objectAt(key: String) = values[key] as? StrictJson.Value.ObjectValue
    private fun StrictJson.Value.ObjectValue.arrayAt(key: String) = (values[key] as? StrictJson.Value.ArrayValue)?.values
    private fun StrictJson.Value.ObjectValue.stringAt(key: String) = (values[key] as? StrictJson.Value.StringValue)?.value
    private fun StrictJson.Value.ObjectValue.numberAt(key: String) = (values[key] as? StrictJson.Value.NumberValue)?.doubleOrNull()
    private fun StrictJson.Value.ObjectValue.intAt(key: String) = (values[key] as? StrictJson.Value.NumberValue)?.intOrNull() ?: -1

    private fun escapeYaml(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
    private fun number(value: Float): String = java.lang.String.format(java.util.Locale.US, "%.5f", value)

}
