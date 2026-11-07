package com.mouya.phirachartstudio.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** In-place radix-2 FFT. Input arrays are reused by the streaming analyser. */
internal class Radix2Fft(val size: Int) {
    private val reverse = IntArray(size)
    private val hann = FloatArray(size) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat()
    }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reverse[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun magnitude(samples: FloatArray, real: FloatArray, imaginary: FloatArray): FloatArray {
        require(samples.size >= size)
        for (i in 0 until size) {
            val j = reverse[i]
            real[j] = samples[i] * hann[i]
            imaginary[j] = 0f
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val angle = (-2.0 * PI / length)
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var start = 0
            while (start < size) {
                var wr = 1f
                var wi = 0f
                for (j in 0 until half) {
                    val even = start + j
                    val odd = even + half
                    val tr = wr * real[odd] - wi * imaginary[odd]
                    val ti = wr * imaginary[odd] + wi * real[odd]
                    val er = real[even]
                    val ei = imaginary[even]
                    real[even] = er + tr
                    imaginary[even] = ei + ti
                    real[odd] = er - tr
                    imaginary[odd] = ei - ti
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                start += length
            }
            length = length shl 1
        }
        return FloatArray(size / 2) { i -> sqrt(real[i] * real[i] + imaginary[i] * imaginary[i]) }
    }
}
