# OrienteerRun — Android Orienteering Training App

> **Canonical project folder:** `C:\Users\krist\OneDrive\DatorsK\FAILI\PROGRAMMING\Running-app-main`  
> Build and edit here (Android Studio). Do not use the duplicate at `FAILI\Projekti\Running-app-main`. See [DEVELOPMENT.md](DEVELOPMENT.md).

A Kotlin Android app that generates random, GPS-verified running routes in orienteering style. The user sets a target distance and the app plots a loop of real walkable checkpoints, then guides the runner checkpoint-by-checkpoint.

**Free for personal use — no ads, no payments.** See [License](#license) for terms.

---

## Table of Contents

1. [Tech Stack Decision](#1-tech-stack-decision)
2. [Architecture Overview](#2-architecture-overview)
3. [Module Breakdown](#3-module-breakdown)
4. [Data Models](#4-data-models)
5. [APIs Used](#5-apis-used)
6. [Route Generation Algorithm](#6-route-generation-algorithm)
7. [Checkpoint Verification](#7-checkpoint-verification)
8. [Run Tracking Design](#8-run-tracking-design)
9. [Current Status](#9-current-status)
10. [Key Kotlin Code Examples](#10-key-kotlin-code-examples)
11. [Possible Future Improvements](#11-possible-future-improvements)

---

## 1. Tech Stack Decision

| Concern | Choice | Reason |
|---|---|---|
| Language | **Kotlin** | First-class Android support, coroutines, null safety |
| UI | **Jetpack Compose + Material 3** | Declarative, modern, no XML layouts |
| Maps | **OSMDroid (OpenStreetMap)** | Free, no API key needed for tile rendering, open data |
| Routing | **OSRM (foot profile)** | Free, open-source, returns actual walking paths with polyline geometry |
| POI data | **Overpass API** | Direct access to OSM point-of-interest nodes for checkpoint descriptions |
| GPS | **FusedLocationProviderClient** | Battery-efficient, high-accuracy Android location API |
| DI | **Hilt** | Compile-time safe, minimal boilerplate, Jetpack-recommended |
| Persistence | **Room + Gson** | Type-safe SQLite ORM; Gson for complex list serialisation |
| Async | **Kotlin Coroutines + Flow** | Structured concurrency, reactive UI state |
| Background tracking | **Foreground Service (LifecycleService)** | Survives app backgrounding; legally required for background GPS on Android 10+ |

### Why OSMDroid over Google Maps?

| | Google Maps SDK | OSMDroid |
|---|---|---|
| API key | Required (billing) | Not required |
| Data | Google proprietary | OpenStreetMap (open) |
| Offline tiles | Limited | Supported |
| Routing | Directions API (paid) | OSRM (free/self-host) |
| Licence | Google ToS | Apache 2 / ODbL |

For a training/hobby app with potentially millions of map requests, OSMDroid + OSRM is the correct zero-cost choice.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│  SetupScreen  ──  MapScreen  ──  RunScreen              │
│  SetupViewModel  MapViewModel  RunViewModel             │
│         ↕ StateFlow<UiState>                            │
├─────────────────────────────────────────────────────────┤
│                      Domain Layer                        │
│  GenerateRouteUseCase                                   │
│  VerifyCheckpointUseCase                               │
│  TrackRunUseCase                                       │
├─────────────────────────────────────────────────────────┤
│                       Data Layer                         │
│  LocationRepository  RouteRepository  RunRepository    │
│       ↕                   ↕                  ↕          │
│  FusedLocation       Room Database     Room Database   │
│  Client              (routes)          (run_sessions)  │
│                           ↕                             │
│                  OSRM API  Overpass API                 │
└─────────────────────────────────────────────────────────┘
```

The architecture follows **MVVM + Clean Architecture**:

- **Presentation** contains only UI state and user event wiring — no business logic.
- **Domain Use Cases** are pure Kotlin functions (no Android imports) — fully unit-testable.
- **Data Repositories** abstract all data sources (network, database, GPS) behind a single interface.

---

## 3. Module Breakdown

```
app/src/main/java/com/orienteer/app/
│
├── OrienteerApp.kt               Application class (@HiltAndroidApp)
├── MainActivity.kt               Single activity; Compose host; permission request
│
├── data/
│   ├── api/
│   │   ├── OsrmApiService.kt     Retrofit interface — nearest + route endpoints
│   │   └── OverpassApiService.kt Retrofit interface — POI queries
│   ├── local/
│   │   ├── AppDatabase.kt        Room database definition
│   │   ├── dao/
│   │   │   ├── RouteDao.kt       CRUD for saved routes
│   │   │   └── RunSessionDao.kt  CRUD for run history
│   │   └── entity/
│   │       └── RouteEntity.kt    Room entities + TypeConverters
│   ├── model/
│   │   ├── GeoPoint.kt           lat/lng value object
│   │   ├── Checkpoint.kt         Orienteering waypoint
│   │   ├── Route.kt              Complete route
│   │   ├── RunSession.kt         Live / completed run state
│   │   ├── OsrmModels.kt         OSRM API response models
│   │   └── OverpassModels.kt     Overpass API response models
│   └── repository/
│       ├── LocationRepository.kt Single fix + live updates via FusedClient
│       ├── RouteRepository.kt    Save / load routes from Room
│       └── RunRepository.kt      Persist run history
│
├── domain/usecase/
│   ├── GenerateRouteUseCase.kt   Core algorithm (see §6)
│   ├── VerifyCheckpointUseCase.kt Proximity check (see §7)
│   └── TrackRunUseCase.kt        Pure run state updater
│
├── presentation/
│   ├── navigation/AppNavigation.kt  Compose NavHost
│   ├── setup/
│   │   ├── SetupScreen.kt        Distance picker + generate button
│   │   └── SetupViewModel.kt     Orchestrates GPS + generation
│   ├── map/
│   │   ├── MapScreen.kt          OSMDroid map + route overlay + start button
│   │   └── MapViewModel.kt       Real-time location + route loading
│   └── run/
│       ├── RunScreen.kt          Metrics + checkpoint list
│       └── RunViewModel.kt       Binds to LocationTrackingService
│
├── service/
│   └── LocationTrackingService.kt  Foreground service; GPS loop; session update
│
├── di/
│   ├── NetworkModule.kt          OkHttp + Retrofit for OSRM & Overpass
│   └── DatabaseModule.kt         Room database + DAOs
│
└── util/
    ├── GeoUtils.kt               Haversine, offset, format helpers
    └── CheckpointDescriptionGenerator.kt  OSM tag → English description
```

---

## 4. Data Models

### GeoPoint
```kotlin
data class GeoPoint(val latitude: Double, val longitude: Double)
```

### Checkpoint
```kotlin
data class Checkpoint(
    val id: Int,
    val position: GeoPoint,
    val description: String,        // "Near a historic fountain"
    val landmarkName: String?,      // OSM name tag
    val landmarkType: String?,      // e.g. "amenity=bench"
    val isStart: Boolean = false,
    val isReached: Boolean = false,
    val distanceFromPrev: Double    // metres
) {
    companion object {
        const val PROXIMITY_RADIUS_M = 15.0
    }
}
```

### Route
```kotlin
data class Route(
    val id: String,                       // UUID
    val checkpoints: List<Checkpoint>,    // [start, cp1, cp2, ..., cpN]
    val polylinePoints: List<GeoPoint>,   // full walking path for map
    val targetDistanceM: Double,
    val totalDistanceM: Double,           // actual OSRM distance
    val estimatedDurationS: Double,
    val createdAt: Long
)
```

### RunSession
```kotlin
data class RunSession(
    val routeId: String,
    val trackedPoints: List<GeoPoint>,   // GPS breadcrumbs
    val currentCheckpointIdx: Int,        // next checkpoint to reach
    val totalDistanceM: Double,
    val elapsedTimeMs: Long,
    val isActive: Boolean,
    val startedAt: Long,
    val finishedAt: Long?
) {
    val paceSecPerKm: Double?   // null if no distance yet
    val speedKmh: Double
}
```

---

## 5. APIs Used

### OSRM (routing.openstreetmap.de/routed-foot)

**Nearest — snap a raw coordinate to the nearest road:**
```
GET /nearest/v1/foot/{lng},{lat}?number=1
```
Returns the nearest walkable road node. Used during route generation to ensure all checkpoints are reachable on foot.

**Route — get a multi-stop walking route:**
```
GET /route/v1/foot/{lng1,lat1;lng2,lat2;...}?overview=full&geometries=geojson
```
Returns total distance, duration, and full GeoJSON polyline. Used to:
1. Measure actual distance of the generated loop.
2. Provide the drawable polyline for the map overlay.

### Overpass API (overpass-api.de)

Queries OSM for POI nodes near each checkpoint coordinate:
```
[out:json][timeout:8];
(
  node["amenity"](around:50,{lat},{lon});
  node["historic"](around:50,{lat},{lon});
  node["tourism"](around:50,{lat},{lon});
  node["natural"](around:50,{lat},{lon});
  node["leisure"](around:50,{lat},{lon});
);
out 3;
```

The best-matching tag is converted to English, e.g.:
- `amenity=bench` → "Near a bench"
- `historic=monument` → "Near a monument"
- `tourism=viewpoint` → "Near a scenic viewpoint"

---

## 6. Route Generation Algorithm

### Pseudocode

```
FUNCTION generateRoute(origin: GeoPoint, targetDistance: Double) -> Route

  n = clamp(targetDistance / 1200, 3, 8)   // ~1 checkpoint per 1.2 km
  
  // Initial polygon radius: derived from regular n-gon perimeter formula
  // perimeter = n × 2R × sin(π/n)   ⟹   R = perimeter / (n × 2 × sin(π/n))
  radius = targetDistance / (n × 2 × sin(π/n))

  bestRoute = null
  bestError = ∞

  FOR iteration IN 0..MAX_ITERATIONS:

    // Step 1: Generate candidate points on a jittered polygon
    candidates = []
    angleStep = 360° / n
    FOR i IN 0..n-1:
      bearing = (i × angleStep) + random(−0.3×angleStep, +0.3×angleStep)
      r = radius × random(0.80, 1.20)    // ±20% radial jitter
      candidates.append( offsetPoint(origin, r, bearing) )

    // Step 2: Snap each candidate to nearest walkable road via OSRM
    snapped = []
    FOR candidate IN candidates:
      wp = OSRM.nearest(candidate)
      IF wp.distanceFromCandidate > 600m: SKIP iteration   // no road nearby
      snapped.append(wp)

    // Step 3: Get actual walking route for the loop
    waypoints = [origin] + snapped + [origin]
    osrmRoute = OSRM.route(waypoints)
    actualDistance = osrmRoute.distance

    // Step 4: Record best result
    error = |actualDistance / targetDistance − 1|
    IF error < bestError:
      bestError = error
      bestRoute = build(snapped, osrmRoute)

    IF error ≤ 0.12: BREAK   // within 12% → good enough

    // Step 5: Damped radius adjustment to avoid oscillation
    // Plain ratio could overshoot; sqrt gives stable convergence
    radius = radius × sqrt(targetDistance / actualDistance)

  // Step 6: Enrich checkpoints with nearby POI descriptions
  FOR each checkpoint in bestRoute:
    elements = Overpass.query(around:50m, checkpoint.position)
    checkpoint.description = describeElement(elements[0])

  RETURN bestRoute
```

### Convergence Analysis

The algorithm uses a **damped scaling rule**:

```
radius_new = radius_old × sqrt(target / actual)
```

Using `sqrt` rather than the plain ratio prevents oscillation:
- If actual = 80 % of target → multiply radius by √(1.25) = 1.118 (not 1.25)
- If actual = 120 % of target → multiply by √(0.833) = 0.913 (not 0.833)

In practice this converges within 3–5 iterations for urban areas.

### Why Points Are On Roads

OSRM `/nearest` snaps each randomly-placed point to the **closest routable edge** in the OSM road graph. The foot profile excludes motorways and private roads — returning only publicly walkable paths, pavements, and trails. Any snap that lands more than 600 m from the original candidate (e.g. a point dropped in the sea) is rejected and the entire iteration is retried with fresh jitter.

### Handling Obstacles

| Obstacle | Mitigation |
|---|---|
| Water | OSRM snap fails or exceeds 600 m threshold → iteration retried |
| Private property | OSM foot graph excludes `access=private` ways |
| Buildings | Points snap to the road network, not building interiors |
| Highways / motorways | OSRM foot profile excludes `highway=motorway` |

---

## 7. Checkpoint Verification

```kotlin
// VerifyCheckpointUseCase.kt

operator fun invoke(currentPosition: GeoPoint, target: Checkpoint): Boolean {
    if (target.isReached) return false
    return GeoUtils.distanceMeters(currentPosition, target.position) <= 15.0
}
```

The **Haversine formula** is used for distance calculation:

```kotlin
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val R = 6_371_000.0
    val lat1 = toRadians(a.latitude);  val lat2 = toRadians(b.latitude)
    val dLat = toRadians(b.latitude - a.latitude)
    val dLng = toRadians(b.longitude - a.longitude)
    val h = sin(dLat/2).pow(2) + cos(lat1)*cos(lat2)*sin(dLng/2).pow(2)
    return 2 * R * asin(sqrt(h))
}
```

**Why 15 m?** Modern smartphones achieve 3–5 m horizontal accuracy in open sky. 15 m gives reasonable tolerance for:
- Dense urban canyons (GPS multi-path errors)
- Slight deviation from the checkpoint due to obstacles
- Natural running path variations

---

## 8. Run Tracking Design

### Components

```
┌──────────────────────────────────────────────────────┐
│  RunScreen (Compose)                                 │
│    observes ──▶ RunViewModel.uiState (StateFlow)     │
│                     │                                │
│                  binds to                            │
│                     ▼                                │
│  LocationTrackingService (Foreground)               │
│    ┌─────────────────────────────────┐              │
│    │  FusedLocationClient            │              │
│    │    every 2 seconds              │              │
│    │         ↓ GeoPoint              │              │
│    │  TrackRunUseCase.invoke(...)    │              │
│    │    - accumulate distance        │              │
│    │    - verify checkpoints        │              │
│    │    - detect route completion   │              │
│    │         ↓ RunSession           │              │
│    │  _runSession.emit(updated)     │              │
│    └─────────────────────────────────┘              │
└──────────────────────────────────────────────────────┘
```

### Optional Tracking Disable

`TrackRunUseCase` is a pure, injectable class. To disable tracking globally:

1. Create `NoOpTrackRunUseCase` that returns the session unchanged.
2. In the Hilt module, conditionally bind the no-op based on a user preference flag.
3. The service and ViewModel need **zero changes**.

### GPS Noise Filtering

```kotlin
// In TrackRunUseCase — ignore implausible speed spikes
val addedDistance = if (prev != null) {
    val d = GeoUtils.distanceMeters(prev, newPoint)
    if (d > 50.0) 0.0 else d  // 50 m / 2 s = 90 km/h — not a runner
} else 0.0
```

---

## 9. Current Status

The app is **implemented** and ready to use. In place:

- **Project setup** — Kotlin, Compose, Hilt, Room, Retrofit, OSMDroid, AndroidManifest permissions
- **GPS** — `LocationRepository` with FusedLocationProviderClient, runtime permissions in MainActivity, `getCurrentLocation()` and `locationUpdates()` Flow
- **Map** — OSMDroid MapView in MapScreen, route polyline, checkpoint markers, current-location marker with live updates
- **Route generation** — `GenerateRouteUseCase` (OSRM nearest + route, polygon candidates with jitter, damped radius scaling), Overpass API for checkpoint descriptions, edge-case handling
- **Checkpoint verification** — `VerifyCheckpointUseCase` (Haversine, 15 m), wired into `TrackRunUseCase`, RunScreen checkpoint list with progress
- **Run tracking** — `LocationTrackingService` foreground service, notification channel, `TrackRunUseCase` with noise filtering, RunViewModel binding, RunRepository persistence
- **UI** — Material 3 theme, Setup distance slider, RunScreen metrics and checkpoint list, run completion flow, permission handling

---

## 10. Key Kotlin Code Examples

### Getting User GPS Location

```kotlin
// LocationRepository.kt
@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(): GeoPoint =
    suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                cont.resume(GeoPoint(location.latitude, location.longitude))
            } else {
                // No cached fix — request a fresh one
                fusedClient.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .build(), null
                )
                .addOnSuccessListener { loc ->
                    cont.resume(GeoPoint(loc.latitude, loc.longitude))
                }
            }
        }
    }
```

### Checking if User Reached a Checkpoint

```kotlin
// VerifyCheckpointUseCase.kt
operator fun invoke(currentPosition: GeoPoint, target: Checkpoint): Boolean {
    if (target.isReached) return false
    val distance = GeoUtils.distanceMeters(currentPosition, target.position)
    return distance <= Checkpoint.PROXIMITY_RADIUS_M  // 15 metres
}
```

### Calculating Distance Between Coordinates (Haversine)

```kotlin
// GeoUtils.kt
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val R = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
    return 2 * R * asin(sqrt(h))
}
```

### Generating Random Polygon Candidates

```kotlin
// GenerateRouteUseCase.kt
private fun generateCandidates(
    center: GeoPoint,
    n: Int,
    radius: Double
): List<GeoPoint> {
    val angleStep = 360.0 / n
    return (0 until n).map { i ->
        val baseAngle = angleStep * i
        // ±30% angular jitter for a natural shape
        val jitter = (Math.random() - 0.5) * angleStep * 0.6
        val bearing = (baseAngle + jitter + 360.0) % 360.0
        // ±20% radial jitter
        val r = radius * (0.8 + Math.random() * 0.4)
        GeoUtils.offsetPoint(center, r, bearing)
    }
}
```

### Snapping to Roads and Getting the Full Route

```kotlin
// GenerateRouteUseCase.kt
private suspend fun snapToRoads(candidates: List<GeoPoint>): List<GeoPoint>? {
    return candidates.map { candidate ->
        val response = osrm.nearest(GeoUtils.toOsrmCoordinate(candidate))
        if (response.code != "Ok") return null
        val wp = response.waypoints.first()
        val snapped = GeoPoint(wp.latitude, wp.longitude)
        // Reject if road is over 600m away (candidate is in water/inaccessible)
        if (GeoUtils.distanceMeters(candidate, snapped) > 600.0) return null
        snapped
    }
}

private suspend fun getRoute(waypoints: List<GeoPoint>): OsrmRoute {
    val coords = GeoUtils.toOsrmCoordinates(waypoints)
    val response = osrm.route(coords)
    check(response.code == "Ok") { "OSRM error: ${response.code}" }
    return response.routes.first()
}
```

### Updating Run State on Each GPS Fix

```kotlin
// TrackRunUseCase.kt
operator fun invoke(
    session: RunSession,
    route: Route,
    newPoint: GeoPoint,
    elapsedTimeMs: Long
): RunSession {
    val addedDistance = session.trackedPoints.lastOrNull()?.let { prev ->
        val d = GeoUtils.distanceMeters(prev, newPoint)
        if (d > 50.0) 0.0 else d   // noise filter
    } ?: 0.0

    val checkpointReached = route.checkpoints
        .getOrNull(session.currentCheckpointIdx)
        ?.let { verifyCheckpoint(newPoint, it) } == true

    val newIdx = if (checkpointReached) session.currentCheckpointIdx + 1
                 else session.currentCheckpointIdx
    val allDone = newIdx >= route.checkpoints.size

    return session.copy(
        trackedPoints = session.trackedPoints + newPoint,
        totalDistanceM = session.totalDistanceM + addedDistance,
        elapsedTimeMs = elapsedTimeMs,
        currentCheckpointIdx = newIdx,
        isActive = !allDone,
        finishedAt = if (allDone) System.currentTimeMillis() else null
    )
}
```

---

## 11. Possible Future Improvements

- **Blind run mode** — Option to hide the route map and/or current location when starting a run (e.g. skip or simplify the Route Preview step) so the run is more like real orienteering: navigate only by checkpoint descriptions. Could be implemented by adding a "Start run without showing map" path from Setup → Run, or a toggle on MapScreen to hide the map and "You are here".

---

## Quick Start

1. Clone this repository and open in Android Studio Hedgehog (2023.1.1) or later.
2. Sync Gradle — all dependencies download automatically.
3. Connect an Android device (API 26+) with **Location → High Accuracy** enabled.
4. Run the `app` configuration.
5. Grant location permissions when prompted.
6. Set your target distance, tap **Generate Route**, and run!

> **Tip:** For production use, host your own OSRM server to avoid rate limits on the public endpoint. See [project-osrm.org](https://project-osrm.org/) for setup instructions.

---

## License

MIT © 2025 OrienteerRun
