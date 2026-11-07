package com.mouya.phirachartstudio.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Streaming monaural feature extractor. It retains a 2048-sample frame and a
 * low-rate feature envelope only; decoded PCM is never accumulated in memory.
 */
class StreamingBeatAnalyzer(private val sampleRate: Int) {
    private val fftSize = 2048
    private val hopSize = max(1, (sampleRate / 100f).roundToInt())
    private val featureRate = sampleRate.toFloat() / hopSize
    private val firstFrameCenterSeconds = fftSize.toFloat() * 0.5f / sampleRate
    // Spectral-flux peaks are biased toward the trailing side of a Hann-windowed
    // frame. Calibrating the feature timestamp by 0.36 window lengths aligns
    // synthetic impulses with their sample-time onset across common PCM rates.
    private val featureTimeOriginSeconds = firstFrameCenterSeconds + 0.36f * fftSize / sampleRate
    private val fft = Radix2Fft(fftSize)
    private val ring = FloatArray(fftSize)
    private val frame = FloatArray(fftSize)
    private val real = FloatArray(fftSize)
    private val imaginary = FloatArray(fftSize)
    private var writeIndex = 0
    private var samplesSeen = 0L
    private var samplesSinceFrame = 0
    private var ringFilled = false
    private var previousMagnitude = FloatArray(fftSize / 2)
    private val onsetEnvelope = ArrayList<Float>(4096)
    private val rmsEnvelope = ArrayList<Float>(4096)
    private val lowEnvelope = ArrayList<Float>(4096)
    private val midEnvelope = ArrayList<Float>(4096)
    private val highEnvelope = ArrayList<Float>(4096)

    init { require(sampleRate >= 8_000) { "Unsupported sample rate: $sampleRate" } }

    fun accept(monoSamples: FloatArray, length: Int = monoSamples.size) {
        require(length in 0..monoSamples.size)
        for (i in 0 until length) {
            val value = monoSamples[i].takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
            ring[writeIndex] = value
            writeIndex = (writeIndex + 1) % fftSize
            samplesSeen++
            if (!ringFilled && samplesSeen >= fftSize) ringFilled = true
            samplesSinceFrame++
            if (ringFilled && samplesSinceFrame >= hopSize) {
                samplesSinceFrame = 0
                extractFrame()
            }
        }
    }

    fun finish(): AnalysisResult {
        if (onsetEnvelope.isEmpty()) {
            return AnalysisResult(sampleRate, samplesSeen.toFloat() / sampleRate, 120f, 0f, 0f,
                emptyList(), emptyList(), emptyList(), listOf("音频过短或未包含可分析的样本"))
        }
        val duration = samplesSeen.toFloat() / sampleRate
        val normalized = normalizeEnvelope(onsetEnvelope)
        val tempo = estimateTempo(normalized)
        val onsetTimes = detectOnsets(normalized)
        val rmsRange = rmsEnvelope.minOrNull().orZero() to rmsEnvelope.maxOrNull().orOne()
        val lowRange = lowEnvelope.minOrNull().orZero() to lowEnvelope.maxOrNull().orOne()
        val midRange = midEnvelope.minOrNull().orZero() to midEnvelope.maxOrNull().orOne()
        val highRange = highEnvelope.minOrNull().orZero() to highEnvelope.maxOrNull().orOne()
        val points = rmsEnvelope.indices.map { i ->
            EnergyPoint(
                timeSeconds = featureTimeOriginSeconds + i / featureRate,
                rms = normalizeValue(rmsEnvelope[i], rmsRange.first, rmsRange.second),
                low = normalizeValue(lowEnvelope[i], lowRange.first, lowRange.second),
                mid = normalizeValue(midEnvelope[i], midRange.first, midRange.second),
                high = normalizeValue(highEnvelope[i], highRange.first, highRange.second),
                onsetStrength = normalized.getOrElse(i) { 0f },
            )
        }
        val segments = makeSegments(points, duration)
        val warnings = buildList {
            if (duration < 12f) add("音频较短，BPM 置信度可能偏低")
            if (tempo.confidence < 0.16f) add("节拍特征不明显，建议试听后手动校准 BPM")
            if (onsetTimes.size < 8) add("检测到的起音较少，谱面生成将更多依靠节拍网格")
        }
        return AnalysisResult(
            sampleRate = sampleRate,
            durationSeconds = duration,
            bpm = tempo.bpm,
            beatOffsetSeconds = tempo.offsetSeconds,
            confidence = tempo.confidence,
            onsetTimesSeconds = onsetTimes,
            energyEnvelope = points,
            segments = segments,
            warnings = warnings,
        )
    }

    private fun extractFrame() {
        var index = writeIndex
        var sumSquares = 0.0
        for (i in 0 until fftSize) {
            val value = ring[index]
            frame[i] = value
            sumSquares += value * value
            index = (index + 1) % fftSize
        }
        val magnitude = fft.magnitude(frame, real, imaginary)
        var flux = 0f
        var low = 0f
        var mid = 0f
        var high = 0f
        val last = magnitude.lastIndex.coerceAtLeast(1)
        for (bin in 1 until magnitude.size) {
            val current = magnitude[bin]
            val positiveDelta = max(0f, current - previousMagnitude[bin])
            flux += positiveDelta
            when {
                bin <= last * 0.08f -> low += current
                bin <= last * 0.38f -> mid += current
                else -> high += current
            }
            previousMagnitude[bin] = current
        }
        val rms = sqrt(sumSquares / fftSize).toFloat()
        val spectralScale = (magnitude.sum() + 1e-5f)
        val normalizedFlux = (flux / spectralScale).coerceIn(0f, 3f)
        val rmsDelta = if (rmsEnvelope.isEmpty()) 0f else max(0f, rms - rmsEnvelope.last())
        onsetEnvelope += normalizedFlux * 0.82f + rmsDelta * 0.18f
        rmsEnvelope += rms
        lowEnvelope += low / (spectralScale + 1e-5f)
        midEnvelope += mid / (spectralScale + 1e-5f)
        highEnvelope += high / (spectralScale + 1e-5f)
    }

    private data class Tempo(val bpm: Float, val offsetSeconds: Float, val confidence: Float)

    private fun estimateTempo(values: FloatArray): Tempo {
        if (values.size < (featureRate * 5f).roundToInt()) return Tempo(120f, 0f, 0.05f)
        val minLag = max(2, (featureRate * 60f / 200f).roundToInt())
        val maxLag = min((featureRate * 60f / 55f).roundToInt(), values.size / 2)
        val mean = values.average().toFloat()
        val centered = FloatArray(values.size) { values[it] - mean }
        val scores = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in lag until centered.size) {
                val a = centered[i].toDouble()
                val b = centered[i - lag].toDouble()
                sum += a * b
                normA += a * a
                normB += b * b
            }
            val denom = sqrt(normA * normB).coerceAtLeast(1e-12)
            scores[lag] = (sum / denom).toFloat()
        }
        var bestLag = minLag
        var bestScore = Float.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            // A peak at twice the interval is a common half-tempo ambiguity:
            // if the half-lag is also strongly correlated, the candidate interval
            // skips every other beat and should be penalized, not reinforced.
            val halfLagCorrelation = scores.getOrElse(lag / 2) { 0f }.coerceAtLeast(0f)
            val doubleLagCorrelation = scores.getOrElse(lag * 2) { 0f }.coerceAtLeast(0f)
            val score = scores[lag] - 0.32f * halfLagCorrelation + 0.025f * doubleLagCorrelation
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        val left = scores.getOrElse(bestLag - 1) { scores[bestLag] }
        val center = scores[bestLag]
        val right = scores.getOrElse(bestLag + 1) { center }
        val denom = left - 2f * center + right
        val correction = if (abs(denom) > 1e-6f) (0.5f * (left - right) / denom).coerceIn(-0.45f, 0.45f) else 0f
        val refinedLag = (bestLag + correction).coerceAtLeast(1f)
        val bpm = (60f * featureRate / refinedLag).coerceIn(55f, 200f)
        val phase = bestPhase(normalized = values, periodFrames = refinedLag)
        val sorted = scores.slice(minLag..maxLag).sorted()
        val baseline = sorted[sorted.size / 2]
        val confidence = ((bestScore - baseline) / (abs(bestScore) + 0.15f)).coerceIn(0f, 1f)
        return Tempo(bpm, featureTimeOriginSeconds + phase / featureRate, confidence)
    }

    private fun bestPhase(normalized: FloatArray, periodFrames: Float): Float {
        val period = periodFrames.roundToInt().coerceAtLeast(1)
        val search = min(period, 120)
        val sums = DoubleArray(search)
        val counts = IntArray(search)
        normalized.forEachIndexed { index, value ->
            val phase = index % period
            if (phase < search) {
                sums[phase] += value.toDouble()
                counts[phase]++
            }
        }
        val scores = DoubleArray(search) { phase -> sums[phase] / counts[phase].coerceAtLeast(1) }
        val bestPhase = scores.indices.maxByOrNull { scores[it] } ?: 0
        val left = scores[(bestPhase - 1 + search) % search]
        val center = scores[bestPhase]
        val right = scores[(bestPhase + 1) % search]
        val denominator = left - 2.0 * center + right
        val subFrame = if (kotlin.math.abs(denominator) > 1e-12) {
            (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
        } else {
            0.0
        }
        return ((bestPhase + subFrame).toFloat() % periodFrames).let { if (it < 0f) it + periodFrames else it }
    }

    private fun detectOnsets(values: FloatArray): List<Float> {
        if (values.size < 3) return emptyList()
        val candidates = ArrayList<Pair<Int, Float>>()
        val radius = 18
        for (i in 2 until values.size - 2) {
            if (values[i] < values[i - 1] || values[i] < values[i + 1]) continue
            val from = max(0, i - radius)
            val to = min(values.lastIndex, i + radius)
            var localMean = 0f
            for (j in from..to) localMean += values[j]
            localMean /= (to - from + 1).coerceAtLeast(1)
            val threshold = max(0.18f, localMean * 1.65f)
            if (values[i] >= threshold) candidates += i to values[i]
        }
        val accepted = ArrayList<Int>()
        val blocked = BooleanArray(values.size)
        for ((index, _) in candidates.sortedByDescending { it.second }) {
            if (blocked[index]) continue
            accepted += index
            for (near in max(0, index - 8)..min(values.lastIndex, index + 8)) blocked[near] = true
        }
        return accepted.sorted().map { featureTimeOriginSeconds + it / featureRate }
    }

    private fun normalizeEnvelope(input: List<Float>): FloatArray {
        val sorted = input.sorted()
        val floor = sorted[(sorted.size * 0.35f).toInt().coerceIn(0, sorted.lastIndex)]
        val ceiling = sorted[(sorted.size * 0.97f).toInt().coerceIn(0, sorted.lastIndex)].coerceAtLeast(floor + 1e-5f)
        return FloatArray(input.size) { i -> ((input[i] - floor) / (ceiling - floor)).coerceIn(0f, 1.5f) }
    }

    private fun normalizeValue(value: Float, low: Float, high: Float): Float =
        ((value - low) / (high - low).coerceAtLeast(1e-5f)).coerceIn(0f, 1f)

    private fun Float?.orZero(): Float = this ?: 0f
    private fun Float?.orOne(): Float = this ?: 1f

    private fun makeSegments(points: List<EnergyPoint>, duration: Float): List<EnergySegment> {
        if (points.isEmpty()) return emptyList()
        val segmentFrames = (featureRate * 8f).roundToInt().coerceAtLeast(1)
        val raw = points.chunked(segmentFrames).mapIndexed { index, chunk ->
            val average = chunk.map { it.rms }.average().toFloat()
            val start = points.first().timeSeconds + index * segmentFrames / featureRate
            val end = min(duration, start + chunk.size / featureRate)
            Triple(start, end, average)
        }
        val low = raw.map { it.third }.minOrNull() ?: 0f
        val high = raw.map { it.third }.maxOrNull() ?: 1f
        return raw.map { (start, end, energy) ->
            val ratio = (energy - low) / (high - low).coerceAtLeast(1e-5f)
            val label = when {
                ratio < 0.28f -> "低能量段"
                ratio < 0.62f -> "过渡段"
                else -> "高能量段"
            }
            EnergySegment(start, end, energy, label)
        }
    }
}
