# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#加载动态库配置 add_library
set(DIR ${CMAKE_SOURCE_DIR}/../jni/${ANDROID_ABI})
#set 设置变量 变量的使用 ${DIR}
set(INCLUDE_DIR ${DIR}/../include)
#头文件的加载
include_directories(${INCLUDE_DIR} ${INCLUDE_DIR}/ffmpeg ${INCLUDE_DIR}/libyuv ${INCLUDE_DIR}/x264 ${INCLUDE_DIR}/librtmp ${INCLUDE_DIR}/faac)

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
        m_player_v1.c
            m_player.c
             queue0.c
             native-lib.cpp )

add_library(avcodec-57 SHARED IMPORTED)
set_target_properties(avcodec-57
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libavcodec-57.so)

add_library(avdevice-57 SHARED IMPORTED)
set_target_properties(avdevice-57
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libavdevice-57.so)

add_library(avfilter-6 SHARED IMPORTED)
set_target_properties(avfilter-6
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libavfilter-6.so)

add_library(avformat-57 SHARED IMPORTED)
set_target_properties(avformat-57
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libavformat-57.so)

add_library(avutil-55 SHARED IMPORTED)
set_target_properties(avutil-55
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libavutil-55.so)

add_library(postproc-54 SHARED IMPORTED)
set_target_properties(postproc-54
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libpostproc-54.so)

add_library(swresample-2 SHARED IMPORTED)
set_target_properties(swresample-2
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libswresample-2.so)

add_library(swscale-4 SHARED IMPORTED)
set_target_properties(swscale-4
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libswscale-4.so)


add_library(yuv SHARED IMPORTED)
set_target_properties(yuv
        PROPERTIES IMPORTED_LOCATION
        ${DIR}/libyuv.so)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )
find_library(android-lib android)
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib

                        avcodec-57
                        avdevice-57
                        avfilter-6
                        avformat-57
                        avutil-55
                        postproc-54
                        swresample-2
                        swscale-4

                        yuv
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib}
                       ${android-lib})