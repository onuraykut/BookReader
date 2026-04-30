plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kryptow.epub.reader.bookreader.automotive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kryptow.epub.reader.bookreader.automotive"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":epub-reader"))

    // AAOS'a özgü Car App Library uzantısı
    implementation(libs.car.app.automotive)

    implementation(libs.androidx.core.ktx)
}
