plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The version comes from the release, not from this file.
 *
 * It used to be hardcoded, so every build called itself 1.0 no matter which tag published
 * it -- the installed app kept offering an update to a version it was already running.
 * CI passes -PappVersion=<tag>; a local build without it is 0.0.0, which is honestly older
 * than any release.
 */
val appVersion: String = (project.findProperty("appVersion") as String?)?.trim().orEmpty()
    .ifEmpty { "0.0.0" }

fun versionCodeOf(version: String): Int {
    val parts = version.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}

android {
    namespace = "ru.maxalert.notifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.maxalert.notifier"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    signingConfigs {
        // A fixed sideload key, committed on purpose: without it every CI build would be
        // signed by a freshly generated debug key and Android would refuse to install the
        // update over the previous version. It is a debug key with the well-known password,
        // not a secret -- it proves nothing about who built the APK.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.msgpack:msgpack-core:0.9.8")
    implementation("com.github.luben:zstd-jni:1.5.6-9@aar")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
