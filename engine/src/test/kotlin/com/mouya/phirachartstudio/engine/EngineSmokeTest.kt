package com.mouya.phirachartstudio.engine

import kotlin.math.PI
import kotlin.math.sin
import java.io.File

/** Executable without JUnit: compile with kotlinc and run this main in offline CI environments. */
object EngineSmokeTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val sampleRate = 22_050
        val duration = 28
        val bpm = 120.0
        val signal = FloatArray(sampleRate * duration)
        for (i in signal.indices) {
            val time = i.toDouble() / sampleRate
            val beatPhase = time % (60.0 / bpm)
            val click = if (beatPhase < 0.035) (1.0 - beatPhase / 0.035) * sin(2.0 * PI * 1800.0 * time) else 0.0
            val tone = 0.07 * sin(2.0 * PI * 220.0 * time)
            signal[i] = (click * 0.9 + tone).toFloat()
        }
        val analyzer = StreamingBeatAnalyzer(sampleRate)
        var cursor = 0
        while (cursor < signal.size) {
            val length = minOf(3072, signal.size - cursor)
            analyzer.accept(signal.copyOfRange(cursor, cursor + length))
            cursor += length
        }
        val analysis = analyzer.finish()
        check(analysis.durationSeconds > 27.9f)
        check(analysis.bpm in 112f..128f) { "BPM estimate ${analysis.bpm} was outside expected range" }
        check(analysis.onsetTimesSeconds.size >= 20) { "Too few onsets: ${analysis.onsetTimesSeconds.size}" }

        val generator = ChartGenerator()
        val easy = generator.generate("Synthetic Test", analysis, DifficultyPreset.EASY)
        val hard = generator.generate("Synthetic Test", analysis, DifficultyPreset.HARD)
        val inferno = generator.generate("Synthetic Test", analysis, DifficultyPreset.INFERNO)
        check(easy.notes.isNotEmpty() && hard.notes.size >= easy.notes.size && inferno.notes.size >= hard.notes.size)
        val json = RpeSerializer.chartJson(hard, "song.wav", "background.png")
        val yaml = RpeSerializer.infoYaml(hard, "song.wav", "background.png")
        val report = ChartValidator.validate(hard, json, yaml, "song.wav")
        check(report.isValid) { report.errors.joinToString() }
        check(json.contains("\"RPEVersion\": 160"))
        check(ChartValidator.validate(hard, json.dropLast(3), yaml, "song.mp3").isValid.not())
        check(json.contains("\"eventLayers\": [{}]"))
        val output = File(args.firstOrNull() ?: "build/local-engine/fixture")
        output.mkdirs()
        File(output, "chart.json").writeText(json, Charsets.UTF_8)
        File(output, "info.yml").writeText(yaml, Charsets.UTF_8)
        println("PASS: BPM=${analysis.bpm}, offset=${hard.offsetSeconds}s, onsets=${analysis.onsetTimesSeconds.size}, EZ=${easy.notes.size}, HD=${hard.notes.size}, IN=${inferno.notes.size}, fixture=${output.absolutePath}")
    }
}
