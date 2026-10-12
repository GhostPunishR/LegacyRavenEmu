cmake_minimum_required(VERSION 3.22.1)
project(RavenEmuV3Native LANGUAGES C CXX)

# Lock the emulator engines AND public C interface to a reviewed source commit.
# RavenEmu-Cores 0.1.0 after PRs #1-#8: hardened state validation,
# native C API additions and optimized save states. Use an immutable commit SHA.
include(FetchContent)
set(RAVENEMU_BUILD_LIBRETRO OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(RAVENEMU_BUILD_TESTS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(ravenemu_cores
    GIT_REPOSITORY https://github.com/GhostPunishR/RavenEmu-Cores.git
    GIT_TAG 9f5d4d99e40054f84b0e74efa04135edd1ef9288
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(ravenemu_cores)

# The installed preview is assembled with Gradle's debug variant. Without
# overriding Debug's -O0, the emulated CPU/PPU loops run unoptimized even on
# high-end Android devices. Keep symbols/debuggability but optimize just the
# native engine targets in the Android preview. Host/iOS SDKs are untouched.
if(ANDROID)
    foreach(core_target IN ITEMS
            ravenemu_common gb_raven_core gbc_raven_core gba_raven_core ravenemu_c_api)
        target_compile_options(${core_target} PRIVATE "$<$<CONFIG:Debug>:-O2>")
    endforeach()
endif()

file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/include")
configure_file("${ravenemu_cores_SOURCE_DIR}/sdk/include/ravenemu/bridge.h"
               "${CMAKE_BINARY_DIR}/include/bridge.h" COPYONLY)
configure_file("${CMAKE_CURRENT_SOURCE_DIR}/apple/module.modulemap"
               "${CMAKE_BINARY_DIR}/include/module.modulemap" COPYONLY)

if(ANDROID)
    add_library(ravenemu_v3_android SHARED android/bridge_jni.cpp)
    target_compile_features(ravenemu_v3_android PRIVATE cxx_std_20)
    target_link_libraries(ravenemu_v3_android PRIVATE ravenemu_c_api)
endif()
