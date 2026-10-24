# Changelog

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
