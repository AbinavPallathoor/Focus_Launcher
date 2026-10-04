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
  section) — reads bank/UPI debit *and credit* alerts straight out of the SMS inbox the
  moment they arrive (a broadcast receiver, not just resume-time polling) and classifies
  each one with a small LLM running entirely on-device — nothing leaves the device, no
  network calls, no login. Enabling it for the first time pulls in the current calendar
  month's messages (not the device's entire SMS history), so "this month" is correct from
  the moment it's turned on; every check after that is purely incremental. Totals always
  include every synced transaction, classified or not, so you're never stuck waiting on
  the model before you see what you've spent.
  - **On-device classification** (`LlamaEngine.kt`, `TransactionClassifier.kt`) runs
    Qwen2.5-1.5B-Instruct (Apache-2.0, ungated on Hugging Face) via a vendored llama.cpp
    JNI bridge (`cpp/`), with the output constrained by a GBNF grammar so it's always
    valid, schema-exact JSON — never free text that needs parsing-and-praying. A
    counterpart seen before is recognized instantly from memory with no model call at all;
    a genuinely new one gets a real classification, few-shot-prompted from the user's own
    past answers. Every transaction gets a kind (expense, lent, borrowed, repayment, or
    income) and, for expenses, a category — fully automatic, no "tag this merchant" step.
  - **Debt ledger** — lending money, borrowing it, and repayments are tracked as their own
    kinds rather than folded into a generic category, with a running per-person balance
    derived straight from the transaction log (never a separately-maintained number that
    can drift). The classifier is balance-aware: a credit from someone already owed money
    is recognized as a repayment (even a partial or combined one) rather than guessed as
    plain income.
  - **On the home screen itself**, below the calendar block: today's total as the
    headline with this month's total as a smaller line beneath it, a bar graph of
    categories, and a `>`-prefixed breakdown line per category. Below that, a stack of
    terminal-style cards — the most recent transactions, newest in front, older ones
    peeking out behind — each with a tick to confirm the auto-tag is right (it disappears
    from the stack once confirmed) and a prompt to type what it actually was if it's
    wrong. Tapping that prompt brings the card to the center of the screen with the rest
    dimmed, auto-focused and keyboard up; Enter submits and the card leaves the stack
    immediately rather than waiting on the model.
  - **The dashboard** (Settings → Open dashboard, or tap the home screen summary) adds:
    a free-text command bar at the top — one instruction to the model that can add a
    transaction the SMS pipeline never saw (cash, say), delete one, or retag one, matched
    against a plain-language description rather than picking from a menu — today's total,
    an "Owing" section (who owes the user, who the user owes), a per-category breakdown
    with animated bars, a 14-day line graph, and the recent transaction list with the same
    confirm-tick and inline-retag affordances as the home screen.
  - **Settings** also gets a model download row (shows live progress, a reason if it
    stalls, and a cancel action — the download isn't Wi-Fi-restricted, since that silently
    stalls forever if the device isn't on a network Android recognizes as Wi-Fi) and an
    AI mode picker (Low / Medium / High) that controls how many CPU threads inference is
    allowed to use, resolved against the actual device's core count rather than a fixed
    number.
  - A small model running fully offline won't always get a judgment call right (debt
    direction and category are the two it's most often unsure about) — the memory system
    means a correction only has to happen once per counterpart, not every time.
  - Bank SMS formats vary a lot and there's no universal standard, so parsing (in
    `SmsTransactionParser.kt`) covers common Indian bank/UPI debit and credit phrasing —
    extend its regexes for formats it doesn't already catch.

## Project structure

```
app/src/main/java/com/focuslauncher/app/
├── MainActivity.kt                 — navigation between Home / Search / Settings, status bar
├── AppRepository.kt                — queries installed apps, launches/uninstalls/closes apps
├── AppLabelStore.kt                — persists per-app rename overrides
├── HiddenAppsStore.kt              — persists which apps are hidden from search
├── RadialMenuStore.kt              — persists which apps are assigned to each dial's slots
├── GestureAppStore.kt              — persists swipe-left/right overrides
├── DeviceUsage.kt                  — today's screen time via UsageStatsManager
├── CalendarRepository.kt           — next upcoming event via the device's calendar provider
├── PackageChangeReceiver.kt        — refreshes the app list on install/uninstall
├── ExpenseCategory.kt              — the 5 fixed spend categories
├── TransactionKind.kt              — expense/lent/borrowed/repayment/income + who resolved it
├── ExpenseDbHelper.kt              — SQLite schema: transactions, memory, debt ledger
├── SmsTransactionParser.kt         — regex parsing of bank/UPI debit+credit SMS
├── SmsReceivedReceiver.kt          — fires the moment a new transaction SMS arrives
├── ClassifyTransactionWorker.kt    — WorkManager job: sync + on-device classification
├── MemoryStore.kt                  — per-counterpart classification cache + few-shot examples
├── ExpenseRepository.kt            — SMS sync, classification, debt ledger, dashboard queries
├── ModelDownloader.kt              — fetches the GGUF model via DownloadManager, no login
├── ClassifierPerformanceStore.kt   — the Low/Medium/High AI mode → CPU thread count
├── LlamaModelLoader.kt             — shared "is the model loaded" check for both LLM features
├── LlamaEngine.kt                  — JNI bridge to the vendored llama.cpp (see cpp/)
├── TransactionClassifier.kt        — per-transaction classification + free-text correction
├── DashboardCommandInterpreter.kt  — the dashboard's free-text add/remove/retag command bar
└── ui/
    ├── Theme.kt                 — black/white/grey palette, monospace typography
    ├── DotMatrixClock.kt        — the segmented clock display
    ├── HomeScreen.kt            — clock, gesture handling, the two dials, the terminal feed
    ├── RadialAppMenu.kt         — the pull-and-rotate dial menu
    ├── ScrambleText.kt          — hacker-style decrypt text animation
    ├── SearchScreen.kt          — full-screen search overlay + app action menu
    ├── SettingsScreen.kt        — dial slot, gesture, hidden-app, expense-tracker config
    └── ExpenseTrackerScreen.kt  — spend dashboard: command bar, totals, owing, graph

app/src/main/cpp/                — vendored llama.cpp (MIT) + jni_bridge.cpp, CMake-built
app/src/main/assets/             — GBNF grammars constraining the model's JSON output
```

## Building

Requires Android Studio (or a local Gradle + Android SDK install) plus the Android NDK and
CMake, for the vendored llama.cpp native build. `minSdk 26`, `targetSdk 34`.

1. Open the project in Android Studio — it will fetch the Gradle wrapper, NDK, and CMake
   automatically on first sync. Alternatively, with Gradle, the Android SDK, and NDK
   `27.2.12479018` installed locally, create a `local.properties` file pointing at your SDK
   (`sdk.dir=/path/to/Android/sdk`) and run `gradle assembleDebug`.
2. Install the debug APK on a device or emulator and set it as the default launcher from
   Android Settings.
3. The on-device classifier's model (Qwen2.5-1.5B-Instruct, ~1 GB) isn't bundled in the
   APK — enable the expense tracker, then download it from Settings. Classification
   features stay inert (transactions sync but sit unclassified) until the download
   completes.

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
- `READ_SMS` / `RECEIVE_SMS` — only used if the expense tracker is enabled, requested at
  runtime from the Settings toggle. The tracker never sends a message; it only reads/reacts
  to inbox messages that parse as a bank/UPI debit or credit alert. `RECEIVE_SMS` is what
  lets a new transaction get classified the moment it arrives rather than waiting for the
  next time the app is opened — it's requested alongside `READ_SMS` but optional, the
  feature still works via resume-time sync if only that one is denied.
- `INTERNET` — used for exactly one thing: downloading the on-device model file from
  Hugging Face (a public, ungated URL, no account/login/token involved). Once downloaded,
  classification itself makes no network calls at all.

## Known limitations

- Fonts: body text uses Android's built-in monospace typeface (no network dependency).
- Screen time requires the manual Usage Access grant described above; until granted, the
  home screen shows "Enable screen time" instead of a duration.
- The on-device model (Qwen2.5-1.5B, quantized) is small enough to run on a phone CPU but
  isn't always right — it's most often unsure about debt direction (lent vs. borrowed) and
  category judgment calls. The memory system means a given merchant or person only ever
  needs correcting once, not every time.
- "Close" is a best-effort background-process kill (`killBackgroundProcesses`) — Android
  doesn't let one app force-stop another's foreground activity, so this mainly helps with
  apps already in the background.
- The calendar event comes from whatever's already synced to the device's local calendar
  provider; there's no in-app Google account sign-in.
