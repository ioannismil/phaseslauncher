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
| Edge bar (right or left) | Swipe toward the middle, release on a theme | Switch theme (release back at the edge to cancel) |
| Edge bar / theme label under the clock | Tap | Open the theme arc; tap a theme, long-press it for Edit / Delete |
| Theme arc | Release on **+** | New theme |
| Anywhere on home | Swipe up | App drawer with search |
| App (grid, dock, drawer) | Long-press | App menu |
| App (grid, dock) | Long-press, then drag | Reorder |
| Empty space | Long-press | New theme, wallpaper, launcher settings |
| Drawer search bar | Gear | Launcher settings (bar side, themes, default home, setup) |

## Code map

- `data/apps` — installed apps via `LauncherApps` (+ change callback), icon cache, search
- `data/db`, `data/themes`, `data/settings` — Room (themes, assignments, favorites), repositories, DataStore
- `LauncherViewModel` — combines everything into `HomeState` / `DrawerState`
- `ui/home`, `ui/drawer`, `ui/editor`, `ui/onboarding` — screens; `ui/components/Reorder.kt` — drag-to-reorder
