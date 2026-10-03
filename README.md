# Focus Launcher

A minimalist Android home-screen launcher built with Kotlin and Jetpack Compose. Pure black
background, white text, monospace font, no app icons, no clutter — just a clock, two
gesture-driven radial dials for quick app launching, and a full-text search.

## Features

- **Dot-matrix clock** — a 5×3 segmented-display clock (`HH:mm`), with the date
  (`DD/MM/YY`) and today's screen time flanking it at the bottom-left/right. Everything
  below the clock — that row and the calendar block — is pinned to the clock's own
  measured width, so long text truncates with an ellipsis instead of stretching the block
  wider than the clock.
- **Two radial dial menus**, stacked in the bottom-right corner:
  - Pull from either handle and rotate through the arc to light up an app; the selected
    app's name glides into a left-side callout box as you rotate. Release to launch.
  - The bottom dial sweeps a 90° quarter-circle; the upper dial sweeps a full 180°.
  - Node spacing grows automatically so apps never overlap, however many are assigned.
  - The pull handle has a generous invisible touch zone around it (much bigger than the
    visible dot) so it's easy to find without looking, and once you've pulled far enough
    from the anchor, releasing always launches the nearest app — a direction that's
    slightly outside the arc still resolves to the nearest edge app instead of launching
    nothing.
- **Swipe up** anywhere on the home screen to open search. Results appear instantly as
  you type, with the top (most likely) match highlighted and launched immediately once
  typing narrows to a single app (no debounce — it opens the moment it's unambiguous) or
  on pressing Enter.
- **Swipe left / right** on the home screen trigger quick actions — Camera and Contacts
  by default, both overridable. **Swipe down** pulls down the system notification shade,
  same as it would from any other app.
- **Next calendar event** — shown below the date as three short lines (title, truncated
  with an ellipsis if it's long; `> `-prefixed date; `> `-prefixed start–end time), read
  straight from the device's calendar provider (whatever's synced from a Google account
  already on-device — no separate sign-in).
- **Long-press an app** in search to reveal an inline action panel — filled monochrome
  icons (same pack, same size) for Uninstall, Rename, Hide, and Close, sliding in from the
  right edge of that same row (no separate popup), separated from the app name by a thin
  divider. A long name is simply covered by the panel's own opaque background rather than
  being clipped right at the divider. While held, the row's border fills in white (or black
  on the highlighted top match) proportional to how long you've been pressing, fading out
  on release.
  - Rename sets a launcher-only display name used everywhere the app appears.
  - Hide removes it from search while keeping any dial/gesture assignment working.
- **Settings** (long-press the clock) — grouped into labeled sections, in order: Expense
  tracker, Gestures, Upper dial, Bottom dial, Hidden apps, each under a thin divider. The
  two dial sections show a small radial replica of the actual on-screen dial — the same
  arc, in the same corner — instead of a plain list, so it's immediately obvious which pull
  direction launches which app. Tap a node to assign/replace that slot; long-press a filled
  one to clear it. Gesture overrides and unhiding apps are still plain rows.
- Status bar is hidden, and system window/activity-open animations are disabled for this
  activity, so the home screen reads as the persistent OS shell rather than an app that
  keeps opening and closing on top of it.
- **Expense tracker** (optional — enable under Settings → Expense tracker, now the first
  section) — reads bank/UPI debit alerts straight out of the SMS inbox and tracks spend
  locally, nothing leaves the device. Enabling it for the first time pulls in today's
  messages only, not the device's entire SMS history; every check after that is purely
  incremental.
  - **On the home screen itself**, below the calendar block: this month's total, a bar
    graph of categories sorted shortest to tallest left-to-right (only categories with
    spend so far are shown, to cut clutter), and a `>`-prefixed breakdown line per category
    — matching the calendar block's own `>` convention. Tapping this area opens the
    dashboard; a white badge on its `>` button shows how many merchants still need tagging.
  - **The dashboard** (Settings → Open dashboard, or tap the home screen summary) adds:
    today's total (animating in like an odometer when a background re-sync finds new
    spend), any merchant seen for the first time with one-tap category chips — each
    category shown with its own icon (fork/knife, car, cart, star, subscriptions) — a
    per-category breakdown with animated bars, a 14-day line graph that grows up from the
    baseline on data changes, and a list of recent transactions. Tagging a merchant
    retroactively re-tags its past transactions and is remembered for every future one.
  - Bank SMS formats vary a lot and there's no universal standard, so parsing (in
    `SmsTransactionParser.kt`) covers common Indian bank/UPI debit phrasing — extend its
    regexes for formats it doesn't already catch.

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
├── ExpenseCategory.kt        — the 5 fixed spend categories
├── ExpenseDbHelper.kt        — SQLite schema for transactions + merchant→category tags
├── SmsTransactionParser.kt   — regex parsing of bank/UPI debit SMS into amount + merchant
├── ExpenseRepository.kt      — SMS sync, tagging, and dashboard queries
└── ui/
    ├── Theme.kt              — black/white/grey palette, monospace typography
    ├── DotMatrixClock.kt     — the segmented clock display
    ├── HomeScreen.kt         — clock, gesture handling, hosts the two dials
    ├── RadialAppMenu.kt      — the pull-and-rotate dial menu
    ├── ScrambleText.kt       — hacker-style decrypt text animation
    ├── SearchScreen.kt       — full-screen search overlay + app action menu
    ├── SettingsScreen.kt     — dial slot, gesture, hidden-app, expense-tracker config
    └── ExpenseTrackerScreen.kt — spend dashboard: totals, categories, graph, tagging
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
- `EXPAND_STATUS_BAR` — a normal, auto-granted permission used to pull down the
  notification shade on swipe-down, the same way every other custom launcher does (there's
  no public API for this, just a long-standing reflection call against the hidden
  `StatusBarManager`).
- `READ_SMS` — only used if the expense tracker is enabled, requested at runtime from the
  Settings toggle. The tracker never sends a message and never leaves the device; it only
  reads inbox messages that parse as a bank/UPI debit alert.

## Known limitations

- Fonts: body text uses Android's built-in monospace typeface (no network dependency).
- Screen time requires the manual Usage Access grant described above; until granted, the
  home screen shows "Enable screen time" instead of a duration.
- "Close" is a best-effort background-process kill (`killBackgroundProcesses`) — Android
  doesn't let one app force-stop another's foreground activity, so this mainly helps with
  apps already in the background.
- The calendar event comes from whatever's already synced to the device's local calendar
  provider; there's no in-app Google account sign-in.
