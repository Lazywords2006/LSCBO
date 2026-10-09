cmake_minimum_required(VERSION 3.20)
project(lscbo_cec2017 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(ENABLE_ASAN "Enable AddressSanitizer" OFF)
option(ENABLE_UBSAN "Enable UndefinedBehaviorSanitizer" OFF)

if(ENABLE_ASAN)
    add_compile_options(-fsanitize=address -fno-omit-frame-pointer)
    add_link_options(-fsanitize=address)
endif()

if(ENABLE_UBSAN)
    add_compile_options(-fsanitize=undefined -fno-omit-frame-pointer)
    add_link_options(-fsanitize=undefined)
endif()

enable_testing()

add_library(lscbo_core
    src/protocol.cpp
    src/cec_problem.cpp
    src/runner.cpp
    src/statistics.cpp
    src/algorithms/registry.cpp
    src/algorithms/cbo_lscbo.cpp
    src/algorithms/classic.cpp
    src/algorithms/recent.cpp
    src/official_state.cpp
    third_party/cec2017/cec17_test_func.cpp
)
target_include_directories(lscbo_core PUBLIC include)
target_include_directories(lscbo_core PRIVATE compatibility)
if(NOT MSVC)
    set_source_files_properties(
        third_party/cec2017/cec17_test_func.cpp
        PROPERTIES COMPILE_OPTIONS "-Wno-deprecated-declarations"
    )
endif()

add_executable(lscbo_tests tests/test_main.cpp)
target_include_directories(lscbo_tests PRIVATE include)
target_link_libraries(lscbo_tests PRIVATE lscbo_core)

add_test(NAME lscbo_tests COMMAND lscbo_tests)
set_tests_properties(lscbo_tests PROPERTIES WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})

add_executable(lscbo_cec2017 src/main.cpp)
target_link_libraries(lscbo_cec2017 PRIVATE lscbo_core)
