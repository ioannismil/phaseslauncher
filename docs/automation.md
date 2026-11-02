# Switching phases from other apps

How Tasker, Samsung Modes and Routines, shortcuts and links can switch phases. Back to the [README](../README.md).

Other apps can switch phases, so Phases can follow your system modes instead of competing with them. A switch from another app acts like picking the phase yourself: the phase's wallpaper and sound apply, and rules that apply at that moment don't undo it. These switches don't feed schedule suggestions.

- **Broadcast** (Tasker, MacroDroid, Automate, adb): first turn on Settings → Automation apps → *Other apps can switch phases*. It's off by default, because a phase can silence the phone and hold back apps. Then send:
  - action `com.phaseslauncher.action.SWITCH_PHASE`, package `com.phaseslauncher` (Android only delivers it with the package set);
  - extra `phase` with the phase's name (any case), or `phase_id` with its id.

  In Tasker: *Send Intent* → Action as above, Extra `phase:Work`, Package `com.phaseslauncher`, Target *Broadcast Receiver*. From adb: `adb shell am broadcast -a com.phaseslauncher.action.SWITCH_PHASE -p com.phaseslauncher --es phase Work`. The result code is `-1` on success, `1` if no phase matches and `2` if the setting is off, with the reason as the result data.
- **App shortcuts:** each phase gets a "Switch to …" shortcut, kept in step with your phases. Apps that run app shortcuts, such as Samsung Modes and Routines and Google Assistant, can use them, and they show when you long-press Phases in an app list. They don't need the setting.
- **Link / activity:** `phases://switch?phase=Work` (or `?phase_id=12`) opened as an activity, for example with Tasker's *Send Intent*, Target *Activity*. Also works without the setting. Android doesn't let background apps start activities, so this only runs from something on screen or from an automation app the system trusts.
