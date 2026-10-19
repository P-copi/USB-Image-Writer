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
        versionCode = 12
        versionName = "0.2.2"
    }


    // A single permanent signing identity, unlocked only via GitHub Actions secret.
    // Never commit plain .p12 files or the secret to this public repository.
    val signingPass = System.getenv("USB_SIGNING_PASSPHRASE")
    val keyFilePath = System.getenv("USB_KEYSTORE_PATH")
    if (!signingPass.isNullOrBlank() && !keyFilePath.isNullOrBlank()) {
        signingConfigs {
            create("stableRelease") {
                storeFile = file(keyFilePath)
                storePassword = signingPass
                keyAlias = "usb-image-writer"
                keyPassword = signingPass
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            if (!signingPass.isNullOrBlank() && !keyFilePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
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
    // - bounded low-level USB transfer timeout
    // - Bulk-Only recovery after transient transfer errors
    // - ByteBuffer position/limit reset before low-level retries
    // v0.1.8 keeps one USB interface/session claimed through Write + Verify and
    // retries the same block without releasing the interface during recovery.
    implementation(files("libs/libaums-patched.aar"))
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.core:core-ktx:1.9.0")
}
