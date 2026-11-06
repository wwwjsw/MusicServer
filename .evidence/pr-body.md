This is by an automated agent, not a human.

Closes #15.

## What changed

The Android app had no explanation of what it does, so a first-time user only saw a QR code, an "Open Web Player" button and two cards. This PR adds a user guide (in Portuguese, matching the app's user-facing copy):

- **First-run modal** (`UserTutorial.kt`): a 5-step guide explaining what the app does — connecting a browser on the same Wi-Fi via the QR code / `http://<ip>:8080` / the "Open Web Player in Browser" button, browsing albums, listing all songs, and the playback controls. It opens automatically on the first launch only; the "seen" flag is persisted in `SharedPreferences` (`music_server_prefs/tutorial_seen`). `Pular` / `Começar` dismiss it, `Voltar` / Back move to the previous step, and a dot indicator shows the position.
- **Navbar with help icon** (`MainActivityContent.kt`): a small top bar with the "Music Server" title and a help (`?`) button. Tapping it re-opens the same guide at any time. The icon is a local vector drawable (`ic_help_24.xml`), so no new dependency was added.

Only the Android app is changed; the desktop/Compose Desktop client is out of scope for this issue.

## Visual evidence — Before / After

Same scenario: fresh install on an Android 15 (API 35) emulator, 720x1280, light theme, `READ_MEDIA_AUDIO` granted, empty music library.

**Baseline** — `production` @ `6b53715` (`Merge pull request #14 from wwwjsw/13-lazy-album-loading`), debug build. First launch with no music library: no explanation, no help entry point.

| Baseline: first launch (production) | After: first launch of the updated app |
| --- | --- |
| ![Baseline main screen](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/01-baseline-main-production-6b53715.png) | ![After first-run modal](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/02-after-first-run.png) |

**After** — this PR (`9174a43`), debug build. Steps 2–5 of the guide:

| Step 2 – browser connection | Step 3 – albums | Step 4 – all songs | Step 5 – playback |
| --- | --- | --- | --- |
| ![Step 2](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/03-after-step2.png) | ![Step 3](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/04-after-step3.png) | ![Step 4](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/05-after-step4.png) | ![Step 5](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/06-after-step5.png) |

After behaviour, same build:

| Relaunch after finishing the guide – no auto modal, navbar shows the help icon | Tapping the help icon re-opens the guide |
| --- | --- |
| ![Relaunch](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/07-after-relaunch-no-modal.png) | ![Help icon reopens guide](https://raw.githubusercontent.com/wwwjsw/MusicServer/agent/user-tutorial-modal-fdddd798e4da/.evidence/08-after-help-icon-reopens.png) |

## Verification

- `./gradlew :app:assembleDebug` compiles cleanly (Kotlin warnings only).
- On the API 35 emulator: fresh install → guide opens by itself; steps navigate forward/back; `Começar` and `Pular` both close it; force-stop + relaunch → guide does not re-open; tapping `?` opens it again; `Pular` closes it. No crash in `logcat`.

## Limitations

- The guide text is hard-coded Portuguese, like the rest of the app's user-facing copy (the desktop/web player is where English labels live); `strings.xml` migration was not part of this change.
- The desktop client (`:desktop`) has no tutorial.

## Review notes

No human review yet. Automated review rounds: 0.
