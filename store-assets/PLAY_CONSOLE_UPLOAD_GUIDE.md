# SubTerrania — Play Console Upload Guide

This is a step-by-step walkthrough for shipping SubTerrania to Internal Testing on Google Play. **You** (signed in as `atlyn.help@gmail.com` on Edge) follow these steps; I cannot drive the browser for you because Google explicitly blocks automation against signed-in Play Console sessions.

Estimated time: **~30 minutes**, mostly clicking and pasting.

If you've already done some of these steps, jump to the first unchecked one.

---

## 0. Prerequisites — before you start

- [ ] Signed in to `https://play.google.com/console` as **`atlyn.help@gmail.com`** in Edge.
- [ ] Google Play Developer account is **registered** (one-time $25 USD fee paid; identity verified).
  - If not yet registered: `https://play.google.com/console/signup` — fill in the form, pay $25, complete identity verification (driver's license / passport upload). Verification can take a few hours to a few days. Without this, none of the other steps work.
- [ ] You have the new signed AAB at hand:
  - `Q:\Repos\SubTerrania\app\build\outputs\bundle\release\app-release.aab`
  - SHA-256 `D56D13EA96A46D14CB07DB912DADA87E4C934D3CE35D87BB72C08FDE7709C4E4`
  - 17.49 MB
- [ ] You have `Q:\Repos\SubTerrania\store-assets\listing.md` open in another tab/editor — it contains every text field you'll paste below.

---

## 1. Enable GitHub Pages (privacy policy hosting)

The Play Console requires a **public URL** for the privacy policy. Pages config is already in the repo; you just flip the switch.

- [ ] Open `https://github.com/garrett-hamers/SubTerrania/settings/pages`.
- [ ] Under **Build and deployment**:
  - Source: **Deploy from a branch**
  - Branch: **`master`** / **`/docs`**
  - Click **Save**.
- [ ] Wait ~1 minute, then open `https://garrett-hamers.github.io/SubTerrania/privacy-policy.html` in a new tab. Confirm you see the policy.
- [ ] If you see a 404 after 5 minutes, go back to Settings → Pages and re-save; sometimes the first deploy needs a nudge.

You'll paste this URL into Play Console in steps 4 and 6.

---

## 2. Create the app in Play Console

If the SubTerrania app already exists in Play Console, skip to step 3.

- [ ] Open `https://play.google.com/console/u/0/developers/{your-account}/app-list` (Play Console → All apps).
- [ ] Click **Create app** (top-right).
- [ ] Fill in:
  - **App name:** `SubTerrania`
  - **Default language:** `English (United States) – en-US`
  - **App or game:** `Game`
  - **Free or paid:** `Free`
- [ ] Tick all four declarations at the bottom:
  - "I confirm I have access to all required permissions to publish my app."
  - Developer Program Policies acceptance.
  - US Export laws acceptance.
  - "This app meets the Play Families policies" → **only if** you intend to target families. We don't, so leave **unchecked** unless you want to.
- [ ] Click **Create app**.

You'll land on the Dashboard. You'll see a checklist of "Set up your app" tasks. Work through them in order.

---

## 3. App access

- [ ] Dashboard → **App access** → **Manage**.
- [ ] Select **All functionality is available without special access** (the game is single-player, no login).
- [ ] Save.

---

## 4. Ads, content rating, target audience, news app, COVID-19, government

These are quick declarations. Open each section from the Dashboard:

| Section | Answer |
|---|---|
| **Ads** | "No, my app does not contain ads." |
| **Content rating (IARC)** | See step 7 below. |
| **Target audience and content** | Target age groups: **18+** (or **13+** if you want a wider reach — anything 12 and under triggers the much stricter Designed for Families program). Recommended: **18+** for simplicity. Not appealing to children: **No** (the game is benign but isn't designed for kids specifically). |
| **News app** | "My app is not a news app." |
| **COVID-19 contact tracing** | "My app is neither a publicly available COVID-19 contact tracing nor status app." |
| **Data safety** | See step 8 below. |
| **Government apps** | "My app is not a government app." |
| **Financial features** | "My app does not have financial features." |
| **Health apps** | "My app does not have health features." |

---

## 5. Store settings → App category

- [ ] Dashboard → **Store settings** (in the left nav under "Grow" → "Store presence" or directly in the Dashboard checklist).
- [ ] **App category** → **Game** → **Strategy**.
- [ ] **Tags** (optional): `Strategy`, `Turn-Based`, `Single Player`.
- [ ] **Store listing contact details:**
  - **Email** (required, public): `atlyn.help@gmail.com`
  - **Phone** (optional, public): leave blank
  - **Website** (optional, public): `https://garrett-hamers.github.io/SubTerrania/`
- [ ] **External marketing**: leave default ("Allow promotion outside Google Play").
- [ ] Save.

---

## 6. Main store listing (the public listing)

This is where most of the copy lives. All values are in `store-assets/listing.md`.

- [ ] Dashboard → **Main store listing** → click into it.
- [ ] **App name**: `SubTerrania`
- [ ] **Short description** (max 80 chars): paste from `listing.md` "Short description" section. Default:
  ```
  Roll, build, and survive in a turn-based subterranean strategy adventure.
  ```
- [ ] **Full description** (max 4,000 chars): paste from `listing.md` "Full description" section. The triple-backtick block; paste only the text inside.
- [ ] **App icon**: upload `Q:\Repos\SubTerrania\store-assets\icon-512.png`.
- [ ] **Feature graphic**: upload `Q:\Repos\SubTerrania\store-assets\feature-graphic-1024x500.png`.
- [ ] **Video** (optional): leave blank.
- [ ] **Phone screenshots**: upload all four from `Q:\Repos\SubTerrania\store-assets\screenshots\`:
  - `01_difficulty_select.png`
  - `02_resume_game.png`
  - `03_gameplay_board.png`
  - `04_gameplay_resumed.png`
- [ ] **7-inch tablet** and **10-inch tablet** screenshots: leave blank (we'll add these in 1.0.2).
- [ ] **Languages**: keep the single English (US) entry for now.
- [ ] Click **Save** at the top-right.

---

## 7. Content rating (IARC questionnaire)

- [ ] Dashboard → **Policy and programs** → **App content** → **Content ratings** → **Start questionnaire**.
- [ ] **Email address**: `atlyn.help@gmail.com`.
- [ ] **Category**: **Game** → **Other** (no shooter / sim / casino sub-category fits).
- [ ] Now answer every checkbox **No / None**. Specifically:
  - Violence: **None** (no combat, no weapons, no death animations).
  - Sexuality: **None**.
  - Language: **None** (no profanity).
  - Controlled substances: **None**.
  - Gambling: **None**. Dice are deterministic gameplay, not simulated gambling, no wagering, no purchase of in-game tokens, no probability-of-win indicators that mimic casino games.
  - Miscellaneous: **None** (no loot-box mechanics, no user-to-user messaging, no shared user content, no location).
  - Online interactivity: **None** (game is fully offline).
- [ ] Submit. You'll get an immediate rating: **Everyone / 3+ / Everyone 10+** depending on board. All should be PEGI 3 / ESRB Everyone / IARC 3+.
- [ ] Click **Apply rating**.

---

## 8. Data safety

- [ ] Dashboard → **Policy and programs** → **App content** → **Data safety**.
- [ ] **Privacy policy URL**: `https://garrett-hamers.github.io/SubTerrania/privacy-policy.html`.
- [ ] **Data collection and security:**
  - Does your app collect or share any of the required user data types? **No**
  - Is all of the user data collected by your app encrypted in transit? **N/A** (no data collected)
  - Do you provide a way for users to request that their data be deleted? **N/A** (no data collected)
- [ ] **Data types**: tick **nothing**. Save with empty selections.
- [ ] Submit.

---

## 9. Privacy policy field (separate from Data Safety)

- [ ] Dashboard → **Policy and programs** → **App content** → **Privacy policy**.
- [ ] Paste: `https://garrett-hamers.github.io/SubTerrania/privacy-policy.html`.
- [ ] Save.

---

## 10. Upload the AAB to Internal Testing

- [ ] Left nav → **Testing** → **Internal testing**.
- [ ] **Tab: Testers** (do this first):
  - Click **Create email list**.
  - Name: `SubTerrania Internal Testers`.
  - Add tester email: `atlyn.help@gmail.com` (and any other emails you want to invite).
  - Save.
  - Tick the new list under "Testers".
  - **Feedback URL or email**: `atlyn.help@gmail.com`.
  - Save changes.
- [ ] **Tab: Releases** → **Create new release**.
- [ ] **App signing by Google Play**: if this is the first release, Play will offer to **let Google sign your app** (managed signing). Click **Use Google-generated key** — recommended; your upload key (the one in `keystore`) becomes the upload key only, and Google holds the signing key. If a previous release already chose otherwise, follow what's already configured.
- [ ] **Upload**: drag in `Q:\Repos\SubTerrania\app\build\outputs\bundle\release\app-release.aab`. Confirm SHA-256 matches `D56D13EA96A46D14CB07DB912DADA87E4C934D3CE35D87BB72C08FDE7709C4E4`.
- [ ] **Release name**: auto-fills from versionName (`1.0.1 (2)`); leave default.
- [ ] **Release notes** (in `<en-US>` block):
  ```
  Initial internal-testing release. Single-player turn-based strategy. No ads, no tracking, no in-app purchases.
  ```
- [ ] Click **Next** → review the summary → **Save** as draft.
- [ ] Click **Review release** → **Start rollout to Internal testing**. Confirm.

---

## 11. Wait for the pre-launch report

- [ ] Left nav → **Testing** → **Internal testing** → **Pre-launch report** (tab appears after the first upload).
- [ ] Wait 30 minutes to a few hours. Google runs the AAB on a small fleet of real devices and reports:
  - Crashes / ANRs
  - Performance issues
  - Accessibility issues
  - Security warnings
- [ ] Address anything **critical** before promoting.

---

## 12. Smoke-test on your own device

- [ ] On your own Android phone, sign in to the **Play Store** app with `atlyn.help@gmail.com`.
- [ ] Open the **opt-in URL** Play Console gives you on the Internal testing tab. Accept.
- [ ] Install **SubTerrania** from the Play Store. Launch it. Play one full game.
- [ ] Verify:
  - The home-screen icon shows "**SubTerrania**" (matches the listing title).
  - The Resume Game flow works after force-stop (the Phase E behaviour).
  - No unexpected ANRs, crashes, or weird permissions prompts.

---

## 13. Promote to Closed → Production (when you're ready)

- [ ] **Closed testing** (optional, recommended for ~1 week with 10–100 testers): copy the same AAB to a Closed track.
- [ ] **Production**: when pre-launch report is clean, smoke-test passes, and any closed-testing feedback is addressed:
  - **Production** → **Create new release** → **Add from library** → pick the same AAB you uploaded to Internal.
  - **Countries**: select where you want the game available (recommend **all countries** for max reach).
  - **Release notes**: copy/edit from internal.
  - **Roll out percentage**: start at **20%** for a staged rollout, monitor crashes for 24h, then bump.
  - **Save** → **Review release** → **Start rollout**.
- [ ] Google reviews production releases. Approval is usually **a few hours to 7 days** for first releases (longer if anything trips a manual review).

---

## 14. After publish — quick post-launch checklist

- [ ] Star/follow your own listing on Play Store and check it renders correctly across the home page, search results, and the listing page.
- [ ] Set up a simple feedback channel (Gmail filter on `atlyn.help@gmail.com`).
- [ ] Bookmark `https://play.google.com/console/u/0/developers/{your-id}/app/{app-id}/statistics` to monitor installs / vitals.
- [ ] Plan 1.0.2 — Crashlytics, edge-to-edge, balance tweaks, tablet screenshots (see `PRELAUNCH_REPORT.md` "Items deferred" list).

---

## Troubleshooting

**"Your APK / AAB needs to be signed with the upload certificate"**
You uploaded an AAB signed with a different key than the one Play has on file. If this is your *first* upload, that's a UI bug; refresh and try again. If it's a subsequent upload, you need to use the same `keystore` you used last time. The Phase H AAB was signed with `Q:\Repos\SubTerrania\keystore` (alias `subterranea`); credentials are in `files/NEW_KEYSTORE_CREDENTIALS.txt`.

**Privacy policy URL gives 404**
Pages takes 1–5 minutes to deploy. If it's still 404 after 10 minutes, go back to Settings → Pages and confirm the source is `master /docs` and click Save again.

**"The launcher icon doesn't match the AAB"**
The icon you upload to Play Console (`icon-512.png`) is the **store listing** icon and is independent of the launcher icon baked into the AAB. They can differ. Both come from the same source PNG in our case, so they look identical.

**IARC says "Gambling" because of the dice mechanic**
Dice in SubTerrania are a gameplay mechanic (resource production), not a wagering simulation. Answer **No** to gambling. If IARC pushes back, the appeal flow lets you write a 500-character explanation: paste *"Dice rolls determine resource production each turn — comparable to Settlers of Catan or Monopoly. There is no wagering, no purchase of probability boosts, no virtual currency, and no real or simulated casino mechanics."*

---

## Path 2 — for future releases (optional)

If you want subsequent AAB uploads to be a single command instead of UI clicks, see `tools/README.md` to set up the Google Play Developer API service account. **One-time setup** (~15 min), then `python tools/upload-to-play-console.py` ships any AAB to Internal Testing. Only useful for releases 2+; the initial release still requires this guide.

---

*Last updated: Phase I — upload tooling.*
