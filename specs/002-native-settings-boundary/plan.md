# Implementation plan

1. Update architecture documentation and native migration rules.
2. Add `NativeSettingsStore` using Android app-private SharedPreferences for a versioned JSON platform-settings snapshot.
3. Add an origin-restricted `settings` route through the existing message bridge.
4. Add an Android-only `native-settings.js` adapter loaded after current printer settings code.
5. Mirror current printer/notification settings at startup and after successful save/delete/backup-restore actions.
6. Keep current localStorage values as compatibility cache and backup source for this stage.
7. Extend regression tests to recognize the Android-only settings adapter and bridge channel.
8. Build/lint and then perform physical restart/settings checks.
