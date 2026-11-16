plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.indrajith.poth"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.indrajith.poth"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-dev"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // No release key yet: poth is installed from this machine over adb.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
}
