# Consumer proguard rules — applied to apps that depend on epub-reader
-keep class com.kryptow.epub.reader.EpubReader { *; }
-keep class com.kryptow.epub.reader.EpubReaderActivity { *; }
-keep class com.kryptow.epub.reader.bookreader.domain.model.** { *; }
