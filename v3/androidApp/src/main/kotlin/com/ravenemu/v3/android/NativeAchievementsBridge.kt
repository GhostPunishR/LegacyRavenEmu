package com.ravenemu.v3.android

/**
 * V2's official rcheevos JNI adapter, linked into the V3 native library.
 * One client handle belongs to exactly one worker thread.
 */
internal class NativeAchievementsBridge private constructor() {
    fun interface Memory {
        fun read(address: Int, count: Int): ByteArray
    }

    companion object {
        init { System.loadLibrary("ravenemu_v3_android") }

        @JvmStatic external fun create(): Long
        @JvmStatic external fun destroy(handle: Long)
        @JvmStatic external fun login(handle: Long, username: String, secret: String, token: Boolean)
        @JvmStatic external fun load(handle: Long, console: Int, rom: ByteArray)
        @JvmStatic external fun tick(handle: Long, memory: Memory?, frame: Boolean)
        @JvmStatic external fun reset(handle: Long)
        @JvmStatic external fun requests(handle: Long): Array<String>
        @JvmStatic external fun respond(
            handle: Long, id: Int, body: ByteArray, status: Int, memory: Memory?,
        )
        @JvmStatic external fun events(handle: Long): Array<String>
        @JvmStatic external fun summary(handle: Long): Array<String>
        @JvmStatic external fun achievements(handle: Long): Array<String>
    }
}
