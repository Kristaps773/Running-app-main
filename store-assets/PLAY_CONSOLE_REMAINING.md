# Remaining Play Console answers (OrienteerRun)

Dashboard was **6 of 11** after: Privacy, Sign-in (No), Ads (No), Government (No), Financial (none), Health (Activity and fitness), Category (Sports).

Still needed:

## 1. Store Listing contact (Store settings → Edit contact)
- Email: `kristapsduda773@gmail.com` (required)
- Phone: optional
- Website: `https://kristaps773.github.io/Running-app-main/privacy-policy.html` (optional)

## 2. Target audience
- Age groups: **18 and over** only (or 13+ if you want teens; not “Designed for children”)
- Not primarily for children
- Store presence: appeal to adults training outdoors

## 3. Content rating (IARC questionnaire)
Typical answers for this app:
- Violence / blood / weapons: **No**
- Sexual content: **No**
- Language: **No**
- Controlled substances: **No**
- User interaction / UGC / location sharing to others: **No** (GPS stays on-device; no social feed)
- Purchases: **No**
- Email of contact for questionnaire: `kristapsduda773@gmail.com`

## 4. Data safety (match privacy policy)
**Does your app collect or share user data?** → **Yes** (location + optional crash diagnostics)

### Location
- Approximate / Precise location: **Collected**, purpose **App functionality**
- Collected while in use / during run (foreground service)
- **Not** shared with other companies for advertising
- **Not** sold
- Optional vs required: required for core features (route + tracking)
- Ephemeral? No (used during session; history on device)

### Device / other
- Crash logs / diagnostics (Sentry): **Collected**, purpose **Analytics** or **App functionality** (diagnostics)
- Not sold; not used for ads

### Data handling
- Data encrypted in transit: **Yes** (HTTPS to APIs / Sentry)
- Users can request deletion: mostly by clearing app data / uninstall (no account)

### Do not declare
- Personal info name/email collected by the app itself: **No**
- Financial info, photos, contacts, etc.: **No**

Third parties for maps/routing (OSM tiles, OSRM, Overpass) receive coordinates as needed for those APIs — describe as shared for app functionality if the form asks about sharing with service providers.

## 5. Main store listing
Copy: `store-assets/STORE_LISTING.txt`  
Graphics ready:
- `store-assets/icon-512.png`
- `store-assets/feature-graphic-1024x500.png`

**You still need phone screenshots** (min 2): Setup, Map with route, Active run, History.  
Upload those in Console → Grow → Store listings → Main store listing.  
(Browser automation cannot pick local files.)

## 6. Closed testing → production
After dashboard forms are green:
1. Testing → Closed testing → create track → use same AAB **4 (1.2.2)**
2. Add **≥12 tester emails**, keep test active **14 days**
3. Then **Apply for production** unlocks
4. Promote AAB to Production → Submit for review

Privacy URL (live): https://kristaps773.github.io/Running-app-main/privacy-policy.html
