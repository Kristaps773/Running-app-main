# OrienteerRun

Android app for GPS orienteering-style training: generate a loop of checkpoints, run the course, verify each control on the map.

- **Free** — no ads, no account, no in-app purchases (v1)
- **Package:** `com.orienteer.app`
- **Stack:** Kotlin, Jetpack Compose, OSMDroid, OSRM (foot), Overpass (OpenStreetMap POIs)

## Build

See [DEVELOPMENT.md](DEVELOPMENT.md) for the canonical project folder and Android Studio setup.

```powershell
.\gradlew.bat :app:assembleDebug
```

Release Play bundle (signing in gitignored `local.properties`): see [PLAY_PUBLISH.md](PLAY_PUBLISH.md).

## Docs

| Doc | Purpose |
|-----|---------|
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | Features, screens, tuning |
| [PLAY_PUBLISH.md](PLAY_PUBLISH.md) | Google Play checklist |
| [docs/privacy-policy.md](docs/privacy-policy.md) | Privacy policy (host on GitHub Pages for Play) |

## Open data & APIs

Map tiles and POI data come from **OpenStreetMap** ([ODbL](https://www.openstreetmap.org/copyright)). Routing uses public **OSRM** and **Overpass** endpoints; respect their [usage policies](https://operations.osmfoundation.org/policies/) and consider self-hosting for heavy use.

The app queries Overpass once per route (candidate pool around your start) using `nwr[...]` for amenity, historic, tourism, natural, leisure, man_made, barrier, shop, craft, sport, plus highway nodes (bus stop, crossing, signals). Per-checkpoint Overpass enrichment is off by default for speed (`OVERPASS_ENRICH_CHECKPOINTS` in `GenerateRouteUseCase.kt`).

## License

**Source code:** [MIT](LICENSE) — Copyright (c) 2025 Kristaps Duda. Others may use, modify, and distribute the **source** under MIT terms.

**App name “OrienteerRun”** is a product name, not a registered company. Play distribution is under your personal developer account.

**MIT does not** grant rights to Google Play’s published APK, OSM data (separate ODbL terms), or third-party service trademarks. Map/routing data remains © OpenStreetMap contributors.
