cmake_minimum_required(VERSION 3.28)
project(emuladornds_melonds LANGUAGES C CXX)

include(FetchContent)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(BUILD_QT_SDL OFF CACHE BOOL "" FORCE)
set(ENABLE_OGLRENDERER OFF CACHE BOOL "" FORCE)
set(ENABLE_GDBSTUB OFF CACHE BOOL "" FORCE)
set(ENABLE_LTO OFF CACHE BOOL "" FORCE)
set(ENABLE_LTO_RELEASE OFF CACHE BOOL "" FORCE)
set(USE_CCACHE OFF CACHE BOOL "" FORCE)
set(USE_VCPKG OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    melonDS
    URL https://github.com/melonDS-emu/melonDS/archive/refs/tags/1.0.tar.gz
    URL_HASH SHA256=378e5f6f412ca6f846cce68f8919fa457ba1bb010783aba1442be505cf0dfad4
    EXCLUDE_FROM_ALL
)
FetchContent_MakeAvailable(melonDS)

add_library(
    emuladornds_melonds
    SHARED
    emuladornds_melonds.cpp
    Platform.cpp
)
target_link_libraries(emuladornds_melonds PRIVATE core log dl)
