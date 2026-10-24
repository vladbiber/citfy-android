# Ctify (Android)

A native Android music player, built as a polished, Spotify-styled companion to the
original C `ctify` desktop app. Written in **Kotlin + Jetpack Compose** with
**Media3 / ExoPlayer** for playback.

It ships with bundled demo tracks so it works out of the box on any device or emulator,
and can also scan the device's own local audio (with permission).

## Download

**[⬇ Download the APK](https://github.com/Vifuddyxg/citfy-android/releases/latest/download/ctify.apk)** — Android 7.0+

All versions are on the [Releases page](https://github.com/Vifuddyxg/citfy-android/releases).
After downloading, open the file and allow "Install from unknown sources" if asked.

## Features

- Dark UI with a blue accent, Home / Search / Library / Sound tabs
- Five interactive player looks: vinyl (tonearm, scratch to seek), CD, cassette,
  TP-7 style device and plain cover; design inspired by
  [Spun](https://github.com/yappologistic/Spun) by
  [@yappologistic](https://github.com/yappologistic) (independent Compose
  reimplementation, no Spun code or assets)
- Sound effects: slow + pitch, reverb, bass, "magic Hz" tuning, stereo width,
  8D rotation, "another room", one-tap presets
- Real vinyl sound (crackle, wow and flutter, warmth) and calm rain sounds
- Reverse mode for any track
- Local files only: import songs or folders through the system picker
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
| blue accent | blue accent |

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

## Releases and signing

Every push builds a debug APK (Actions artifact). Pushing a tag like `v2.1.3`
builds a release APK signed with one fixed key and attaches it to that tag's
GitHub Release as `ctify.apk`. Local release builds without the key stay
unsigned.

One-time setup for the maintainer:

```sh
keytool -genkeypair -v -keystore ctify-release.jks -alias ctify \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 ctify-release.jks   # paste the output into CTIFY_KEYSTORE_BASE64
```

Add these repository secrets (Settings → Secrets and variables → Actions):

- `CTIFY_KEYSTORE_BASE64` — the base64 of the `.jks` file
- `CTIFY_KEYSTORE_PASSWORD` — the keystore password
- `CTIFY_KEY_ALIAS` — `ctify` (the `-alias` above)
- `CTIFY_KEY_PASSWORD` — the key password (same as the keystore password if
  keytool didn't ask for a separate one)

Back up `ctify-release.jks` and its passwords somewhere safe and never commit
them. If the key is lost, no future build can update installed copies.

Older APKs were signed with whichever machine built them, so moving to the
signed releases needs one uninstall: first use Settings → Save backup, then
uninstall, install the new `ctify.apk` and use Settings → Restore.

## Credits

The player looks (vinyl, CD, cassette and TP-7 style) are inspired by
[Spun](https://github.com/yappologistic/Spun), a CD-shaped music player for
Linux by [@yappologistic](https://github.com/yappologistic). Ctify's versions
are an independent Jetpack Compose reimplementation; no Spun code or assets are
included. Thank you for the beautiful designs.

## Demo assets

The files in `app/src/main/assets/demo/` (audio + cover gradients) are generated
locally with `ffmpeg` — short ambient chord pads, royalty-free, purely for the demo.
`manifest.json` lists their metadata.
