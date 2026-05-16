cmake_minimum_required(VERSION 3.22.1)

project("omr_native")

# 1. THIẾT LẬP C++ STANDARD VÀ TỐI ƯU HIỆU NĂNG
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math")

# 2. CẤU HÌNH ĐƯỜNG DẪN OPENCV
set(OpenCV_DIR "C:/Users/ADMIN/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)

# 3. KHAI BÁO THƯ MỤC CHỨA FILE HEADER (.h)
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    "C:/Users/ADMIN/OpenCV-android-sdk/sdk/native/jni/include"
)

# 4. KHAI BÁO THƯ VIỆN NATIVE
add_library(${CMAKE_PROJECT_NAME} SHARED
    # ─── Existing modules ───
    native_bridge.cpp
    distortion_analyzer.cpp
    tps_warper.cpp

    # ─── NEW: OMR Processing Pipeline ───
    layout_calculator.cpp
    marker_detector.cpp
    omr_processor.cpp
)

# 5. LIÊN KẾT THƯ VIỆN
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${OpenCV_LIBS}
    android
    log
    jnigraphics  # Required for AndroidBitmap_* functions
)