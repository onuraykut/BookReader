# BookReader (Lumen Engine)

Modern EPUB & PDF reader engine for Android — packaged as a Kotlin Android library.
Drop in via JitPack and launch a fully featured reading activity in one call.

[![](https://jitpack.io/v/onuraykut/BookReader.svg)](https://jitpack.io/#onuraykut/BookReader)

## Features

- **EPUB 2 & 3 reader** — auto TOC, vertical scroll or page-turn modes
- **PDF reader** — high-res rendering, pinch zoom, in-document text search (PdfBox)
- **Customization** — fonts, sizes, line height, margins, day/night themes
- **Highlights & notes** — 5 colors, TXT export
- **Text-to-speech** — chapter playback, Bluetooth & lock-screen controls
- **In-book search** — full-text across all chapters
- **Built-in dictionary + translation** — tap any word
- **i18n** — TR / EN / ES

## Quick Start

`settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

`app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.onuraykut:BookReader:1.1.0")
}
```

`MyApp.kt`:
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EpubReader.init(this)
    }
}
```

Launch a book:
```kotlin
// By DB id (already in library)
EpubReader.openBook(context, bookId = 42L)

// By file path
EpubReader.openFile(context, "/storage/emulated/0/book.epub")

// By URI (content:// or file://)
EpubReader.openUri(context, uri)
```

## Migrating from a Legacy Reader

If your app is migrating users from a previous reader (e.g. FolioReader) that stored
only a flat `pageNumber` per book, pass it to `openFile` on the first open so the
reader seeks to an approximate position:

```kotlin
EpubReader.openFile(
    context,
    filePath,
    legacyPageNumber = legacyPosition.pageNumber,   // > 0 to enable
)
```

Behavior:

- **Page-turn mode**: `legacyPageNumber` is used as the chapter-relative page index
  in chapter 0 (no math).
- **Vertical scroll mode**: collapsed into a pixel Y offset inside chapter 0 using
  `ESTIMATED_PIXELS_PER_LEGACY_PAGE = 1500` (≈ one screen of body text at 1080px
  wide). This is a heuristic — the user lands near where they left off and a small
  scroll resolves the rest. Tune the constant in
  `bookreader/ui/screen/reader/InitialPosition.kt` if you need different defaults.
- **Saved progress always wins**: if the user has already read this book in the new
  reader (i.e. `BookEntity.currentChapter > 0` or `currentScrollOffset > 0`), the
  legacy value is ignored and the saved position is restored.

The same applies to `openUri` and to direct `Intent` extras (see
`EpubReaderActivity.EXTRA_LEGACY_PAGE_NUMBER`).

### Full signature

```kotlin
@JvmStatic
@JvmOverloads
fun openFile(
    context: Context,
    filePath: String,
    initialChapter: Int = 0,
    initialScrollOffset: Int = 0,
    legacyPageNumber: Int = -1,
)
```

`@JvmOverloads` means Java callers see overloads for every parameter combination —
the 2-arg `openFile(context, filePath)` keeps working unchanged.

## Permissions

The library declares:

- `READ_EXTERNAL_STORAGE` (≤ API 32) — for `file://` paths
- `INTERNET` — dictionary & translation only (optional; reader works offline)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — for TTS
  notification playback

Add these (or rely on manifest merging) in your consumer app.

## Android Auto / AAOS

The library ships with an experimental `CarAppService` for Android Auto media
browsing. To enable in your consumer app, declare:

```xml
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />
```

Omit (or `tools:node="remove"` the `BookReaderCarAppService`) to ship a phone-only
build.

## License

See [LICENSE](LICENSE).

---

Built with ❤️ by [Kryptow](https://github.com/onuraykut).
