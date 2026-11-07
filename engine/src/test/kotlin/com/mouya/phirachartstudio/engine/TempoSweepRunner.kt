package com.mouya.phirachartstudio.engine

import kotlin.math.PI
import kotlin.math.sin

object TempoSweepRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val expectedTempos = listOf(55.0, 60.0, 75.0, 90.0, 100.0, 110.0, 120.0, 128.0, 140.0, 150.0, 160.0, 180.0, 200.0)
        val results = expectedTempos.map { expected ->
            val actual = analyze(expected, 22)
            val error = kotlin.math.abs(actual.bpm - expected.toFloat())
            check(error < 5.5f) { "Expected $expected BPM, got ${actual.bpm} (error=$error)" }
            check(actual.onsetTimesSeconds.size >= (expected * 22.0 / 60.0 * 0.55).toInt()) {
                "Too few onsets at $expected BPM: ${actual.onsetTimesSeconds.size}"
            }
            "${expected.toInt()}:${"%.2f".format(java.util.Locale.US, actual.bpm)}"
        }
        println("PASS tempo sweep: ${results.joinToString(" | ")}")
    }

    private fun analyze(bpm: Double, seconds: Int): AnalysisResult {
        val sampleRate = 22_050
        val period = 60.0 / bpm
        val samples = FloatArray(sampleRate * seconds)
        for (i in samples.indices) {
            val time = i.toDouble() / sampleRate
            val phase = time % period
            val transient = if (phase < 0.024) (1.0 - phase / 0.024) * sin(2.0 * PI * 1650.0 * time) else 0.0
            val pad = 0.04 * sin(2.0 * PI * 196.0 * time)
            samples[i] = (transient * 0.85 + pad).toFloat()
        }
        val analyzer = StreamingBeatAnalyzer(sampleRate)
        var offset = 0
        while (offset < samples.size) {
            val next = minOf(samples.size, offset + 4096)
            analyzer.accept(samples.copyOfRange(offset, next))
            offset = next
        }
        return analyzer.finish()
    }
}
