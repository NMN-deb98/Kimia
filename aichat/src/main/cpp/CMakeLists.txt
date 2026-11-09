cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# arm64 only (see aichat/build.gradle.kts)
if(DEFINED ANDROID_ABI AND NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif()
set(GGML_SYSTEM_ARCH "ARM")
set(GGML_CPU_KLEIDIAI ON)
set(GGML_OPENMP ON)

# llama.cpp is NOT vendored: CI (and scripts/fetch-llama.sh) checks out a pinned release into <repo>/llama.cpp
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../llama.cpp)
if(NOT EXISTS ${LLAMA_SRC}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_SRC}. Run scripts/fetch-llama.sh first.")
endif()
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED ai_chat.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(${CMAKE_PROJECT_NAME} llama llama-common android log)
