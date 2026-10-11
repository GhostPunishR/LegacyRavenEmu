#include <jni.h>
#include <rc_client.h>
#include <rc_error.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
struct Pending { rc_client_server_callback_t callback; void* data; };
struct Client {
    rc_client_t* rc{};
    JNIEnv* env{};
    jobject memory{};
    jmethodID read{};
    int next_id{};
    std::unordered_map<int, Pending> pending;
    std::vector<std::string> requests, events;
    ~Client() { if (rc) rc_client_destroy(rc); }
    void event(const char* kind, const char* title, const char* detail = "", const char* extra = "") {
        for (const char* s : {kind, title, detail, extra}) events.emplace_back(s ? s : "");
    }
};
Client& client(jlong h) { return *reinterpret_cast<Client*>(static_cast<std::uintptr_t>(h)); }
Client& client(rc_client_t* rc) { return *static_cast<Client*>(rc_client_get_userdata(rc)); }
struct MemoryScope {
    Client& c;
    MemoryScope(Client& c_, JNIEnv* env, jobject memory) : c(c_) {
        c.env = env; c.memory = memory;
        if (memory) {
            auto type = env->GetObjectClass(memory);
            c.read = env->GetMethodID(type, "read", "(II)[B");
            env->DeleteLocalRef(type);
        }
    }
    ~MemoryScope() { c.env = nullptr; c.memory = nullptr; c.read = nullptr; }
};
uint32_t read_memory(uint32_t address, uint8_t* buffer, uint32_t count, rc_client_t* rc) {
    auto& c = client(rc);
    if (!c.memory || !c.read || count > 0x58000) return 0;
    auto bytes = static_cast<jbyteArray>(c.env->CallObjectMethod(c.memory, c.read, static_cast<jint>(address), static_cast<jint>(count)));
    if (c.env->ExceptionCheck()) { c.env->ExceptionClear(); return 0; }
    if (!bytes) return 0;
    auto size = std::min(count, static_cast<uint32_t>(c.env->GetArrayLength(bytes)));
    c.env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(size), reinterpret_cast<jbyte*>(buffer));
    c.env->DeleteLocalRef(bytes);
    return size;
}
void server_call(const rc_api_request_t* request, rc_client_server_callback_t callback, void* data, rc_client_t* rc) {
    auto& c = client(rc);
    const int id = ++c.next_id;
    c.pending.emplace(id, Pending{callback, data});
    c.requests.push_back(std::to_string(id));
    for (const char* s : {request->url, request->post_data, request->content_type}) c.requests.emplace_back(s ? s : "");
}
void logged_in(int result, const char*, rc_client_t* rc, void*) {
    auto& c = client(rc);
    if (result == RC_OK) {
        const auto* user = rc_client_get_user_info(rc);
        c.event("login", user->username, user->display_name, user->token);
    } else c.event("login_error", "Connexion RetroAchievements impossible. Vérifiez vos identifiants et le réseau.");
}
void loaded(int result, const char*, rc_client_t* rc, void*) {
    auto& c = client(rc);
    const auto* game = rc_client_get_game_info(rc);
    if (result == RC_OK && game) c.event("game", game->title, "Succès en mode normal (Softcore)");
    else c.event("game_error", "Succès indisponibles pour cette ROM, ou connexion impossible.");
}
void event_handler(const rc_client_event_t* event, rc_client_t* rc) {
    auto& c = client(rc);
    if (event->type == RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED && event->achievement) {
        const auto& a = *event->achievement;
        const auto points = std::to_string(a.points);
        c.event("achievement", a.title, a.description, points.c_str());
    } else if (event->type == RC_CLIENT_EVENT_DISCONNECTED) c.event("notice", "RetroAchievements hors ligne", "Les succès en attente seront renvoyés pendant cette session.");
    else if (event->type == RC_CLIENT_EVENT_RECONNECTED) c.event("notice", "RetroAchievements reconnecté");
    else if (event->type == RC_CLIENT_EVENT_SERVER_ERROR) c.event("notice", "Erreur RetroAchievements", "Un succès peut ne pas avoir été enregistré sur le serveur.");
    else if (event->type == RC_CLIENT_EVENT_GAME_COMPLETED) c.event("notice", "Tous les succès obtenus !");
}
// Java uses UTF-16, the SDK uses standard UTF-8 (not JNI modified UTF-8).
std::string utf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    auto type = env->FindClass("java/lang/String");
    auto method = env->GetMethodID(type, "getBytes", "(Ljava/lang/String;)[B");
    auto encoding = env->NewStringUTF("UTF-8");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(value, method, encoding));
    env->DeleteLocalRef(encoding); env->DeleteLocalRef(type);
    if (!bytes) return {};
    std::string text(static_cast<std::size_t>(env->GetArrayLength(bytes)), '\0');
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<jbyte*>(text.data()));
    env->DeleteLocalRef(bytes); return text;
}
jstring java_string(JNIEnv* env, const std::string& text) {
    auto bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte*>(text.data()));
    auto type = env->FindClass("java/lang/String");
    auto constructor = env->GetMethodID(type, "<init>", "([BLjava/lang/String;)V");
    auto encoding = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(type, constructor, bytes, encoding));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(type); env->DeleteLocalRef(encoding);
    return result;
}
jobjectArray drain(JNIEnv* env, std::vector<std::string>& records) {
    auto type = env->FindClass("java/lang/String");
    auto array = env->NewObjectArray(static_cast<jsize>(records.size()), type, nullptr);
    env->DeleteLocalRef(type);
    if (!array) return nullptr;
    for (std::size_t i = 0; i < records.size(); ++i) {
        auto value = java_string(env, records[i]);
        env->SetObjectArrayElement(array, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    records.clear(); return array;
}
// Never unwind a C++ allocation failure through JNI.
template<typename T, typename F> T guarded(JNIEnv* env, T fallback, F f) {
    try { return f(); } catch (...) {
        auto type = env->FindClass("java/lang/IllegalStateException");
        if (type) env->ThrowNew(type, "RetroAchievements native failure");
        return fallback;
    }
}
}
#define JNI_RA(name) Java_com_ravenemu_nativebridge_NativeAchievementsBridge_##name
extern "C" {
JNIEXPORT jlong JNICALL JNI_RA(create)(JNIEnv* env, jclass) {
    return guarded<jlong>(env, 0, [] {
        auto c = std::make_unique<Client>();
        c->rc = rc_client_create(read_memory, server_call);
        if (!c->rc) return jlong{0};
        rc_client_set_userdata(c->rc, c.get());
        rc_client_set_host(c->rc, "https://retroachievements.org");
        rc_client_set_hardcore_enabled(c->rc, 0);
        rc_client_set_allow_background_memory_reads(c->rc, 0);
        rc_client_set_event_handler(c->rc, event_handler);
        return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(c.release()));
    });
}
JNIEXPORT void JNICALL JNI_RA(destroy)(JNIEnv*, jclass, jlong h) { delete &client(h); }
JNIEXPORT void JNICALL JNI_RA(login)(JNIEnv* env, jclass, jlong h, jstring user, jstring secret, jboolean token) {
    guarded<int>(env, 0, [&] {
        auto u = utf8(env, user); auto s = utf8(env, secret);
        if (token) rc_client_begin_login_with_token(client(h).rc, u.c_str(), s.c_str(), logged_in, nullptr);
        else rc_client_begin_login_with_password(client(h).rc, u.c_str(), s.c_str(), logged_in, nullptr);
        std::fill(s.begin(), s.end(), '\0');
        return 0;
    });
}
JNIEXPORT void JNICALL JNI_RA(load)(JNIEnv* env, jclass, jlong h, jint console, jbyteArray rom) {
    guarded<int>(env, 0, [&] {
        std::vector<uint8_t> data(static_cast<std::size_t>(env->GetArrayLength(rom)));
        env->GetByteArrayRegion(rom, 0, static_cast<jsize>(data.size()), reinterpret_cast<jbyte*>(data.data()));
        rc_client_begin_identify_and_load_game(client(h).rc, static_cast<uint32_t>(console), nullptr, data.data(), data.size(), loaded, nullptr);
        return 0;
    });
}
JNIEXPORT void JNICALL JNI_RA(tick)(JNIEnv* env, jclass, jlong h, jobject memory, jboolean frame) {
    guarded<int>(env, 0, [&] { auto& c = client(h); MemoryScope scope(c, env, memory); if (frame) rc_client_do_frame(c.rc); else rc_client_idle(c.rc); return 0; });
}
JNIEXPORT void JNICALL JNI_RA(reset)(JNIEnv*, jclass, jlong h) { rc_client_reset(client(h).rc); }
JNIEXPORT jobjectArray JNICALL JNI_RA(requests)(JNIEnv* env, jclass, jlong h) { return guarded<jobjectArray>(env, nullptr, [&] { return drain(env, client(h).requests); }); }
JNIEXPORT jobjectArray JNICALL JNI_RA(events)(JNIEnv* env, jclass, jlong h) { return guarded<jobjectArray>(env, nullptr, [&] { return drain(env, client(h).events); }); }
JNIEXPORT void JNICALL JNI_RA(respond)(JNIEnv* env, jclass, jlong h, jint id, jbyteArray body, jint status, jobject memory) {
    guarded<int>(env, 0, [&] {
        auto& c = client(h); const auto entry = c.pending.find(id); if (entry == c.pending.end()) return 0;
        const auto pending = entry->second; c.pending.erase(entry);
        std::string data(static_cast<std::size_t>(env->GetArrayLength(body)), '\0');
        env->GetByteArrayRegion(body, 0, static_cast<jsize>(data.size()), reinterpret_cast<jbyte*>(data.data()));
        const rc_api_server_response_t response{data.c_str(), data.size(), status};
        MemoryScope scope(c, env, memory); pending.callback(&response, pending.data); return 0;
    });
}
}
