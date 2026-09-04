# Contributing to Consensus Weather

Thanks for helping improve Consensus Weather! This is a small, dependency-free project, so contributing is intentionally low-friction.

## Reporting bugs & requesting features

- **Bugs:** open an [issue](https://github.com/Szebastian31/Consensus_Weather/issues/new) with what you did, what you expected, what happened, your device/OS, and a screenshot if it's visual.
- **Feature ideas & questions:** start a thread in [Discussions](https://github.com/Szebastian31/Consensus_Weather/discussions) — good for anything open-ended before it becomes a concrete issue.

## Project layout (the short version)

Almost the entire app is one self-contained file:

```
app/src/main/assets/index.html   ← all UI, styling, and logic (live data + rendering)
docs/index.html                  ← a copy of the same file, served as the web/iPhone version
```

The rest is a thin native Android WebView wrapper (`MainActivity.java`, `AndroidManifest.xml`, `build.gradle`) plus the cloud build at `.github/workflows/build.yml`. You rarely need to touch those.

> ⚠️ If you change the app, update **both** `app/src/main/assets/index.html` and `docs/index.html` so Android and the web version stay in sync.

## Dev setup & running it

No toolchain required for app changes:

1. Open `app/src/main/assets/index.html` directly in a browser (double-click it).
2. Edit, save, refresh. That's the whole loop.

- Live data needs an internet connection (Open-Meteo, no API key). With no connection the app falls back to a bundled Gliwice snapshot (`buildFallback()`), which is handy for offline UI work.
- **The APK is built in the cloud** by GitHub Actions on every push — you don't need Android Studio or Gradle locally. Download the result from the **Actions** tab → newest run → **Artifacts**.

## Testing

There's no automated test suite yet (contributions to add one are welcome). Before opening a PR, please run through this manual checklist in a browser:

- Loads without console errors; switch between several cities (incl. Southern Hemisphere and the Americas).
- `°C` / `°F` toggle updates every temperature.
- Day **and** night icons render correctly (test a city where it's currently night).
- Each condition looks right (clear, cloudy, rain, snow, thunder, fog).
- Missing values show `–`, never `0` or `NaN`.
- Popups (part-by-part, manage locations) open/close; hourly rail and chart scroll.
- Looks good on a narrow phone width and a wide screen.

## Branch & PR workflow

1. Create a focused branch: `fix/night-icon` or `feature/wind-gusts`.
2. Keep commits small and scoped to one change.
3. Open a PR against `main`. Describe what changed and why, and **include before/after screenshots for any UI change**.
4. Make sure the **Actions build stays green** (the APK must still build).

### Code style

- **Vanilla only** — plain HTML/CSS/JS, no frameworks, no build step.
- **No external scripts or CDNs.** They're blocked by design and would break offline use. Icons and the chart are hand-rolled SVG; keep it that way.
- Match the existing compact style: template-literal rendering in `render()`, 2-space indentation, and the null-safe helpers (`R()`, `tC()`, `coalesce()`) — preserve `–` for missing data rather than showing `0`.
- Keep the app in a single file; don't split it into modules.

## How to add a new weather source (provider)

The headline numbers are an **average of several national models pulled from Open-Meteo** via its `models=` parameter. Adding a model is usually a one-line change.

1. Pick a valid model id from the [Open-Meteo docs](https://open-meteo.com/en/docs) (the "Weather models" section lists ids like `ecmwf_ifs025`, `icon_seamless`, `gfs_seamless`).
2. Add it to the `MODELS` array near the top of the config script in `index.html`:

   ```js
   const MODELS=[
     {id:'ecmwf_ifs025',name:'ECMWF'},
     {id:'icon_seamless',name:'DWD ICON'},
     // …existing models…
     {id:'ukmo_seamless',name:'UK Met Office'}   // ← your new provider
   ];
   ```

That's it. The averaging (`avg`/`mode`), the per-model rows in the "How the sources compare" table, and the "N models" labels all read from `MODELS` automatically.

**Notes:**
- Not every model covers every region or variable. Models return `null` where they have no data, and the averaging skips nulls — so a model that doesn't cover a location simply doesn't contribute there. Test a few global cities.
- Prefer reputable national/global models over niche ones, to keep the consensus meaningful.
- Update the model list and any "7-model" wording in `README.md` if you change the count.

**Adding a non-Open-Meteo provider** (a different API entirely) is a bigger job: fetch it inside `fetchWeather()`, normalize its response into the same `hourly` / `daily` / `current` shape the rest of the app expects, and merge it into the averages. Open an issue first so we can talk through the data mapping.

---

Questions about any of this? Ask in [Discussions](https://github.com/Szebastian31/Consensus_Weather/discussions). 🌦️
