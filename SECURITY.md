# Security

## Reporting a problem

Please report security problems privately: open the repository's **Security** tab and choose **Report a vulnerability**. Don't open a public issue for them.

Say what an attacker could do and how. A small file, link or `adb` command that shows it helps a lot. I'll reply as soon as I can, and credit you in the release notes if you'd like.

## What's supported

Only the latest release gets fixes. Updates install over older versions, so updating is the fix.

## What's worth reporting

Phases has no internet access, so the ways in are what other apps on the phone can send it:

- **Shared phases**: `phases://phase` links, QR codes and `.phases.json` files ([ThemeSharing](app/src/main/java/com/phaseslauncher/data/share/ThemeSharing.kt)).
- **Switching from other apps**: the `SWITCH_PHASE` broadcast and `phases://switch` links ([PhaseApi](app/src/main/java/com/phaseslauncher/api/PhaseApi.kt)). The broadcast is off until the user turns it on.
- **Imported files**: backups and other launchers' backups ([BackupRepository](app/src/main/java/com/phaseslauncher/data/backup/BackupRepository.kt), [LauncherImporter](app/src/main/java/com/phaseslauncher/data/importer/LauncherImporter.kt)).
- **"Add to home screen"** requests from other apps ([PinItemActivity](app/src/main/java/com/phaseslauncher/ui/pin/PinItemActivity.kt)).
- **Data leaving the phone**: places, Wi-Fi network and Bluetooth device names and app history are meant to stay on it (Android's cloud backup leaves Phases out).

Some things work as designed and aren't vulnerabilities: when allowed, any app can switch phases, and switching can change sound settings and the wallpaper. Anything that needs root or a phone already compromised is out of scope.
