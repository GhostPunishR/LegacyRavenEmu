cmake_minimum_required(VERSION 3.22.1)
project(RavenEmuV3Native LANGUAGES C CXX)

# Lock the emulator engines AND public C interface to a reviewed source commit.
# This is the feature/v3-native-bridge PR in RavenEmu-Cores; update this
# pin when that PR is merged and validated on both host platforms.
include(FetchContent)
set(RAVENEMU_BUILD_LIBRETRO OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(RAVENEMU_BUILD_TESTS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(ravenemu_cores
    GIT_REPOSITORY https://github.com/GhostPunishR/RavenEmu-Cores.git
    GIT_TAG c22e0a79117f70e784974f16e88460ff4161ae17
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(ravenemu_cores)

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
