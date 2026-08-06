# Ctify (Android)

A native Android music player, built as a polished, Spotify-styled companion to the
original C `ctify` desktop app. Written in **Kotlin + Jetpack Compose** with
**Media3 / ExoPlayer** for playback.

It ships with bundled demo tracks so it works out of the box on any device or emulator,
and can also scan the device's own local audio (with permission).

## Download

**[⬇ Download the APK](https://github.com/Vifuddyxg/ctify-android/raw/main/release/ctify.apk)** — Android 7.0+

After downloading, open the file and allow "Install from unknown sources" if asked.

## Features

- Spotify-like dark UI (green accent), with Home / Search / Library tabs
- Bundled offline demo library (9 tracks across 3 albums, generated locally)
- Full-screen **Now Playing** with album art, scrub bar, shuffle, repeat,
  previous/next, and volume
- Persistent **mini-player** with progress
- **Favorites** (heart any track) and user **playlists** (create / add / remove / delete)
- Background playback with a system media notification + lock-screen controls
- Resumes the last track and position on reopen
- Optional scan of on-device music via `MediaStore`

## Map to the C original

| C `ctify` | This app |
|-----------|----------|
| SDL window, mpv backend | Compose UI, ExoPlayer backend |
| Home / All Songs / Favorites / Playlists | Home / Search / Library tabs |
| `~/.local/state/ctify` (favorites.txt, playlists, playback_state) | `SharedPreferences` (JSON) |
| `.m3u` playlists | in-app playlists |
| shuffle / repeat / seek / volume | same controls in Now Playing |
| blue accent | Spotify green accent |

## Build

Requires a JDK 17 and the Android SDK (platform 35, build-tools 35).

```sh
# point at your JDK + SDK if not already on PATH
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device/emulator:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in Android Studio and press Run.

## Demo assets

The files in `app/src/main/assets/demo/` (audio + cover gradients) are generated
locally with `ffmpeg` — short ambient chord pads, royalty-free, purely for the demo.
`manifest.json` lists their metadata.
