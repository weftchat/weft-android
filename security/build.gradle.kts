plugins {
    alias(libs.plugins.android.library)
}

// PIN → database key (Android Keystore), failed-attempt delays. No UI, no dependencies.
android {
    namespace = "app.weft.security"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}
