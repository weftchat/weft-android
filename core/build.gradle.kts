plugins {
    alias(libs.plugins.android.library)
}

// The Haskell core (libsimplex.so, libsupport.so) is built by native/build-native.sh in CI and dropped
// into native/out/<abi>/. Until it exists the module builds without native code, so the rest of the
// project keeps working. Set WEFT_REQUIRE_NATIVE=1 (release CI) to make a missing library a hard error.
val nativeDir = rootProject.layout.projectDirectory.dir("native/out")
val haveNativeLibs = nativeDir.file("arm64-v8a/libsimplex.so").asFile.exists()
if (System.getenv("WEFT_REQUIRE_NATIVE") == "1" && !haveNativeLibs) {
    throw GradleException("native/out/arm64-v8a/libsimplex.so is missing: run native/build-native.sh or download the CI artifact")
}

android {
    namespace = "app.weft.core"
    compileSdk = 37
    ndkVersion = "28.2.13676358"   // pinned for reproducible native builds
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
    }
    if (haveNativeLibs) {
        sourceSets {
            getByName("main") { jniLibs.srcDir(nativeDir.asFile.absolutePath) }
        }
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
        defaultConfig {
            externalNativeBuild {
                cmake { arguments += "-DWEFT_NATIVE_DIR=${nativeDir.asFile.absolutePath}" }
            }
        }
    }
}
