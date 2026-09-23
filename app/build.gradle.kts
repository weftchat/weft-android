import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing key lives outside the repo. Point WEFT_KEYSTORE_PROPERTIES at a file with
// storeFile / storePassword / keyAlias / keyPassword (CI writes it from secrets).
val keystoreProps: Properties? = (System.getenv("WEFT_KEYSTORE_PROPERTIES")
    ?: "${System.getProperty("user.home")}/weft-signing/keystore.properties")
    .let { file(it) }
    .takeIf { it.exists() }
    ?.let { f -> Properties().also { p -> f.inputStream().use(p::load) } }

android {
    namespace = "app.weft"            // provisional, confirm before the first release
    compileSdk = 37

    defaultConfig {
        applicationId = "app.weft"    // provisional, cannot change after publishing
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        // Only arm64: GrapheneOS runs on recent Pixels (decision 3).
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = false   // minSdk 26: v2 + v3 is enough
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // Reproducible builds: do not embed the (encrypted) dependency list Google adds to the APK signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":design-system"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.qrcodegen) // QR code of the one-time invitation link (Add contact)
    debugImplementation(libs.compose.ui.tooling.preview)
}
