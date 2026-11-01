# Wallc

A lightweight, root-powered wallpaper manager for Android, built with Jetpack Compose and Miuix.

**Application ID:** `org.eu.suisei.wallc`

## Features

- Set home-screen and lock-screen wallpapers independently.
- Preview selected images and optionally crop them to the display aspect ratio.
- Use English or Simplified Chinese, with Android per-app language preferences on Android 13 and later.
- Follow the system light or dark theme.
- Display adaptive and themed launcher icons using Google Material Symbols.

## Requirements

- Android 9 (API 28) or later.
- A rooted device with a working `su -c` command.
- Root authorization for Wallc in the installed root manager.

Samsung One UI 9 on Android 17 is supported. Wallc does not require Shizuku, a running ADB service, network access, or broad photo-library permissions.

## Usage

1. Download the APK from [Releases](https://github.com/asuharayuuki/Wallc/releases/latest), install it, and open Wallc.
2. Select **Authorize** and allow the request in your root manager.
3. Choose **Home** or **Lock** and select an image.
4. Adjust **Center crop** if required, then select **Apply**.

Each wallpaper target retains its own image selection. On Android 13 and later, the application language can also be changed from the system's per-app language settings.

## Build

| Component | Version |
| --- | --- |
| JDK | 17 or later |
| Android SDK | Platform 37 |
| Android Build Tools | 36.0.0 |
| Android NDK | 28.2.13676358 |
| Gradle | 9.7.1 |
| Android Gradle Plugin | 9.4.1 |
| Compose Compiler | 2.4.21 |
| Miuix | 0.9.4 |

Configure `JAVA_HOME` and `ANDROID_HOME`, then build with the included Gradle wrapper:

```sh
./gradlew :app:assembleRelease
```

On Windows, use `gradlew.bat`. The APK is generated at `app/build/outputs/apk/release/app-release.apk`.

Release builds enable R8 and resource shrinking, and disable application debugging. The repository uses the local Android development signing configuration for installable builds; replace `signingConfig` with a private release key before publishing through a distribution channel.

## Architecture

Root operations run in short-lived worker processes launched through `su`. The native launcher establishes shell credentials before starting the Android runtime. The worker creates an independent application context, validates package attribution, and calls the system `WallpaperManager` with one destination flag per operation.

Images are decoded with orientation handling, bounded to 16 megapixels and an 8192-pixel maximum edge, and encoded as PNG. A length-prefixed stream transfers the complete image before the worker submits it to the wallpaper service. Temporary files are removed when the operation finishes.

The application-scoped controller preserves in-flight operations across activity recreation. Image selections, the active target, and crop preferences survive interface recreation.

## Localization

English resources are defined in `app/src/main/res/values/strings.xml`; Simplified Chinese resources are defined in `app/src/main/res/values-zh/strings.xml`. User-facing text is selected through Android resource resolution. Internal diagnostics, source comments, and project documentation use English.

## Validation

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The test suite covers wallpaper destination validation, image geometry, transfer integrity, command construction, result parsing, and application-context creation. Interface validation covers both supported languages and theme modes. Launcher assets are checked with circular masks in their normal and monochrome forms.

## Third-party assets

Interface components and icons are provided by [Miuix](https://github.com/compose-miuix-ui/miuix). The application icon uses the **Wallpaper** symbol from [Google Material Symbols Rounded](https://fonts.google.com/icons). The original symbol geometry is retained and uniformly scaled within the adaptive icon's foreground layer.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for asset provenance and license details.
