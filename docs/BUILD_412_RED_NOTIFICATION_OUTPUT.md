# Netrunner-Launcher Build 412: Red Notification Output

Build 412 aligns notification output with the launcher's red terminal theme.
Previously, the default notification format could add green text and a teal
title even when the saved profile used red notification and module colours.

## Changes

- The default notification text colour is now `#ffff1b1b`.
- The default notification format explicitly renders notification titles and
  bodies in red; timestamps and package names inherit the same red base colour.
- The notification reveal animation uses the configured notification text
  colour instead of a fixed green.
- Existing notification profiles, the Home layout, wallpaper, and other theme
  settings remain unchanged.

## Version

- Android version code: `412`
- Android version name: `2-red-notification-output`
- Minimum Android version: API 23 / Android 6.0
- Target SDK: API 36
