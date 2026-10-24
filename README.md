<p align="center">
  <img src="docs/hero.png" alt="LiteDroid Launcher" width="100%">
</p>

<p align="center">
  <b>A fast, glass-styled home screen for Android TV & Google TV.</b><br>
  Continue watching across apps · two layouts · six built-in games · a cinematic screensaver · no ads, no root.
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-1.0.0-ff5c8a?style=for-the-badge">
  <img alt="Platform" src="https://img.shields.io/badge/Android%20TV%20%7C%20Google%20TV-3ddc84?style=for-the-badge&logo=androidtv&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285f4?style=for-the-badge&logo=jetpackcompose&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-555?style=for-the-badge">
</p>

<p align="center">
  <a href="README.tr.md">🇹🇷 Türkçe</a> ·
  <a href="#-features">Features</a> ·
  <a href="#-under-the-hood">Under the hood</a> ·
  <a href="#-install">Install</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

---

LiteDroid started as a weekend question — *"can my Chromecast with Google TV feel like an Apple TV?"* — and grew into a full launcher written from scratch in Kotlin and Jetpack Compose. It replaces the stock home screen with frosted-glass menus, a colorful showcase, a "continue watching" row that works across streaming apps, built-in remote-friendly games and a hand-drawn animated screensaver, while staying smooth on modest TV hardware.

## 📸 Screenshots

| Modern layout | Classic ("Casual") layout |
|---|---|
| ![Modern home](docs/screenshots/modern-home.png) | ![Casual home](docs/screenshots/casual-home.png) |
| **Built-in games** | **Settings** |
| ![Games](docs/screenshots/games.png) | ![Settings](docs/screenshots/settings.png) |

<p align="center"><img src="docs/screenshots/about.png" alt="About" width="70%"></p>

## ✨ Features

### Two layouts, one launcher
- **Modern** — tabbed navigation (Home · Apps · Games · Store), a rotating featured hero for your apps, wide card rows, search, a bell, quick settings, profile and a big clock. A slim status bar shows Wi-Fi/Ethernet, weather and location.
- **Casual** — a floating glass top bar with your favorite apps docked in the middle, a cinematic showcase that recolors itself after the focused app, and big bouncy tiles. Three showcase styles: *Cinematic*, *Card*, *Minimal*.
- Switch instantly in **Settings → Appearance → Layout**.

### Continue watching — across apps
- Picks up what you play in **YouTube, Prime Video, Apple TV, Netflix** and other video apps through Android media sessions; music apps (Spotify & co.) are left out.
- One card per piece of content (never duplicated), up to **3 unfinished items per app**. Several cards from the same app get their **own color, a number badge, "21 min left" and "2 h ago"**, so you can tell them apart even when the app doesn't share titles.
- Selecting a card **resumes where possible**: it returns to the app's player if it's still alive, opens YouTube at the exact second, tries Netflix / Prime Video / Disney+ deep links, and otherwise opens the app.
- Finished content drops off by itself. The bell menu doubles as a *Continue watching* list.

### Six built-in games, made for the remote
| | |
|---|---|
| 🐍 **Snake** | speeds up as you grow, best score saved |
| 📦 **Box Push** | five hand-made Sokoban levels, all verified solvable |
| 🏎️ **Mini Racer** | three lanes, rising speed, smooth 60 fps loop |
| 🔤 **SOS** | 6×6 board against a CPU that hunts for SOS lines and avoids setting you up |
| ❌ **Tic-Tac-Toe** | minimax opponent with a touch of randomness, so it can be beaten |
| 🔢 **Sudoku** | six puzzles (easy & medium), each verified to have a unique solution; number keys supported |

Installed games from Google Play get their own row underneath.

### And the rest
- **Store** — curated picks (streaming, Turkish platforms, music, players, tools) with *Popular / Free / Local* tags; installs and updates go through Google Play, LiteDroid never side-loads APKs.
- **Screensaver** — four scenes drawn entirely in code (*Movie night*, *Match day*, *Game time* synthwave, *Quiet night* aurora) behind a glass clock card, with drifting light dust. Can also be set as the system screensaver.
- **Sound & motion** — navigation, select, back and edge-bump sounds; spring animations with three speed presets.
- **Turkish & English** UI, switchable live. Weather from Open-Meteo, location by IP or a city you type.
- Favorites, hide/move apps, seven wallpapers, and *Back* never kicks you out of the launcher.

## 🛠 Under the hood

TV chips are slow at exactly the things launchers love: full-screen blur, shadows and stacked transparency. LiteDroid is built around avoiding them.

| Problem | What LiteDroid does instead |
|---|---|
| Animated wallpaper redrawn every frame | Baked once into a **320×180 bitmap**, then scaled by the GPU |
| `Modifier.blur` / live blur | Removed entirely; "blurred" app art is **pre-baked** to 64×36 / 40×40 bitmaps with a box blur at load time |
| `Modifier.shadow` on every card | A **few concentric rounded strokes** drawn behind the card (`softGlow`, `softShadow`) |
| `Crossfade` allocating offscreen layers | A custom **`FastCrossfade`** using `CompositingStrategy.ModulateAlpha` |
| Infinite transitions always running | Created **only while motion is on** and paused under menus |
| Recomposition from animated values | Animated values are **read in `graphicsLayer` / layout lambdas**, not in composition |

Other bits worth a look:
- **Media capture** (`media/NowPlayingStore.kt`): keys content by media id → real title → app, ignores fake titles such as an app echoing its own name, remembers the last id per session (some apps blank it mid-playback), filters trailers by requiring ~12 s of actual playback, keeps the last known position when an app reports 0 on stop.
- **Resume** (`media/Resume.kt`): live session → app-specific deep link → the app's own "return to player" intent → any URL found in the session metadata.
- **Remote input**: select fires on key-up only when armed (no accidental double actions), long-press on first repeat, focus memory that returns you to where you were after a menu closes.
- **i18n** with a tiny `tx("tr", "en")` helper and a static composition local so a language switch recomposes everything instantly.

## 📦 Install

**Requirements:** an Android TV / Google TV device (tested on Chromecast with Google TV, Android 14), a computer with `adb`, JDK 17.

```bash
git clone https://github.com/aliapaydin/litedroid_launcher.git
cd litedroid_launcher
./gradlew assembleRelease        # needs JDK 17 (macOS: export JAVA_HOME=$(/usr/libexec/java_home -v 17))
```

Enable **Developer options → Wireless debugging** on the TV, then:

```bash
adb pair  <TV_IP>:<PAIRING_PORT>      # first time only
adb connect <TV_IP>:<PORT>
adb install -r app/build/outputs/apk/release/app-release.apk
```

Press **Home** on the remote and choose **LiteDroid Launcher → Always**.

### One-time permissions (recommended)
Android TV doesn't expose these in its UI, so they're granted once over adb:

```bash
# Continue watching (media sessions)
adb shell cmd notification allow_listener dev.litedroid.launcher/dev.litedroid.launcher.media.MediaListenerService
# Real "recently used" list
adb shell appops set dev.litedroid.launcher GET_USAGE_STATS allow
# Optional: use LiteDroid as the system screensaver
adb shell pm grant dev.litedroid.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell appops set dev.litedroid.launcher WRITE_SETTINGS allow
```

<details>
<summary>Make it the only home screen (optional, reversible)</summary>

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
# undo:
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```
</details>

<details>
<summary>Grab screenshots from the TV</summary>

```bash
TV=<TV_IP>:<PORT> ./tools/tvshot.sh        # one shot
TV=<TV_IP>:<PORT> ./tools/tvshot.sh 5 1    # five shots, one second apart
```
DRM-protected playback (Netflix, Prime Video…) shows up black, that's Android's protection.
</details>

<details>
<summary>Debug continue watching</summary>

```bash
adb logcat -s LiteDroidMedia
```
Each line shows what an app shares about its playback (title, id, keys) and how LiteDroid resumed it.
</details>

## ℹ️ How "continue watching" behaves per app

Google TV keeps its own continue-watching data private to system apps, so LiteDroid works from what each app publishes in its media session:

| App | What it shares | What you get |
|---|---|---|
| YouTube | title, channel, artwork, position, video id | Titled card with artwork; resumes at the exact second |
| Prime Video | content id, position (no title) | One card per title (up to 3), numbered and colored; deep link attempted |
| Apple TV | duration only | App card with time left |
| Netflix | nothing beyond play/pause | App card; returns to the player if Netflix is still in the background |

## 🗂 Project structure

```
app/src/main/java/dev/litedroid/launcher/
├── MainActivity.kt          Home intent, key handling, idle timer, sounds
├── LauncherViewModel.kt     Apps, favorites, settings, history, weather, network
├── I18n.kt                  Turkish / English
├── data/                    Apps, settings, usage stats, weather, store catalog
├── media/                   Media session listener, history store, resume logic
├── dream/                   System screensaver (DreamService)
├── games/                   Snake, Box Push, Mini Racer, SOS, Tic-Tac-Toe, Sudoku
└── ui/                      Modern & Casual layouts, cards, glass, screensaver, store, settings
```

**Stack:** Kotlin 2.0 · Jetpack Compose (BOM 2024.12, Material 3) · Coroutines · Coil 3 · Android Gradle Plugin 8.7 · minSdk 26 / targetSdk 35. No TV UI library, D-pad focus is handled by hand; no third-party UI kits: custom glyphs, the games and every screensaver scene are drawn in code. Sound effects were synthesized for the project.

## 🙏 Credits

- [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans) typeface, SIL Open Font License 1.1 ([license](licenses/PlusJakartaSans-OFL.txt))
- [Open-Meteo](https://open-meteo.com) weather data, [ipwho.is](https://ipwhois.io) approximate location
- Jetpack Compose and Coil

## 👤 Author

**Ali Apaydın** · [apaydin.a@gmail.com](mailto:apaydin.a@gmail.com)

If you try LiteDroid on your own TV, I'd love to see a screenshot. Issues and ideas are welcome.
