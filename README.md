# mrowser

A lightweight Android TV browser optimized for streaming video. It browses any
site from a remote and hands its HLS video off to a native Media3/ExoPlayer
player for tight audio/video sync that the WebView's built-in player cannot
deliver.

## Status

Working. A home screen with favorites, a D-pad mouse cursor with fullscreen
video, and automatic handoff of HLS streams to a native ExoPlayer. Design docs
and plans live in `docs/superpowers/`.

## Features

- Home screen with a favorites grid (add the current page with the ★ button)
- D-pad mouse cursor for sites a remote can't navigate; MENU opens the address bar
- Fullscreen HTML5 video
- Automatic handoff of the page's HLS stream to a native Media3/ExoPlayer for
  correct A/V sync — with in-player quality, audio-track, and subtitle selection
- Netflix-style dark UI; sideload-only, no Google Play Services

## Requirements

- JDK 17
- Android SDK with `platforms;android-36` and `build-tools;36.0.0`

Toolchain: AGP 9.1.1 (built-in Kotlin) · Gradle 9.3.1 · `minSdk 23` /
`targetSdk 34` / `compileSdk 36`. No Google Play Services dependency.

## Build

```bash
./gradlew assembleDebug      # debug APK   -> app/build/outputs/apk/debug/
./gradlew assembleRelease    # signed APK  -> app/build/outputs/apk/release/  (needs keystore.properties)
./gradlew test               # unit tests
```

## Install

Copy the APK to the device (USB or a file manager) and install it. Enable
"install from unknown sources" if prompted. No ADB required.

## Release signing

Release builds read `keystore.properties` (gitignored). If it is absent, the
release build is produced unsigned. CI builds a signed APK on GitHub Releases
using the repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`,
and `KEY_PASSWORD`.

## Disclaimer

mrowser is a general-purpose web browser. It ships with no bundled content and
no preset sites. You are responsible for the content you choose to access and
for complying with applicable law.

## License

MIT — see [LICENSE](LICENSE).
