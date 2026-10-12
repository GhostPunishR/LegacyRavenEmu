package com.ravenemu.v3.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.ravenemu.v3.shared.GbPcmQueue
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Audio output based on RavenEmu 0.8.1's proven transport.
 *
 * Convert the native GB stereo PCM16 stream with a band-limited resampler to
 * the phone's preferred rate (usually 48kHz). Keep two frames queued before
 * starting playback and gently adapt to the device audio clock.
 *
 * Unlike the V2 sink, the V3 preview keeps non-blocking AudioTrack writes
 * so a temporary device stall does not freeze the video coroutine. Pending
 * samples are bounded and always kept as complete stereo pairs.
 */
class AndroidGbAudioOutput(
    context: Context,
    sampleRateHz: Int,
    videoRateHz: Double,
) : AutoCloseable {
    private val lock = Any()
    private val outputRate = resolveNativeRate(context)
    private val resampler = BandLimitedResampler(sampleRateHz, outputRate)
    private val outputSamplesPerFrame: Int
    private val primer: AudioBufferPrimer
    private val governor: AudioClockGovernor
    private val queue: GbPcmQueue
    private val scratch = ShortArray(4096)
    private var resampled = ShortArray(4096)
    private val track: AudioTrack

    private var writtenFrames = 0L
    private var droppedSamples = 0L
    private var startupBlocks = 0
    private val report = StringBuilder("RavenEmu V3 GB audio startup diagnostic\n")
    private val fadeFrames = (outputRate / 20).coerceAtLeast(1) // ~50 ms fade, without delaying playback
    private var rampFrame = 0
    private var closed = false

    init {
        require(sampleRateHz in 8000..192000) { "Fréquence audio native invalide." }
        require(videoRateHz > 0.0 && videoRateHz.isFinite()) { "Cadence GB invalide." }
        outputSamplesPerFrame = ceil(outputRate / videoRateHz).toInt() * 2
        primer = AudioBufferPrimer(outputSamplesPerFrame * PRIME_VIDEO_FRAMES)
        governor = AudioClockGovernor(outputSamplesPerFrame / 2 * PRIME_VIDEO_FRAMES)
        queue = GbPcmQueue(maxOf(outputRate / 2, 8192).let { if (it % 2 == 0) it else it + 1 })

        val minBuffer = AudioTrack.getMinBufferSize(
            outputRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "Sortie stéréo PCM16 indisponible." }
        val bufferBytes = maxOf(
            minBuffer,
            outputSamplesPerFrame * BYTES_PER_SAMPLE * BUFFER_VIDEO_FRAMES,
        )

        val candidate = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(outputRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        if (candidate.state != AudioTrack.STATE_INITIALIZED) {
            candidate.release()
            throw IllegalStateException("Impossible d'initialiser AudioTrack.")
        }
        track = candidate
    }

    /** Submit samples on the emulation worker after each RavenGB frame. */
    fun submit(samples: ShortArray, count: Int) {
        synchronized(lock) {
            if (closed) return
            require(count in 0..samples.size && count % CHANNELS == 0) {
                "Échantillons PCM stéréo invalides."
            }
            if (startupBlocks < 12) logPcm("native", samples, count)
            val needed = resampler.maxOutput(count, correctionForDrift())
            if (needed > resampled.size) resampled = ShortArray(needed)
            val produced = resampler.resample(
                samples,
                count,
                resampled,
                governor.rateScale,
            )
            fadeIn(resampled, produced)
            if (startupBlocks < 12) {
                logPcm("output", resampled, produced)
                startupBlocks++
            }
            droppedSamples += queue.append(resampled, produced).toLong()

            // A partial non-blocking write stays in the bounded FIFO and is
            // retried on the next frame, without causing skips or duplicates.
            while (queue.size > 0) {
                val available = queue.copyHead(scratch)
                val written = track.write(
                    scratch, 0, available, AudioTrack.WRITE_NON_BLOCKING,
                )
                if (written < 0) {
                    throw IllegalStateException("AudioTrack.write: $written")
                }
                if (written == 0) break
                require(written <= available && written % CHANNELS == 0) {
                    "AudioTrack a retourné un nombre incomplet d'échantillons stéréo."
                }
                queue.discard(written)
                writtenFrames += written / CHANNELS
                if (primer.onSamplesQueued(written)) {
                    record("AudioTrack.play: startupBlocks=$startupBlocks queuedFrames=$writtenFrames underruns=${track.underrunCount}")
                    track.play()
                }
            }
        }
    }

    private fun correctionForDrift(): Double {
        if (!primer.playbackStarted) return 1.0
        val played = track.playbackHeadPosition.toLong() and MASK_32
        val queued = (writtenFrames - played) and MASK_32
        return governor.onQueuedFrames(queued.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /** Diagnostic for the first PCM blocks; avoids recording or exposing game audio. */
    private fun logPcm(stage: String, samples: ShortArray, count: Int) {
        var peak = 0
        var jump = 0
        var previousLeft = 0
        var previousRight = 0
        for (i in 0 until count step CHANNELS) {
            val left = samples[i].toInt()
            val right = samples[i + 1].toInt()
            peak = maxOf(peak, kotlin.math.abs(left), kotlin.math.abs(right))
            if (i != 0) jump = maxOf(jump, kotlin.math.abs(left - previousLeft), kotlin.math.abs(right - previousRight))
            previousLeft = left
            previousRight = right
        }
        record("startup block=$startupBlocks stage=$stage samples=$count firstL=${if (count > 0) samples[0] else 0} firstR=${if (count > 1) samples[1] else 0} peak=$peak maxJump=$jump")
    }

    private fun record(message: String) {
        Log.d(TAG, message)
        if (report.length < 16000) report.append(message).append('\n')
    }

    fun exportReport(): String = synchronized(lock) {
        report.toString() + diagnostics() + "\n"
    }

    /** Live diagnostics to distinguish device underruns from bounded FIFO overflow. */
    fun diagnostics(): String = synchronized(lock) {
        "Audio: underruns=${track.underrunCount}, pertesPCM=${droppedSamples / CHANNELS} frames, " +
            "file=${queue.size / CHANNELS} PCM, " +
            "latence~=${((writtenFrames - (track.playbackHeadPosition.toLong() and MASK_32)).coerceAtLeast(0L) * 1000L / outputRate)}ms"
    }

    /** Start from silence even if the native APU already has nonzero samples. */
    private fun fadeIn(samples: ShortArray, count: Int) {
        for (index in 0 until count step CHANNELS) {
            if (rampFrame >= fadeFrames) break
            val gain = rampFrame.toDouble() / fadeFrames
            for (channel in 0 until CHANNELS) {
                samples[index + channel] = (samples[index + channel] * gain)
                    .roundToInt().coerceIn(-32768, 32767).toShort()
            }
            rampFrame++
        }
    }

    /** Stop promptly when the user exits or backgrounds the app. */
    fun stopNow() {
        synchronized(lock) {
            if (closed) return
            closed = true
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {
                // A failed track must not prevent lifecycle cleanup.
            }
            queue.clear()
        }
    }

    override fun close() {
        stopNow()
        synchronized(lock) {
            track.release()
        }
    }

    private companion object {
        const val TAG = "RavenEmuAudio"
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_VIDEO_FRAMES = 4
        const val PRIME_VIDEO_FRAMES = 2
        const val MASK_32 = 0xFFFF_FFFFL

        fun resolveNativeRate(context: Context): Int = try {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val reported = manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            if (reported != null && reported in 8000..192000) reported else 48000
        } catch (_: Exception) {
            48000
        }
    }
}
