package com.mouya.phirachartstudio.engine

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class EngineRegressionTest {
    @Test
    fun estimatesDistinctTemposFromChunkedSamples() {
        for (expected in listOf(55.0, 60.0, 75.0, 90.0, 100.0, 110.0, 120.0, 128.0, 140.0, 150.0, 160.0, 180.0, 200.0)) {
            val result = analyzeClickTrack(expected, 22)
            assertTrue(kotlin.math.abs(result.bpm - expected.toFloat()) < 5.5f,
                "Expected $expected BPM, got ${result.bpm}")
            assertTrue(result.onsetTimesSeconds.size >= 15)
        }
    }

    @Test
    fun beatPhaseTracksDelayedDownbeatsAtCommonSampleRates() {
        for (rate in listOf(22_050, 44_100)) {
            for (expectedOffset in listOf(0.0, 0.13, 0.23, 0.37)) {
                val durationSeconds = 14
                val period = 0.5
                val signal = FloatArray(rate * durationSeconds) { sample ->
                    val time = sample.toDouble() / rate
                    val phase = ((time - expectedOffset + period) % period + period) % period
                    val click = if (phase < 0.035) (1.0 - phase / 0.035) * sin(2.0 * PI * 1_800.0 * time) else 0.0
                    (click * 0.9 + 0.07 * sin(2.0 * PI * 220.0 * time)).toFloat()
                }
                val analyser = StreamingBeatAnalyzer(rate)
                var cursor = 0
                while (cursor < signal.size) {
                    val length = minOf(4096, signal.size - cursor)
                    analyser.accept(signal.copyOfRange(cursor, cursor + length))
                    cursor += length
                }
                val result = analyser.finish()
                val actual = ((result.beatOffsetSeconds % period.toFloat()) + period.toFloat()) % period.toFloat()
                val expected = expectedOffset.toFloat()
                val difference = kotlin.math.abs(actual - expected)
                val circularError = minOf(difference, period.toFloat() - difference)
                assertTrue(circularError < 0.025f,
                    "Expected phase $expectedOffset s at $rate Hz, got $actual s (error $circularError)")
            }
        }
    }

    @Test
    fun signedChartOffsetIsSerializedWithoutNegativeBeatPositions() {
        val analysis = analyzeClickTrack(120.0, 24).copy(beatOffsetSeconds = -0.035f)
        val chart = ChartGenerator().generate("Offset Test", analysis, DifficultyPreset.HARD)
        assertTrue(chart.offsetSeconds < 0f)
        assertTrue(chart.notes.all { it.beat.whole >= 0 })
        val json = RpeSerializer.chartJson(chart, "song.mp3", "background.png")
        val yaml = RpeSerializer.infoYaml(chart, "song.mp3", "background.png")
        assertTrue(json.contains("\"offset\": -35"))
        assertTrue(ChartValidator.validate(chart, json, yaml, "song.mp3").isValid)
    }

    @Test
    fun strictJsonAndValidatorRejectMalformedRpeDocuments() {
        assertTrue(runCatching { StrictJson.parse("{\"broken\":}") }.isFailure)
        val analysis = analyzeClickTrack(120.0, 24)
        val chart = ChartGenerator().generate("Malformed Test", analysis, DifficultyPreset.HARD)
        val yaml = RpeSerializer.infoYaml(chart, "song.mp3", "background.png")
        val invalid = RpeSerializer.chartJson(chart, "song.mp3", "background.png").replace("\"RPEVersion\": 160", "\"RPEVersion\": nope")
        assertTrue(!ChartValidator.validate(chart, invalid, yaml, "song.mp3").isValid)
        val safe = ArrangementHints(Float.NaN, Float.POSITIVE_INFINITY, -1f, 5f).validated()
        assertTrue(safe.densityMultiplier == 1f && safe.holdBias == 0.08f)
    }

    @Test
    fun difficultyAndRpeOutputRemainMonotonicAndValid() {
        val analysis = analyzeClickTrack(120.0, 28)
        val generator = ChartGenerator()
        val easy = generator.generate("Regression", analysis, DifficultyPreset.EASY)
        val hard = generator.generate("Regression", analysis, DifficultyPreset.HARD)
        val inferno = generator.generate("Regression", analysis, DifficultyPreset.INFERNO)
        assertTrue(easy.notes.size < hard.notes.size)
        assertTrue(hard.notes.size < inferno.notes.size)
        assertTrue(inferno.notes.all { it.positionX in -675f..675f })
        val json = RpeSerializer.chartJson(hard, "song.mp3", "background.png")
        val yaml = RpeSerializer.infoYaml(hard, "song.mp3", "background.png")
        assertTrue(ChartValidator.validate(hard, json, yaml, "song.mp3").isValid)
        assertTrue(json.contains("\"Texture\": \"line.png\""))
        assertTrue(json.contains("\"startTime\": [0, 0, 1]"))
        assertTrue(yaml.contains("format: rpe"))
    }

    private fun analyzeClickTrack(bpm: Double, seconds: Int): AnalysisResult {
        val rate = 22_050
        val signal = FloatArray(rate * seconds)
        val beatDuration = 60.0 / bpm
        for (i in signal.indices) {
            val time = i.toDouble() / rate
            val phase = time % beatDuration
            val click = if (phase < 0.03) (1.0 - phase / 0.03) * sin(2.0 * PI * 1750.0 * time) else 0.0
            val pad = 0.045 * sin(2.0 * PI * 220.0 * time)
            signal[i] = (click * 0.8 + pad).toFloat()
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
