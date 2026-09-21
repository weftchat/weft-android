cmake_minimum_required(VERSION 3.22.1)
project("weft-jni" C)

# Prebuilt Haskell libraries produced by native/build-native.sh (see docs/ARCHITECTURE.md).
# WEFT_NATIVE_DIR is passed by Gradle and points at native/out.
set(NATIVE_LIBS "${WEFT_NATIVE_DIR}/${ANDROID_ABI}")

add_library(simplex SHARED IMPORTED)
set_target_properties(simplex PROPERTIES IMPORTED_LOCATION "${NATIVE_LIBS}/libsimplex.so")

add_library(support SHARED IMPORTED)
set_target_properties(support PROPERTIES IMPORTED_LOCATION "${NATIVE_LIBS}/libsupport.so")

add_library(weft-jni SHARED weft-jni.c)
target_compile_options(weft-jni PRIVATE -g0)

# 16 KB page size (required on current Android / GrapheneOS) and no build-id, for reproducible output.
target_link_options(weft-jni PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--build-id=none")

target_link_libraries(weft-jni simplex support)

add_custom_command(TARGET weft-jni POST_BUILD
    COMMAND ${CMAKE_STRIP} --remove-section=.comment $<TARGET_FILE:weft-jni>
    COMMENT "Stripping .comment section from weft-jni")
