plugins {
    id("com.android.application")
}

val signingFile=System.getenv("BYD_SIGNING_KEYSTORE")
    ?: throw GradleException("Missing stable BYD signing keystore")
val signingPassword=System.getenv("BYD_SIGNING_PASSWORD")
    ?: throw GradleException("Missing stable BYD signing password")

android {
    namespace = "com.pcopi.gpacknavcompanion"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pcopi.gpacknavcompanion"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.1.6"
    }
    signingConfigs {
        create("bydPermanent"){
            storeFile = file(signingFile)
            storePassword = signingPassword
            keyAlias = "byd-phone-space"
            keyPassword = signingPassword
            storeType = "pkcs12"
        }
    }
    buildTypes {
        getByName("debug"){signingConfig=signingConfigs.getByName("bydPermanent")}
        getByName("release"){
            isMinifyEnabled = false
            signingConfig=signingConfigs.getByName("bydPermanent")
        }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
}
dependencies {}
