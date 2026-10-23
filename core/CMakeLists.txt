cmake_minimum_required(VERSION 3.28)
project(transpiler_core VERSION 0.1.0 LANGUAGES CXX)

# Language-independent orchestrator: discovers sources, drives translator workers over the
# NDJSON worker protocol, routes decisions to providers. No COBOL or Java specifics live here.

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

include(FetchContent)
# Pinned by URL and SHA-256; downloaded once at configure time.
FetchContent_Declare(nlohmann_json
  URL https://github.com/nlohmann/json/releases/download/v3.12.0/json.tar.xz
  URL_HASH SHA256=42f6e95cad6ec532fd372391373363b62a14af6d771056dbfc86160e6dfff7aa
  DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_MakeAvailable(nlohmann_json)

add_library(transpiler_core_lib
  src/decision.cpp
  src/discovery.cpp
  src/log.cpp
  src/orchestrator.cpp
  src/process.cpp
  src/protocol.cpp
  src/translator_registry.cpp
  src/worker_client.cpp)
target_include_directories(transpiler_core_lib PUBLIC include)
target_link_libraries(transpiler_core_lib PUBLIC nlohmann_json::nlohmann_json)
target_compile_options(transpiler_core_lib PRIVATE -Wall -Wextra -Wpedantic)

add_executable(transpiler-core src/main.cpp)
target_link_libraries(transpiler-core PRIVATE transpiler_core_lib)
target_compile_options(transpiler-core PRIVATE -Wall -Wextra -Wpedantic)

option(TRANSPILER_BUILD_TESTS "Build GoogleTest unit tests" ON)
if(TRANSPILER_BUILD_TESTS)
  enable_testing()
  FetchContent_Declare(googletest
    URL https://github.com/google/googletest/releases/download/v1.17.0/googletest-1.17.0.tar.gz
    URL_HASH SHA256=65fab701d9829d38cb77c14acdc431d2108bfdbf8979e40eb8ae567edf10b27c
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
  set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
  FetchContent_MakeAvailable(googletest)
  add_subdirectory(tests)
endif()
