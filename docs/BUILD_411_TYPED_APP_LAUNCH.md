# Netrunner-Launcher Build 411: Typed App Launch

Build 411 makes app names easier to discover from the existing terminal prompt.
Typing an app name now searches the installed app's displayed label and Android
package/activity identifiers when the normal label lookup does not find a match.

For example, on a device where Android lists WhatsApp Business as **WA Business**
and installs it as `com.whatsapp.w4b`, typing `WA Business`, `whatsapp`, or
`com.whatsapp.w4b` opens that app through the existing launcher flow.

## Matching behavior

- Existing exact and prefix label matches keep priority.
- Additional matching accepts familiar words from the displayed label, package
  name, or activity name, without requiring the user to know Android's internal
  identifier.
- Multiword input must match every typed word; a small one-character typo is
  tolerated for longer words.
- When several apps match, label matches rank ahead of package/activity matches,
  with the launcher's existing profile and usage information used to break ties.
- Apps hidden from the launcher's visible app list are not newly exposed by this
  command path.

## Preserved interface

This update changes command-to-app resolution only. It does not change the
terminal prompt, Home dashboard, theme, wallpaper, navigation, or app settings.

## Version

- Android version code: `411`
- Android version name: `2-typed-app-launch`
- Minimum Android version: API 23 / Android 6.0
- Target SDK: API 36
