# Anchor

An Android app for ADHD brains: **to-dos** that stay in sight, **reminders** that wait for their time, and **notes** that never bother you — plus **Focus** and **In&Out** timers when you're ready to act.

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/JunjoSick/anchor)

or **[⬇ download Anchor.apk](https://github.com/JunjoSick/anchor/releases/latest/download/Anchor.apk)**

## What it does

- **To-do:** your list, kept on the home screen, the lock screen and floating over your apps, with notes (plain or checklist) underneath. Tap to tick off, long-press to change, put first, start In&Out, or hide until later.
- **+:** one morphing capture sheet for *To-do*, *Remind*, *Note* (plain or checklist), and *Focus* (also via the floating bubble, widget, Quick Settings and app icon shortcuts).
- **Focus:** ▶ next to a to-do, pick how long, *Start*. Or *Start in 5 min* to wrap up first — it rings when it's time to start. Counts down in the status bar and rings until you tap Done.
- **In&Out:** a 1–5 min timed errand in another app — stays on the floating pill, cues halfway, and covers the feed with a STOP card when time is up.
- **Reminders** (the bell on To-do): once, on set days, or every few days or hours. Rings like an alarm when it matters and lands on your list until you tick it off.
- **No guilt:** to-dos left untouched for 3 days ask cleanly: keep or drop.

Android 12 or newer. No account, no internet, no ads: everything stays on your phone.

## Install

- **Obtainium** (it tells you about updates): tap the badge on your phone, then *Add*.
- **By hand:** tap *download*, open the file and allow installing from this source when asked. To update, do the same again; your list is kept.

Then open Anchor, allow notifications, and follow the **Setup** card on To-do for the widget, the bubble and (on Samsung, Xiaomi, OnePlus…) keeping timers on time.

## Tips

- Keep the list short: 3 to-dos you'll do beat 12 you'll ignore.
- Small and specific: "reply to Sam about Saturday", not "sort out messages".
- Stuck? ▶ → *5 min* → *Start*. Five minutes is easy to say yes to.

## Build

Kotlin, Jetpack Compose (Material 3 Expressive) and Glance for the widget. Data is three JSON files on the device.

```
./gradlew testDebugUnitTest assembleRelease
```

Release builds are shrunk with R8: if a new library creates classes by name, add a rule to `app/proguard-rules.pro` and test the release build.
