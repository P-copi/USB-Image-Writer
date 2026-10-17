plugins {
    id("com.android.application")
}

android {
    namespace = "com.pcopi.bydphonespace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pcopi.bydphonespace"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.3.1"
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
}

dependencies {
}
