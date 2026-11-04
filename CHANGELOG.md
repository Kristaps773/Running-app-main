# OrienteerRun changelog

Play release history reconstructed from Cursor chats and AAB build dates.
**Do not keep a stack of old `.aab` files as history** — use this file + git commits.
Local Play upload copies live only under `store-assets/releases/` (gitignored); keep the latest AAB.

Current app version in source: **1.3.24** (`versionCode` 29).

## 1.3.24 (vc29) — 2026-11-05

Course points catalog + reuse-from-route, live footpath reroute, run map camera / path-lead modes, route polyline progress, locked course points refinements, setup layout metrics. Domain unit tests green after agent fixes.

## 1.3.23 (vc28) — 2026-11-04

Min checkpoint slider / layout metrics + locked-course budget fixes (tests green). Built after other agents finished.

## 1.3.22 (vc27) — 2026-11-04

Locked course points + checkpoint-count planner (unit tests green). Built after other agents finished that work.

## 1.3.21 (vc26) — 2026-11-03

Fix PDF export hang/ANR when fitting the map to the page (safe zoom border; Sentry ORIENTEERRUN-J). New Play upload after version code 25 was already used.

## 1.3.20 (vc25) — 2026-11-02

New Play upload after version code 24 was already used on the internal track. Same app as 1.3.19 (separate start/finish, endpoint picker, active-run notification controls).

## 1.3.19 (vc24) — 2026-11-01

Optional separate start/finish (one-tap return-to-start remains default), endpoint picker UX next to Generate, active-run notification controls (pause / unreachable / resume / end, pace), and related map/setup polish. Rebuilt signed AAB for Play upload.

## 1.3.18 (vc23) — 2026-10-31

Object-ring generator refinements: between-attempt radius adjust and dogleg-aware POI swaps.

## 1.3.17 (vc22) — 2026-10-31

Object-ring route generator (seed ring → lock controls on OSM objects → OSRM → swaps). Air treated as max (shorter OK); path/air still hard. Warmup from run history (else 5 km). Classic generator only as fallback when the POI pool is thin.

## 1.3.15 (vc20) — 2026-10-30

Fix release crash `ClassCastException` / Gson `ParameterizedType` under R8: OSRM/Overpass parse via raw `ResponseBody` + `Gson.fromJson(..., Class)` (Sentry ORIENTEERRUN-7).

## 1.3.14 (vc19) — 2026-10-29

Finish must be at the start location: after the last control you return near start (~15 m) to complete; marking later CPs unreachable no longer ends the run early. Trailing finish checkpoint + Sentry checkpoint diagnostics.

## 1.3.13 (vc18) — 2026-10-29

Bottom sheet drag performance: rubber-band/spring settle without per-frame coroutines; FAB/attribution follow sheet via `graphicsLayer`.

## 1.3.11 (vc16) — 2026-10-27

True drag handle on route preview / pre-run bottom sheet (same panel slides; no peek UI swap / tap-toggle).

## 1.3.9 (vc14) — 2026-10-26

Route generation: up to **5** full attempts before failing. Around the same window: unlimited run history list → detail, editable leg timings, hardened persist (also built as 1.3.9 in chat — prefer git/CHANGELOG over AAB filename alone if unsure).

## 1.3.8 (vc13) — 2026-10-26

More reliable generation (locale coordinate fix, retries, best-effort when limits are tight). Settings profiles: Precise / Balanced / Flexible. Safer everyday defaults.

## 1.3.7 (vc12) — 2026-10-26

Defaults path/air **1.8×** / air tolerance **2000 m**; tighter short-route fast-accept; matrix-informed presets.

## 1.3.6 (vc11) — 2026-10-26

Best-effort routes when OSRM succeeds but strict settings gates miss; defaults 1.8× / 1500 m; live route matrix under `store-assets/releases/route-matrix/` (local only).

## 1.3.5 (vc10) — 2026-10-25

New Play upload after vc9 already used; includes History empty/corrupt-run crash fix work (Sentry ORIENTEERRUN-4 / Gson run-detail parsing).

## 1.3.4 (vc9) — 2026-10-25

Misleading “rate-limited” on first generate: any OSRM failure was labeled that way; setup warmup stopped hammering OSRM before Generate.

## 1.3.3 (vc8) — 2026-10-25

Five free basemap styles + POI expansion for internal testing.

## 1.3.2 (vc7) — 2026-10-24

Pro features unlocked for testers (`ProAccess.forceProForTesting`): PDF export, map layers, etc., without a paywall.

## 1.3.1 (vc6) — 2026-10-24

R8/minify re-enabled to clear Play vitals / DEX size warning after 1.3.0.

## Earlier

- **1.3.0 (vc5)** — first signed internal-testing style upload path (Desktop AAB era).
- Pre-1.3 Play checklist, privacy policy, store graphics, FGS demo video — see early git commits on `master`.
