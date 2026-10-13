package com.ravenemu.v3.android

/**
 * V2's official rcheevos JNI adapter, linked into the V3 native library.
 * One client handle belongs to exactly one worker thread.
 */
internal object NativeAchievementsBridge {
    fun interface Memory {
        fun read(address: Int, count: Int): ByteArray
    }

    init { System.loadLibrary("ravenemu_v3_android") }

    external fun create(): Long
    external fun destroy(handle: Long)
    external fun login(handle: Long, username: String, secret: String, token: Boolean)
    external fun load(handle: Long, console: Int, rom: ByteArray)
    external fun tick(handle: Long, memory: Memory?, frame: Boolean)
    external fun reset(handle: Long)
    external fun requests(handle: Long): Array<String>
    external fun respond(
            handle: Long, id: Int, body: ByteArray, status: Int, memory: Memory?,
        )
    external fun events(handle: Long): Array<String>
    external fun summary(handle: Long): Array<String>
    external fun achievements(handle: Long): Array<String>
}
