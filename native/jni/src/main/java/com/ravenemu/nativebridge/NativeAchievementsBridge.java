package com.ravenemu.nativebridge;

/** All calls for one handle belong to one thread. HTTP workers never call JNI. */
public final class NativeAchievementsBridge {
    static { NativeCoreBridge.ensureLoaded(); }
    private NativeAchievementsBridge() {}
    public interface Memory { byte[] read(int address, int count); }
    /** Eleven columns: display name, softcore and hardcore points, game title and id,
     *  unlocked and total achievements, unlocked and total points, account name, avatar URL. */
    public static native String[] summary(long handle);
    /** Nine columns per achievement, the last one the badge URL for its current state. */
    public static native String[] achievements(long handle);
    /** Rich Presence is evaluated by rcheevos on the emulation thread. */
    public static native String richPresence(long handle);
    public static native boolean hardcore(long handle);
    /** Call only for a user-initiated pause attempt on the owner thread. */
    public static native boolean canPause(long handle);
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
    /** Flattened records: kind, title, detail, extra (token only on login), image URL. */
    public static native String[] events(long handle);
}
