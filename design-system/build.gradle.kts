plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.weft.design"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    implementation(libs.activity.compose) // BackHandler: system back closes sheets
    debugApi(libs.compose.ui.tooling.preview)
}
