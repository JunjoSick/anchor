# Anchor

A small Android app for people with ADHD (or anyone whose short-term goals keep slipping out of their head).

The idea is simple: the problem usually isn't *knowing* what to do, it's remembering it at the right moment. So Anchor keeps your goals **in sight**, on the home screen, the lock screen and floating over your apps, instead of hiding them in an app you have to remember to open.

## What it does

- **Goals that stay in sight:** a home-screen widget, the lock screen, and an optional little bubble that floats over every app with your top goal. Tap a goal to tick it off.
- **Add a goal or a note in two seconds:** from the widget, a Quick Settings button, the app icon (long-press it for "New note"), or straight from the notification. The keyboard is already open.
- **Focus timer:** pick how long, then start now or **"Start in 5 min"**: a short get-ready countdown, after which focus begins by itself. A shrinking disc so you *see* time passing, a nudge halfway and 5 minutes before the end, and an alarm that keeps ringing until you tap Done. On Android 16 it's a **Live Update**: the countdown sits in the status bar, with a progress bar on top of your notifications.
- **Reminders:** meds, water, bedtime. On set days, or **every few days or hours**. Quiet ones are a normal notification; important ones can **ring like an alarm** until you answer. When one goes off it stays on your goal list until you tick it off, so it can't get swiped away and forgotten.
- **Notes:** for things that aren't tasks: thoughts, ideas, stuff to look up. One that turns out to be a task becomes a goal with one tap.
- **No guilt:** unfinished goals from yesterday ask once: keep or drop. Dropping is fine.
- **Light on battery:** nothing runs while the screen is off.

Needs **Android 12 or newer**. Everything stays on your phone: no account, no internet, no ads.

## Install it (no tech skills needed)

1. On your phone, tap this link to download the app: **[⬇ Download Anchor.apk](https://github.com/JunjoSick/anchor/releases/latest/download/Anchor.apk)**
   (It always gets the newest version. You can also find it on the [Releases page](https://github.com/JunjoSick/anchor/releases), under *Assets*.)
2. Open the downloaded file. Your phone will probably say it can't install apps from this source. Tap **Settings**, turn on **Allow from this source**, then go back and tap **Install**.
   - This warning appears for any app that doesn't come from the Play Store; it's normal.
3. Open **Anchor**. It asks to send notifications; say **Allow** (timers and reminders need it).

To update later, tap the same download link again and install it over the old one. Your goals are kept.

## First-time setup (2 minutes)

The app shows a **Setup** card with buttons for each of these. Do the ones you like:

- **Put your goals on the home screen** adds the widget.
- **Keep a goal floating on screen** turns on the bubble. Android asks you to allow *Display over other apps*: find Anchor in the list and switch it on.
- **Keep timers on time:** some phones (Samsung, Xiaomi, OnePlus…) pause apps to save battery, which can make timers late. Allow it if you have one of those.
- At the bottom of the app: **Add an "Add goal" button to Quick Settings** puts a one-tap button in the pull-down menu.

## Tips

- Keep the list **short**. 3 goals you'll actually do beat 12 you'll ignore. The app nudges you if the list gets long.
- Make goals **small and specific**: "reply to Sam about Saturday", not "sort out messages".
- Stuck? Tap ▶ next to a goal and choose **Just start · 5 min**. Starting is the hard part; five minutes is easy to say yes to. Not quite ready? **Get ready, then focus** gives you a few minutes to wrap up, then starts for you.
- The floating bubble can be dragged anywhere. Push it off the side of the screen and it tucks into a small tab; tap the tab to bring it back.

---

## For developers

Kotlin + Jetpack Compose (Material 3 Expressive), Glance for the widget, no backend. Data is three small JSON files on the device (goals, reminders, notes). Unit tests for the timing code: `./gradlew testDebugUnitTest`.

Build with Android Studio, or from the command line with JDK 17+ and the Android SDK:

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Release builds are shrunk with R8; if you add a library that creates classes by name, check `app/proguard-rules.pro` and test the release build, not just the debug one.
