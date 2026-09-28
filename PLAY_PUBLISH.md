# Publish OrienteerRun to Google Play

One-shot checklist: **internal testing → same AAB → production**. Contact email is filled later (before final submit).

## Monetization (locked for v1)

- **v1 launch: free only** — no ads, no Play Billing yet.
- **Later: one-time Pro unlock** (no ads as the main model). Free stays usable day-to-day; Pro adds power/data for serious users.
- **Pro candidates (decide after real users):** custom/long distances, advanced route tuning, deeper history/analytics, PDF/GPX export, dark basemap / future offline packs.
- Do **not** gate Start Run / basic generate / live tracking.

---

## Local toolchain (this PC)

Portable install (no Android Studio GUI required for CLI builds):

- JDK: `D:\Android\jdk\jdk-17.0.20.1+1`
- Android SDK: `D:\Android\Sdk` (`sdk.dir` in `local.properties`)
- Debug APK: `app\build\outputs\apk\debug\app-debug.apk`

```powershell
$env:JAVA_HOME = "D:\Android\jdk\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
cd D:\PROJECTS\Running-app-main
.\gradlew.bat :app:assembleDebug
```

A winget Android Studio install may still be waiting on a Windows **Administrator** prompt if you want the full IDE.

---

## Phase 0 — Accounts (you must do these)

### 0.1 Google Play Console (~$25, required)

1. Open [Google Play Console](https://play.google.com/console) and sign in with the Google account you will use as developer.
2. Pay the one-time registration fee and complete **identity verification** (can take days).
3. Create app:
   - App name: **OrienteerRun** (changeable later)
   - Default language: your choice
   - App or game: **App**
   - Free / paid: **Free**
   - Declarations: accept Play policies
4. Package name at first upload must be **`com.orienteer.app`** (set in the project; cannot change after first publish).

You cannot upload an AAB until this account is active and verified.

### 0.2 Sentry + Cursor

1. Sentry org: **`krists-kristaps-duda`** (region `de.sentry.io`).
2. Android project **`orienteerrun`** created; DSN is in local `local.properties` (gitignored).
3. Cursor MCP: `~/.cursor/mcp.json` points at `https://mcp.sentry.dev/mcp` — authenticate via Cursor MCP settings if tools stop working.
4. Create an **Auth Token** with mapping/release upload scopes and set `SENTRY_AUTH_TOKEN` in `local.properties` for ProGuard upload on `bundleRelease`.

### 0.3 Privacy policy URL

Play requires a **public https** privacy page for location apps (not in-app only).

1. Use the draft in [`docs/privacy-policy.md`](docs/privacy-policy.md).
2. Host it (recommended: **GitHub Pages** on this repo):
   - Settings → Pages → Deploy from branch → `/docs` or root `gh-pages`
   - Or add a static `docs/privacy-policy.html` and enable Pages
3. Expected URL shape (adjust to your user/org):  
   `https://<github-user>.github.io/Running-app-main/privacy-policy.html`
4. Put the final URL in `local.properties` as `PRIVACY_POLICY_URL=...` (see below) and in Play Console → App content → Privacy policy.
5. Add **contact email** on the policy and Play listing **before submit** (placeholder until then).

---

## Phase 1 — Local secrets (`local.properties`, gitignored)

Add (do not commit):

```properties
# Sentry
SENTRY_DSN=https://YOUR_KEY@oYOUR_ORG.ingest.sentry.io/YOUR_PROJECT
SENTRY_AUTH_TOKEN=sntrys_...
# Optional if not using defaults from sentry {}
# SENTRY_ORG=your-org-slug
# SENTRY_PROJECT=orienteerrun

# Release signing (after you create the keystore — see below)
STORE_FILE=D\:\\path\\to\\orienteer-upload.jks
STORE_PASSWORD=...
KEY_ALIAS=orienteer_upload
KEY_PASSWORD=...

# Public privacy page (used in Settings + BuildConfig)
PRIVACY_POLICY_URL=https://YOUR_USER.github.io/Running-app-main/privacy-policy.html
```

Optional root `sentry.properties` (also gitignored pattern in docs; prefer `local.properties` + Gradle):

```properties
defaults.orga=YOUR_ORG_SLUG
defaults.project=YOUR_PROJECT_SLUG
auth.token=YOUR_AUTH_TOKEN
```

---

## Phase 2 — Upload keystore (once; back up offline)

```bash
keytool -genkey -v -keystore orienteer-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias orienteer_upload
```

- Store the `.jks` and passwords somewhere safe (not in git).
- Point `STORE_FILE` in `local.properties` at that file.
- Enable **Play App Signing** when Console asks; this keystore is your **upload** key.

---

## Phase 3 — Build the release App Bundle

```bash
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

Requires signing props in `local.properties`. Without them, release builds are unsigned and Play will reject the upload.

With `SENTRY_DSN` + `SENTRY_AUTH_TOKEN`, the Sentry Gradle plugin uploads ProGuard mappings on release builds so stack traces are readable.

---

## Phase 4 — Store listing assets (you produce)

In Play Console → Grow → Store presence:

| Asset | Spec |
|-------|------|
| App icon | 512×512 PNG |
| Feature graphic | 1024×500 |
| Phone screenshots | Min 2; aim 4–8 (home, generate, active run, history) |
| Short description | ≤80 characters |
| Full description | Route generation + GPS checkpoint training |
| Category | Health & Fitness or Sports |
| Contact email | **Add before submit** |
| Content rating | Complete questionnaire |
| Target audience | Not “Designed for kids” unless it is |
| Pricing | Free, no IAP |

---

## Phase 5 — Data safety (match privacy policy)

Typical answers for this app:

- **Location**: collected, app functionality, while using / during run (foreground service). Not sold. Not shared except as needed for map/routing providers you already use (OSM/OSRM/Overpass) — describe accurately.
- **Crash logs / diagnostics**: Sentry (device model, OS, stack traces). Purpose: App functionality / Analytics (diagnostics). Not sold.
- **No** accounts, payments, ads, or personal name/email collection by the app itself.

---

## Phase 6 — Internal test → production

1. Play Console → Testing → **Internal testing** → create release → upload `app-release.aab`.
2. Add yourself as a tester; install from the internal track link.
3. Smoke test: generate route, start run (grant location + notifications), confirm GPS notification.
4. In **debug** builds only: Settings → “Send test event to Sentry” (or trigger a known error) and confirm it appears in Sentry with a readable stack if mappings uploaded.
5. Complete privacy URL, Data safety, content rating, location / foreground service declarations.
6. Promote the **same** AAB to **Production** → Submit for review.

---

## Agent vs you

| Done in repo | You still do |
|--------------|--------------|
| Sentry SDK + Gradle + mapping upload wiring | Play Console signup, DSN, auth token, Cursor↔Sentry auth |
| Signing Gradle config | Create keystore + passwords |
| targetSdk 35, notification permission, Settings privacy link | Host privacy URL; fill contact email |
| `docs/privacy-policy.md`, this checklist | Screenshots, Console forms, AAB upload, promote |

---

## Success criteria

- [ ] Play Console app exists for `com.orienteer.app`
- [ ] Signed AAB installs from internal testing
- [ ] Sentry receives at least one event (readable frames after mapping upload)
- [ ] Privacy URL live; Data safety complete; production submitted / published
