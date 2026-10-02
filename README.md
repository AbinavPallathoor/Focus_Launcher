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
- **Settings** (long-press the clock) — assign apps to each radial dial's slots, and
  override the swipe-left/right actions.
- Status bar is hidden for a fully immersive home screen.

## Project structure

```
app/src/main/java/com/focuslauncher/app/
├── MainActivity.kt           — navigation between Home / Search / Settings, status bar
├── AppRepository.kt          — queries installed apps, launches apps/camera/contacts
├── RadialMenuStore.kt        — persists which apps are assigned to each dial's slots
├── GestureAppStore.kt        — persists swipe-left/right overrides
├── DeviceUsage.kt            — today's screen time via UsageStatsManager
├── PackageChangeReceiver.kt  — refreshes the app list on install/uninstall
└── ui/
    ├── Theme.kt              — black/white/grey palette, monospace typography
    ├── DotMatrixClock.kt     — the segmented clock display
    ├── HomeScreen.kt         — clock, gesture handling, hosts the two dials
    ├── RadialAppMenu.kt      — the pull-and-rotate dial menu
    ├── ScrambleText.kt       — hacker-style decrypt text animation
    ├── SearchScreen.kt       — full-screen search overlay
    └── SettingsScreen.kt     — dial slot and gesture configuration
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

## Known limitations

- Fonts: body text uses Android's built-in monospace typeface (no network dependency).
- Screen time requires the manual Usage Access grant described above; until granted, the
  home screen shows "Enable screen time" instead of a duration.
