# Changelog

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
