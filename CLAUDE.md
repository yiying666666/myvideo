# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

This is a single-module Android app (`:app`) built with Gradle. Use the wrapper (`gradlew`/`gradlew.bat`), not a system-installed Gradle.

**JDK 17 required to build.** AGP 8.13.2 refuses to run under anything older, but this machine's default `JAVA_HOME` points at JDK 11 (`gradlew` will fail immediately with `Android Gradle plugin requires Java 17`). Override `JAVA_HOME` for every Gradle invocation, e.g. pointing at a JBR bundled with an installed Android Studio:

```bash
JAVA_HOME="/c/Users/<user>/.jdks/jbr-17.0.7" ./gradlew.bat :app:assembleDebug
```

Common tasks:
- Compile only (fast sanity check without packaging): `./gradlew.bat :app:compileDebugKotlin :app:compileDebugJavaWithJavac`
- Full debug build: `./gradlew.bat :app:assembleDebug`
- Unit tests (`app/src/test`): `./gradlew.bat :app:testDebugUnitTest`
- Run a single unit test: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ExampleUnitTest"`
- Instrumented tests (`app/src/androidTest`, need a device/emulator): `./gradlew.bat :app:connectedDebugAndroidTest`

There is no configured lint/format command beyond Android Lint (`./gradlew.bat :app:lintDebug`); no ktlint/detekt/checkstyle is set up.

## Architecture

- **Namespace**: `com.example.myapplication`. **minSdk 24**, **compileSdk/targetSdk 36**, Java 11 bytecode target. Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog) and referenced via `libs.*` in `app/build.gradle`; a few dependencies (`kotlin-stdlib`, `kotlinx-coroutines-android`, `lifecycle-runtime-ktx`) are declared inline with hardcoded versions instead of through the catalog — follow whichever pattern is already used nearby when adding a new one, but prefer the catalog for anything reusable.
- **Mixed Java/Kotlin in one module**: the Kotlin plugin (`org.jetbrains.kotlin.android`) plus `kotlin-parcelize` are applied, but only `MainActivity.java` is plain Java — all other app code is Kotlin. New feature code should generally be Kotlin; `MainActivity` stays Java only because it predates the Kotlin code and calls into it via straightforward Java/Kotlin interop (Kotlin `object`s are accessed as `Foo.INSTANCE` from Java, etc.).
- **`cover/` package** (`app/src/main/java/com/example/myapplication/cover/`) — a JianYing/CapCut-style "pick a cover frame from a video" feature, self-contained and reusable via `CoverSelectionActivity`:
  - `FrameExtractor` wraps `MediaMetadataRetriever` for a given video `Uri`. All calls run on `Dispatchers.IO` and are serialized through a `Mutex`, because a single `MediaMetadataRetriever` instance is not safe to drive concurrently — the filmstrip-thumbnail generation and the live drag-preview extraction both go through the same extractor instance and would otherwise race. Frame extraction intentionally has two precision modes: `OPTION_CLOSEST_SYNC` (nearest keyframe, fast — used while actively dragging) vs `OPTION_CLOSEST` (exact frame, slower — used for the filmstrip thumbnails and to lock in the final selection on release).
  - `CoverSelectionOverlayView` is a custom `View` drawn on top of the thumbnail strip. It deliberately only claims a touch gesture that starts on the selection block itself (with some grab slop); a touch elsewhere is left unconsumed so the ancestor `HorizontalScrollView` handles it as a normal swipe. While the block is actually being dragged it auto-scrolls that same ancestor when dragged near the visible edge (see `autoScrollIfNearEdge`, which walks `parent.parent` to find the `HorizontalScrollView` — the view is written assuming that exact two-level parent chain from `activity_cover_selection.xml`, so don't restructure that layout without updating it).
  - `CoverSelectionActivity` samples one filmstrip thumbnail per second of video (`THUMBNAIL_INTERVAL_US`), sizing the filmstrip container and the overlay explicitly in code (fixed-width `ImageView`s summed up) rather than relying on `wrap_content`/weights, so long videos naturally produce a wider-than-screen scrollable strip.
  - `CoverSelectionContract` (an `ActivityResultContract<Uri, CoverResult?>`) plus the `CoverResult` sealed interface (`VideoFrame(timestampUs)` / `StaticImage(imageUri)`) is the intended integration surface — a caller launches with a video `Uri` and gets back either a chosen timestamp or a static image picked from the album via `ActivityResultContracts.PickVisualMedia`. `ParcelableCompat.kt` holds small `getParcelableExtra`/`getParcelable` helpers that pick the right call depending on API level (33+ vs below) without deprecation warnings.
  - `MainActivity` wires a demo entry point only for manually exercising this flow (pick a video → launch `CoverSelectionActivity` → show the returned result) — it is not part of the feature itself.
- **Dormant/unimplemented scaffold — do not assume it's wired up**: `app/src/main/res/layout/activity_photo_book.xml` references `com.example.myapplication.bookview.PageCurlView` / `PhotoBookActivity`, and related strings (`btn_open_photo_book`, `photo_book_empty_hint`, etc.) and colors (`book_background`, `page_paper*`) exist in `values/`. None of those classes exist and the activity is not registered in `AndroidManifest.xml`. It's an orphaned layout from a never-implemented feature, unrelated to the `cover` package — leave it alone unless specifically asked to build it out.
- **`AndroidManifest.xml`** only registers two activities: `MainActivity` (launcher) and `.cover.CoverSelectionActivity` (portrait-locked, its own always-dark theme `Theme.MyApplication.CoverSelection` defined in `values/themes.xml`, independent of system day/night).
