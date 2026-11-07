package com.mouya.phirachartstudio.data

import android.content.Context
import com.mouya.phirachartstudio.engine.AnalysisResult
import com.mouya.phirachartstudio.engine.BeatPosition
import com.mouya.phirachartstudio.engine.ChartNote
import com.mouya.phirachartstudio.engine.DifficultyPreset
import com.mouya.phirachartstudio.engine.EnergyPoint
import com.mouya.phirachartstudio.engine.EnergySegment
import com.mouya.phirachartstudio.engine.GeneratedChart
import com.mouya.phirachartstudio.engine.NoteKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.system.Os

/** Keeps a resumable working state and derived features, never the user's raw audio bytes. */
class ProjectStore(context: Context) {
    data class Snapshot(
        val sourceUri: String,
        val title: String,
        val analysis: AnalysisResult,
        val charts: List<GeneratedChart>,
        val selectedDifficulty: DifficultyPreset,
        val bpmAdjustment: Float = 0f,
        val offsetAdjustment: Float = 0f,
    )

    private val file = File(context.filesDir, "last_project.json")

    fun save(snapshot: Snapshot) {
        val json = JSONObject().apply {
            put("schema", 2)
            put("sourceUri", snapshot.sourceUri)
            put("title", snapshot.title)
            put("selectedDifficulty", snapshot.selectedDifficulty.name)
            put("bpmAdjustment", snapshot.bpmAdjustment)
            put("offsetAdjustment", snapshot.offsetAdjustment)
            put("analysis", encodeAnalysis(snapshot.analysis))
            put("charts", JSONArray().apply { snapshot.charts.forEach { put(encodeChart(it)) } })
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Os.rename(temporary.absolutePath, file.absolutePath)
    }

    fun restore(): Snapshot? {
        if (!file.isFile || file.length() !in 1..40_000_000) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            require(root.optInt("schema") in 1..2)
            val analysis = decodeAnalysis(root.getJSONObject("analysis"))
            val chartsJson = root.getJSONArray("charts")
            val charts = buildList {
                for (i in 0 until chartsJson.length()) add(decodeChart(chartsJson.getJSONObject(i)))
            }
            require(charts.isNotEmpty())
            Snapshot(
                sourceUri = root.optString("sourceUri"),
                title = root.optString("title", "Untitled"),
                analysis = analysis,
                charts = charts,
                selectedDifficulty = runCatching { DifficultyPreset.valueOf(root.optString("selectedDifficulty")) }
                    .getOrDefault(DifficultyPreset.EASY),
                bpmAdjustment = root.optDouble("bpmAdjustment", 0.0).toFloat(),
                offsetAdjustment = root.optDouble("offsetAdjustment", 0.0).toFloat(),
            )
        }.getOrNull()
    }

    fun clear() { file.delete() }

    private fun encodeAnalysis(a: AnalysisResult) = JSONObject().apply {
        put("sampleRate", a.sampleRate)
        put("duration", a.durationSeconds)
        put("bpm", a.bpm)
        put("offset", a.beatOffsetSeconds)
        put("confidence", a.confidence)
        put("onsets", JSONArray(a.onsetTimesSeconds))
        put("energy", JSONArray().apply {
            a.energyEnvelope.forEach { p -> put(JSONArray().put(p.timeSeconds).put(p.rms).put(p.low).put(p.mid).put(p.high).put(p.onsetStrength)) }
        })
        put("segments", JSONArray().apply {
            a.segments.forEach { s -> put(JSONArray().put(s.startSeconds).put(s.endSeconds).put(s.averageEnergy).put(s.label)) }
        })
        put("warnings", JSONArray(a.warnings))
    }

    private fun decodeAnalysis(j: JSONObject): AnalysisResult {
        val energy = j.getJSONArray("energy")
        val points = buildList {
            for (i in 0 until energy.length()) {
                val a = energy.getJSONArray(i)
                add(EnergyPoint(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(),
                    a.getDouble(3).toFloat(), a.getDouble(4).toFloat(), a.getDouble(5).toFloat()))
            }
        }
        val segmentsJson = j.getJSONArray("segments")
        val segments = buildList {
            for (i in 0 until segmentsJson.length()) {
                val a = segmentsJson.getJSONArray(i)
                add(EnergySegment(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getString(3)))
            }
        }
        return AnalysisResult(
            sampleRate = j.getInt("sampleRate"), durationSeconds = j.getDouble("duration").toFloat(),
            bpm = j.getDouble("bpm").toFloat(), beatOffsetSeconds = j.getDouble("offset").toFloat(),
            confidence = j.getDouble("confidence").toFloat(),
            onsetTimesSeconds = j.getJSONArray("onsets").toFloatList(), energyEnvelope = points,
            segments = segments, warnings = j.optJSONArray("warnings").toStringList(),
        )
    }

    private fun encodeChart(c: GeneratedChart) = JSONObject().apply {
        put("title", c.title); put("difficulty", c.difficulty.name); put("bpm", c.bpm)
        put("offsetSeconds", c.offsetSeconds)
        put("duration", c.durationSeconds); put("warnings", JSONArray(c.warnings))
        put("notes", JSONArray().apply {
            c.notes.forEach { n ->
                put(JSONObject().apply {
                    put("kind", n.kind.name); put("beat", n.beat.toJson()); put("endBeat", n.endBeat.toJson())
                    put("x", n.positionX); put("above", n.above); put("visible", n.visibleTimeSeconds); put("size", n.size)
                })
            }
        })
    }

    private fun decodeChart(j: JSONObject): GeneratedChart {
        val notesJson = j.getJSONArray("notes")
        val notes = buildList {
            for (i in 0 until notesJson.length()) {
                val n = notesJson.getJSONObject(i)
                add(ChartNote(
                    kind = NoteKind.valueOf(n.getString("kind")), beat = decodeBeatPosition(n.getJSONArray("beat")),
                    endBeat = decodeBeatPosition(n.getJSONArray("endBeat")), positionX = n.getDouble("x").toFloat(),
                    above = n.optBoolean("above", true), visibleTimeSeconds = n.optDouble("visible", 1.5).toFloat(),
                    size = n.optDouble("size", 1.0).toFloat(),
                ))
            }
        }
        return GeneratedChart(
            title = j.getString("title"), difficulty = DifficultyPreset.valueOf(j.getString("difficulty")),
            bpm = j.getDouble("bpm").toFloat(), offsetSeconds = j.optDouble("offsetSeconds", 0.0).toFloat(),
            notes = notes, durationSeconds = j.getDouble("duration").toFloat(),
            warnings = j.optJSONArray("warnings").toStringList(),
        )
    }

    private fun BeatPosition.toJson() = JSONArray().put(whole).put(numerator).put(denominator)

    private fun decodeBeatPosition(value: JSONArray): BeatPosition =
        BeatPosition(value.getInt(0), value.getInt(1), value.getInt(2))

    private fun JSONArray?.toFloatList(): List<Float> = this?.let { array ->
        buildList { for (i in 0 until array.length()) add(array.getDouble(i).toFloat()) }
    } ?: emptyList()

    private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
        buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    } ?: emptyList()
}
