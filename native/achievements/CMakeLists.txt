include(FetchContent)
# Official SDK v12.5.0, immutable revision. No emulator implementation is imported.
FetchContent_Declare(rcheevos
    GIT_REPOSITORY https://github.com/RetroAchievements/rcheevos.git
    GIT_TAG 1433173220a7eaede6a9ed7a18e94117be1821e0)
FetchContent_MakeAvailable(rcheevos)
file(GLOB RC_SOURCES CONFIGURE_DEPENDS
    "${rcheevos_SOURCE_DIR}/src/*.c"
    "${rcheevos_SOURCE_DIR}/src/rcheevos/*.c"
    "${rcheevos_SOURCE_DIR}/src/rapi/*.c"
    "${rcheevos_SOURCE_DIR}/src/rhash/*.c")
list(FILTER RC_SOURCES EXCLUDE REGEX "/rc_(libretro|client_external)\\.c$")
add_library(ravenemu_rcheevos STATIC ${RC_SOURCES})
set_target_properties(ravenemu_rcheevos PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(ravenemu_rcheevos PUBLIC "${rcheevos_SOURCE_DIR}/include")
target_compile_definitions(ravenemu_rcheevos PUBLIC RC_DISABLE_LUA RC_CLIENT_SUPPORTS_HASH)

option(RAVENEMU_BUILD_ACHIEVEMENT_TESTS "Test the RetroAchievements adapter with synthetic data" OFF)
if(RAVENEMU_BUILD_ACHIEVEMENT_TESTS)
    add_executable(ravenemu_achievement_tests tests/achievements_test.cpp)
    target_include_directories(ravenemu_achievement_tests PRIVATE ../../cores/common/tests/support)
    target_link_libraries(ravenemu_achievement_tests PRIVATE ravenemu_rcheevos ravenemu_cores)
endif()
