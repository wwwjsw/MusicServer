# Music Server – Desktop (Linux)

Linux/Desktop port of the Music Server Android app, implemented via
**Kotlin Multiplatform** + **Compose Desktop** + **Ktor 3.x**.

## Features

- Dark-themed Compose Desktop UI with folder picker, port control, and Start/Stop button
- Scans any local folder recursively for audio files (`mp3`, `flac`, `ogg`, `m4a`, `aac`, `wav`, `opus`, `wma`)
- Reads ID3 / FLAC / OGG / M4A tags via **JAudioTagger** (title, artist, album, duration, artwork)
- Skips `WhatsApp Audio`, `WhatsApp Voice Notes`, and `Telegram Audio` folders (mirrors Android `MusicFilter`)
- HTTP server via **Ktor + Netty** with HTTP Range support for seekable streaming
- QR code (ZXing) showing the server URL for easy phone pairing
- Live track list shown in the UI after scanning

## API (same as Android)

| Endpoint | Description |
|---|---|
| `GET /` | Serves `index.html` from the bundled `music.zip` (if present) |
| `GET /js/app.js` | Serves the web app JS from `music.zip` |
| `GET /music` | Returns JSON list of all tracks |
| `GET /music?audio_id=<id>` | Streams the audio file; supports HTTP Range |
| `GET /albuns` | Returns JSON list of albums (name kept for API compatibility) |

## Running

```bash
./gradlew :desktop:run
```

## Building a distributable

```bash
# Debian / Ubuntu
./gradlew :desktop:packageDeb

# RPM-based (Fedora, openSUSE)
./gradlew :desktop:packageRpm

# Portable AppImage
./gradlew :desktop:packageAppImage
```

## Module structure

```
desktop/
└── src/jvmMain/kotlin/com/wwwjsw/musicserver/
    ├── Main.kt                          # Compose Desktop entry point & UI
    ├── models/
    │   ├── MusicTrack.kt               # JVM model (no Android Bitmap)
    │   └── Album.kt
    └── server/
        ├── MusicScanner.kt             # Filesystem walker + JAudioTagger
        └── DesktopMediaServer.kt       # Ktor server (mirrors Android MediaServer)
```
