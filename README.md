# Persistent Reminder — Android MVP

A native Android app with full-screen event reminders that work like an alarm clock. Add one-time events manually, store them on your device, and mark reminders as done or snooze them for five minutes. The interface is in English. No account or internet connection is required to use the app.

## Download an APK on your phone

1. Open [Actions](https://github.com/ut8uu/persistent-reminder-android/actions/workflows/android.yml).
2. Select the latest successful **Build Android APK** run (green check mark).
3. Under **Artifacts**, download **nagadai-debug-apk**. You need to sign in to GitHub.
4. Extract the ZIP and open `app-debug.apk`. Allow installation from that source if Android asks.
5. Open **Persistent Reminder** and tap **Set up permissions** to grant the required access.

The workflow runs on pushes to `main`, on pull requests, and manually using **Run workflow**. APKs and Android Lint reports are kept for seven days. If an artifact has expired, run the workflow again. Builds use a standard Linux runner.

This is a debug APK signed with an automatically generated build key. The key is not preserved between workflow runs, so installing a newer build may require uninstalling the previous version, which also deletes locally saved events. A permanent signing key is needed for reliable updates that preserve app data.

## Build locally

1. Install a recent Android Studio version, Android SDK Platform 35, and JDK 17 or later.
2. Open the repository root as a Gradle project. Configure Gradle 8.9; a Gradle wrapper is not included.
3. Run Gradle Sync and **Build → Build APK(s)**, or use an installed Gradle 8.9: `gradle :app:assembleDebug`.
4. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.
5. Install on Android 8.0 or later. Use **Set up permissions** to allow notifications, exact alarms, and full-screen notifications on Android versions that require them.

To run the same checks as CI: `gradle :app:assembleDebug :app:lintDebug`.

## How reminders work

Events are stored only on your device. **Done** deletes the event. **Snooze for 5 minutes** reschedules it for five minutes from now. After a reboot, future reminders are scheduled again and overdue reminders are triggered after startup. Event times are stored as absolute timestamps; changing the time zone changes the displayed local time.

Alarms use a notification channel with alarm audio and `FLAG_INSISTENT`. The app requests a full-screen presentation through `fullScreenIntent`; when the phone is unlocked, Android may show a heads-up notification instead. Volume, sound duration, Do Not Disturb, channel settings, and manufacturer restrictions affect alerts. The app does not force a muted channel to play sound. Pressing Back on the alarm screen does not complete the event: its actions remain available in the notification.

App text, date formatting, and the in-app date and time pickers use English. Android permission dialogs and system settings follow the phone's language. Event titles are displayed exactly as entered.

## Manual device checks

- Deny each permission: the app explains the required setup and prevents adding events until the required permissions are enabled.
- Add an event two minutes ahead and lock the phone: check sound, vibration, and full-screen presentation.
- Tap **Done**: the alert stops and the event disappears.
- Snooze from both the full-screen view and the notification: the alert stops and returns after five minutes.
- Delete a future event: its alarm should not trigger.
- Reboot with a future event saved: its alarm should be restored.
- Check simultaneous events, an unlocked screen, Do Not Disturb, denied full-screen access, and revoked exact alarm access.
- Check the English interface and date/time pickers with the phone set to another language.

## Status

An early prototype without external app libraries. GitHub Actions builds the debug APK and runs Android Lint. Device behavior still needs broader testing, including the English interface. The app has not been validated for critical reminders. Event editing, recurring events, calendar synchronization, and ringtone selection are not implemented yet.
