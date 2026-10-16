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
        versionCode = 4
        versionName = "0.1.3"
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
    // CI builds this AAR from libaums develop, which contains the SCSI Request Sense
    // ByteBuffer overflow fix for: newLimit > capacity: (36 > 18)
    implementation(files("libs/libaums-patched.aar"))
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.9.0")
}
