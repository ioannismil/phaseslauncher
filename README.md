<p align="center">
  <img src="docs/brand/icon.svg" width="96" height="96" alt="">
</p>

<h1 align="center">Phases</h1>

<p align="center">
  <strong>A home screen that changes with your day.</strong><br>
  Work, gym, weekend, night: give each part of your day its own apps, and switch with one swipe.
</p>

<p align="center">
  <a href="https://github.com/ioannismil/phaseslauncher/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/ioannismil/phaseslauncher?label=download&color=4C7DFF"></a>
  <a href="https://github.com/ioannismil/phaseslauncher/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/ioannismil/phaseslauncher/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/github/license/ioannismil/phaseslauncher?color=lightgrey"></a>
</p>

---

Most launchers show you everything, all the time. **Phases** shows you what you need *right now*. A phase is a named set of apps with its own look and behavior: your Work phase can show Mail and Calendar and silence the phone, while Weekend brings back the games and the music. Swipe in from the edge of the screen, slide to a phase, let go, and the whole home screen changes.

> [!NOTE]
> **This is a hobby project, made for me.** I built Phases to fit the way I use my own phone, so it follows my habits and my taste rather than a roadmap. There are no promises about features or timelines. You're very welcome to try it, fork it, or borrow ideas from it.

## What it does

- **Phases.** Each one has its own apps, folders, widgets, wallpaper, colors, icon pack and sound mode (sound on, vibrate, silent, do not disturb). A dock of favorites stays put whichever phase you're on.
- **Switch with a swipe.** Swipe in from the edge bar and pick a phase. Choose how they appear: an arc, an edge rail, a picker wheel, a dial, a card stack or color bands.
- **Switch automatically.** Rules can change phase by time of day, Wi-Fi network, Bluetooth device or place.
- **Stay focused.** A phase can hold back distracting apps and filter notifications until you switch away.
- **Works with your automations.** Tasker, Samsung Modes and Routines and shortcuts can switch phases too.
- **Share and back up.** Send a phase as a file, link or QR code, and save everything to a backup file.

## Pick your switcher

Swipe in from the edge bar and the phases fan out in the style you choose in *Settings → Phase switcher → Style*. Slide to one and let go to switch.

<table align="center">
  <tr>
    <td align="center"><img src="docs/screenshots/framed/switcher-arc.png" width="220" alt="Arc switcher: phases fanned out in a half circle beside the edge"><br><b>Arc</b><br><sub>Phases fan out around your thumb</sub></td>
    <td align="center"><img src="docs/screenshots/framed/switcher-edge-rail.png" width="220" alt="Edge rail switcher: a list sliding out of the edge with the chosen phase popped out"><br><b>Edge rail</b><br><sub>A list slides out of the edge</sub></td>
    <td align="center"><img src="docs/screenshots/framed/switcher-picker-wheel.png" width="220" alt="Picker wheel switcher: phase names scrolling past a highlighted strip"><br><b>Picker wheel</b><br><sub>Names scroll past as you slide</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/framed/switcher-dial.png" width="220" alt="Dial switcher: phases on a full circle with the chosen one named in the middle"><br><b>Dial</b><br><sub>A full circle; slide onto a phase</sub></td>
    <td align="center"><img src="docs/screenshots/framed/switcher-card-stack.png" width="220" alt="Card stack switcher: a card per phase showing its apps"><br><b>Card stack</b><br><sub>A card per phase, with its apps</sub></td>
    <td align="center"><img src="docs/screenshots/framed/switcher-color-bands.png" width="220" alt="Color bands switcher: a full-width bar per phase, the chosen one expanded"><br><b>Color bands</b><br><sub>The bar under your finger grows</sub></td>
  </tr>
</table>

## Download

<a href="https://github.com/ioannismil/phaseslauncher/releases/latest"><img src="https://img.shields.io/badge/Get%20it%20on-GitHub-24292F?style=for-the-badge&logo=github" height="48" alt="Get it on GitHub"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ioannismil/phaseslauncher"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="48" alt="Get it on Obtainium"></a>

Grab the latest APK from **[Releases](https://github.com/ioannismil/phaseslauncher/releases/latest)** and open it on your phone (Android 8.0 or newer). Each release installs over the one before, so updating is just installing the new one. With [Obtainium](https://github.com/ImranR98/Obtainium) you'll get updates automatically.

Phases doesn't ask for internet access. It has no ads, no tracking and no account, and everything it learns about your habits stays on your phone.

## Learn more

- **[Using Phases](docs/usage.md):** setting it as your home app, every gesture, launcher settings, per-phase options, setup and search.
- **[Switching from other apps](docs/automation.md):** Tasker, Samsung Modes and Routines, shortcuts and `phases://` links.
- **[What's new](CHANGELOG.md):** the changes in each release.
- **[Contributing](CONTRIBUTING.md):** building it, the code map and how releases are made.

## License

[MIT](LICENSE). Fork it, change it, borrow from it.
