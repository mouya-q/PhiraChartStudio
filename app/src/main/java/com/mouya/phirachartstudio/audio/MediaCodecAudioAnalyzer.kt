package com.mouya.phirachartstudio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.mouya.phirachartstudio.engine.AnalysisResult
import com.mouya.phirachartstudio.engine.StreamingBeatAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Decodes through Android's system codecs and feeds short mono chunks into the
 * streaming analyser. No complete PCM track is retained in memory.
 */
class MediaCodecAudioAnalyzer(private val context: Context) {
    data class Progress(val fraction: Float, val stage: String)

    suspend fun analyze(uri: Uri, onProgress: (Progress) -> Unit): AnalysisResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        try {
            extractor.setDataSource(context, uri, null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("所选文件中没有可解码的音轨")
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("音频 MIME 类型缺失")
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else 0L
            extractor.selectTrack(audioTrack)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var analyser = StreamingBeatAnalyzer(sampleRate)
            var inputEos = false
            var outputEos = false
            val bufferInfo = MediaCodec.BufferInfo()
            var lastProgress = -1

            onProgress(Progress(0f, "正在打开音频流"))
            while (!outputEos) {
                currentCoroutineContext().ensureActive()

                if (!inputEos) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: error("解码器输入缓冲区不可用")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        val nextRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val nextChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        val nextEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        if (nextRate != sampleRate || nextChannels != channels || nextEncoding != pcmEncoding) {
                            sampleRate = nextRate
                            channels = nextChannels
                            pcmEncoding = nextEncoding
                            // Format renegotiation is uncommon for file decoders. We restart the feature
                            // stream rather than mixing sample rates, and expose this as an explicit error.
                            error("音频解码过程中采样格式发生变化，请转换音频后重试")
                        }
                        onProgress(Progress(0.04f, "音频格式已识别"))
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (bufferInfo.size > 0 && outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val mono = toMonoFloat(outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN), channels, pcmEncoding)
                            analyser.accept(mono)
                            if (durationUs > 0L) {
                                val fraction = (bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                                val percent = (fraction * 100).toInt()
                                if (percent != lastProgress && percent % 2 == 0) {
                                    lastProgress = percent
                                    onProgress(Progress(0.05f + fraction * 0.90f, "正在分析节奏 · $percent%"))
                                }
                            }
                        }
                        outputEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            onProgress(Progress(0.98f, "正在计算节拍与起音"))
            val result = analyser.finish()
            require(result.durationSeconds >= 2f) { "音频不足 2 秒，无法生成谱面" }
            onProgress(Progress(1f, "分析完成"))
            result
        } finally {
            if (decoderStarted) runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    private fun toMonoFloat(buffer: ByteBuffer, channelCount: Int, encoding: Int): FloatArray {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> error("当前设备解码器返回了不支持的 PCM 编码：$encoding")
        }
        val frameBytes = bytesPerSample * channelCount
        val frameCount = buffer.remaining() / frameBytes
        val result = FloatArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0f
            repeat(channelCount) {
                val sample = when (encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
                    AudioFormat.ENCODING_PCM_16BIT -> buffer.short / 32768f
                    AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                        val b0 = buffer.get().toInt() and 0xff
                        val b1 = buffer.get().toInt() and 0xff
                        val b2 = buffer.get().toInt()
                        val signed = (b2 shl 16) or (b1 shl 8) or b0
                        signed / 8_388_608f
                    }
                    AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648f
                    else -> 0f
                }
                sum += sample.coerceIn(-1f, 1f)
            }
            result[frame] = (sum / max(1, channelCount)).coerceIn(-1f, 1f)
        }
        return result
    }
}
