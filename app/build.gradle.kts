plugins {
    id("com.android.application")
}

// Fail closed when the permanent signing identity is missing.
// Ephemeral Android debug keys prevent installing updates over older builds.
val bydKeystorePath = System.getenv("BYD_SIGNING_KEYSTORE")
    ?: throw GradleException("BYD_SIGNING_KEYSTORE is missing. Configure stable GitHub Actions signing secrets.")
val bydSigningPassword = System.getenv("BYD_SIGNING_PASSWORD")
    ?: throw GradleException("BYD_SIGNING_PASSWORD is missing. Configure stable GitHub Actions signing secrets.")

android {
    namespace = "com.pcopi.bydphonespace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pcopi.bydphonespace"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.6.1"
    }

    signingConfigs {
        create("bydPermanent") {
            storeFile = file(bydKeystorePath)
            storePassword = bydSigningPassword
            keyAlias = "byd-phone-space"
            keyPassword = bydSigningPassword
            storeType = "pkcs12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("bydPermanent")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("bydPermanent")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
}
