# Contributing

Phases is a hobby project that follows my own habits, so I may not take every feature. Bug reports and small fixes are very welcome. For anything bigger, open an issue first so we can talk it over before you put time in.

By sending a pull request you agree that your contribution is licensed under the [Apache License 2.0](LICENSE), like the rest of Phases.

## Build and run

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · minSdk 26, target/compile SDK 37.

Requires JDK 17 and an Android Studio recent enough for AGP 9.4 / API 37, or just the command line:

```
.\gradlew.bat installDebug                 # build + install on the running emulator/device
.\gradlew.bat testDebugUnitTest            # JVM unit tests
.\gradlew.bat connectedDebugAndroidTest    # instrumented tests (uninstalls the app afterwards)
```

### Performance

- **Measure a release build** (R8 on, like the real thing; debug builds are several times slower): `.\gradlew.bat installRelease -PdebugSignedRelease` signs it with the debug key so it installs over a debug build.
- **Baseline profile:** `app/src/main/baselineProfiles/baseline-prof.txt` lists the code that runs at startup and in the drawer, so it's compiled ahead of time. Regenerate it after big UI changes with `.\gradlew.bat :app:generateBaselineProfile` (device or emulator, Android 9+), then copy the `*-prof.txt` from `baselineprofile/build/outputs/connected_android_test_additional_output/` over it.
- **Check it helps:** `.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest` compares cold start and drawer frames with and without the profile (on an emulator add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`; real phones give the meaningful numbers).
- Debug builds run StrictMode and log disk/network work on the main thread and leaked resources.

## Code map

- `data/apps` — installed apps and profiles via `LauncherApps` (+ change callback), icon cache, search, app shortcuts
- `data/notifications` — notification dots listener; `data/backup` — JSON export/import; `data/system` — notification shade, lock-screen accessibility service
- `data/items` — folders, pinned shortcuts ("Add to home screen") and the item keys phases store; `ui/pin` — the phase picker shown for those requests
- `data/widgets` — app widget host; `data/icons` — icon packs, custom names/icons; `data/search` — contacts, settings, web
- `data/rules` — automatic switching rules, `RuleEngine` (which phase wins), calendar and location watchers, schedule alarm; `data/focus` — per-phase focus settings and the launch gate; `data/suggestions` — history and `SuggestionEngine`; `data/share` — phase files, links, QR codes; `data/importer` — other launchers' backups; `ui/tile` — Quick Settings tile; `api` — switching from other apps (broadcast, shortcuts, links); `data/themes/ThemeEffects` — per-phase wallpaper and sound
- `data/db`, `data/themes`, `data/settings` — Room (phases, assignments, favorites), repositories, DataStore
- `LauncherViewModel` — combines everything into `HomeState` / `DrawerState`
- `ui/home`, `ui/drawer`, `ui/editor`, `ui/onboarding` — screens; `ui/components/Reorder.kt` — drag-to-reorder

## Releasing

Every push to `main` and every pull request is built, tested and linted (*CI* workflow). To publish a version:

1. Add a `## [0.4.0] - YYYY-MM-DD` section at the top of [CHANGELOG.md](CHANGELOG.md): a one-line headline, then the changes people will notice. Commit it.
2. Push a tag:

   ```
   git tag v0.4.0
   git push origin v0.4.0
   ```

   or run *Release* from the Actions tab with the version.

The *Release* workflow builds a signed APK and publishes it on the Releases page. The release notes are that version's CHANGELOG section, followed by the commits since the last release. Without a section, only the commit list is used.

Signing uses the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Keep a backup of the keystore: Android only installs updates signed with the same key.
