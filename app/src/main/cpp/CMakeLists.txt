# Builds the ARM64 JNI runtime and pinned third-party inference engine.
# 构建 ARM64 JNI 运行时与固定版本的第三方推理引擎。
cmake_minimum_required(VERSION 3.22.1)

project(pokedex_inference LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Debug APKs are used for on-device profiling, but an unoptimized 548M-parameter
# vision encoder cannot complete interactively. Keep symbols while compiling the
# native inference graph with production optimization.
add_compile_options("$<$<CONFIG:Debug>:-O3>")

# The Android target intentionally builds the CPU backend only.  The source tree is
# pinned in third_party/llama.cpp so the mtmd ABI and MiniCPM-V support stay stable.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/llama.cpp)

add_library(pokedex_inference SHARED pokedex_inference.cpp)

target_include_directories(pokedex_inference PRIVATE
    third_party/llama.cpp/include
    third_party/llama.cpp/ggml/include
    third_party/llama.cpp/common
    third_party/llama.cpp/tools/mtmd
)

target_compile_features(pokedex_inference PRIVATE cxx_std_17)
target_compile_definitions(pokedex_inference PRIVATE ANDROID_NDK)
target_link_libraries(pokedex_inference PRIVATE
    llama-common
    mtmd
    log
    android
)
