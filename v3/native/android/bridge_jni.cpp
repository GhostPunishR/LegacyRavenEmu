#include <jni.h>
#include <ravenemu/bridge.h>
#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <limits>

namespace {
ravenemu_v3_bridge* handle(jlong pointer) {
    return reinterpret_cast<ravenemu_v3_bridge*>(static_cast<std::uintptr_t>(pointer));
}
jlong to_long(ravenemu_v3_bridge* pointer) {
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(pointer));
}
}

extern "C" {
JNIEXPORT jlong JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeCreate(JNIEnv*, jobject, jint system) {
    return to_long(ravenemu_v3_create(system));
}
JNIEXPORT void JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeDestroy(JNIEnv*, jobject, jlong pointer) {
    ravenemu_v3_destroy(handle(pointer));
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeLoad(
    JNIEnv* env, jobject, jlong pointer, jbyteArray rom, jbyteArray battery
) {
    if (!rom) return JNI_FALSE;
    jbyte* data = env->GetByteArrayElements(rom, nullptr);
    if (!data) return JNI_FALSE;
    jbyte* saved = battery ? env->GetByteArrayElements(battery, nullptr) : nullptr;
    if (battery && !saved) {
        env->ReleaseByteArrayElements(rom, data, JNI_ABORT);
        return JNI_FALSE;
    }
    const auto ok = ravenemu_v3_load(
        handle(pointer),
        reinterpret_cast<const uint8_t*>(data),
        static_cast<size_t>(env->GetArrayLength(rom)),
        reinterpret_cast<const uint8_t*>(saved),
        battery ? static_cast<size_t>(env->GetArrayLength(battery)) : 0U
    );
    if (saved) env->ReleaseByteArrayElements(battery, saved, JNI_ABORT);
    env->ReleaseByteArrayElements(rom, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeWidth(JNIEnv*, jobject, jlong pointer) {
    return ravenemu_v3_video_width(handle(pointer));
}
JNIEXPORT jint JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeHeight(JNIEnv*, jobject, jlong pointer) {
    return ravenemu_v3_video_height(handle(pointer));
}
JNIEXPORT jdouble JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeFps(JNIEnv*, jobject, jlong pointer) {
    return ravenemu_v3_video_fps(handle(pointer));
}
JNIEXPORT jint JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeAudioRate(JNIEnv*, jobject, jlong pointer) {
    return ravenemu_v3_audio_rate(handle(pointer));
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeFrame(
    JNIEnv* env, jobject, jlong pointer, jintArray output
) {
    if (!output) return JNI_FALSE;
    jint* pixels = env->GetIntArrayElements(output, nullptr);
    if (!pixels) return JNI_FALSE;
    const auto ok = ravenemu_v3_run_frame(
        handle(pointer),
        reinterpret_cast<uint32_t*>(pixels),
        static_cast<size_t>(env->GetArrayLength(output)));
    env->ReleaseIntArrayElements(output, pixels, ok ? 0 : JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeReadAudio(
    JNIEnv* env, jobject, jlong pointer, jshortArray output
) {
    if (!output) return 0;
    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (!samples) return 0;
    const auto count = ravenemu_v3_read_audio(
        handle(pointer),
        reinterpret_cast<int16_t*>(samples),
        static_cast<size_t>(env->GetArrayLength(output)));
    env->ReleaseShortArrayElements(output, samples, 0);
    return static_cast<jint>(count);
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeButton(
    JNIEnv*, jobject, jlong pointer, jint button, jboolean down
) {
    return ravenemu_v3_set_button(handle(pointer), button, down == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeReset(JNIEnv*, jobject, jlong pointer) {
    return ravenemu_v3_reset(handle(pointer)) ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jstring JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeLastError(JNIEnv* env, jobject) {
    return env->NewStringUTF(ravenemu_v3_last_error());
}
JNIEXPORT jint JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeStateSize(JNIEnv*, jobject, jlong pointer) {
    const auto n = ravenemu_v3_state_size(handle(pointer));
    return n <= static_cast<size_t>(std::numeric_limits<jint>::max()) ? static_cast<jint>(n) : 0;
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeSaveState(
    JNIEnv* env, jobject, jlong pointer, jbyteArray output
) {
    if (!output) return JNI_FALSE;
    auto* bytes = env->GetByteArrayElements(output, nullptr);
    if (!bytes) return JNI_FALSE;
    const auto ok = ravenemu_v3_save_state(handle(pointer),
        reinterpret_cast<uint8_t*>(bytes), static_cast<size_t>(env->GetArrayLength(output)));
    env->ReleaseByteArrayElements(output, bytes, ok ? 0 : JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeLoadState(
    JNIEnv* env, jobject, jlong pointer, jbyteArray input
) {
    if (!input) return JNI_FALSE;
    auto* bytes = env->GetByteArrayElements(input, nullptr);
    if (!bytes) return JNI_FALSE;
    const auto ok = ravenemu_v3_load_state(handle(pointer),
        reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(env->GetArrayLength(input)));
    env->ReleaseByteArrayElements(input, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeBatterySize(JNIEnv*, jobject, jlong pointer) {
    const auto n = ravenemu_v3_battery_size(handle(pointer));
    return n <= static_cast<size_t>(std::numeric_limits<jint>::max()) ? static_cast<jint>(n) : 0;
}
JNIEXPORT jboolean JNICALL
Java_com_ravenemu_v3_android_NativeCore_nativeCopyBattery(
    JNIEnv* env, jobject, jlong pointer, jbyteArray output
) {
    if (!output) return JNI_FALSE;
    auto* bytes = env->GetByteArrayElements(output, nullptr);
    if (!bytes) return JNI_FALSE;
    const auto ok = ravenemu_v3_copy_battery(handle(pointer),
        reinterpret_cast<uint8_t*>(bytes), static_cast<size_t>(env->GetArrayLength(output)));
    env->ReleaseByteArrayElements(output, bytes, ok ? 0 : JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}
} // extern C
