plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kryptow.epub.reader.bookreader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kryptow.lumenreader"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true   // R8 + AAPT ile kullanılmayan resource'ları sil
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = false   // Lumen'in BuildConfig'e ihtiyacı yok — daha küçük dex
    }

    // ─── Paketleme optimizasyonları ─────────────────────────────────────────
    // PdfBox'ın transitive bağımlısı BouncyCastle, post-quantum kripto için
    // ~7.8 MB'lık parametre dosyası taşıyor (PICNIC, SIKE). Lumen PDF
    // imzalamıyor / decrypt etmiyor — bu dosyaları APK'ya almıyoruz.
    packaging {
        resources {
            // BouncyCastle Post-Quantum Crypto parametreleri — kullanılmıyor
            // (PICNIC ~3.6 MB, SIKE ~4.5 MB — sadece bu satır ~8 MB tasarruf sağlar)
            excludes += "org/bouncycastle/pqc/**"
            excludes += "META-INF/services/org.bouncycastle.pqc.*"
            // BouncyCastle X.509 sertifika doğrulama mesajları — kullanılmıyor
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages*.properties"
            // Yaygın gereksiz META-INF dosyaları (lisans / metadata)
            excludes += "META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"   // compile-time only
            excludes += "META-INF/proguard/**"       // tüketici-side ProGuard kuralları
            excludes += "DebugProbesKt.bin"          // coroutines debug — release'de yok
        }
    }
}

dependencies {
    // ─── EPUB Reader kütüphanesi (tüm okuyucu işlevselliği burada) ──────────
    implementation(project(":epub-reader"))

    // Core (navigation + activity compose için)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.icons.extended)

    // Navigation
    implementation(libs.navigation.compose)

    // Koin (navigation compose için)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.navigation)

    // Coil (kütüphane ekranı kapak resimleri için)
    implementation(libs.coil.compose)

    // ViewModel + Compose
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Android Auto desteği (AAOS için :automotive modülü kullanılır)
    implementation(libs.media)

    // Splash Screen (Android 12+ API, eski sürümlere backport)
    implementation(libs.androidx.splashscreen)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
