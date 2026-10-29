# ThemeLauncher

An Android home-screen launcher built around **themes**: named sets of apps (Gaming, Work, …) you switch between with one tap, plus a fixed dock of up to 5 favorites shared by every theme.

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · minSdk 26, target/compile SDK 37.

## Build and run

Requires JDK 17 and an Android Studio recent enough for AGP 9.4 / API 37, or just the command line:

```
.\gradlew.bat installDebug                 # build + install on the running emulator/device
.\gradlew.bat testDebugUnitTest            # JVM unit tests
.\gradlew.bat connectedDebugAndroidTest    # instrumented tests (uninstalls the app afterwards)
```

## Make it the default home app

- **First launch:** setup ends with a "Set as default home app" button (system dialog on Android 10+).
- **Later:** long-press empty space on the home screen → *Set as default home app*.
- **Emulator / adb:** `adb shell cmd package set-home-activity com.themelauncher/.MainActivity`
- **Settings:** Apps → Default apps → Home app → ThemeLauncher.

## Switch back to the normal launcher

- Settings → Apps → Default apps → Home app → *Pixel Launcher* (Samsung: *One UI Home*).
- adb (Pixel emulator): `adb shell cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity`
- Last resort: `adb uninstall com.themelauncher` — Android falls back to the stock launcher.

## Using it

| Where | Gesture | What happens |
|---|---|---|
| Theme row | Tap | Switch theme |
| Theme row | Long-press | Edit / Reorder / Delete theme |
| Theme row | `+` | New theme (name, icon, color, app checklist) |
| Anywhere on home | Swipe up | App drawer with search |
| App (grid, dock, drawer) | Long-press | App menu |
| App (grid, dock) | Long-press, then drag | Reorder |
| Empty space | Long-press | New theme, wallpaper, default home, run setup again |

## Code map

- `data/apps` — installed apps via `LauncherApps` (+ change callback), icon cache, search
- `data/db`, `data/themes`, `data/settings` — Room (themes, assignments, favorites), repositories, DataStore
- `LauncherViewModel` — combines everything into `HomeState` / `DrawerState`
- `ui/home`, `ui/drawer`, `ui/editor`, `ui/onboarding` — screens; `ui/components/Reorder.kt` — drag-to-reorder
