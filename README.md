# Guava Player (Android)

A native Android music player, formerly **ctify**. Written in **Kotlin + Jetpack Compose**
(Material 3 Expressive) with **Media3 / ExoPlayer** for playback. Dark theme only.

## Download

**[⬇ Download the APK](https://github.com/vladbiber/guava-player/releases/latest/download/ctify.apk)** (Android 8.0+)

All versions are on the [Releases page](https://github.com/vladbiber/guava-player/releases).
After downloading, open the file and allow "Install from unknown sources" if asked.

## Features

- Your local music, plus YouTube search, streaming and downloads
- Internet radio from Radio Browser, with live channels picked for you
- Hand-drawn player looks: vinyl (scratch to seek), TP-7, tape, MiniDisc, boombox, car radio
- Sound: EQ, speed and pitch, reverb, bass, "magic Hz" tuning, 8D, vinyl crackle, reverse
- Synced lyrics (LRCLIB), visualizers, sleep timer, spin-the-wheel song picker
- Home shelves from what you play, Last.fm genres, artists and charts, MusicBrainz album info
- ListenBrainz scrobbling, ratings with RateYourMusic CSV import
- Tie-dye backgrounds (spiral, bullseye, crumple, ice dye, chevron) in your theme colors,
  a rainbow or the album cover's colors; a choice of fonts
- Android Auto, media notification and lock-screen controls, keyboard-phone shortcuts

## Build

Requires JDK 17 and the Android SDK (compileSdk 37).

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

## Releases and signing

Every push builds a debug APK (Actions artifact). Pushing a tag like `v3.0.0`
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

- `CTIFY_KEYSTORE_BASE64`: the base64 of the `.jks` file
- `CTIFY_KEYSTORE_PASSWORD`: the keystore password
- `CTIFY_KEY_ALIAS`: `ctify` (the `-alias` above)
- `CTIFY_KEY_PASSWORD`: the key password (same as the keystore password if
  keytool didn't ask for a separate one)

Back up `ctify-release.jks` and its passwords somewhere safe and never commit
them. If the key is lost, no future build can update installed copies.

## Credits

- [Levyra](https://github.com/LUC4N3X/Levyra-deepsound) (GPL-3.0): the Material 3
  Expressive shape style (cookie, clover and sunny shapes); its NewPipe Extractor fork
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0): YouTube
- [Radio Browser](https://www.radio-browser.info): radio stations
- [LRCLIB](https://lrclib.net): synced lyrics
- [Last.fm](https://www.last.fm): genres, artists and charts
- [MusicBrainz](https://musicbrainz.org): album info
- [ListenBrainz](https://listenbrainz.org): scrobbling
- [Spun](https://github.com/yappologistic/Spun) by
  [@yappologistic](https://github.com/yappologistic): the first player looks (vinyl, CD,
  cassette, TP-7) were inspired by it; an independent Compose reimplementation, no Spun
  code or assets

## License

GPL-3.0.

## Demo assets

The files in `app/src/main/assets/demo/` (audio + cover gradients) are generated
locally with `ffmpeg`: short ambient chord pads, royalty-free, purely for the demo.
`manifest.json` lists their metadata.
