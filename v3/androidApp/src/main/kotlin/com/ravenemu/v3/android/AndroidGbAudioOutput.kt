package com.ravenemu.v3.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.ravenemu.v3.shared.GbPcmQueue
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Audio output based on RavenEmu 0.8.1's proven transport.
 *
 * Convert the native GB stereo PCM16 stream with a band-limited resampler to
 * the phone's preferred rate (usually 48kHz). Keep six frames queued before
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
    private val fadeFrames = (outputRate / 33).coerceAtLeast(1) // ~30 ms
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
            val needed = resampler.maxOutput(count, correctionForDrift())
            if (needed > resampled.size) resampled = ShortArray(needed)
            val produced = resampler.resample(
                samples,
                count,
                resampled,
                governor.rateScale,
            )
            fadeIn(resampled, produced)
            queue.append(resampled, produced)

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
                if (primer.onSamplesQueued(written)) track.play()
            }
        }
    }

    private fun correctionForDrift(): Double {
        if (!primer.playbackStarted) return 1.0
        val played = track.playbackHeadPosition.toLong() and MASK_32
        val queued = (writtenFrames - played) and MASK_32
        return governor.onQueuedFrames(queued.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
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
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_VIDEO_FRAMES = 8
        const val PRIME_VIDEO_FRAMES = 6
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
