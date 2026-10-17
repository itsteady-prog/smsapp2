plugins {
    id("com.android.application")
}

android {
    namespace = "com.storicracy.smsgateway"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.storicracy.smsgateway"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
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
    // The only library: a QR reader for the "Scan code to link" button. Version 4.3.0 of the wrapper with ZXing core 3.3.0 supports Android 6 (minSdk 23).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("com.google.zxing:core:3.3.0")
}
