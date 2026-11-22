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
        // The build to read with: a debug build is debuggable, which keeps Android from
        // compiling it ahead and Compose from running at speed; lists and page turns stutter.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No release key yet: poth is installed from this machine over adb, and signed with
            // the debug key it installs over a debug build, keeping the books and dictionary.
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
    implementation(libs.glance.appwidget)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
