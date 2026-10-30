# Changelog

## 3.0.3
- PIN entry: `SlotPinDialog` (4 animated reels) is replaced by `PinDialog`, a 3×4 keypad (1-9, ⌫ 0 ✕) with a single-focus cursor that wraps at the edges and only gets a slightly lighter fill and a thin border. OK types the highlighted digit as a hidden dot; on the 4th digit the PIN is checked without pressing OK (right → in, wrong → shake and clear). Digits are never drawn; number keys and Backspace on the remote still work. No endless animations.

## 3.0.2
- **Time-up freeze fixed.** The full-screen `TimeUpScreen` was a focusable accessibility overlay that relied on getting window focus; when it didn't, remote keys went to the (still playing) app underneath and the box looked frozen until the 60 s auto-leave. The overlay is now `FLAG_NOT_FOCUSABLE` and display-only; `RemoteKeyService.onTimeUpKey` handles the keys itself (OK = request time, any other key incl. Home = Who's watching?, volume passes through).
- On lock, Who's watching? opens underneath the overlay, so the video app goes to the background and releases the decoder and memory instead of running under an opaque window.
- Parent actions apply instantly: the service collects `Parental.version`; added time or unlock removes the lock screen at once ("You got more time" notice), "lock now" locks without waiting for the 15 s tick, and extra time during the final countdown cancels it.
- The last-seconds notice now really counts down 10 → 1 (it used to show a fixed number and vanish after 5 s).
- Leaving the lock screen is debounced (key + timer could open the picker twice); blocked/timed-out apps go straight to LiteDroid home instead of via the box's own launcher and a redirect.
- Less main-thread work: settings read on window changes are cached for 5 s, parsed parental rules are cached per raw value, the phone panel caches app labels; the lock screen has no endless animation.

## 3.0.1
- Phone panel: light/dark theme (follows the phone, or set manually; remembered), sticky top bar with the TV's name, LiteDroid version and a connection dot, "now on the TV" profile line, remaining-time ring per kid, segmented quick-add, bottom-sheet confirmation for "lock now", collapsible app list with bars, refresh on return to the page.
- `/api/state` returns `device`: TV name (`Settings.Global.DEVICE_NAME`, falls back to model), manufacturer/model, Android version, LiteDroid version, IP, profile count and the active profile.

## 3.0.0
- **Profiles** (`data/Profiles.kt`): up to 5, adult/kid, name, emoji/color or avatar, optional PIN (salted SHA-256, never stored in clear). Per-profile storage via `Profiles.scoped(base)`: the first profile (`main`) keeps the legacy file names, so upgrading migrates nothing; others use `<base>__p_<id>`. Settings are split: device keys (API keys, language, weather, Wi-Fi, updates, remote/Input, home redirect, canvas, app lock) in `litedroid`, profile keys (`SettingsRepository.PROFILE_KEYS`: layout, favorites, teams, sounds, AI choices…) in the profile file. App order/hidden/theme/recents, now-playing history, story prefs and library, avatars, game records, browser history, bell state and Wrapped counters are per profile.
- `ProfileTimeline` logs profile switches (30 days); Wrapped day snapshots and the usage report clip device sessions to the intervals each profile was active.
- **Who's watching?** (`ui/ProfilesUi.kt`): shown at start-up and on `ACTION_SCREEN_ON` when there are 2+ profiles (the accessibility service brings LiteDroid to front with `EXTRA_PICK_PROFILE`); the top-right profile opens it too. Idle saver after 1 minute while it's open. Slot-machine PIN picker (4 animated reels, ▲▼/◀▶/OK, number keys), welcome toast, profile manager (add adult/kid, name, icon, color, type, PIN, delete with data).
- **Parental controls** (`data/Parental.kt`, Settings → Parental controls, asks the current adult's PIN): per kid daily minutes (weekday/weekend), screen hours (with bedtime), blocked apps and per-app daily limits, Claude kids mode (system prompt for children, no web search) or off, LiteDroid browser/store toggles. Safe defaults block Android settings and Play Store. Day starts at 06:00.
- Enforcement in `RemoteKeyService` (15 s tick): counts while the screen is on and not in the screensaver, dream, lock screen or profile picker; 5- and 1-minute notices, a countdown in the last 15 s, then media pause and a full-screen `TimeUpScreen` (OK = ask for time, any other key = Who's watching?). Blocked or timed-out apps send the kid home with a notice. Locked kids are dimmed on the picker and show "Talk to your parent" with a time request.
- Adult PINs are mandatory while any kid has limits (set on the spot when saving a limit; can't be removed; a PIN-less adult is entered with another adult's PIN). Settings in a kid profile ask an adult PIN.
- **Phone panel** (`tools/ParentServer.kt`, ports 8801-8809, `assets/parent/index.html`): one-time QR pairing code (10 min) gives the phone a random token; every call also needs an adult PIN (5 wrong → 5 min wait). Shows each kid's used/remaining time, lock state, today's top apps; add 15/30/60 min, lock/unlock, approve/decline requests, set weekday/weekend daily time. Paired phones can be removed on the TV.
- Usage report: profile picker (yourself + kids for adults).

## 2.7.0
- **LiteDroid Wrapped** (`wrapped/`): weekly (Mon-Sun) recap, ready Mondays at 09:00, per profile (`filesDir/wrapped/main/`; LiteDroid has one profile today).
  - Usage events only live ~7 days on Android, so every finished day is snapshotted (`days/<date>.json`: foreground sessions incl. LiteDroid itself via `UsageRepository.sessions`, plus the day's LiteDroid counters) every 30 min from the ViewModel. The week is computed from these (`weeks/<monday>.json`) and kept; day files are pruned after 21 days. Weeks with no usage and no counters produce no Wrapped. Locked apps are left out.
  - Counters (`WrappedStats`, numbers only, never content): games/tools opened, records broken (`GameScores.submit`), quiz and Draw & Guess games + winners, stories (new/replay, hero), Claude questions, sleep-timer switch-offs, screensaver time (in-app + dream), Living Painting images shown, screenshots.
  - Week data: total/daily time, 7×24 heatmap, top 15 apps (time, opens, longest), longest sitting, latest night, earliest morning, opens, distinct apps, TV-off days, followed teams' finished matches (`ScoresHub.finishedBetween`, ESPN date range) with "was the TV on" flag, viewer persona (night owl, binger, gamer, story lover, weekend, early bird, loyal, explorer, balanced).
  - Show (`WrappedShow`): story-style progress, 11 animated pages that appear only when they have data, comparisons with the previous week (deltas, rank arrows, new entries, dethroned #1), ◀ ▶ / OK pause / Back. Summary card can be captured (accessibility screenshot) and served by QR via `ShotShareServer`.
  - Music: `GeminiMedia.music` (Interactions API, `lyria-3.5`, falls back to `lyria-3-clip-preview`), instrumental prompt from persona, peak hour and a weekly flavor; cached next to the week (last 12 kept). Plays looped with fade-in; without a key or on failure the show runs silently (with a short note).
  - Bell: unseen Wrapped adds a special animated card at the top of the bell menu; the Modern bell gets a spinning gradient ring, wiggle and 🎁 badge; Casual shows a gift button in the top bar. Archive in Settings → More → LiteDroid Wrapped, with a "this week so far" preview.
- **No AI key / AI down:** Living Painting can't be selected without a Gemini key (scene cycling skips it, its settings rows hide, a stored `CANVAS` scene renders Mixed); the Story Machine shows a locked screen (saved stories still replay offline) and a lock on its Extras card / picker entry; Draw & Guess skips all remaining AI rounds after the first failure. Runtime failures already fell back (Living Painting: cached paintings or night scene; Gemini voice: device TTS).
- Floating menu closes when the launcher comes to front (Home key or window change) instead of staying on top of the home screen.
- In-app release notes: 1.4 folded into 1.7.

## 2.6.0
- **Draw & Guess** (`draw/`, Extras → Çiz-Bil): LAN game on the quiz's port range (8765-8775), phone page `assets/draw/index.html`.
  - Turns rotate through joined players (1-3 each, 60/80/100 s). The drawer picks one of three words (easy/medium/hard, 218-word TR/EN list) on the phone; strokes are sent every 70 ms as ordered, idempotent batches (`seq`; points 0-1000 normalized, width in thousandths of the board, undo/clear ops) and drawn on a square paper board on the TV with quadratic smoothing.
  - Guessers type on their phones: Turkish-folded matching (`ç→c`, `ı→i`…, any word in the guess), "so close" (edit distance 1-2) shown only to the guesser, wrong guesses stream on the TV. Score: 100 + up to 300 for speed, +50 for the first; the drawer earns 50-100 per correct guesser. Letter hints open at 50 % and 75 %. Round ends early when everyone guessed; a drawer silent for 20 s ends the round.
  - **AI draws** round (Gemini key): a doodle via `GeminiMedia.image` from the English word, prefetched during earlier rounds, revealed through 10 pixelation levels (5 s each); everyone guesses. Solo play is AI-only.
  - Quiz UI helpers (`QuizChip`, `StartButton`, `PlayerSlot`, `PodiumColumn`, `Confetti`, `TimerRing`) are now `internal` and shared.
- Floating menu: Claude and screenshot buttons removed.
- **Removed** 2.5.0's "turn off when the show ends" (media-session stop detection was unreliable in practice). 2.5.0 was never published as a release; 2.6.0 replaces it on devices where it was sideloaded.
- In-app release notes kept to six entries (2.4.1 folded into 2.4.0, 1.9 into 2.0).

## 2.4.1
- **Input button**: the accessibility key filter catches `KEYCODE_TV` / `KEYCODE_TV_INPUT` (Chromecast remote's input key arrives as `KEY_TV`) and runs the chosen action: quick panel (`EXTRA_OPEN_PANEL`, toggles), last app (recent foreground packages), floating menu, screenshot, LiteDroid home, or Off (pass through).

## 2.4.0
- **Guest Wi-Fi QR** (`ui/HomeExtras.kt`): `WIFI:T:…;S:…;P:…;;` payload with escaping, from the quick panel or Settings → Home and family; SSID → password → QR flow.
- **Send from phone** (`tools/PhoneBridge.kt`): LAN page (ports 8791-8799, stops after 30 min idle). Links open in the LiteDroid browser (`BrowserLaunch` + `EXTRA_OPEN_BROWSER`); text fills an open LiteDroid text dialog, otherwise the focused input of the foreground app via `AccessibilityNodeInfo.ACTION_SET_TEXT` (accessibility now `canRetrieveWindowContent`, used only for that).
- **Morning weather alert**: Open-Meteo hourly code + precipitation probability; first wet hour ≥ 50 % from now; card once a day between 06 and 12.
- **Break reminder**: accessibility tracks the foreground app; repeats every N minutes (30/45/60/90/120); screen-off, screensaver and the launcher reset the clock.
- Games tab renamed **Extras** (Games / Tools shelves); the top-bar quick panel button is a down arrow.
- Settings: the category column scrolls; first/last rows absorb Up/Down with a bounce (`Sfx.EDGE`, soft) instead of jumping to another category.
- Modern top bar: while the profile pill is focused the clock/date (and divider) hide so the greeting and name don't squeeze the clock.
- Text dialogs retry focus until the field has it; an open text dialog counts as a page so the home grid doesn't steal focus.
- **App lock**: locked packages get a full-screen opaque accessibility overlay with a 4-digit PIN pad (number keys work); Back goes home; unlock lasts until another app or the launcher comes to front. Lock/unlock from the app menu, PIN change and "remove all" require the current PIN.

## 2.2.0
- **Browser** tool (`tools/Browser.kt`, full screen): single-tab Android System WebView, created lazily and `destroy()`ed on exit; `onPause`/`pauseTimers` when LiteDroid stops. Remote pointer (accelerating on hold), OK sends a synthetic touch, edges and channel keys send mouse-wheel `ACTION_SCROLL` so inner scroll containers work too. Focused page inputs are detected via JS and filled through LiteDroid's own text dialog (value setter + input/change events, optional Enter/submit). Address bar (URL or Google search), back/forward/reload/stop, home page with quick sites and recent history, text zoom, desktop/mobile user agent. Camera/mic/location requests are denied; `intent:` links open the target app.
- Scores: a "match day" runs 06:00–06:00 local, so a 23:00 kick-off and its final score stay visible after midnight; live matches always show.
- **Downloader** (`tools/Downloader.kt`): takes over WebView downloads (cookies + UA, manual redirects), live progress/speed, cancel/delete, copies finished files to `Downloads/LiteDroid`, opens or installs APKs via a `FileProvider`.

## 2.1.0
- **Living Painting** screensaver (`canvas/LivingCanvas.kt`, scene `CANVAS`): Gemini paints a city at its real local time (Open-Meteo `utc_offset_seconds`), weather (WMO code → prompt phrase), time of day and season, in the chosen style (oil, watercolor, Van Gogh, ukiyo-e, pixel, photo). Cities: home (cached weather city), a 16-city world tour, or alternating. Each city/day-part/weather/season/style combination is painted once and cached (newest 80 kept), so repeats cost nothing. Slow pan, 3 s crossfades, burn-in-safe drifting info. Falls back to cached paintings or the night scene without a key.
- **Tools** shelf in the Games tab (`BuiltInGame.isTool`, `tools/`):
  - Calculator: remote-friendly keypad, number keys, × ÷ before + −, live preview, history (BigDecimal).
  - Speed Test: Cloudflare `speed.cloudflare.com` ping/jitter, 4-stream download and 3-stream upload with warm-up excluded, sqrt-scaled gauge, verdict, last 5 results.
  - Screenshots: accessibility `takeScreenshot` (Android 11+, `canTakeScreenshot`) from the floating menu or with a 3 s timer; JPEGs in app storage and `Pictures/LiteDroid`; a small LAN web server + QR serves them to phones.
  - System Info: device, Android, SoC, RAM/storage, display modes and HDR, video/audio decoders, network, LiteDroid permission status.
  - Avatar Maker: Canvas-drawn avatars (skin, 8 hair styles, hair color, eyes, mouth, 8 accessories, cheeks, background), saved list, random, "use as profile" — the top bar profile badge shows it.

## 2.0.0
- **Story Machine** (`story/`): pick hero, place, theme, length, narrator, picture density, music and "turn the TV off at the end". Gemini writes the whole interactive story as JSON in one call (intro pages, a choice, two branches, up to 3 characters with their own Gemini voices and speaking styles, an English image prompt and a mood per page).
- Pages are illustrated (16:9) and narrated one page ahead while the current one plays; multi-speaker narration (narrator + one character per request, groups joined). Ken Burns zoom, crossfades, a procedurally generated lullaby (`Lullaby.kt`, AudioTrack, mood-driven chords and bells, ducked under narration), title card, choice screen, end screen with optional sleep timer.
- Everything is stored in `filesDir/stories/<id>` (JSON, JPEG, WAV); the library replays without any API calls. Newest 15 kept.
- `data/GeminiMedia.kt`: TTS and image generation with the user's Gemini key. Tries the Interactions API and the legacy `generateContent` across several model IDs, remembers the first route that works, converts WAV/L16, maps auth/quota errors. `PcmPlayer` plays PCM with pause/stop.
- Ask Claude/Gemini answers can be voiced with Gemini TTS (`Speaker` cloud pipeline: chunks synthesized one ahead, device TTS fallback per chunk). New settings: Voice engine, Narrator voice (10 voices, default Sulafat), Try the voice.

## 1.9.3
- Update screens fit a 540 dp-tall TV screen: the release-notes area takes the leftover height (`weight(fill = false)` + scroll) so the buttons on the "Updated" screen, the prompt and the update window never slip off the bottom edge.
- Illustrated Turkish setup and update guide: `docs/KURULUM.md` with images in `docs/kurulum/`.

## 1.9.2
- One update window at a time: a manual check or the bell menu opens the prompt directly in Automatic mode, and the background prompt never opens over the update window.
- Settings can't take focus while any update window is open (the remote no longer jumps behind it).
- Secondary update buttons get a solid fill and border so their labels stay readable.
- Without install permission, "Update now" opens the unknown-sources screen and shows the one-time adb command.

## 1.9.1
- Update prompt and window show the APK download size.

## 1.9.0
- **Minesweeper** (`games/Minesweeper.kt`): Easy 9×9/10, Medium 12×11/22, Hard 16×12/38; OK opens (flood fill), holding OK flags, OK on a number chords; mines are planted after the first open so it and its neighbours are always safe; timer, mines left and best time per level; level row above the board.

## 1.8.2
- Fix a startup crash after updating: the view model set `updatedTo` from its first `init` block before that property was initialized (Kotlin initializes members in order). Update checks now start from an `init` block placed after the update properties.

## 1.8.1
- Update check: a 404 from `releases/latest` (nothing published yet) counts as up to date instead of a connection error.

## 1.8.0
- **Self-update** (`data/Updater.kt`): checks `api.github.com/repos/aliapaydin/litedroid_launcher/releases/latest` 20 s after start and every 30 min, compares the tag with the installed `versionName`, downloads the `.apk` asset into the cache and installs it with a `PackageInstaller` session (`USER_ACTION_NOT_REQUIRED` on Android 12+, so once LiteDroid is the installer of record, later updates need no confirmation).
- **Automatic** mode: when LiteDroid is in front a prompt offers Update now / in 1 hour / in 3 hours (Back = 1 hour). Updating shows a full-screen progress screen (download → install → restart) that can be minimized to a corner card; the bell menu brings it back.
- **Notify only**: a corner card (and an accessibility overlay notice over other apps), the update window shows notes without an install button. **Off**: no checks.
- After an update `MY_PACKAGE_REPLACED` reopens LiteDroid and an "Updated to X" screen lists the version's notes. Signature mismatches get a clear message.
- `tools/release.sh`: builds, takes the Turkish notes from `Changelog.kt` (`tools/release_notes.py`) and publishes a GitHub release with the APK via `gh`.

## 1.7.1
- Gemini keys: hints mention the new `AQ.` format (the client never checked the prefix); a readable message for `ACCESS_TOKEN_TYPE_UNSUPPORTED`.
- README: new hero and screenshots (Gemini, listening panel, floating menu, sleep timer, team picker, usage report, release notes, Quiz Night on TV and phones).

## 1.7.0
- **In-app voice input** (`data/VoiceListener.kt`): `SpeechRecognizer` with partial results instead of the system voice screen. A panel shows "Getting the mic ready…" → "Speak now" (on `onReadyForSpeech`) with a level meter and the live transcript; longer silence thresholds so pauses don't cut the sentence; the last partial is used if the final result is empty. Falls back to the system screen without permission or a recognition service. Needs `RECORD_AUDIO` (asked once).
- Asking again stops the current answer and speech immediately; Back while listening cancels listening only.
- **Streaming speech** (`data/Speaker.kt`): sentences are spoken as soon as they complete (first one after ~8 characters), the TTS engine is warmed up when the screen opens.
- **Gemini** (`data/GeminiClient.kt`): `streamGenerateContent?alt=sse` with Google Search grounding and sources; models via the rolling aliases `gemini-flash-latest` / `gemini-flash-lite-latest`. Settings: AI provider, Gemini key (also `--es gemini_key`), Gemini model.

## 1.6.0
- **Quiz Night** (Games → Quiz Night): a Kahoot-style quiz played with phones. The TV runs a tiny HTTP server (`quiz/QuizServer.kt`, ports 8765–8775) and shows a QR code (ZXing) with its LAN address; Android and iPhone browsers open a self-contained page (`assets/quiz/index.html`) to join with a name and avatar (up to 8 players) and answer.
- 12 categories × 3 levels (Beginner / Medium / Hard, or mixed), 388 Turkish questions in `assets/quiz/questions.json`, options shuffled per game, recently asked questions avoided.
- Flow: lobby → 3-2-1 → question with countdown ring and who-answered badges (ends early when everyone answered) → reveal with each player's pick and points (500–1000 by speed + streak bonus) → leaderboard where rows slide from old to new rank and scores count up → podium with confetti. OK skips waits, Back returns to the lobby.

## 1.5.0
- **UEFA competitions:** Champions League (`uefa.champions`), Europa League (`uefa.europa`) and Conference League (`uefa.europa.conf`), on by default (also for existing installs, via a "known leagues" migration) and switchable like the others.
- **Team follow:** Settings → Live scores → Pick teams. Leagues on the left, the league's teams (ESPN `/teams`) as a logo grid on the right, with Select all / Clear / one-by-one. A league with no customization follows every team; otherwise only matches involving the selected teams reach the scores card, strip, menu, screensaver and goal alerts.
- **Sleep timer:** by duration (15–120 min) or at a set time (hour/minute picker). The remote shortcut service shows a 5 s notice bottom-right at 45/30/15/5 min left and a 10 s ring countdown at the end (any key cancels), then pauses playback and calls `GLOBAL_ACTION_LOCK_SCREEN` to put the device to sleep (HDMI-CEC turns the TV off). State survives restarts. Settings section, quick-panel tile and floating-menu chips.
- **Floating menu redesign:** 340 dp wide, compact clock/weather, small media row, sleep chips, two strip toggles, up to 6 matches, three small shortcut buttons; slides in from the right and slides out before the window is removed.

## 1.4.1
- Home redirect fix: declare a `<queries>` entry for `MAIN`/`HOME` so the stock launcher is visible under Android 11+ package visibility (it resolved to null before); fall back to the highest-priority home activity if resolution is empty; log Home key presses under `LiteDroidRemote`.

## 1.4.0
- **Home redirect** for TV boxes that lock their own launcher (e.g. Botech Wzone, where `pm disable-user` is refused): when another app is the default home, the remote shortcut accessibility service swallows the Home key and opens LiteDroid, and whenever that stock home screen comes to the front (boot, leaving an app) it switches to LiteDroid. Does nothing when LiteDroid is already the default home. Toggle in Settings ("Send Home to LiteDroid", on by default).

## 1.3.1
- Usage report reworked: an opaque dark page (the home screen no longer shows through), one vertical page that scrolls with the remote (summary → charts side by side → apps), apps as two-column cards with icon, rank badge, time, share and opens.
- Packages that can't be resolved to an app name (Google TV ambient mode and other system components) are named when known, otherwise left out.

## 1.3.0
- **Usage report** (Settings → Content and privacy → Usage report): screen time for Today / Yesterday / Last 7 days, an app ranking with total time, share, opens, longest session and last use, an hour-of-day chart, a 7-day chart, the busiest hour and the change from the previous day.
- Sessions are rebuilt from `UsageEvents` (activity resumed/paused/stopped, screen off, shutdown), merged when the same app returns within 15 s and capped at 6 h if a close event is missing. LiteDroid itself, System UI and other launchers are not counted.

## 1.2.0
- **Now playing for every music app:** YouTube Music, Apple Music, TIDAL, Qobuz, Deezer, fizy and the rest show title, artist, artwork and progress on the screensaver, floating menu and quick panel, just like Spotify.
  - Media notifications are read too: their session is tracked and their title/text/large icon fill gaps when an app leaves them out of the session.
  - Artwork given only as a URI (`content://` or `https://`) is loaded in the background.
  - Players that close drop off; with several apps playing, the most recently changed one wins.
- Modern layout: the black bottom status bar (hint, Wi-Fi, weather, location) is removed.
- **Settings → More → Release notes:** every version since 0.1, browsable with the remote.

## 1.1.2
- Floating score strip: the overlay window is now fully hidden (`View.GONE`) when there is nothing to show, so no empty frame lingers at the screen edge on the home screen or screensaver.
- README redesigned from scratch in Turkish (`README.md`) with new screenshots and a new hero image; English version moved to `README.en.md`.

## 1.1.1
- **Remote shortcut:** while any app is open, press **Back twice** to open a floating LiteDroid menu (clock, weather, now-playing controls, floating-score toggles, all of today's matches, shortcuts to Home and Ask Claude). A single press is still a normal Back (after a ~0.3 s wait for a second press). Uses an accessibility service that only watches keys and the foreground app.
- The floating score strip hides on the LiteDroid home screen, during any screensaver (system dream included) and while the menu is open.
- Quick panel scrolls with focus, so controls below the now-playing card are reachable.
- Floating score strip redesigned: smaller rows, narrower box sized to 1-3 matches, more than three live matches rotate every 10 s with a slide-up animation; red cards shown next to teams.
- Goal, red card and full-time events play as a short animated card inside the strip (and on the screensaver), with scorer/player and minute when available, then the strip returns to the matches.

## 1.1.0
- **Ask Claude:** voice (or typed) questions, streamed large-type answers, text-to-speech replies, web search with sources, follow-ups, Sonnet/Haiku choice; uses your own API key.
- **Live scores** for Süper Lig, Premier League, LaLiga, Serie A, Bundesliga and Ligue 1 with team logos and goal alerts; choose leagues in Settings.
- **Floating score strip** over other apps (overlay permission granted once via adb), hidden while LiteDroid itself is on screen.
- **Screensaver widgets:** Now playing (any app, music included) and today's scores.
- Modern top bar made compact so the clock always fits; ✦ Ask Claude buttons in both layouts.
- **Settings in tabs:** categories on the left (focus to open), rows on the right, description below.
- **New quick panel:** slides in from the right like Android's quick settings: clock and status, media controls for what's playing, toggle tiles (layout, sounds, floating scores, weather, hidden apps, animation/showcase) and shortcuts.
- Continue cards open the exact content only when a precise link exists (e.g. YouTube video id); otherwise the app starts fresh instead of jumping into whatever played last.

## 1.0.1
- Continue watching cards show the real video thumbnail for YouTube (from the video id) instead of the app logo.
- Artwork refreshes when an app swaps its placeholder logo for the real cover.
- Frosted text band at the bottom of continue cards (a tiny, upscaled copy of the artwork, no live blur) plus text shadows, so titles stay readable on bright images.
- Released under the MIT License.

## 1.0.0 — LiteDroid Launcher
- Renamed from *Camekan* to **LiteDroid Launcher** (package `dev.litedroid.launcher`).
- Continue watching: one card per content, up to 3 per app, per-card color, number badge, time left and "watched ago"; marquee titles on focus.
- Media capture keys content by id → real title → app; ignores app-name "titles"; remembers ids per session; filters trailers.
- Six built-in games: Snake, Box Push, Mini Racer, SOS, Tic-Tac-Toe, Sudoku. Installed games in a separate row.
- Store: Turkish platforms, music, tools and media players with Popular / Free / Local tags.
- New About screen, upgraded screensaver (glass clock card, light dust, scene progress).

## 0.7
- Modern hero shows only apps; continue row shows only resumable videos; music apps excluded.
- Bell menu becomes a *Continue watching* list with an unseen badge.
- App-specific deep links (YouTube, Netflix, Prime Video, Disney+) and session-activity resume.
- Apps that publish no metadata (e.g. Netflix) still get an app-level continue card.

## 0.6
- Continue watching from media sessions, resume attempts, media-access hint card.
- Utility apps (Settings, Play Store, file managers) kept out of the hero and home rows.
- Blurred-icon app cards; redesigned top bars (gradient wordmark, tabular clock, glass sheen).

## 0.5
- Major performance pass: baked wallpaper, no blur, no shadows, `FastCrossfade`, motion only when enabled.
- Softer Modern cards, Plus Jakarta Sans everywhere, thin dark status bar.
- English UI option (Turkish stays default).

## 0.4
- Modern layout with tabs, featured hero, wide card rows, search, bell, weather and network bar.
- Redrawn screensaver scenes.

## 0.3
- Floating top bar with favorites dock, profile and clock; sounds and bouncy animations; logo; screensaver with four scenes; settings page.

## 0.2
- Showcase styles, colorful backgrounds, continue-watching strip, performance work.

## 0.1
- First Apple-TV-style launcher: glass menus, app grid, About screen, store linking to Google Play.
