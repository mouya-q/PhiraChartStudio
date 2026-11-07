package com.mouya.phirachartstudio.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Deterministic, offline chart arranger. No network, native runtime or model files required. */
class ChartGenerator {
    fun generate(
        title: String,
        analysis: AnalysisResult,
        difficulty: DifficultyPreset,
        arrangementHints: ArrangementHints = ArrangementHints(),
    ): GeneratedChart {
        require(title.isNotBlank()) { "谱面名称不能为空" }
        require(analysis.durationSeconds >= 2f) { "音频长度不足 2 秒" }
        val hints = arrangementHints.validated()
        val bpm = analysis.bpm.coerceIn(55f, 200f)
        val beatDuration = 60f / bpm
        val gridStep = 1f / difficulty.gridDenominator
        val maximumPositiveOffset = (analysis.durationSeconds - 0.2f).coerceAtLeast(0f).coerceAtMost(2f)
        val chartOffsetSeconds = analysis.beatOffsetSeconds.finiteOr(0f).coerceIn(-2f, maximumPositiveOffset)
        val gridStepSeconds = beatDuration * gridStep
        val firstGridIndex = kotlin.math.ceil((-chartOffsetSeconds / gridStepSeconds).toDouble()).toInt().coerceAtLeast(0)
        val gridCount = kotlin.math.floor(((analysis.durationSeconds - 0.08f - chartOffsetSeconds) / gridStepSeconds).toDouble())
            .toInt().coerceIn(-1, 200_000)
        val notes = ArrayList<ChartNote>()
        val prefixEnergy = FloatArray(analysis.energyEnvelope.size + 1)
        analysis.energyEnvelope.forEachIndexed { index, point -> prefixEnergy[index + 1] = prefixEnergy[index] + point.rms }
        fun localEnergy(time: Float): Float {
            if (analysis.energyEnvelope.isEmpty()) return 0.5f
            val frameRate = analysis.energyEnvelope.lastIndex / analysis.durationSeconds.coerceAtLeast(0.01f)
            val center = (time * frameRate).roundToInt().coerceIn(0, analysis.energyEnvelope.lastIndex)
            val radius = (frameRate * 2f).roundToInt().coerceAtLeast(1)
            val left = max(0, center - radius)
            val right = min(analysis.energyEnvelope.size, center + radius + 1)
            return (prefixEnergy[right] - prefixEnergy[left]) / (right - left).coerceAtLeast(1)
        }
        var previousTime = -10f
        var laneCursor = 0
        val lanes = if (difficulty == DifficultyPreset.EASY) 5 else 7
        val center = (lanes - 1) / 2f
        val maxDensity = when (difficulty) {
            DifficultyPreset.EASY -> 3.0f
            DifficultyPreset.HARD -> 5.2f
            DifficultyPreset.INFERNO -> 7.8f
        }

        for (stepIndex in firstGridIndex..gridCount) {
            val beat = stepIndex * gridStep
            val time = chartOffsetSeconds + beat * beatDuration
            if (time >= analysis.durationSeconds - 0.08f) break
            val nearestOnset = analysis.onsetNear(time, when (difficulty) {
                DifficultyPreset.EASY -> 0.105f
                DifficultyPreset.HARD -> 0.085f
                DifficultyPreset.INFERNO -> 0.065f
            })
            val onsetMatch = nearestOnset != null
            val energy = analysis.energyAt(time)
            val segmentIndex = (time / 8f).toInt().coerceIn(0, analysis.segments.lastIndex.coerceAtLeast(0))
            val sectionEnergy = analysis.segments.getOrNull(segmentIndex)?.averageEnergy ?: energy
            val macroEnergy = localEnergy(time)
            val stepPhase = stepIndex % difficulty.gridDenominator
            // Grid step zero is aligned to the detected beat phase; the BPM phase offset
            // must not change which subdivision is a whole beat.
            val downbeat = stepPhase == 0
            val shouldPlace = when (difficulty) {
                DifficultyPreset.EASY -> downbeat || (onsetMatch && energy > 0.14f) ||
                    (stepIndex % 2 == 0 && energy > 0.36f)
                DifficultyPreset.HARD -> downbeat || onsetMatch ||
                    (stepPhase == 1 && (sectionEnergy > 0.075f || macroEnergy > 0.22f))
                DifficultyPreset.INFERNO -> onsetMatch || downbeat ||
                    (stepIndex % 4 != 1 && (sectionEnergy > 0.075f || macroEnergy > 0.18f))
            }
            if (!shouldPlace) continue

            val minimumGap = difficulty.minimumSpacingSeconds / hints.densityMultiplier.coerceIn(0.8f, 1.18f)
            if (time - previousTime < minimumGap) continue
            val target = when (difficulty) {
                DifficultyPreset.EASY -> energy > 0.2f || downbeat || onsetMatch
                DifficultyPreset.HARD -> energy > 0.02f || sectionEnergy > 0.075f || onsetMatch || downbeat
                DifficultyPreset.INFERNO -> energy > 0.015f || sectionEnergy > 0.075f || onsetMatch || downbeat
            }
            if (!target) continue
            if (notes.size / analysis.durationSeconds > maxDensity * hints.densityMultiplier) continue

            val highBand = nearestEnergy(time, analysis, EnergyBand.HIGH)
            val lowBand = nearestEnergy(time, analysis, EnergyBand.LOW)
            val offbeat = stepPhase != 0
            val normalizedOnset = nearestOnset?.let { (1f - abs(it - time) / 0.12f).coerceIn(0f, 1f) } ?: 0f
            val patternKey = stableHash(stepIndex, difficulty.ordinal, laneCursor)
            val holdThreshold = 0.78f - hints.holdBias * 0.75f
            val flickThreshold = 0.88f - hints.flickBias * 0.55f
            val kind = when {
                energy > 0.82f && lowBand > 0.33f && patternKey % 17 == 0 -> NoteKind.HOLD
                highBand > flickThreshold && (onsetMatch || offbeat) -> NoteKind.FLICK
                lowBand > 0.35f && energy < 0.34f && patternKey % 5 == 0 -> NoteKind.DRAG
                energy > holdThreshold && downbeat && patternKey % 9 == 0 -> NoteKind.HOLD
                (offbeat && normalizedOnset > 0.55f && hints.syncopation > 0.12f && patternKey % 3 == 0) -> NoteKind.FLICK
                else -> NoteKind.TAP
            }
            val lane = chooseLane(laneCursor, lanes, stepIndex, downbeat, offbeat)
            laneCursor = laneCursor + if (patternKey % 2 == 0) 1 else 2
            val x = ((lane - center) / center.coerceAtLeast(1f) * 540f).coerceIn(-560f, 560f)
            val beatPosition = toBeatPosition(beat, difficulty.gridDenominator)
            val holdLength = if (kind == NoteKind.HOLD) {
                if (difficulty == DifficultyPreset.EASY) 1f else 1.5f
            } else 0f
            val endBeatValue = beat + holdLength
            val endPosition = if (holdLength > 0f && endBeatValue * beatDuration < analysis.durationSeconds - 0.05f) {
                toBeatPosition(endBeatValue, difficulty.gridDenominator)
            } else {
                beatPosition
            }
            val actualKind = if (kind == NoteKind.HOLD && endPosition == beatPosition) NoteKind.TAP else kind
            notes += ChartNote(
                kind = actualKind,
                beat = beatPosition,
                endBeat = if (actualKind == NoteKind.HOLD) endPosition else beatPosition,
                positionX = x,
                above = true,
                visibleTimeSeconds = 1.55f,
                size = when (difficulty) {
                    DifficultyPreset.EASY -> 0.94f
                    DifficultyPreset.HARD -> 1f
                    DifficultyPreset.INFERNO -> if (offbeat) 0.96f else 1.04f
                },
            )
            previousTime = time
        }

        if (notes.isEmpty()) {
            val firstFallbackBeat = kotlin.math.ceil((-chartOffsetSeconds / beatDuration).toDouble()).toInt().coerceAtLeast(0)
            val fallbackCount = min(16, ((analysis.durationSeconds - chartOffsetSeconds) / beatDuration).toInt().coerceAtLeast(1))
            repeat(fallbackCount) { index ->
                val beat = (firstFallbackBeat + index).toFloat()
                if (chartOffsetSeconds + beat * beatDuration < analysis.durationSeconds - 0.05f) {
                    notes += ChartNote(NoteKind.TAP, toBeatPosition(beat, 1), positionX = laneX(index, 5))
                }
            }
        }

        val warnings = buildList {
            if (analysis.confidence < 0.16f) add("BPM 置信度较低，导出前建议使用预览校准节拍")
            if (notes.size / analysis.durationSeconds > maxDensity) add("谱面密度较高，建议降低难度或手动删减音符")
            if (notes.size < 8) add("有效音符较少，可能是静音、环境声或节奏不明显的音频")
        }
        return GeneratedChart(
            title = title.trim().take(80),
            difficulty = difficulty,
            bpm = bpm,
            offsetSeconds = chartOffsetSeconds,
            notes = notes.sortedBy { it.beat.toBeats() },
            durationSeconds = analysis.durationSeconds,
            warnings = warnings,
        )
    }

    private enum class EnergyBand { LOW, HIGH }

    private fun nearestEnergy(time: Float, result: AnalysisResult, band: EnergyBand): Float {
        if (result.energyEnvelope.isEmpty()) return 0f
        val frameRate = result.energyEnvelope.lastIndex / result.durationSeconds.coerceAtLeast(0.01f)
        val index = (time * frameRate).roundToInt().coerceIn(0, result.energyEnvelope.lastIndex)
        return when (band) {
            EnergyBand.LOW -> result.energyEnvelope[index].low
            EnergyBand.HIGH -> result.energyEnvelope[index].high
        }
    }

    private fun chooseLane(cursor: Int, laneCount: Int, step: Int, downbeat: Boolean, offbeat: Boolean): Int {
        val base = when {
            downbeat -> (laneCount / 2 + (step % 2)) % laneCount
            offbeat -> (cursor + laneCount / 2) % laneCount
            else -> cursor % laneCount
        }
        return base.coerceIn(0, laneCount - 1)
    }

    private fun laneX(index: Int, count: Int): Float = (index % count - (count - 1) / 2f) * 260f

    private fun toBeatPosition(value: Float, denominator: Int): BeatPosition {
        val den = denominator.coerceAtLeast(1)
        val ticks = (value.coerceAtLeast(0f) * den).roundToInt()
        val whole = ticks / den
        val numerator = ticks % den
        return BeatPosition(whole, numerator, den)
    }

    private fun stableHash(a: Int, b: Int, c: Int): Int {
        var x = a * 0x45d9f3b + b * 0x119de1f3 + c * 0x3449
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        return (x xor (x ushr 16)) and Int.MAX_VALUE
    }
}
