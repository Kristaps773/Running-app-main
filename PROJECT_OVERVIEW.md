# PROJECT_OVERVIEW — OrienteerRun (Android)

> **Canonical project folder:** `C:\Users\krist\OneDrive\DatorsK\FAILI\PROGRAMMING\Running-app-main`  
> This is the app built in Android Studio. See [DEVELOPMENT.md](DEVELOPMENT.md) — do not edit `FAILI\Projekti\Running-app-main` by mistake.

This document explains **what happens in the project end-to-end**: what the app does, how screens connect, where data is stored, how route generation works, and how run tracking continues while you navigate around (and via the foreground service).

If you want the higher-level design/algorithm writeup, also see `readme.md` (it contains more theory and pseudocode). This file focuses on **“what happens when the user taps X”** and **where the code lives**.

## What the app does (user perspective)

- **Pick a target distance** on the Setup screen.
- Open **Settings** (from Setup) to adjust **how strict route generation is** (straight-line vs OSRM walking path ratios) and **map appearance** (straight-line legs, leg labels, walking polyline, dark basemap).
- While Setup is open (and you are idle, not generating), the app may **pre-generate** up to three routes in the background: your **current slider distance** plus your **most frequent saved target distances** (from history; defaults to 3 / 5 / 10 km if new). Cached routes are **not** saved until you tap **Generate Route**.
- Tap **Generate Route**:
  - Pre-generation is **cancelled**; if a matching in-memory route exists for your selected distance and GPS is still within ~400 m of when it was built, the app **reuses** it (new id, then saved). Otherwise it generates as usual.
  - App fetches your current GPS location.
  - App generates a loop route made of checkpoints and a walking path polyline.
  - Route is saved locally so you can revisit it.
- Preview the route on a map.
- Tap **Start Run**:
  - A **foreground location-tracking service** starts.
  - You can navigate to other screens; the run keeps tracking.
  - A notification shows live distance/time.
- When the run completes (or you stop it), the app optionally saves it to **Run History**.

## Tech stack (what’s used)

- **Language/UI**: Kotlin + Jetpack Compose + Material 3
- **Navigation**: `androidx.navigation.compose`
- **DI**: Hilt
- **Maps**: OSMDroid (`MapView` hosted inside Compose via `AndroidView`)
- **Networking**: Retrofit + OkHttp
- **Persistence**: Room (SQLite) + Gson (serialize polylines and lists)
- **Background tracking**: Foreground service (`LifecycleService`)
- **State**: Kotlin `Flow`/`StateFlow`

## High-level architecture (where code lives)

The app uses **MVVM + clean-ish layering**:

- **Presentation**
  - Compose screens: `presentation/*/*Screen.kt`
  - ViewModels: `presentation/*/*ViewModel.kt` expose `StateFlow` UI state
- **Domain**
  - Use cases: `domain/usecase/*UseCase.kt` (pure logic, unit-testable)
- **Data**
  - Repositories: `data/repository/*Repository.kt`
  - Local DB (Room): `data/local/*`
  - Network APIs: `data/api/*`
- **Service**
  - Foreground tracking service: `service/LocationTrackingService.kt`

## App entry points (startup)

- **Application**: `app/src/main/java/com/orienteer/app/OrienteerApp.kt`
  - Initializes OSMDroid config (user agent + preferences).
- **Activity**: `app/src/main/java/com/orienteer/app/MainActivity.kt`
  - Requests location permissions.
  - Hosts Compose content.
- **Navigation graph**: `app/src/main/java/com/orienteer/app/presentation/navigation/AppNavigation.kt`

## Screen flow (navigation)

Defined in `AppNavigation.kt`:

- `setup` → `map/{routeId}` → `run/{routeId}`
- `run/{routeId}/map` is a map view used “during run”
- `history` shows run history + detail replay

### “Run in progress” banner (global entry point)

Also in `AppNavigation.kt`:

- If there is an active run (based on persisted active-run state), the app shows a top banner **Run in progress** with **Open**.
- This makes it easy to return to the run after navigating to Setup/History/Map.

## Persistence model (what is saved where)

### Room database

- DB definition: `data/local/AppDatabase.kt`
- DAOs: `data/local/dao/RouteDao.kt` (also contains `RunSessionDao`)
- Entities: `data/local/entity/*`

Stored tables:

- **routes**
  - Saved routes including checkpoints + polyline as JSON.
- **run_sessions**
  - Completed (or stopped) runs including tracked points + timestamps as JSON.

### “Active run” state (DataStore)

File: `data/local/ActiveRunStore.kt`

- Persists the **active route id** (and started time) while a run is tracking.
- Used to:
  - show the global “Run in progress” banner,
  - allow screens to reconnect to the service without resetting the run.

## Main user journeys (what happens step-by-step)

### 1) Setup → Generate Route

Files:
- `presentation/setup/SetupScreen.kt` (starts/stops warmup via `DisposableEffect` → `onSetupScreenActive` / `onSetupScreenInactive`)
- `presentation/setup/SetupViewModel.kt` (warmup job, in-memory cache, reuse on Generate)
- `data/repository/LocationRepository.kt`
- `domain/usecase/GenerateRouteUseCase.kt`
- `data/repository/RouteRepository.kt` (includes `getFrequentTargetDistancesM` from recent saved routes)
- `data/local/dao/RouteDao.kt` (`getRecentTargetDistancesM` for frequency)

Flow:

1. User selects distance:
   - Preset chips (3/5/10 km)
   - **Other** swaps the second row in-place: the full-width “Other” chip becomes a compact single-line `OutlinedTextField` (same slot, no extra labeled row)
   - Slider fine-tunes distance
2. **Background warmup** (only while UI state is **Idle** and the Setup screen is composed): `SetupViewModel` can call `GenerateRouteUseCase` for up to three targets (current distance + frequent buckets, deduped). Leaving Setup **cancels** warmup and **clears** the cache. Dismissing a generation error **restarts** warmup.
3. User taps **Generate Route** (button is **pinned** in a bottom bar with navigation-bar insets so it stays visible above gesture navigation):
   - Warmup **job is cancelled** immediately.
   - If a run is active, the app **stops the run first** (foreground service stop).
   - App gets a current GPS location fix via `LocationRepository`.
   - If a **warmed** route matches the selected distance (250 m bucket) and origin within ~400 m, that route is persisted (new UUID) and the flow skips a second full generation.
   - Otherwise the app calls `GenerateRouteUseCase(origin, targetDistanceM)` (tuned for fewer remote calls: smaller candidate Overpass, fewer OSRM iterations/retries, no per-checkpoint Overpass “enrichment” by default — see `OVERPASS_ENRICH_CHECKPOINTS` in that file if you want richer POI text back).
   - On success:
     - route is persisted via `RouteRepository.saveRoute(route)`
     - navigation moves to Map screen with that route id.

### 2) Map → Route preview (and export)

Files:
- `presentation/map/MapScreen.kt`
- `presentation/map/MapViewModel.kt`
- `util/RoutePdfExporter.kt` (PDF export from the map view; **GPX share** for importing checkpoints/path into Oomap — the “Open O-map” browser link only centers the map, it does not transmit geometry)

Flow:

1. `MapViewModel` loads the route from Room by `routeId`.
2. `MapScreen` hosts an OSMDroid `MapView` and draws:
   - checkpoint markers
   - “air leg” straight lines and distance labels (visual guidance)
3. Bottom “RouteInfoCard” contains the **Start Run** button.

### 3) Start Run → Foreground tracking

Files:
- `presentation/run/RunScreen.kt`
- `presentation/run/RunViewModel.kt`
- `service/LocationTrackingService.kt`
- `domain/usecase/TrackRunUseCase.kt`
- `domain/usecase/VerifyCheckpointUseCase.kt`
- `data/repository/RunRepository.kt`

Flow:

1. User taps **Start Run**:
   - `RunViewModel` starts the service with:
     - `ACTION_START`
     - `EXTRA_ROUTE_ID`
     - `EXTRA_SAVE_RUN`
   - ViewModel binds to the service to receive live session updates.
2. Service starts foreground mode:
   - Loads the `Route` by id from `RouteRepository`.
   - Creates a `RunSession` (start time, checkpoint 0 reached time).
   - Writes “active route id” into `ActiveRunStore`.
   - Starts collecting GPS points every ~2s from `LocationRepository.locationUpdates(...)`.
3. Each GPS fix:
   - `TrackRunUseCase` updates `RunSession`:
     - distance accumulation with noise filter
     - checkpoint reach verification
     - finish detection
   - Service updates the notification.
4. Completion / stop:
   - If `shouldSaveRun` is true, the service persists the session into `run_sessions`.
   - Service clears `ActiveRunStore` and stops itself.

### 4) Navigate away during a run (run continues)

Key behavior:

- The run keeps tracking because it’s a **foreground service**.
- The UI can freely navigate to other screens.
- The app shows a **Run in progress** banner as an entry point back to the run.

Important: The run session must **not** be reset by rebinding.
- The service owns session creation; binding only observes `runSession`.

### 5) Run history → replay + delete

Files:
- `presentation/history/HistoryScreen.kt`
- `presentation/history/HistoryViewModel.kt`
- `data/repository/RunRepository.kt`
- `data/local/dao/RouteDao.kt` (`RunSessionDao`)

Flow:

1. `HistoryViewModel` observes recent runs (Flow from Room).
2. Selecting a run loads:
   - run detail (tracked points, timestamps)
   - the route (planned polyline)
3. `HistoryScreen` draws a replay map:
   - planned route polyline (semi-transparent)
   - actual tracked polyline (solid)
4. Delete:
   - “Delete run” button in the detail view opens a confirm dialog.
   - On confirm, DAO deletes by `sessionId`.

## Background/lifecycle expectations (what is guaranteed)

- On Setup, **idle** warmup may issue **up to three** full route generations (Overpass/OSRM) while you browse distances; that work stops when you leave Setup, tap **Generate Route**, or hit a non-idle state (e.g. locating / generating).
- While the run is active, the tracking is a **foreground service** (notification visible).
- You can:
  - switch screens,
  - background the app,
  - reopen the app and return to the run via the banner.

What is *not* fully guaranteed without more work:

- If Android kills the entire process and the service does not restart cleanly, rebuilding a full in-memory `RunSession` from disk would require additional persistence of the “live session so far”. Currently the service persists the **active route id** (for UX) and persists the run session when stopped/finished.

## Where to change things (common tasks)

- **Change route generation**: `domain/usecase/GenerateRouteUseCase.kt`
- **Change route warmup (prefetch count, distance bucket, origin match radius)**: `presentation/setup/SetupViewModel.kt` (companion constants and `scheduleRouteWarmup`)
- **Change “frequent distance” stats**: `data/repository/RouteRepository.kt` → `getFrequentTargetDistancesM` (+ `RouteDao.getRecentTargetDistancesM`)
- **User settings (path/air ratios, map visuals, dark basemap)**: `presentation/settings/SettingsScreen.kt` + `SettingsViewModel.kt` + `data/local/UserPreferencesStore.kt` (DataStore). Ratios are read in `GenerateRouteUseCase`; map toggles apply in `MapScreen` / `HistoryScreen` replay map via `MapViewModel` / `HistoryViewModel`.
- **Change checkpoint reach radius**: `domain/usecase/VerifyCheckpointUseCase.kt` / `Checkpoint.PROXIMITY_RADIUS_M`
- **Change GPS update frequency**: `LocationTrackingService` → `locationUpdates(intervalMs = ...)`
- **Change run persistence**: `data/repository/RunRepository.kt` + `RunSessionDao` in `data/local/dao/RouteDao.kt`
- **Change map drawing**: `presentation/map/MapScreen.kt` (OSMDroid overlays)
- **Change history replay rendering**: `presentation/history/HistoryScreen.kt`

## Quick “how to read the code” map

Start here (in order):

1. `MainActivity.kt` → permission + Compose host
2. `AppNavigation.kt` → all screens and routes (includes **Settings**)
3. `SetupScreen.kt` / `SetupViewModel.kt` → generate route flow; **Settings** entry opens `SettingsScreen`
4. `MapScreen.kt` / `MapViewModel.kt` → map preview flow
5. `RunScreen.kt` / `RunViewModel.kt` → run UI + service binding
6. `LocationTrackingService.kt` → the truth for live tracking
7. `TrackRunUseCase.kt` → pure run updates per GPS fix
8. `HistoryScreen.kt` / `HistoryViewModel.kt` → history + replay + delete

