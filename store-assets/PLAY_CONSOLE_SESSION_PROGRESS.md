# Play Console progress (2026-10-06 session)

## Done this session
- **Content ratings (IARC):** Saved (Everyone / All ages). Email `kristapsduda773@gmail.com`.
- **Target audience:** Saved — **18 and over**.
- App content “Need attention” dropped from **5 → 3**.

## Still need attention (3)
1. **Data safety** — location + crash diagnostics; not sold; encrypted in transit (answers in `PLAY_CONSOLE_REMAINING.md`).
2. **Advertising ID** — declare **No** (app does not use AD_ID).
3. **Foreground service permissions** — declare **LOCATION** for run tracking FGS.

Also still on dashboard (outside App content):
- Store listing contact + main listing graphics/screenshots upload (manual file picker).
- Closed testing (≥12 testers, 14 days) before production apply.

## Screenshots → email
- Zip: `%USERPROFILE%\Desktop\PlayStore-OrienteerRun-Assets\OrienteerRun-phone-screenshots.zip`
- Gmail compose draft opened to `kristaps.duda@gmail.com` — **attach the zip and hit Send**.
- Text draft: `EMAIL-DRAFT-to-kristaps.duda.txt` in the same Desktop folder.
- Live emulator re-capture blocked (emulator stuck offline / low host RAM). Install new debug APK and recapture on device when possible.

## Code polish shipped (build OK)
- Spacing/radius tokens (`Spacing.kt` / `Shape.kt`)
- Active Run HUD/map z-order fix (Column layout)
- Setup / Map / History / Settings spacing cleanup
