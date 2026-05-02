# SubTerrania — Play Console Listing Copy

This file is a draft for the Google Play Console store listing. Review and edit anything you'd like before pasting into Play Console.

---

## App name (max 30 chars)
> Used at the top of the listing and under the home-screen icon. Must match `strings.xml` `app_name`.

```
SubTerrania
```

11 / 30 characters.

---

## Short description (max 80 chars)
> Shown directly under the title in search results and the listing header. The hook.

```
Roll, build, and survive in a turn-based subterranean strategy adventure.
```

73 / 80 characters.

### Alternates (pick whichever you prefer)

- `Turn-based hex strategy. Roll dice, build, and race for victory underground.` — 76 chars
- `Solo turn-based strategy below the surface. No ads, no internet, no tracking.` — 77 chars
- `Mine, build, and outlast the depths in a tactical solo strategy game.` — 69 chars

---

## Full description (max 4000 chars)
> Shown when the user taps "Read more". Used for ASO. Lead with the hook, then features, then trust signals.

```
Descend into SubTerrania — a fully offline, single-player turn-based strategy game where every roll, every tile, and every trade matters.

You play an explorer pushing into a hex-grid underworld. Roll dice each turn to harvest resources from the tiles you've claimed, build structures to expand your reach, trade what you have for what you need, and chase a victory-point target before the turn timer runs out. Choose your difficulty, pick a character with a unique twist on the rules, and try to master each map preset.

KEY FEATURES

• Turn-based hex strategy with no waiting, no timers, and no opponent moves to sit through. Take your turn at your own pace.
• Four difficulty modes — Easy, Normal, Hard, and Nightmare. Easy is forgiving; Nightmare is brutal. Pick the one that fits your mood.
• Multiple playable characters, each with a different starting kit and a distinct strategic angle. Find your favorite, then try to master the rest.
• Multiple map presets that change the shape of the underworld and the path to victory.
• Resource production driven by dice rolls — high variance, high tension, but always tactical: every build, every trade, and every ability lets you bend the odds.
• Build structures that unlock new options: light up dark tiles, boost production, and open trade ratios.
• Auto-save and Resume. Force-stop, lock your screen, take a phone call — your game is exactly where you left it when you come back.
• Achievements that reward both first-timers and long-haul players.

FAIR-PLAY PROMISE

• 100% offline — no internet connection required, ever.
• No ads. No paywalls. No in-app purchases. No "premium" currencies.
• No tracking. No analytics. No advertising SDKs. No user accounts.
• No personal data collected, transmitted, or stored. The only permission requested is VIBRATE for haptic feedback.
• Plays on a phone in a tunnel, on a flight, or anywhere else with no signal.

ACCESSIBILITY

• Built for one-handed play on phones.
• Large-tap targets for the action bar.
• Respects your system text size and color settings.
• Haptic feedback can be turned off in your phone's accessibility settings.

WHO IS IT FOR

• Players who love board-game-style turn-based strategy (think dice-driven resource production with a tactical exploration twist).
• Anyone tired of free-to-play games that demand attention, energy timers, or a credit card.
• Commuters, travelers, and anyone who wants a real game in their pocket — not a Skinner box.

SubTerrania is a love letter to small, complete, finishable games. There is a beginning, a middle, and an end to every run. You play, you win or you lose, and you move on.

Roll the dice. Plumb the depths. See how far you can go.

Privacy policy: https://garrett-hamers.github.io/SubTerrania/privacy-policy.html
Source code: https://github.com/garrett-hamers/SubTerrania
Contact: atlyn.help@gmail.com
```

Length: ~2,830 characters, well under the 4,000-char cap.

---

## Category & tags

| Field | Value |
|------|-------|
| App category | **Games → Strategy** |
| Tags | turn-based, strategy, hex, dice, single-player, offline, no-ads, no-iap |
| Content rating (IARC) | Expected: **PEGI 3 / ESRB Everyone** — no violence, no gambling simulation, no in-game purchases, no user-generated content. |
| Contains ads | **No** |
| In-app purchases | **No** |

---

## Contact information for Play Console

| Field | Value |
|------|-------|
| Email (required, public) | `atlyn.help@gmail.com` (matches privacy policy contact — confirm this is the address you want public) |
| Website (optional) | `https://garrett-hamers.github.io/SubTerrania/` |
| Privacy policy URL (required) | `https://garrett-hamers.github.io/SubTerrania/privacy-policy.html` |

> **Action:** Confirm `atlyn.help@gmail.com` is the address you want listed publicly. If you want a separate contact email for Play Console, swap it here and in `docs/privacy-policy.html` before going live.

---

## Data Safety form answers (cheat sheet)

| Question | Answer |
|----------|--------|
| Does your app collect or share any user data? | **No** |
| Does your app collect any data types from any of these data categories? | None of them. Tick nothing. |
| Is all of the user data collected by your app encrypted in transit? | N/A (no data collected). |
| Do you provide a way for users to request that their data be deleted? | N/A (no data collected). |

---

## IARC questionnaire (cheat sheet)

| Question | Answer |
|----------|--------|
| Violence (cartoon / fantasy / realistic) | None |
| Sex / nudity | None |
| Gambling / simulated gambling | **None.** Dice rolls are deterministic gameplay mechanics with no real or simulated wagering, no purchase of tokens, and no probability indicators that mimic casino games. |
| Profanity / crude humor | None |
| Drugs / alcohol / tobacco | None |
| Fear / horror | None (Nightmare is a difficulty label only — no horror imagery) |
| Sensitive social topics | None |
| User-generated content / online interaction | **None.** App is fully offline. |
| Sharing user location | **No.** App does not request or use location. |
| Personal info shared | **None.** |

Expected outcome: **3+ / Everyone** across all rating boards.

---

## Screenshot captions (optional, shown under each screenshot)

If Play Console asks for per-screenshot captions, suggested copy:

1. `01_difficulty_select.png` → "Pick your difficulty: Easy to Nightmare."
2. `02_resume_game.png` → "Auto-save and Resume — never lose progress."
3. `03_gameplay_board.png` → "Roll dice, harvest resources, build to expand."
4. `04_gameplay_resumed.png` → "Plan every turn. Race to victory before time runs out."

---

## Asset checklist (what to upload in Play Console)

- [x] **Hi-res icon (512×512 PNG):** `store-assets/icon-512.png`
- [x] **Feature graphic (1024×500 PNG):** `store-assets/feature-graphic-1024x500.png`
- [x] **Phone screenshots (≥2, 1080×2160 PNG):** `store-assets/screenshots/01..04.png`
- [ ] **Tablet screenshots (optional):** not produced — defer until 1.0.2 if you want to widen device targeting.
- [ ] **Promo video (optional):** none yet — defer.
- [x] **Signed AAB:** `app/build/outputs/bundle/release/app-release.aab`
- [x] **Privacy policy:** `docs/privacy-policy.html` (public URL after enabling Pages, see below)

---

## After-this-PR checklist for you

1. **Enable GitHub Pages** for `garrett-hamers/SubTerrania`:
   - Settings → Pages → "Build and deployment" → Source: **Deploy from a branch** → Branch: **`master`**, Folder: **`/docs`** → Save.
   - Wait ~1 minute, then verify `https://garrett-hamers.github.io/SubTerrania/privacy-policy.html` resolves.
2. **Upload assets in Play Console** (Main store listing → Graphics):
   - Icon → `store-assets/icon-512.png`
   - Feature graphic → `store-assets/feature-graphic-1024x500.png`
   - Phone screenshots → all 4 PNGs from `store-assets/screenshots/`
3. **Paste copy** from this file into Play Console: title, short description, full description.
4. **Privacy & contact** — paste URLs from the Contact section above.
5. **Complete IARC + Data Safety** using the cheat sheets above.
6. **Upload the AAB** to Internal Testing track (Release → Testing → Internal testing).
7. **Wait for the pre-launch report** (auto-runs in ~30 min). Address any crashes/ANRs it reports.
8. **Promote** Internal → Closed → Production once the pre-launch report is clean and you've smoke-tested the internal build on a real device.

---

*Last updated: Phase H — listing assets prep.*
