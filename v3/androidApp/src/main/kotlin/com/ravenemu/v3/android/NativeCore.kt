package com.ravenemu.v3.android

/**
 * JNI adapter over RavenEmu-Cores' pinned portable C ABI.
 * Own one instance per running game and call it from a single emulation thread.
 * No ROMs or BIOS files are bundled with the APK.
 *
 * Rendering, audio timing, file import and persistence will be wired into the
 * shared Compose player in the next increment.
 */
class NativeCore(system: Int) : AutoCloseable {
    private var pointer: Long = nativeCreate(system).also {
        check(it != 0L) { nativeLastError() }
    }
    val width: Int get() = nativeWidth(requireOpen())
    val height: Int get() = nativeHeight(requireOpen())
    val fps: Double get() = nativeFps(requireOpen())
    val audioRate: Int get() = nativeAudioRate(requireOpen())

    fun load(rom: ByteArray, battery: ByteArray? = null) {
        check(nativeLoad(requireOpen(), rom, battery)) { nativeLastError() }
    }

    /** Returns packed ARGB8888 pixels. The caller schedules the frame clock. */
    fun frame(): IntArray {
        val pixels = IntArray(width * height)
        check(nativeFrame(requireOpen(), pixels)) { nativeLastError() }
        return pixels
    }

    fun readAudio(maxSamples: Int = 4096): ShortArray {
        require(maxSamples >= 0)
        val buffer = ShortArray(maxSamples)
        val count = nativeReadAudio(requireOpen(), buffer)
        return buffer.copyOf(count)
    }

    fun button(button: Int, pressed: Boolean) {
        check(nativeButton(requireOpen(), button, pressed)) { nativeLastError() }
    }

    fun reset() {
        check(nativeReset(requireOpen())) { nativeLastError() }
    }

    fun saveState(): ByteArray {
        val data = ByteArray(nativeStateSize(requireOpen()))
        check(data.isNotEmpty() && nativeSaveState(requireOpen(), data)) { nativeLastError() }
        return data
    }

    fun loadState(data: ByteArray) {
        check(nativeLoadState(requireOpen(), data)) { nativeLastError() }
    }

    fun batterySnapshot(): ByteArray {
        val size = nativeBatterySize(requireOpen())
        if (size == 0) return ByteArray(0)
        return ByteArray(size).also {
            check(nativeCopyBattery(requireOpen(), it)) { nativeLastError() }
        }
    }

    private fun requireOpen(): Long = pointer.also {
        check(it != 0L) { "Native emulator is closed" }
    }

    override fun close() {
        if (pointer != 0L) {
            nativeDestroy(pointer)
            pointer = 0L
        }
    }

    private external fun nativeCreate(system: Int): Long
    private external fun nativeDestroy(pointer: Long)
    private external fun nativeLoad(pointer: Long, rom: ByteArray, battery: ByteArray?): Boolean
    private external fun nativeWidth(pointer: Long): Int
    private external fun nativeHeight(pointer: Long): Int
    private external fun nativeFps(pointer: Long): Double
    private external fun nativeAudioRate(pointer: Long): Int
    private external fun nativeFrame(pointer: Long, output: IntArray): Boolean
    private external fun nativeReadAudio(pointer: Long, output: ShortArray): Int
    private external fun nativeButton(pointer: Long, button: Int, pressed: Boolean): Boolean
    private external fun nativeReset(pointer: Long): Boolean
    private external fun nativeStateSize(pointer: Long): Int
    private external fun nativeSaveState(pointer: Long, output: ByteArray): Boolean
    private external fun nativeLoadState(pointer: Long, input: ByteArray): Boolean
    private external fun nativeBatterySize(pointer: Long): Int
    private external fun nativeCopyBattery(pointer: Long, output: ByteArray): Boolean
    private external fun nativeLastError(): String

    companion object {
        const val GB = 0
        const val GBC = 1
        const val GBA = 2
        init { System.loadLibrary("ravenemu_v3_android") }
    }
}
