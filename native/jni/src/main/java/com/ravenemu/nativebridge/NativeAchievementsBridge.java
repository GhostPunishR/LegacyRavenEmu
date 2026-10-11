package com.ravenemu.nativebridge;

/** All calls for one handle belong to one thread. HTTP workers never call JNI. */
public final class NativeAchievementsBridge {
    static { NativeCoreBridge.ensureLoaded(); }
    private NativeAchievementsBridge() {}
    public interface Memory { byte[] read(int address, int count); }
    public static native String[] summary(long handle);
    public static native String[] achievements(long handle);
    public static native long create();
    public static native void destroy(long handle);
    public static native void login(long handle, String username, String secret, boolean token);
    public static native void load(long handle, int console, byte[] rom);
    public static native void tick(long handle, Memory memory, boolean frame);
    public static native void reset(long handle);
    /** Flattened records: id, URL, POST body, content type. */
    public static native String[] requests(long handle);
    /** Response callbacks execute synchronously, with access to current memory. */
    public static native void respond(long handle, int id, byte[] body, int status, Memory memory);
    /** Flattened records: kind, title, detail, extra (token only on login). */
    public static native String[] events(long handle);
}
