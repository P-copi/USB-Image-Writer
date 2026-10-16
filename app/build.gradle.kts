plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pcopi.usbimager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pcopi.usbimager"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // CI builds a patched libaums AAR with:
    // - SCSI Request Sense buffer overflow fix
    // - longer USB transfer timeout
    // - stronger Bulk-Only recovery after transient transfer errors
    implementation(files("libs/libaums-patched.aar"))
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.9.0")
}
