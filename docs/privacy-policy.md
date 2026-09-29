# Privacy Policy — OrienteerRun

**Last updated:** 26 September 2026  
**Contact:** kristapsduda773@gmail.com

OrienteerRun (“the App”) is a free Android training app that generates GPS-verified orienteering-style running routes. This policy explains what data is involved when you use the App.

## Summary

- No user accounts, ads, or in-app purchases.
- Location is used to generate routes and track runs.
- Route and run history stay on your device.
- Optional crash diagnostics are sent to Sentry.
- Map and routing use public OpenStreetMap-related services.

## Data we process

### Location

While you generate a route or run with tracking active, the App uses precise location (GPS) via a **foreground service** and a visible notification. Location is used only for:

- Placing checkpoints near you
- Verifying checkpoint visits
- Showing your position on the map
- Computing distance and pace for the active run

We do **not** request background location when you are not tracking a run. Location is not sold.

### Local storage

Routes, run history, and preferences are stored **on your device** (local database / preferences). They are not uploaded to our servers (the App has no OrienteerRun backend account).

### Network services (third parties)

To show maps and build walkable routes, the App contacts:

| Service | Purpose |
|---------|---------|
| OpenStreetMap / map tile providers (e.g. Mapnik, Carto) | Map imagery |
| OSRM (public routing endpoints) | Walking-path geometry between checkpoints |
| Overpass API | Nearby points of interest for checkpoint labels |

Those providers receive the network requests needed for those features (for example map tile coordinates or routing queries). Their own terms and privacy policies apply. Map data © OpenStreetMap contributors.

### Crash and performance diagnostics (Sentry)

If configured, the App may send crash reports and limited performance data to **Sentry** (sentry.io), including:

- Device model and OS version
- App version
- Stack traces and related technical diagnostics

This helps fix bugs. We do not use Sentry to sell data or show ads. You can learn more at [https://sentry.io/privacy/](https://sentry.io/privacy/).

## Children

The App is not directed at children under 13. Do not use it if you are under the age required in your country for consent to such processing.

## Your choices

- Deny location or notification permission in Android settings (route generation / tracking / the run notification may not work).
- Clear app data or uninstall to remove local history.
- Contact us (email above) with privacy questions.

## Changes

We may update this policy when the App changes. The “Last updated” date will change accordingly. Continued use after an update means you accept the revised policy.

## Hosting this page

Publish this file (or the HTML copy) on a public HTTPS URL and paste that URL into Google Play Console → App content → Privacy policy. Also set `PRIVACY_POLICY_URL` in `local.properties` so Settings opens the same link.
