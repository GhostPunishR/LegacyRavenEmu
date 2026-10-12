package com.ravenemu.v3.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.ravenemu.v3.shared.GbPcmQueue
import kotlin.math.ceil

/**
 * Game Boy stereo PCM16 output for the Android V3 preview.
 *
 * The core already produces audio at its native sample rate. AudioTrack
 * resamples to the phone output as needed. Non-blocking writes ensure audio
 * congestion never stalls video or touch controls.
 *
 * No Android audio objects escape into the shared iOS UI.
 */
class AndroidGbAudioOutput(sampleRateHz: Int, videoRateHz: Double) : AutoCloseable {
    private val lock = Any()
    private val queue: GbPcmQueue
    private val scratch = ShortArray(4096)
    private val track: AudioTrack
    private val primeSamples: Int
    private var writtenSamples = 0
    private var playing = false
    private var closed = false

    init {
        require(sampleRateHz in 8000..192000) { "Fréquence audio native invalide." }
        require(videoRateHz > 0.0 && videoRateHz.isFinite()) { "Cadence GB invalide." }
        val stereoSamplesPerFrame = ceil(sampleRateHz / videoRateHz).toInt() * 2
        primeSamples = stereoSamplesPerFrame * 3
        // Device FIFO: eight video frames. Producer queue: at most a quarter
        // second. Neither can grow when emulation runs for a long time.
        val deviceBytes = maxOf(
            AudioTrack.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            stereoSamplesPerFrame * 2 * 8,
        )
        require(deviceBytes > 0) { "Sortie stéréo PCM16 indisponible." }
        queue = GbPcmQueue(maxOf(sampleRateHz / 2, 8192).let {
            if (it % 2 == 0) it else it + 1
        })
        val candidate = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(deviceBytes)
            .build()
        if (candidate.state != AudioTrack.STATE_INITIALIZED) {
            candidate.release()
            throw IllegalStateException("Impossible d'initialiser AudioTrack.")
        }
        track = candidate
    }

    /** Called on the emulation worker after every native frame. */
    fun submit(samples: ShortArray, count: Int) {
        synchronized(lock) {
            if (closed) return
            // Native bridge produces interleaved stereo; never split a pair.
            val completeStereoCount = count - count % 2
            queue.append(samples, completeStereoCount)
            while (queue.size > 0) {
                val available = queue.copyHead(scratch)
                val written = track.write(
                    scratch,
                    0,
                    available,
                    AudioTrack.WRITE_NON_BLOCKING,
                )
                if (written < 0) {
                    throw IllegalStateException("AudioTrack.write: $written")
                }
                if (written == 0) break
                require(written <= available) { "AudioTrack a retourné une taille invalide." }
                queue.discard(written)
                writtenSamples += written
            }
            if (!playing && writtenSamples >= primeSamples) {
                track.play()
                playing = true
            }
        }
    }

    /** Called immediately from the Activity when paused or leaving the game. */
    fun stopNow() {
        synchronized(lock) {
            if (closed) return
            closed = true
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {
                // A failed or already-stopped track must not block Activity pause.
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
}
