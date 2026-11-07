package com.mouya.phirachartstudio.engine

import kotlin.math.max
import kotlin.math.min

data class EnergyPoint(
    val timeSeconds: Float,
    val rms: Float,
    val low: Float,
    val mid: Float,
    val high: Float,
    val onsetStrength: Float,
)

data class EnergySegment(
    val startSeconds: Float,
    val endSeconds: Float,
    val averageEnergy: Float,
    val label: String,
)

data class AnalysisResult(
    val sampleRate: Int,
    val durationSeconds: Float,
    val bpm: Float,
    val beatOffsetSeconds: Float,
    val confidence: Float,
    val onsetTimesSeconds: List<Float>,
    val energyEnvelope: List<EnergyPoint>,
    val segments: List<EnergySegment>,
    val warnings: List<String> = emptyList(),
) {
    fun energyAt(timeSeconds: Float): Float {
        if (energyEnvelope.isEmpty()) return 0.5f
        val index = ((timeSeconds / durationSeconds.coerceAtLeast(0.01f)) * energyEnvelope.lastIndex)
            .toInt().coerceIn(0, energyEnvelope.lastIndex)
        return energyEnvelope[index].rms
    }

    fun onsetNear(timeSeconds: Float, toleranceSeconds: Float): Float? {
        if (onsetTimesSeconds.isEmpty()) return null
        var low = 0
        var high = onsetTimesSeconds.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (onsetTimesSeconds[middle] < timeSeconds) low = middle + 1 else high = middle - 1
        }
        val before = onsetTimesSeconds.getOrNull(high)
        val after = onsetTimesSeconds.getOrNull(low)
        val nearest = when {
            before == null -> after
            after == null -> before
            kotlin.math.abs(before - timeSeconds) <= kotlin.math.abs(after - timeSeconds) -> before
            else -> after
        }
        return nearest?.takeIf { kotlin.math.abs(it - timeSeconds) <= toleranceSeconds }
    }
}

enum class DifficultyPreset(
    val displayName: String,
    val level: String,
    val rating: Float,
    val gridDenominator: Int,
    val minimumSpacingSeconds: Float,
    val density: Float,
) {
    EASY("EZ", "EZ", 3.5f, 1, 0.42f, 0.62f),
    HARD("HD", "HD", 8.0f, 2, 0.22f, 1.0f),
    INFERNO("IN", "IN", 13.0f, 4, 0.12f, 1.42f),
}

enum class NoteKind(val rpeType: Int) { TAP(1), HOLD(2), FLICK(3), DRAG(4) }

data class BeatPosition(val whole: Int, val numerator: Int = 0, val denominator: Int = 1) {
    init {
        require(whole >= 0)
        require(denominator > 0)
        require(numerator >= 0 && numerator < denominator)
    }

    fun toBeats(): Double = whole + numerator.toDouble() / denominator
}

data class ChartNote(
    val kind: NoteKind,
    val beat: BeatPosition,
    val endBeat: BeatPosition = beat,
    val positionX: Float,
    val above: Boolean = true,
    val visibleTimeSeconds: Float = 1.5f,
    val size: Float = 1f,
) {
}

data class GeneratedChart(
    val title: String,
    val difficulty: DifficultyPreset,
    val bpm: Float,
    val offsetSeconds: Float,
    val notes: List<ChartNote>,
    val durationSeconds: Float,
    val warnings: List<String>,
) {
    val estimatedDensity: Float
        get() = if (durationSeconds <= 0f) 0f else notes.size / durationSeconds
}

data class ArrangementHints(
    val densityMultiplier: Float = 1f,
    val holdBias: Float = 0.08f,
    val flickBias: Float = 0.12f,
    val syncopation: Float = 0.18f,
) {
    fun validated(): ArrangementHints = ArrangementHints(
        densityMultiplier = densityMultiplier.finiteOr(1f).coerceIn(0.75f, 1.25f),
        holdBias = holdBias.finiteOr(0.08f).coerceIn(0f, 0.18f),
        flickBias = flickBias.finiteOr(0.12f).coerceIn(0f, 0.28f),
        syncopation = syncopation.finiteOr(0.18f).coerceIn(0f, 0.30f),
    )
}

internal fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
internal fun clamp01(value: Float): Float = max(0f, min(1f, value.finiteOr(0f)))
