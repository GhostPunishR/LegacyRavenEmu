#include <rc_client.h>
#include <rc_error.h>
#include <ravenemu/core.hpp>
#include "check.hpp"
#include "synthetic_roms.hpp"
#include <array>
#include <cstring>
#include <string>
#include <vector>
// Exercise the actual JNI adapter lifetime without starting a JVM or HTTP.
#include "../../jni/src/achievements_bridge.cpp"

using ravenemu::testing::check;
namespace {
std::unique_ptr<ravenemu::Core> core;
int awards{}, popups{};
bool logged{}, game_loaded{};
std::vector<std::string> calls;
uint32_t memory(uint32_t address, uint8_t* buffer, uint32_t count, rc_client_t*) {
    return static_cast<uint32_t>(core->read_achievement_memory(address, {buffer, count}));
}
void server(const rc_api_request_t* request, rc_client_server_callback_t callback, void* data, rc_client_t*) {
    const std::string post = request->post_data ? request->post_data : "";
    calls.push_back(post);
    std::string body;
    if (post.find("r=login2&") == 0) body = R"({"Success":true,"User":"Synthetic","Token":"local-test-only","Score":0,"SoftcoreScore":0,"Messages":0})";
    else if (post.find("r=achievementsets&") == 0) body = R"({"Success":true,"GameId":1,"Title":"Synthetic ROM","ConsoleId":4,"ImageIconUrl":"https://media.retroachievements.org/Images/1.png","RichPresencePatch":"","Sets":[{"AchievementSetId":1,"GameId":1,"Title":"Synthetic ROM","Type":"core","ImageIconUrl":"https://media.retroachievements.org/Images/1.png","Achievements":[{"ID":1,"Title":"Read WRAM","Description":"Synthetic test","Flags":3,"Points":5,"MemAddr":"0xHc000=90","Author":"RavenEmu","BadgeName":"00001","Created":1,"Modified":1}],"Leaderboards":[]}]})";
    else if (post.find("r=startsession&") == 0) body = R"({"Success":true,"Unlocks":[],"HardcoreUnlocks":[],"ServerNow":1})";
    else if (post.find("r=awardachievement&") == 0) {
        check(post.find("&h=0") != std::string::npos, "unlock must be softcore");
        ++awards;
        body = R"({"Success":true,"Score":0,"SoftcoreScore":5,"AchievementID":1,"AchievementsRemaining":0})";
    } else if (post.find("r=ping&") == 0) body = R"({"Success":true})";
    else { throw std::runtime_error("Unexpected SDK request: " + post); }
    const rc_api_server_response_t response{body.c_str(), body.size(), 200};
    callback(&response, data);
}
void memory_maps() {
    auto rom = ravenemu::testing::minimal_game_boy_rom();
    rom[0x143] = 0x80; // CGB, write separate physical WRAM banks.
    const std::array<uint8_t, 24> program{
        0x3e,0x11, 0xea,0x00,0xd0, // bank 1
        0x3e,0x02, 0xe0,0x70,      // select bank 2
        0x3e,0x22, 0xea,0x00,0xd0, // bank 2
        0x3e,0x33, 0xea,0x00,0xc0, // fixed bank 0
        0xc3,0x13,0x01,0x00,0x00,
    };
    std::copy(program.begin(), program.end(), rom.begin() + 0x100);
    core = ravenemu::make_game_boy_core(); core->load_rom(rom, {});
    std::vector<int32_t> frame(core->video_spec().pixel_count()); core->run_frame(frame, true);
    const auto before = core->save_state();
    std::array<uint8_t, 4> bytes{};
    check(core->read_achievement_memory(0xd000, bytes) == 4 && bytes[0] == 0x11, "RA bank 1 must not follow SVBK");
    check(core->read_achievement_memory(0x10000, bytes) == 4 && bytes[0] == 0x22, "RA physical bank 2");
    check(core->read_achievement_memory(0xe000, bytes) == 4 && bytes[0] == 0x33, "WRAM echo");
    check(core->read_achievement_memory(0xffffffff, bytes) == 0, "overflow must not wrap");
    check(core->read_achievement_memory(0xff00, bytes) == 0, "unavailable IO must not invent values");
    check(before == core->save_state(), "achievement reads mutated GB state");

    core = ravenemu::make_gba_core(ravenemu::GbaSaveType::flash_64k);
    auto gba = ravenemu::testing::minimal_gba_rom();
    gba[19] = 0x03; // same synthetic program writes IWRAM
    core->load_rom(gba, std::vector<uint8_t>(0x10000, 0xa5));
    frame.resize(core->video_spec().pixel_count()); core->run_frame(frame, true);
    const auto gba_before = core->save_state();
    check(core->read_achievement_memory(0, bytes) == 4 && bytes[0] == 0x5a, "RA GBA IWRAM");
    check(core->read_achievement_memory(0x48000, bytes) == 4 && bytes[0] == 0xa5, "RA raw flash save bytes");
    check(core->read_achievement_memory(0x57ffe, bytes) == 2, "RA GBA range boundary");
    check(gba_before == core->save_state(), "achievement reads mutated GBA/Flash protocol");
}
void sdk_unlock() {
    auto rom = ravenemu::testing::minimal_game_boy_rom();
    const std::array<uint8_t,8> code{0x3e,0x5a,0xea,0x00,0xc0,0xc3,0x05,0x01};
    std::copy(code.begin(),code.end(),rom.begin()+0x100);
    core = ravenemu::make_game_boy_core(); core->load_rom(rom, {});
    auto* client = rc_client_create(memory, server);
    rc_client_set_hardcore_enabled(client, 0);
    rc_client_set_allow_background_memory_reads(client, 0);
    rc_client_set_event_handler(client, [](const rc_client_event_t* event, rc_client_t*) {
        if (event->type == RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED) ++popups;
    });
    rc_client_begin_login_with_password(client,"Synthetic","synthetic-password",[](int result,const char*,rc_client_t*,void*) {
        check(result == RC_OK,"synthetic login failed"); logged=true;
    }, nullptr);
    check(logged,"login callback missing");
    rc_client_begin_identify_and_load_game(client,4,nullptr,rom.data(),rom.size(),[](int result,const char*,rc_client_t*,void*) {
        check(result == RC_OK,"synthetic game failed"); game_loaded=true;
    },nullptr);
    rc_client_do_frame(client);
    check(game_loaded,"game callback missing");
    rc_client_do_frame(client); // condition false first, arm trigger
    check(awards == 0 && popups == 0,"premature unlock");
    std::vector<int32_t> frame(core->video_spec().pixel_count()); core->run_frame(frame,true);
    rc_client_do_frame(client);
    check(awards == 1 && popups == 1,"memory change must emit one award and one popup");
    rc_client_reset(client); rc_client_do_frame(client); rc_client_idle(client);
    check(awards == 1 && popups == 1,"reset must not duplicate unlocked achievements");
    rc_client_destroy(client);
}
void adapter_shutdown() {
    // Close at each asynchronous stage, including an award scheduled for retry.
    for (int stage = 0; stage < 5; ++stage) {
        auto rom = ravenemu::testing::minimal_game_boy_rom();
        const std::array<uint8_t,8> code{0x3e,0x5a,0xea,0x00,0xc0,0xc3,0x05,0x01};
        std::copy(code.begin(), code.end(), rom.begin()+0x100);
        core = ravenemu::make_game_boy_core(); core->load_rom(rom, {});
        Client adapter;
        adapter.rc = rc_client_create(memory, server_call);
        rc_client_set_userdata(adapter.rc, &adapter);
        rc_client_set_get_time_millisecs_function(adapter.rc, time_millis);
        rc_client_set_hardcore_enabled(adapter.rc, 0);
        rc_client_set_allow_background_memory_reads(adapter.rc, 0);
        rc_client_set_event_handler(adapter.rc, event_handler);
        auto answer = [&] {
            auto records = std::move(adapter.requests); adapter.requests.clear();
            for (std::size_t i=0; i<records.size(); i+=4) {
                auto id = std::stoi(records[i]);
                const auto pending = adapter.pending.at(id); adapter.pending.erase(id);
                rc_api_request_t request{};
                request.url=records[i+1].c_str(); request.post_data=records[i+2].c_str();
                server(&request,pending.callback,pending.data,adapter.rc);
            }
        };
        rc_client_begin_login_with_token(adapter.rc,"Synthetic","local-test-only",logged_in,nullptr);
        if (stage == 0) continue; // pending login
        answer();
        rc_client_begin_identify_and_load_game(adapter.rc,4,nullptr,rom.data(),rom.size(),loaded,nullptr);
        if (stage == 1) continue; // pending game metadata
        answer();
        if (stage == 2) continue; // pending session start
        answer(); rc_client_do_frame(adapter.rc); rc_client_do_frame(adapter.rc);
        std::vector<int32_t> frame(core->video_spec().pixel_count()); core->run_frame(frame,true);
        rc_client_do_frame(adapter.rc);
        check(!adapter.pending.empty(), "adapter must have a pending award");
        if (stage == 3) continue; // pending award
        const rc_api_server_response_t unavailable{"",0,-2};
        for (int attempt=0; attempt<2; ++attempt) {
            const auto entry=adapter.pending.begin(); const auto pending=entry->second;
            adapter.pending.erase(entry); pending.callback(&unavailable,pending.data);
        }
        check(adapter.pending.empty(), "failed award should be scheduled for retry");
        // Destructor advances the SDK timer and settles the retry locally.
    }
}

}
int main() { memory_maps(); sdk_unlock(); adapter_shutdown(); }
