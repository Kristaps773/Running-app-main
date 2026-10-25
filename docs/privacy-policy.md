# Privacy Policy — OrienteerRun

**Last updated:** 25 October 2026  
**Contact:** kristapsduda773@gmail.com

OrienteerRun (“the App”) is a free Android training app that generates GPS-verified orienteering-style running routes. This policy explains what data is involved when you use the App.

## Summary

- No user accounts, ads, or in-app purchases.
- Location is used to generate routes and track runs.
- Route and run history stay on your device unless you export or share them.
- Optional crash and performance diagnostics may be sent to Sentry.
- Maps and routing use public OpenStreetMap-related services; tile requests reveal approximate map area to the selected provider.

## Data we process

### Location

While you generate a route or run with tracking active, the App uses precise location (GPS), and may also use approximate location. Tracking runs through a **foreground service** with a visible notification (notification permission on Android 13+). Location is used only for:

- Placing checkpoints near you
- Verifying checkpoint visits
- Showing your position on the map
- Computing distance and pace for the active run

We do **not** request the Android “background location” permission for continuous tracking when you are not in an active run session. Location is not sold and is not shared with other users.

### Local storage

Routes, run history, map preferences, and a local map-tile cache are stored **on your device** (database / preferences / app cache). They are not uploaded to an OrienteerRun backend (the App has no user accounts). Android backup, if enabled on your device, may include app data according to your system settings.

### Exports you choose to share

If you use **GPX** or **PDF** export, the App writes a file on your device and opens the system share sheet. You choose the destination app (email, drive, messaging, etc.). Exported files can include checkpoint coordinates, planned path geometry, and—for run exports—recorded GPS track points and timestamps. OrienteerRun does not upload those files to its own servers.

### Network services (third parties)

To show maps and build walkable routes, the App contacts third-party services over HTTPS. Those providers receive the network requests needed for the feature (for example tile zoom/x/y, which implies approximate map location, or routing/POI queries that include coordinates). Their own terms and privacy policies apply. Map data © OpenStreetMap contributors.

| Service | Purpose |
|---------|---------|
| OpenStreetMap Mapnik tiles | Standard basemap imagery |
| CARTO (basemaps.cartocdn.com) | Optional dark basemap |
| OpenTopoMap | Optional topographic basemap |
| CyclOSM | Optional cycling/outdoors basemap |
| OpenOrienteeringMap (contour overlay) | Optional LiDAR-derived contour layer |
| OSRM (e.g. routing.openstreetmap.de, foot profile) | Walking-path geometry between checkpoints |
| Overpass API (e.g. overpass-api.de) | Nearby points of interest for checkpoint labels |

Which tile hosts are contacted depends on the map style you select in Settings.

### Crash and performance diagnostics (Sentry)

If a Sentry project DSN is configured for the build, the App may send crash reports and limited performance data to **Sentry** (sentry.io), including:

- Device model and OS version
- App version / release identifier
- Stack traces and related technical diagnostics
- Sampled performance / interaction traces (no advertising profiles)

Default personally identifiable information (such as user name or email) is not attached by the App. This helps fix bugs. We do not use Sentry to sell data or show ads. See [https://sentry.io/privacy/](https://sentry.io/privacy/).

## Children

The App is not directed at children under 13. Do not use it if you are under the age required in your country for consent to such processing.

## Your choices

- Deny location or notification permission in Android settings (route generation / tracking / the run notification may not work).
- Change or limit map style so fewer or different tile providers are contacted.
- Clear app data or uninstall to remove local history and caches.
- Avoid GPX/PDF share if you do not want route or track files leaving the device.
- Contact us (email above) with privacy questions.

## Changes

We may update this policy when the App changes. The “Last updated” date will change accordingly. Continued use after an update means you accept the revised policy.

## Hosting this page

Publish this file (or the HTML copy) on a public HTTPS URL and paste that URL into Google Play Console → App content → Privacy policy. Also set `PRIVACY_POLICY_URL` in `local.properties` so Settings opens the same link.
