cmake_minimum_required(VERSION 3.22.1)
project(focuslauncher_llama)

# This app only ever targets arm64 devices (set in app/build.gradle.kts), and we don't need
# llama.cpp's own examples/server/tests/common helpers — our JNI bridge only calls the core
# llama.h API directly, so llama-common isn't even linked.
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(focuslauncher_llama SHARED jni_bridge.cpp)

target_include_directories(focuslauncher_llama PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
)

target_link_libraries(focuslauncher_llama
    llama
    android
    log
)
