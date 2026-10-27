package com.criastudio.ai

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.sqrt

/**
 * Local-only audio silence analysis. Decodes the selected audio track to PCM and
 * calculates RMS levels in short windows. No media bytes leave the device.
 */
internal object SilenceDetector {
    data class Result(
        val durationMs: Long,
        val firstSpeechMs: Long,
        val lastSpeechMs: Long,
        val silences: List<Pair<Long, Long>>
    )

    fun analyze(
        resolver: ContentResolver,
        uri: Uri,
        expectedDurationMs: Long,
        thresholdDb: Double = -42.0,
        minSilenceMs: Long = 700L
    ): Result? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val levels = mutableListOf<Pair<Long, Double>>()
        var sampleRate = 0
        var channels = 1
        try {
            extractor.setDataSource(resolver.openAssetFileDescriptor(uri, "r")!!.fileDescriptor)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i
                    format = candidate
                    break
                }
            }
            if (audioTrack < 0 || format == null) return null
            extractor.selectTrack(audioTrack)
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var fallbackPtsUs = 0L
            var decodedSamples = 0L
            val windowSamples = (sampleRate / 10).coerceAtLeast(1) * channels
            val pcmWindow = ArrayList<Short>(windowSamples)
            var windowStartMs = 0L
            var outputFormatRate = sampleRate
            var outputFormatChannels = channels
            var lastPtsUs = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, fallbackPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, size, pts, 0)
                            fallbackPtsUs = pts
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputFormatRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputFormatChannels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    }
                    outputIndex >= 0 -> {
                        val out = codec.getOutputBuffer(outputIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val ptsMs = (info.presentationTimeUs / 1000L).coerceAtLeast(0L)
                            if (pcmWindow.isEmpty()) windowStartMs = ptsMs
                            while (out.remaining() >= 2) {
                                pcmWindow.add(out.short)
                                if (pcmWindow.size >= (outputFormatRate / 10).coerceAtLeast(1) * outputFormatChannels) {
                                    var sum = 0.0
                                    pcmWindow.forEach { value -> val normalized = value / 32768.0; sum += normalized * normalized }
                                    val rms = sqrt(sum / pcmWindow.size)
                                    val db = if (rms <= 0.000001) -120.0 else 20.0 * kotlin.math.log10(rms)
                                    levels.add(windowStartMs to db)
                                    pcmWindow.clear()
                                    decodedSamples++
                                    windowStartMs = ptsMs + decodedSamples * 100L
                                }
                            }
                            lastPtsUs = info.presentationTimeUs
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
                if (levels.size > 1_000_000) break
            }
            if (pcmWindow.isNotEmpty()) {
                var sum = 0.0
                pcmWindow.forEach { value -> val normalized = value / 32768.0; sum += normalized * normalized }
                val rms = sqrt(sum / pcmWindow.size)
                levels.add(windowStartMs to if (rms <= 0.000001) -120.0 else 20.0 * kotlin.math.log10(rms))
            }
            if (levels.isEmpty()) return null

            val threshold = thresholdDb
            val speechIndices = levels.indices.filter { levels[it].second > threshold }
            if (speechIndices.isEmpty()) return Result(
                expectedDurationMs.coerceAtLeast(0L), 0L, expectedDurationMs.coerceAtLeast(0L), emptyList()
            )
            val firstSpeech = levels[speechIndices.first()].first
            val lastSpeech = (levels[speechIndices.last()].first + 100L)
                .coerceAtMost(expectedDurationMs.takeIf { it > 0L } ?: (lastPtsUs / 1000L + 100L))
            val silences = mutableListOf<Pair<Long, Long>>()
            var silenceStart: Long? = null
            for ((time, db) in levels) {
                if (db <= threshold) {
                    if (silenceStart == null) silenceStart = time
                } else {
                    val start = silenceStart
                    if (start != null && time - start >= minSilenceMs) silences.add(start to time)
                    silenceStart = null
                }
            }
            silenceStart?.let { start ->
                val end = expectedDurationMs.takeIf { it > 0L } ?: (lastPtsUs / 1000L + 100L)
                if (end - start >= minSilenceMs) silences.add(start to end)
            }
            return Result(
                expectedDurationMs.takeIf { it > 0L } ?: (lastPtsUs / 1000L + 100L),
                firstSpeech.coerceAtLeast(0L),
                lastSpeech.coerceAtLeast(firstSpeech),
                silences
            )
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }
}
