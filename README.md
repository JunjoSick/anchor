# Anchor

An Android app for ADHD brains: it keeps your short-term goals in sight on the home screen, the lock screen and floating over your apps, so you don't have to remember to look.

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/JunjoSick/anchor)

or **[⬇ download Anchor.apk](https://github.com/JunjoSick/anchor/releases/latest/download/Anchor.apk)**

## What it does

- **Today:** your goals, with notes underneath. Tap a goal to tick it off, long-press to change it.
- **+:** *Goal*, *Note*, *Remind me*, *Focus*. Also on the floating bubble, the widget, Quick Settings and the app icon's long-press menu.
- **Focus:** ▶ next to a goal, pick how long, *Start*. Or *Start in 5 min* to wrap up first. Counts down in the status bar and rings until you tap Done.
- **Reminders** (the bell on Today): once, on set days, or every few days or hours. Make one ring like an alarm when it matters. When one goes off it waits on your list until you tick it off.
- **No guilt:** yesterday's unfinished goals ask once: keep or drop.

Android 12 or newer. No account, no internet, no ads: everything stays on your phone.

## Install

- **Obtainium** (it tells you about updates): tap the badge on your phone, then *Add*.
- **By hand:** tap *download*, open the file and allow installing from this source when asked. To update, do the same again; your goals are kept.

Then open Anchor, allow notifications, and follow the **Setup** card on Today for the widget, the bubble and (on Samsung, Xiaomi, OnePlus…) keeping timers on time.

## Tips

- Keep the list short: 3 goals you'll do beat 12 you'll ignore.
- Small and specific: "reply to Sam about Saturday", not "sort out messages".
- Stuck? ▶ → *5 min* → *Start*. Five minutes is easy to say yes to.

## Build

Kotlin, Jetpack Compose (Material 3 Expressive) and Glance for the widget. Data is three JSON files on the device.

```
./gradlew testDebugUnitTest assembleRelease
```

Release builds are shrunk with R8: if a new library creates classes by name, add a rule to `app/proguard-rules.pro` and test the release build.
