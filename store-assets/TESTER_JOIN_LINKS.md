# OrienteerRun — self-serve beta testers (Reddit / Play)

Use **Closed testing (Alpha)** for public recruitment. That track counts toward Google Play’s **≥12 testers for 14 days** production requirement. **Internal testing** is for quick installs (latest build) but does **not** count toward production and still uses a manual email list today.

---

## Links to share (real URLs from Play Console)

### Primary — Closed testing (use on Reddit)

**Play opt-in (web):**  
https://play.google.com/apps/testing/com.orienteer.app

**Play Store listing (after they’re a tester):**  
https://play.google.com/store/apps/details?id=com.orienteer.app

### Optional — Internal testing (you + friends, v1.3.0)

**Internal opt-in:**  
https://play.google.com/apps/internaltest/4700453923961759287

Internal still uses the Console email list `testers` (3 addresses). It does **not** use the Google Group until you add people manually or switch setup.

### Google Group (self-serve membership — finish creation first)

Planned group (creation started in browser; **CAPTCHA must be completed manually**):

| Field | Value |
|--------|--------|
| Name | OrienteerRun Testers |
| Group email | `orienteerrun-testers@googlegroups.com` |
| Join policy | **Anyone can join** (set in the create wizard) |

After you finish **Create group** in [Google Groups](https://groups.google.com/my-groups), open the group → **About** (or **Group settings → General**) and copy the **public join link** Google shows. Paste it below for your records:

```
GOOGLE_GROUP_JOIN_URL=  ← paste after group exists
```

Typical pattern (verify on the group page):  
https://groups.google.com/g/orienteerrun-testers

---

## Tester flow (order matters)

1. **Join the Google Group** (link above) while signed into the **same Google account** they use on their phone.
2. Open the **Closed testing opt-in** link: https://play.google.com/apps/testing/com.orienteer.app  
3. Tap **Become a tester** / **Accept invitation**.
4. Install **OrienteerRun** from the Play Store (same account).

**Requirements:** Android device, Google account, country where the closed track is available (currently **178 countries/regions** on Closed Alpha).

---

## What’s configured in Play Console

| Track | Status | Build | Testers |
|--------|--------|-------|---------|
| **Closed testing – Alpha** | Active | 4 (1.2.2) | Email list `testers` (3 users) — **switch to Google Group** (see below) |
| **Internal testing** | Active | 5 (1.3.0) | Email list `testers` (3 users) |

**Closed testers page (Console):**  
https://play.google.com/console/u/0/developers/8746134727230130760/app/4972185293940009796/tracks/4698909467647001938?tab=testers

---

## One-time setup you must finish (~2 minutes)

### A. Create the Google Group (blocked on CAPTCHA)

1. In the browser tab on [Google Groups → My groups](https://groups.google.com/my-groups), finish the open **Create group** dialog.
2. Step 2 privacy: **Who can join group** = **Anyone can join** (already selected in the wizard).
3. Step 3: leave members empty → complete **Captcha** → **Create group**.
4. Copy the group’s **join link** from the group homepage and fill in `GOOGLE_GROUP_JOIN_URL` above.

### B. Attach the group to Closed testing

1. Play Console → **Test and release** → **Testing** → **Closed testing** → **Closed testing - Alpha** → **Testers**.
2. Select **Google Groups** (not Email lists).
3. Enter: `orienteerrun-testers@googlegroups.com`
4. **Uncheck** the email list `testers` if you no longer want manual addresses (optional; group alone is enough for self-serve).
5. Click **Save**.

### C. (Recommended) Ship the same build testers expect

Closed Alpha is still on **1.2.2** while Internal has **1.3.0**. For Reddit, promote or upload **1.3.0** to Closed Alpha when ready:

**Closed track releases:**  
https://play.google.com/console/u/0/developers/8746134727230130760/app/4972185293940009796/tracks/4698909467647001938

---

## Copy-paste for Reddit

**Title idea:** `[Beta] OrienteerRun — GPS orienteering / route app (Android, closed test)`

**Body:**

```
Looking for testers for OrienteerRun (free, no account required in-app).

1) Join the tester group (Google account): <GOOGLE_GROUP_JOIN_URL after step A>
2) Opt in to the Play closed test: https://play.google.com/apps/testing/com.orienteer.app
3) Install from Play Store (same Google account as on your phone).

Need a Google account on your Android phone. Feedback welcome via <your email or Reddit DM>.
```

Replace `<GOOGLE_GROUP_JOIN_URL>` after the group exists.

---

## Production gate reminder

- Google counts **closed testing** testers (opted in **and** eligible via your group/list), not internal-only installs.
- Keep the closed test **active** with **≥12 testers** for **14 consecutive days** before **Apply for production** on the dashboard.

---

*Generated from Play Console session 2026-10-24 (account kristaps.duda@gmail.com, app `com.orienteer.app`).*
