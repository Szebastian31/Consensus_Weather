# Consensus Weather — Android app

A playful weather app (in the spirit of the *(Not Boring) Weather* look) that shows a
36-hour forecast, a part-by-part day/night breakdown, and a 7-day outlook for **any city
you add**. All numbers are a **live average of 7 national weather models** — ECMWF,
DWD ICON, NOAA GFS, MET Norway (Yr), Météo-France, JMA and ECCC — fetched from the free
[Open-Meteo](https://open-meteo.com) API (no API key, no account). Gliwice ships as an
offline fallback so the app shows something even with no connection.

The app is a self-contained web app wrapped in a native Android WebView, so it installs
and runs as a normal APK. You never need Android Studio, Gradle, or a Mac/PC toolchain —
**GitHub builds the APK for you in the cloud.**

---

## Get a working APK in ~5 minutes

### 1. Create a GitHub repo
- Go to <https://github.com/new>, name it e.g. `consensus-weather`, click **Create repository**.

### 2. Upload these files
Easiest (no git needed):
- On the new repo page click **uploading an existing file**.
- Drag in **everything inside this `weather-app-android` folder** (keep the folder
  structure — the `app/`, `.github/` folders and the `.gradle`/`.yml` files must stay where they are).
- Click **Commit changes**.

> Tip: GitHub's web uploader keeps folders if you drag the whole tree in. If it flattens
> them, use the git steps below instead.

Or with git:
```bash
cd weather-app-android
git init && git add . && git commit -m "Consensus Weather"
git branch -M main
git remote add origin https://github.com/<you>/consensus-weather.git
git push -u origin main
```

### 3. Let GitHub build it
- The push triggers the **Build APK** workflow automatically.
- Open the **Actions** tab and watch it run (~2–4 min). First run may take a little longer.

### 4. Download the APK
Two places to grab it once the run is green:
- **Releases** (right sidebar of the repo) → latest release → **app-debug.apk**, or
- **Actions** → the finished run → **Artifacts** → `ConsensusWeather-debug-apk`.

### 5. Install on your Oppo Find X9 Pro
- Transfer `app-debug.apk` to the phone (or open the Releases page in the phone's browser
  and download it directly).
- Tap the file. ColorOS will ask to **allow installing unknown apps** from that source —
  enable it, then tap **Install**.
- Open **Consensus Weather** from your app drawer.

That's it. It's a debug-signed APK, which is completely fine for installing on your own
phone (it just can't be published to the Play Store as-is).

---

## Using the app
- Three tiles at the top: two **side slots** and the **center** (the city you're viewing).
- Tap an empty side (**＋**) to add a city; tap a filled side to bring it to the center (they swap).
- Each side has a small **✎** (change it) and **✕** (clear it); the center's **✎** changes the current city.
- **📍 My location:** on **first launch** the app asks for location permission and sets the center tile to your GPS location automatically (falling back to Gliwice if you decline or it's unavailable). You can also trigger it anytime via the pin on the center tile, or "Use my current location" in the add sheet.
- **Pull down** at the top to refresh the current city.
- Your slots and the last-viewed city persist on the device.

### When it fetches data (and when it doesn't)
Data is fetched from Open-Meteo **only** at these moments — it never polls in the background:
- when you **open the app** (and when you bring it back to the foreground),
- when you **add, switch, or GPS-locate** a city,
- when you **pull down to refresh**.

Adding a city or using GPS needs an internet connection. Everything is served over HTTPS
with no API key. Location permission is optional — the app works fully without it; you just
won't have the 📍 shortcut.

## Rebuilding after edits
Change anything (e.g. `app/src/main/assets/index.html` to tweak the UI), commit/push, and
GitHub rebuilds a new APK automatically. Bump `versionCode`/`versionName` in
`app/build.gradle` if you want Android to treat it as an update.

## Project layout
```
weather-app-android/
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ assets/index.html            ← the whole weather app (UI + live data)
│     ├─ java/com/consensus/weather/MainActivity.java
│     └─ res/…                        ← icon, theme, strings
├─ .github/workflows/build.yml        ← cloud APK build
├─ build.gradle · settings.gradle · gradle.properties
```

## Notes & tech choices
- **minSdk 24 / targetSdk 34**, Java 17, AGP 8.5.2, Gradle 8.7 — a stable, proven combo.
- No third-party libraries: pure framework `WebView` + `Activity`, so builds are fast and
  rarely break.
- The chart and all weather icons are hand-drawn SVG — no external chart library or CDN —
  so the UI renders even offline.
- Live data & city search require internet; the bundled Gliwice snapshot covers the
  no-connection case.
