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
        versionCode = 8
        versionName = "0.1.7"
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
    // - short bounded low-level USB transfer timeout
    // - Bulk-Only recovery after transient transfer errors
    // - ByteBuffer position/limit reset before low-level retries
    // App-level recovery closes/reopens the USB session and retries the same block.
    implementation(files("libs/libaums-patched.aar"))
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.9.0")
}
