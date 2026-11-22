import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing credentials come from CI environment variables first, then a local (gitignored)
// keystore.properties file. Read lazily via providers where possible for the configuration cache.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}
fun signingValue(env: String, prop: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("ANCHOR_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("ANCHOR_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANCHOR_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANCHOR_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "dev.junjo.anchor"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.junjo.anchor"
        // Android 12: dynamic colours, exact-alarm permission model and modern widgets are all there.
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "0.5.1"
    }

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            // Signed with the release keystore when configured (CI secrets or local keystore.properties);
            // falls back to debug so anyone cloning the repo can still test assembleRelease locally.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // A signed list of libraries that only Google Play reads. The app is sideloaded, so leave it out.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
