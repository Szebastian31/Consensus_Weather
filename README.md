# Consensus Weather

A playful weather app that shows a **36-hour forecast**, a **part-by-part** day/night breakdown, and a **7-day outlook** for **any city you add**. Every number is a **live average of 7 national weather models** — ECMWF, DWD ICON, NOAA GFS, MET Norway (Yr), Météo-France, JMA and ECCC — fetched from the free [Open-Meteo](https://open-meteo.com) API (no API key, no account). It also shows sun/moon rise & set with the current moon phase, a live pollen count, air quality, and lifestyle tips. The background is a living sky gradient that shifts with the time of day and conditions. Gliwice ships as an offline fallback so the app shows something even with no connection.

The app is a self-contained web app. It runs two ways from the same file:
- wrapped in a native Android **WebView** and installed as a normal **APK**, and
- as a **home-screen web app** on iPhone/Android via a hosted link (GitHub Pages).

You never need Android Studio, Gradle, or a Mac/PC toolchain — **GitHub builds the APK for you in the cloud.**

---

## Download & install (Android)

Grab the latest APK from either:
- **Actions** tab → the most recent green run → **Artifacts** → `ConsensusWeather-debug-apk`, or
- the **Releases** page (right sidebar) → latest release → `app-debug.apk`.

Then on the phone:
- Download `app-debug.apk` (open the page in the phone's browser, or transfer the file over).
- Tap it. OS will ask to **allow installing unknown apps** from that source — enable it, then tap **Install**.
- Open **Consensus Weather** from your app drawer.

It's a debug-signed APK, which is fine for installing on your own phone (it just can't be published to the Play Store as-is).

## Install on Android

- Download `app-debug.apk` to the phone (open the Actions/Releases page in the phone's browser, or transfer the file).
- Tap the file. OS will ask to **allow installing unknown apps** from that source — enable it, then tap **Install**.
- Open **Consensus Weather** from your app drawer.

It's a debug-signed APK, which is fine for installing on your own phone (it just can't be published to the Play Store as-is).

## Install on iPhone (or any phone, via the web link)

iOS can't install APKs, but the same app runs as a full-screen home-screen app:

- Open the hosted link in **Safari**: `https://szebastian31.github.io/Consensus_Weather/`
- Tap **Share** → **Add to Home Screen** → **Add**.
- It launches full-screen with the app icon, and updates whenever you push to the `docs/` folder.

---

## Using the app

- A centered **location pill** at the top shows the city you're viewing. **Tap it** to open the manage popup.
- In the popup you can **search and add** any city (up to 10), **remove** saved cities, or **use your current GPS location**. Your current-location entry can't be removed.
- With more than one city saved, faint previews of the previous/next city sit on either side of the pill — **tap** them to jump, or **swipe left/right** anywhere on the screen to switch cities.
- **📍 My location:** on first launch the app asks for location permission and sets the view to your GPS location automatically (falling back to Gliwice if you decline or it's unavailable).
- **°C / °F toggle** lives in the main weather card.
- **Tap the main weather card** for the part-by-part day/night breakdown popup.
- **Pull down** at the top to refresh the current city.
- Your saved cities and last-viewed city persist on the device.

### When it fetches data (and when it doesn't)

Data is fetched from Open-Meteo **only** at these moments — it never polls in the background:
- when you **open the app** (and when you bring it back to the foreground),
- when you **add, switch, or GPS-locate** a city,
- when you **pull down to refresh**.

Adding a city or using GPS needs an internet connection. Everything is served over HTTPS with no API key. Location permission is optional — the app works fully without it; you just won't have the 📍 shortcut.

## Rebuilding after edits

Change anything (e.g. `app/src/main/assets/index.html` to tweak the UI), commit, and GitHub rebuilds a new APK automatically. Bump `versionCode`/`versionName` in `app/build.gradle` if you want Android to treat it as an update. For the web/iPhone version, update the copy in `docs/` too.

## Project layout

```
Consensus_Weather/
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ assets/index.html            ← the whole weather app (UI + live data)
│     ├─ java/com/consensus/weather/MainActivity.java
│     └─ res/…                        ← icon, theme, strings
├─ docs/                              ← web/home-screen version (GitHub Pages)
│  ├─ index.html
│  └─ emblem.png
├─ .github/workflows/build.yml        ← cloud APK build
├─ build.gradle · settings.gradle · gradle.properties
```

## Notes & tech choices

- **minSdk 24 / targetSdk 34**, Java 17, AGP 8.5.2, Gradle 8.7 — a stable, proven combo.
- No third-party libraries: pure framework `WebView` + `Activity`, so builds are fast and rarely break.
- The chart, all weather icons, and sun/moon calculations are hand-rolled (SVG + a trimmed SunCalc) — no external chart library or CDN — so the UI renders even offline.
- Live data & city search require internet; the bundled Gliwice snapshot covers the no-connection case.
