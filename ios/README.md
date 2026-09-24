# Consensus Weather — iOS (SwiftUI)

Native iOS port of the Consensus Weather app. Same idea as the Android/web version:
a keyless 13-model Open-Meteo forecast consensus, current + hourly + 7-day views,
unit toggles, saved locations, and a daily local notification.

## Project layout

```
ios/
  project.yml                 # XcodeGen spec (generates the .xcodeproj in CI)
  Sources/
    ConsensusWeatherApp.swift # @main entry
    Models.swift              # Condition mapping, data structs, Color(hex:)
    Localization.swift        # dictionary L10n (en, pl, de, es, fr, it, uk)
    OpenMeteoService.swift    # fetch + 13-model consensus, geocoding, reverse geocode
    LocationManager.swift     # CoreLocation one-shot coordinate
    Notifications.swift       # UNUserNotificationCenter daily schedule
    Store.swift               # app state, units, persistence, formatting
    Views/
      RootView.swift
      ForecastView.swift      # background gradient + hero/stats/hourly/daily + rain-snow overlay
      LocationsView.swift     # search + saved places
      SettingsView.swift      # units, language, notifications
```

## How it builds (CI)

`.github/workflows/ios.yml` runs on a macOS runner: installs XcodeGen, runs
`xcodegen generate`, archives **unsigned** (`CODE_SIGNING_ALLOWED=NO`), zips the
`.app` into `Payload/…` → `ConsensusWeather-unsigned.ipa`, and publishes it to the
moving `ios-latest` release so the download link is stable.

## Build locally

```
brew install xcodegen
cd ios
xcodegen generate
open ConsensusWeather.xcodeproj
```

Set your own signing team in Xcode to run on a device, or run in the Simulator.

## Adding the other languages

`Localization.swift` uses the same keys as the web app's `I18N` object. To add a
language, copy its block, translate the values, and add its 2-letter code to
`L10n.supported`. English is always the fallback.

## Notes / simplifications vs. the web app

- Icons use **SF Symbols** (multicolor) rather than the custom inline SVGs.
- 7 languages are seeded; the rest can be ported as above.
- Rain/snow use a lightweight `Canvas` particle overlay.
- The daily notification body is composed from the latest forecast when the app is
  open (iOS limits background execution); it repeats at the chosen time.
