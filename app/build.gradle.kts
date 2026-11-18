plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.junjo.anchor"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.junjo.anchor"
        // Android 12: dynamic colours, exact-alarm permission model and modern widgets are all there.
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            // The build that goes on the phone. A debug build is deliberately left unoptimized by
            // Android (and Compose is several times slower in it), which costs both smoothness and
            // battery. R8 strips unused code and optimizes the rest; the APK drops from ~35 MB to a
            // few MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded only, so the debug key keeps every build installable over the last one.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
