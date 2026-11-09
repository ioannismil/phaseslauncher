# Changelog

The changes people will notice in each release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

**Swipe between phases.** Swipe left or right on the home screen for the next or previous phase.

### Added
- *Swipe between phases*: swipe sideways on the home screen and the phase's apps slide out as the next phase's slide in. Turn it on in *Settings → Gestures*.
- Three new phase switcher styles in *Settings → Phase switcher → Style*: *Tiles* (a grid of colored tiles near your thumb), *Magnifier* (a dock of icons along the edge that swells under your finger) and *Slider* (swipe in along a track; how far you go picks the phase).

### Changed
- The app drawer follows your finger: swipe up from home to pull it up, or down from the top of the list to push it away. A short pull is enough to open or close it.
- The back gesture previews where it's going: Settings shrinks toward the edge you swipe from, and the drawer sinks, before you let go.
- Pressing Home closes whatever is open, like menus, dialogs, Settings or the phase switcher, and takes you back to the plain home screen.

### Fixed
- Sliding from the phase switcher bar no longer turns into a Back gesture on phones where that gesture reaches further in, like Samsung phones with a higher *Back gesture sensitivity*. The bar is also easier to grab.
- The keyboard no longer pops up over the home screen after pressing Home with the drawer open.
- Missing spaces between sentences in setup and in the screen-lock explanation.

## [0.3.4] - 2026-11-02

### Changed
- Phases is left out of Google's cloud backup, so your places, Wi-Fi and Bluetooth names and app history stay on your phone. Moving to a new phone with a direct phone-to-phone transfer still brings everything along, and *Settings → Backup* to a file works as before.

### Fixed
- Leaving the screen in the middle of an import, backup or share no longer shows it as failed.

## [0.3.3] - 2026-11-02

### Added
- Put apps, folders and widgets anywhere on a phase's home screen: long-press and drag to any cell, gaps and all. Whatever's in the way moves to the nearest free spot.
- Widgets sit in the grid next to apps and can be resized in cells (long-press → *Resize*).

### Changed
- *Fit icons to the screen* keeps your column count and only shrinks icons so your rows fit.
- *Icon arrangement* now decides where apps go until you place them yourself.
- Backups include where everything is on the home screen.

## [0.3.2] - 2026-11-02

**New apps find their phase.** Install an app and Phases asks where it goes.

### Added
- When you install an app, Phases asks which phases it goes on: tick them and tap *Add*, or *Skip*. Turn it off in Settings → Suggestions → New apps.

## [0.3.1] - 2026-11-02

**A new icon.** Four parts of the day around the moon, in the phase colors.

### Changed
- New app icon, also used for themed icons on Android 13 and newer.

## [0.3.0] - 2026-11-02

**The first public release.** Give each part of your day its own home screen, and switch with one swipe.

### Phases
- Each phase has its own apps, folders, widgets, wallpaper, colors, icon pack, home grid and sound mode (sound on, vibrate, silent, do not disturb).
- A shared dock of favorites, or a dock of its own for any phase.
- Starter phases at setup (Work, Gaming, Social, Music, Bedtime and more), pre-filled with matching installed apps.

### Switching
- Swipe in from the edge bar and pick a phase, in one of six styles: arc, edge rail, picker wheel, dial, card stack or color bands.
- Automatic switching by schedule, place, Wi-Fi network, calendar event, Bluetooth device, headphones, game controller or charging, with priorities, delays and *Ask first*.
- Tasker, Samsung Modes and Routines and other apps can switch phases with a broadcast, an app shortcut or a `phases://` link.
- A Quick Settings tile.

### Focus
- A phase can pause or block distracting apps, filter notifications and set daily time limits.

### Everything else
- Share a phase as a file, link or QR code. Back up and restore everything to a file.
- Import home screens from Nova Launcher, Lawnchair and other Launcher3-based launchers.
- A drawer with search for apps, contacts and settings, plus a calculator and unit conversions. It also has work profile and Private Space tabs, hidden apps, icon packs and themed icons.
- Suggestions learned on the phone: apps to add to a phase, schedules to set and phases to switch to.

[Unreleased]: https://github.com/ioannismil/phaseslauncher/compare/v0.3.4...HEAD
[0.3.4]: https://github.com/ioannismil/phaseslauncher/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/ioannismil/phaseslauncher/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/ioannismil/phaseslauncher/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/ioannismil/phaseslauncher/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/ioannismil/phaseslauncher/releases/tag/v0.3.0
