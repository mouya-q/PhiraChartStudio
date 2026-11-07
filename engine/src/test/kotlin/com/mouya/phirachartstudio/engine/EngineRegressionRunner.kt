package com.mouya.phirachartstudio.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Executable regression suite for environments where JUnit / Gradle are unavailable. */
object EngineRegressionRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val tempoResults = listOf(55.0, 60.0, 75.0, 90.0, 100.0, 110.0, 120.0, 128.0, 140.0, 150.0, 160.0, 180.0, 200.0).map { expected ->
            val result = analyse(expected, 18, 22_050)
            check(abs(result.bpm - expected.toFloat()) < 5.5f) {
                "Expected $expected BPM, received ${result.bpm}"
            }
            check(result.onsetTimesSeconds.size >= 14) { "Not enough onsets at $expected BPM" }
            "${expected.toInt()}→${"%.2f".format(java.util.Locale.US, result.bpm)}"
        }

        val phaseCases = listOf(22_050, 44_100).flatMap { rate ->
            listOf(0.0, 0.13, 0.23, 0.37).map { offset -> rate to offset }
        }
        phaseCases.forEach { (rate, expectedOffset) ->
            val result = analyse(120.0, 14, rate, expectedOffset)
            val actual = ((result.beatOffsetSeconds % 0.5f) + 0.5f) % 0.5f
            val difference = abs(actual - expectedOffset.toFloat())
            val circularError = minOf(difference, 0.5f - difference)
            check(circularError < 0.025f) {
                "Expected phase $expectedOffset s at $rate Hz, got $actual s (error $circularError)"
            }
        }

        val analysis = analyse(120.0, 28, 22_050)
        val generator = ChartGenerator()
        val easy = generator.generate("Regression", analysis, DifficultyPreset.EASY)
        val hard = generator.generate("Regression", analysis, DifficultyPreset.HARD)
        val inferno = generator.generate("Regression", analysis, DifficultyPreset.INFERNO)
        check(easy.notes.isNotEmpty() && easy.notes.size < hard.notes.size && hard.notes.size < inferno.notes.size)
        val rpe = RpeSerializer.chartJson(hard, "song.mp3", "background.png")
        val info = RpeSerializer.infoYaml(hard, "song.mp3", "background.png")
        check(ChartValidator.validate(hard, rpe, info, "song.mp3").isValid)
        check(!ChartValidator.validate(hard, "{\"broken\":}", info, "song.mp3").isValid)
        val negative = generator.generate("Negative Offset", analysis.copy(beatOffsetSeconds = -0.035f), DifficultyPreset.HARD)
        check(negative.notes.all { it.beat.whole >= 0 })
        check(ChartValidator.validate(negative, RpeSerializer.chartJson(negative, "song.mp3", "background.png"),
            RpeSerializer.infoYaml(negative, "song.mp3", "background.png"), "song.mp3").isValid)
        val safe = ArrangementHints(Float.NaN, Float.POSITIVE_INFINITY, -1f, 9f).validated()
        check(safe == ArrangementHints(densityMultiplier = 1f, holdBias = 0.08f, flickBias = 0f, syncopation = 0.30f))

        println("PASS tempo sweep: ${tempoResults.joinToString(" | ")} BPM")
        println("PASS phase sweep: ${phaseCases.size} cases across 22.05 / 44.1 kHz; max error < 25 ms")
        println("PASS chart regression: EZ=${easy.notes.size}, HD=${hard.notes.size}, IN=${inferno.notes.size}; RPE, info.yml and negative offset valid")
    }

    private fun analyse(bpm: Double, seconds: Int, rate: Int, offset: Double = 0.0): AnalysisResult {
        val period = 60.0 / bpm
        val signal = FloatArray(rate * seconds) { sample ->
            val time = sample.toDouble() / rate
            val phase = ((time - offset + period) % period + period) % period
            val click = if (phase < 0.035) (1.0 - phase / 0.035) * sin(2.0 * PI * 1_800.0 * time) else 0.0
            val pad = 0.07 * sin(2.0 * PI * 220.0 * time)
            (click * 0.9 + pad).toFloat()
        }
        val analyser = StreamingBeatAnalyzer(rate)
        var cursor = 0
        while (cursor < signal.size) {
            val length = minOf(4096, signal.size - cursor)
            analyser.accept(signal.copyOfRange(cursor, cursor + length))
            cursor += length
        }
        return analyser.finish()
    }
}
