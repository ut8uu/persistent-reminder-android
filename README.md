# Persistent Reminder — Android MVP

A native Android app with full-screen event reminders that work like an alarm clock. Add one-time or recurring events manually, store them on your device, and mark reminders as done or snooze them for five minutes. The interface is in English. No account or internet connection is required to use the app.

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

## Repeating events

When adding an event, choose a repeat mode, then select its start date and time:

| Mode | Settings | Example |
| --- | --- | --- |
| One time | Start date and time | An appointment tomorrow at 14:00 |
| Every day | Start date and time | Every day at 10:00 |
| Custom interval | A positive number of minutes, hours, or days | Every 37 minutes, every 2 hours, or every 3 days |
| Daily series | Interval and reminders per day | 5 times every 1 hour, starting at 11:00: 11:00, 12:00, 13:00, 14:00, 15:00, then again the next day |

Daily repeats and series follow the local clock, including daylight saving and time zone changes. Nonexistent times during a spring clock change move forward by the DST gap; an ambiguous autumn time uses its first occurrence. Duplicate slots caused by a clock change coalesce. A daily series can cross midnight, but the time between its first and last reminder must be less than 24 hours. It supports 1–1,440 reminders per day, with a minimum interval of one minute.

Custom intervals measure elapsed time from the chosen start: one day means 24 hours, so these can shift local clock time across daylight saving changes. Intervals can be up to 36,500 days. Use **Every day** to keep the same local clock time.

**Done** dismisses the current alert and keeps recurring events scheduled. **Snooze for 5 minutes** delays the current alert without shifting the repeat schedule. If the next regular occurrence arrives first, it replaces the pending snooze. **Delete** removes the entire event and all its future reminders.

The next occurrence is scheduled when an alarm fires, even if you have not pressed Done. An event has a single active alert: overlapping or missed occurrences do not create a backlog. After downtime, one overdue alert is shown and the schedule continues with the next future occurrence. Existing one-time events remain compatible with the new storage format.

## How reminders work

Events are stored only on your device. For one-time events, **Done** deletes the event. **Snooze for 5 minutes** schedules another alert five minutes from now. After a reboot, future reminders are scheduled again and overdue reminders are triggered after startup. One-time events keep their absolute timestamp. Daily repeats and daily series preserve their chosen local clock time.

Alarms use a notification channel with alarm audio and `FLAG_INSISTENT`. The app requests a full-screen presentation through `fullScreenIntent`; when the phone is unlocked, Android may show a heads-up notification instead. Volume, sound duration, Do Not Disturb, channel settings, and manufacturer restrictions affect alerts. The app does not force a muted channel to play sound. Pressing Back on the alarm screen does not complete the event: its actions remain available in the notification.

App text, date formatting, and the in-app date and time pickers use English. Android permission dialogs and system settings follow the phone's language. Event titles are displayed exactly as entered.

## Recurrence tests

CI runs deterministic recurrence checks before building the APK. These cover daily repeats, five hourly reminders, custom intervals, midnight rollover, late completion, long downtime, and daylight saving transitions. With a JDK installed, run:

```sh
mkdir -p /tmp/reminder-tests
javac -d /tmp/reminder-tests app/src/main/java/ua/reminder/Recurrence.java tests/RecurrenceTest.java
java -cp /tmp/reminder-tests ua.reminder.RecurrenceTest
```

## Manual device checks

- Deny each permission: the app explains the required setup and prevents adding events until the required permissions are enabled.
- Add an event two minutes ahead and lock the phone: check sound, vibration, and full-screen presentation.
- Tap **Done**: the alert stops and the event disappears.
- Snooze from both the full-screen view and the notification: the alert stops and returns after five minutes.
- Delete a future event: its alarm should not trigger.
- Reboot with a future event saved: its alarm should be restored.
- Check simultaneous events, an unlocked screen, Do Not Disturb, denied full-screen access, and revoked exact alarm access.
- Create a daily event at 10:00 and a daily series of five hourly reminders starting at 11:00; check the displayed next occurrence and repeat summary.
- For a quick device check, create a series of three reminders one minute apart. Verify that Done keeps later occurrences, Snooze preserves the original cadence, and Delete cancels both regular and snoozed alerts.
- Reboot during a series, dismiss an alert after several intervals, and change the device time zone; verify that there is only one active alert per event and that the schedule continues.
- Check the English interface and date/time pickers with the phone set to another language.

## Status

An early prototype without external app libraries. GitHub Actions builds the debug APK and runs Android Lint. Device behavior still needs broader testing, including the English interface. The app has not been validated for critical reminders. Event editing, calendar synchronization, and ringtone selection are not implemented yet.
