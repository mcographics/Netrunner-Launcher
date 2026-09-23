# Netrunner-Launcher Build 410: Storage Gauge + Battery Monitor

Build 410 extends the Netrunner-Launcher red terminal Home dashboard with two
at-a-glance device-status changes designed around the open space in the existing
system-monitor layout.

## Home dashboard changes

- The former textual `STORAGE` row is replaced by a live `BATTERY` row.
- The battery row shows the current phone battery percentage with the same
  terminal-style activity bar used by the CPU, GPU, and RAM rows.
- Internal storage moves into a circular gauge in the open area at the right
  side of the system monitor.
- The storage gauge shows percentage used, with the remaining capacity rendered
  as a dimmer portion of the ring.
- The gauge inherits the current ASCII/system-monitor accent color so it remains
  visually consistent with the active terminal theme.
- The gauge locates the `MEM FREE` section dynamically rather than relying on
  a fixed screen coordinate, allowing it to follow the dashboard if status rows
  above it shift.

## Data sources

Battery percentage is read from Android's sticky
`ACTION_BATTERY_CHANGED` broadcast. Storage usage continues to use Android
`StatFs` against the launcher's internal data partition, matching the storage
source already used by the system monitor.

The storage gauge does not estimate or fabricate capacity. It derives used
space from total bytes minus available bytes and refreshes while the launcher
Home surface is active.

## Preserved Build 409 behavior

Build 410 keeps the Build 409 red terminal dashboard foundation, including:

- live network, IPv4, memory, CPU, GPU, RAM, SoC, and GPU-model information
- compact native weather
- 12-hour clock with seconds and AM/PM
- wallpaper-only secondary Home page
- launcher-scoped fullscreen behavior
- top-aligned notification/output tray
- manual and automatic five-minute clearing of clearable Android notifications
- fixed quick-app access and command-first navigation

## Version

- Android version code: `410`
- Android version name: `2-storage-gauge-battery`
- Minimum Android version: API 23 / Android 6.0
- Target SDK: API 36

## Verification status

The formatter unit test has been updated for the new battery row. Source review
confirms that the change is isolated to the Home dashboard/status surface and
does not alter the wallpaper-only page, notification tray behavior, or Termux
workspace paging.

A signed APK and physical-device verification are not recorded in this source
release note. Those should be completed before presenting a downloadable binary
as device-verified.

## Attribution

Netrunner-Launcher is Kenneth Salmon's customized fork of DvilSpawn's Re:TUI,
which continues Francesco Andreuzzi's original T-UI Console Launcher. The fork
preserves the upstream project history, MIT license, and attribution.
