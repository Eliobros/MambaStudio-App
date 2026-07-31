cmake_minimum_required(VERSION 3.4.1)

# Build native-lib as a shared library from the cpp source
add_library(
    native-lib
    SHARED
    native-lib.cpp
)

# Include Node.js headers (relative to this CMakeLists.txt at app/src/main/cpp/)
include_directories(../../../libnode/include/node/)

# Import prebuilt libnode.so
add_library(
    libnode
    SHARED
    IMPORTED
)

# Point to libnode.so in jniLibs (relative to CMAKE_CURRENT_SOURCE_DIR = app/src/main/cpp/)
set_target_properties(
    libnode
    PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/libnode.so
)

# Find Android log library
find_library(
    log-lib
    log
)

# Link native-lib against libnode and log
target_link_libraries(
    native-lib
    libnode
    ${log-lib}
)
