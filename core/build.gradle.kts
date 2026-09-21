plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.weft.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}
