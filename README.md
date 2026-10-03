# Focus Launcher

A minimalist Android home-screen launcher built with Kotlin and Jetpack Compose. Pure black
background, white text, monospace font, no app icons, no clutter — just a clock, two
gesture-driven radial dials for quick app launching, and a full-text search.

## Features

- **Dot-matrix clock** — a 5×3 segmented-display clock (`HH:mm`), with the date
  (`DD/MM/YY`) and today's screen time flanking it at the bottom-left/right.
- **Two radial dial menus**, stacked in the bottom-right corner:
  - Pull from either handle and rotate through the arc to light up an app; the selected
    app's name glides into a left-side callout box as you rotate. Release to launch.
  - The bottom dial sweeps a 90° quarter-circle; the upper dial sweeps a full 180°.
  - Node spacing grows automatically so apps never overlap, however many are assigned.
- **Swipe up** anywhere on the home screen to open search. Results appear instantly as
  you type — the typed text itself stays hidden, and the top (most likely) match is
  highlighted so pressing Enter launches it immediately.
- **Swipe left / right** on the home screen trigger quick actions — Camera and Contacts
  by default, both overridable.
- **Next calendar event** — shown below the date, read straight from the device's calendar
  provider (whatever's synced from a Google account already on-device — no separate sign-in).
- **Long-press an app** in search to reveal an inline action panel — filled monochrome
  icons (same pack, same size) for Uninstall, Rename, Hide, and Close, sliding in from the
  right edge of that same row (no separate popup), separated from the app name by a thin
  divider. A long name is simply covered by the panel's own opaque background rather than
  being clipped right at the divider. While held, the row's border fills in white (or black
  on the highlighted top match) proportional to how long you've been pressing, fading out
  on release.
  - Rename sets a launcher-only display name used everywhere the app appears.
  - Hide removes it from search while keeping any dial/gesture assignment working.
- **Settings** (long-press the clock) — grouped into labeled sections (Bottom dial / Upper
  dial / Gestures / Hidden apps), each under a thin divider. Assign apps to each radial
  dial's slot, override the swipe-left/right actions, and unhide hidden apps.
- Status bar is hidden for a fully immersive home screen.

## Project structure

```
app/src/main/java/com/focuslauncher/app/
├── MainActivity.kt           — navigation between Home / Search / Settings, status bar
├── AppRepository.kt          — queries installed apps, launches/uninstalls/closes apps
├── AppLabelStore.kt          — persists per-app rename overrides
├── HiddenAppsStore.kt        — persists which apps are hidden from search
├── RadialMenuStore.kt        — persists which apps are assigned to each dial's slots
├── GestureAppStore.kt        — persists swipe-left/right overrides
├── DeviceUsage.kt            — today's screen time via UsageStatsManager
├── CalendarRepository.kt     — next upcoming event via the device's calendar provider
├── PackageChangeReceiver.kt  — refreshes the app list on install/uninstall
└── ui/
    ├── Theme.kt              — black/white/grey palette, monospace typography
    ├── DotMatrixClock.kt     — the segmented clock display
    ├── HomeScreen.kt         — clock, gesture handling, hosts the two dials
    ├── RadialAppMenu.kt      — the pull-and-rotate dial menu
    ├── ScrambleText.kt       — hacker-style decrypt text animation
    ├── SearchScreen.kt       — full-screen search overlay + app action menu
    └── SettingsScreen.kt     — dial slot, gesture, and hidden-app configuration
```

## Building

Requires Android Studio (or a local Gradle + Android SDK install). `minSdk 26`, `targetSdk 34`.

1. Open the project in Android Studio — it will fetch the Gradle wrapper automatically on
   first sync. Alternatively, with Gradle and the Android SDK installed locally, create a
   `local.properties` file pointing at your SDK (`sdk.dir=/path/to/Android/sdk`) and run
   `gradle assembleDebug`.
2. Install the debug APK on a device or emulator and set it as the default launcher from
   Android Settings.

### Permissions

- `QUERY_ALL_PACKAGES` — to list all installed apps (default launchers are generally
  exempt from the stricter package-visibility rules, but this is declared for reliability
  across OEM skins).
- `PACKAGE_USAGE_STATS` — for the screen-time display. This is a special permission the
  user must grant manually under Settings → Apps → Special access → Usage access; the
  launcher prompts for it (tap "Enable screen time" on the home screen).
- `READ_CALENDAR` — for the upcoming-event display, requested at runtime (tap "Enable
  calendar" on the home screen).
- `KILL_BACKGROUND_PROCESSES` — for the "Close" action in an app's long-press menu.

## Known limitations

- Fonts: body text uses Android's built-in monospace typeface (no network dependency).
- Screen time requires the manual Usage Access grant described above; until granted, the
  home screen shows "Enable screen time" instead of a duration.
- "Close" is a best-effort background-process kill (`killBackgroundProcesses`) — Android
  doesn't let one app force-stop another's foreground activity, so this mainly helps with
  apps already in the background.
- The calendar event comes from whatever's already synced to the device's local calendar
  provider; there's no in-app Google account sign-in.
