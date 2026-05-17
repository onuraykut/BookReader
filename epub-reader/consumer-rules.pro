# Consumer proguard rules — applied to apps that depend on epub-reader

# ─── EpubReader public API ──────────────────────────────────────────────────
-keep class com.kryptow.epub.reader.EpubReader { *; }
-keep class com.kryptow.epub.reader.EpubReaderActivity { *; }
-keep class com.kryptow.epub.reader.bookreader.domain.model.** { *; }

# ─── PdfBox-Android (PDF arama için kullanılır) ─────────────────────────────
# JPEG 2000 codec opsiyonel — uygulamada gerekmez
-dontwarn com.gemalto.jp2.**
# BouncyCastle (PDF şifreleme/imza) opsiyonel
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.junit.**
-dontwarn javax.xml.**
# PdfBox'ın çekirdek sınıflarını koru
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }

# Room (DAO interface'leri reflection ile çağrılır)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Koin DI (sınıf adlarıyla reflection)
-keep class org.koin.** { *; }
-keep class * implements org.koin.core.module.Module

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
