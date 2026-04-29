# SubTerrania — Pre-Launch Deep Research Report

> **Scope.** Evidence-based assessment of what should be improved before publishing
> SubTerrania to the Google Play Store. Two parallel lenses: **gameplay
> fun/replayability** and **launch readiness**.
>
> **Methods.**
> 1. Static code & config audit (domain logic, ViewModel, Compose UI, manifest, build config, ProGuard, privacy policy).
> 2. JVM playtests — `AutoPlaytest` (100 games × 4 difficulties × 4 AI profiles) and `FunFactorPlaytest`.
> 3. Real Android emulator smoke test — Pixel 6 / API 34 AVD, debug APK (v1.0.1, vc 2). Cold start, multi-turn play, rotation, force-stop recovery, font scaling, rapid-restart bug repro.
>
> **No code changes were made for this report.** Findings are intended to drive a follow-up implementation sprint.
>
> Raw artifacts (playtest summaries, logcat, gfxinfo, meminfo, screenshots, UI dumps) are stored in the session workspace, not committed.

---

## Executive Summary

SubTerrania is a remarkably polished solo Catan-inspired hex-strategy roguelike. The simulation engine, the privacy posture, the meta-progression, and the "Lucky 7" / consolation system are all in good shape. Empirical playtest guardrails pass 23 of 24 checks, frustration scores are healthy on every difficulty, and engagement scores sit between 85% and 95%.

That said, **the build is not yet ready for the Play Store** for three independent reasons, and there are several gameplay tuning issues that will hurt week-1 retention if shipped as-is.

**TL;DR — top 5 things to do before publishing:**

1. **🔴 P0 — Remove the committed `keystore` file from version control**, rotate the upload key, and re-enable the release signing config that's currently commented out.
2. **🔴 P0 — Bump `targetSdk` from 34 → 35** (Google Play requires API 35 for new submissions since Aug 2025) and update the Compose BOM (currently 2023.08.00, ~2 years stale).
3. **🔴 P0 — Persist active `GameState`** so an in-progress game survives process death. Today only meta-progression is saved; force-stopping mid-game (verified on emulator) drops the player back to the difficulty menu with no resume option.
4. **🟠 P1 — Rebalance the build/explore loop.** The "Builder/Optimizer" AI profile wins **only 46% of games**, while "Aggressive Explorer" wins **96%**. Structures aren't pulling their weight versus exploration on Hard/Nightmare.
5. **🟠 P1 — Fix the "Easy is too easy" retention flag** (96% win rate, 1/25 close finishes). The playtest harness itself flags this. Easy is a new player's first 1–3 sessions, and 96% wins teaches them the game has no challenge.

The rest of this report unpacks each item with empirical numbers and identifies further P1/P2 issues.

---

## 1. What's already great — preserve these

These are real differentiators. Don't regress them while addressing the issues below.

| Strength | Evidence |
|---|---|
| **Privacy posture is best-in-class for indie**. Single VIBRATE permission, no analytics SDKs, telemetry only writes to Logcat via reflection (`android.util.Log.d`), never transmits. Privacy policy in `docs/privacy-policy.html` matches actual app behavior. | `AndroidManifest.xml`, `domain/telemetry/GameTelemetry.kt`, `docs/privacy-policy.html` |
| **ProGuard strips Log calls in release** (`-assumenosideeffects class android.util.Log`). | `app/proguard-rules.pro` |
| **Engagement scores 85–95% on all 4 difficulties**, frustration ≤ 40 on Easy/Normal/Hard, dead-roll streak ≤ 3 everywhere. | `FunFactorPlaytest` guardrails — 23/24 PASS |
| **The "Lucky 7" consolation system** prevents dead turns. Empirically: 169 Hustle, 67 Barter, 23 Scavenge picks across 100 games — players engage with the choice. | `AutoPlaytest`, observed live in smoke test on Hard turn 4 |
| **Hard difficulty is the sweet spot** — 80% win rate, 44% close finishes, 5.2 "aha moments" per game, 11.2 high-decision-richness turns. | `AutoPlaytest` Section 1, 2 |
| **Comeback potential on Nightmare** — 19/25 games are comebacks from a midpoint deficit, 0.39 average comeback score. | `AutoPlaytest` pacing section |
| **Rotation/configuration changes preserve state** — verified live by switching to landscape mid-turn on Hard. | Smoke-test screenshot `05_landscape.png` |
| **Modal priority chain in `GameScreen.kt` (lines 121–133)** prevents the Feb-2026 "difficulty selection rendered over Turn 1" bug from recurring. The `hasHigherPriorityModal` guard auto-dismisses the End Turn confirm if a higher-priority modal opens. | `GameScreen.kt:121-133`, smoke-test rapid-restart did not repro |
| **Engine architecture is clean** — immutable `GameState` + pure transforms in `GameEngine`, `StructureEngine`, `TradeEngine`, `ExplorationEngine`, `EventEngine`. Easy to test and reason about. | Domain layer review |
| **Meta-progression already exists** — characters, achievements, totals persisted to SharedPreferences `subterranea_meta`. | `MetaProgression.kt`, `GameViewModel.kt:868-900` |
| **Cold-start & memory budget are reasonable for a debug build** — 89 MB PSS at Turn 1, 4-5s cold start. Release build with R8 will be substantially smaller and faster. | `meminfo_turn1.txt` |
| **Tutorial hint system is contextual** ("Tip: Build a Lantern to light up dark tiles!" appeared on Turn 1 of a fresh Hard game). | Smoke test `ui_02_after_difficulty.xml` |

---

## 2. Gameplay: Fun & Engagement

### 2.1 What's working

- Median game length is short and mobile-appropriate: Easy 13 turns, Normal 14 turns, Hard 17 turns (manageable in a single 5–15 min sit-down session).
- "Aha moments" (turns with VP jump ≥ 2) are dense: 3.3–5.2 per game, peaking at Hard.
- Decision richness is high: 50–65% of turns offer 3+ distinct action types.
- Multi-tile rolls (dice hitting 2+ tiles — the "Catan moment") fire 22–31% of rolls. Good.

### 2.2 Issues

**🟠 P1 — Normal difficulty's early-game engagement is below target.** The fun-factor guardrails pass 23 of 24 checks; the **one failure** is Normal's early engagement: actual 1.4 actions/turn (T1–3) vs target ≥ 1.5. This is the "cold opening" problem — Normal players make slightly fewer meaningful moves than the design wants in their first three turns. Likely cause: starting resources on Normal allow exactly one immediate action, and the second slot frequently goes unused while the player saves up.

**🟠 P1 — Players forget abilities.** Only **21 of 100** games used a character ability. Across the 4 AI profiles, ability usage averages 0.1–0.3 per game. Compare to ~13 explores per game. Abilities are the biggest character-differentiation lever; if 79% of players never press the button, the meta-progression character system is half-implemented from the player's perspective.
- *Likely cause:* Abilities are gated behind a UI step that's not in the same place as the constantly-pressed Roll/Build/Explore buttons. The smoke-test UI dump shows "Roll, Build, Clear, Trade, End" in the action bar — **no Ability button**.
- *Verify:* Where is ability triggered today? If only via tile selection or a sub-menu, surface it in the bottom action bar. Add a one-line tutorial hint the first time a player rolls without using an ability.

**🟠 P1 — Trade usage collapses on Hard/Nightmare.** Easy: 2.6 avg trades per game, 21/25 games used trading. Hard: 0.2 avg, 5/25 games used it. Nightmare: 0.2 avg, 4/25 games. Trade is part of the core Catan-DNA loop — players are bypassing it as soon as resources get scarce.
- *Diagnosis:* The trade rate scales **worse** with difficulty (3:1 on Hard, 4:1 on Nightmare) just when the player has fewer resources. The math says "don't trade." This is correct realism but bad fun design — denial of a system that was promoted to the player.
- *Recommendation:* Make at least one trade slot generous on every difficulty (e.g., a fixed 2:1 on a single rotating resource per turn, regardless of difficulty), or have certain structures provide a trade-rate discount that snaps Hard/Nightmare back to ≤ 3:1.

**🟢 P2 — Action type variety is low.** Players use 2.2–3.2 distinct action types per game out of 5 available (EXPLORE, BUILD, TRADE, ABILITY, CLEAR). The retention-risk module flagged this internally.
- This compounds the ability and trade findings above. Rubble-clearing in particular is rarely used — possibly because rubble is rare, possibly because clearing it doesn't feel rewarding.

---

## 3. Gameplay: Replayability

### 3.1 What's working

- 9 terrain types, 6 zones with distinct number-pool distributions, 4 difficulties × 4 character classes × map presets — substantial mathematical variety.
- Achievement attainment varies meaningfully by playstyle: First Explorer 99%, Master Builder 92%, Deep Delver 81%, Core Seeker 80%, The Illuminator 80%, Crystal Baron 9%. Crystal Baron in particular is a great "long-term mastery" goal.
- Structure diversity (Shannon entropy) climbs nicely from 0.90 (Easy) → 1.49 (Hard), suggesting late-game build choices open up.

### 3.2 Issues

**🟠 P1 — The "Builder" playstyle is non-viable.** The four AI profiles in `AutoPlaytest` produced very uneven results:

| Profile | Wins/Games | Win % | Avg Turns | Avg VP | Structures | Explores |
|---|---|---|---|---|---|---|
| **Aggressive Explorer** | 23/24 | **96%** | 15.0 | 16.0 | 5.7 | 13.7 |
| **Balanced Player** | 20/28 | 71% | 17.4 | 15.1 | 7.2 | 13.3 |
| **Cautious Newbie** | 19/24 | 79% | 17.3 | 15.2 | 9.0 | 13.9 |
| **Builder/Optimizer** | 11/24 | **46%** | 19.7 | 14.3 | 7.2 | 12.2 |

Spread of 50 percentage points (46% → 96%) between two valid playstyles is a major balance issue. A "build a thriving colony" player loses **half** the time, while an "explore aggressively" player loses **once**. This trains players to abandon the build/economy loop — the genre's most iconic mechanic.

- *Likely root causes:*
  - **Crystal scarcity hurts Refineries and Excavators.** `BoardGenerator` Surface terrain pool excludes `CRYSTAL_GROTTO` and contains a duplicate `LICHEN_FIELD` (lines 54–61) — confirmed intentional by code comment, but the playtest data shows this bites the Builder profile hard. Crystal Baron achievement attainment is only 9%.
  - **Deep Excavator is underbuilt** — 23% of games, avg 0.3 per game. The Excavator *replaces* an Outpost (it's removed from `state.structures`), and exploration-first players win before reaching the cost threshold.
  - **Core Anchor is rarely built** — 12% of games. Players win without it.
- *Recommendation:* Either buff late-game structures (faster VP gain, ability synergies) or add a "hidden VP bonus for unique structure types" to reward diversification.

**🟠 P1 — Easy difficulty is too easy** (96% win rate, 1/25 close finishes = 4%, "AvgVP 12.2/12" means the average game OVERSHOOTS the target). The fun-factor harness itself flags this with `ℹ️ Easy 96% win rate — maybe too easy`.
- A first-time player on Easy will win their first three games in 13 turns each, learning that the game has no challenge. They will not return for Normal/Hard if their first impression is "this is a walkover." Recommend either:
  - Bumping Easy VP target from 12 to 14, OR
  - Slowing starting resources by 1–2, OR
  - Adding 1 mandatory "Hazard tile" on Easy so the player encounters the hazard system at least once.

**🟢 P2 — Achievement deflation.** First Explorer (99%) and Master Builder (92%) achieve in nearly every game — the bar is too low for them to feel earned. The harness flags this internally as `ℹ️ ... too common`. Recommend making them tiered (e.g., First Explorer I/II/III for 1/5/10 explores) so the first hit comes early but the next hit is a meaningful goal.

**🟢 P2 — Hazard variety is uneven.** GasLeak fires 102 times across 100 games, MagmaBurst only 35. If MagmaBurst is meant to be a rare set-piece, fine; if it's meant to be one of four equally-weighted hazards, rebalance.

---

## 4. Gameplay: Balance (empirical)

### 4.1 Difficulty curve

| Difficulty | Win % | Avg Turns | Avg VP / Target | Dead Roll % | VP Momentum | Frustration | Engagement |
|---|---|---|---|---|---|---|---|
| Easy | 96% | 13.0 | 12.2 / 12 | 40.4% | 1.02 | 33.8 | 89.8 |
| Normal | 88% | 14.0 | 14.1 / 14 | 45.6% | 1.15 | 34.5 | 88.4 |
| Hard | 80% | 17.4 | 18.8 / 19 | 33.5% | 1.18 | 38.2 | 95.3 |
| Nightmare | 28% | 25.0 | 15.3 / 18 | 38.8% | 0.67 | 61.5 | 84.8 |

- **Hard is the gold standard.** 80% win, the highest engagement, and 44% of games are close finishes. Lean into Hard as the "real" experience and tune Easy/Normal as ramps to it.
- **Nightmare's VP momentum (0.67) is below the design floor of 0.8** for VP/turn. That means Nightmare players slog. They're meant to slog, but document this is intentional, otherwise it looks like a balance bug.
- **Normal has the highest dead-roll percentage (45.6%)**, and is the difficulty most new players will start on after Easy. This is uncomfortable.

### 4.2 Snowball Index (>1.5 = early lead compounds too fast)

| Difficulty | Snowball Index | Status |
|---|---|---|
| Easy | 1.16 | Healthy |
| Normal | **1.44** | Borderline |
| Hard | 1.26 | Healthy |
| Nightmare | **1.52** | Over threshold |

Nightmare's runaway-leader behavior is mitigated by the 0.39 comeback potential, but Normal's 1.44 snowball + 0.27 comeback is the worse combination — a slightly bad start there feels deterministic.

### 4.3 Resource economy

- Easy spends 130% of what it produces (relies on starting bank + scavenge).
- Hard 111.7%, Nightmare 89.0% — Nightmare players are net **storing** resources (good — implies depth), but the lower production also explains why structures aren't built.
- Peak inventory rises sharply on harder difficulties (12.6 → 25.3) because the game lasts longer and players don't get to spend.

---

## 5. UX & UI (smoke test observations)

### 5.1 What works

- The difficulty selection screen reads clearly: each card shows VP target, action count, and trade rate. Visible from `ui_01_main_menu.xml`.
- Resource icons have `content-desc` attributes (Mycelium Stalks, Basalt Blocks, Beetle Chitin/Tallow, Cave Lichen, Iron Ore, Luminary Crystal) and an explicit "Victory Points" content description — basic accessibility scaffolding is present.
- End-turn confirmation dialog asks "End turn now? You still have 2 actions left" — prevents accidental skips. Good defensive UX.
- Tutorial hint surfaces immediately on Turn 1 ("Tip: Build a Lantern to light up dark tiles!").
- The "ROLL DICE" header changes to "MAIN ACTION" after rolling, which is good phase signposting.

### 5.2 Issues

**🟠 P1 — Cold start is slow even after warm-up.** First cold start: 5132ms. Force-stop + relaunch (warm dex cache): 4402ms. Both are noticeably slow on a debug build. On a low-end real device (e.g., 2GB RAM Android Go), expect 2–3× this. Consider:
- A baseline profile (`androidx.profileinstaller`) — significant first-launch wins.
- Moving any work currently happening in `GameViewModel` `init`/`reset` paths off the main thread.

**🟠 P1 — Skipped frames during initial composition.** Logcat shows `Choreographer: Skipped 138 frames!` ~5s after launch (≈ 2.3 seconds of work on the main thread). Then `Skipped 49 frames` and `Skipped 65 frames` shortly after. During gameplay, another `Skipped 40 frames` was observed.
- `gfxinfo` reports 94.57% janky frames with median 89ms (target 16.7ms). This includes substantial software-rendering overhead from the SwiftShader emulator, but the **shape** matters — most slow frames are "Slow UI thread" (3,652) and "Slow issue draw commands" (4,153) out of 4,399 total. That points at heavy Compose composition, not GPU.
- Suspects to profile in Android Studio: HexMap rendering (presumably draws every hex on every recomposition), the resource bar's `painterResource` PNGs (31 PNG drawables in xxhdpi, no vectors).

**🟠 P1 — Game state is lost on process death.** Verified live: started a Hard game, played to Turn 4, ran `adb shell am force-stop`, relaunched — landed back on the difficulty selection screen with no "Resume" option. Players will hit this when:
- Phone reboots, system update applies, OS kills app under memory pressure, user swipes the app away.
- *Recommendation:* Persist `GameState` to a single DataStore Preferences entry (or a small JSON file) on every `endTurn` and on `onSaveInstanceState`. Add a "Resume game?" button to the difficulty screen if a saved state exists. This is the single highest-impact mobile-fit change you can make.

**🟢 P2 — `LaunchState: COLD` after force-stop, even though dex cache is warm.** Expected on Android, but consider lazy-init for the audio `SoundManager` (created in `GameScreen` via `remember { SoundManager(context) }`); if it's loading sound files synchronously on first composition, that's 1–2s.

**🟢 P2 — All drawables are PNG (31 PNGs in `mipmap/drawable` directories).** Vector drawables would shrink the APK and render crisply at any density. Easy migration with `Image -> Vector Asset Studio`.

**🟢 P2 — Limited Compose accessibility.** Searches across `GameScreen.kt` (76 KB) found only ~9 `contentDescription` / `semantics` usages. Resource bar is good, but most action buttons, menu items, structure cards, and the hex map itself lack semantic labels. Without these, TalkBack users get a degraded experience and Play Store accessibility scans will flag them. Recommend a focused pass to add `Modifier.semantics { contentDescription = "..." }` to every clickable Composable.

**🟢 P2 — Font scaling pass.** Setting `font_scale 1.3` on the emulator did not visibly break the difficulty screen, but the bottom action bar (5 buttons across) is already tight at scale 1.0. At scale 1.5 or 2.0 it will overflow. Test and add `Modifier.weight(...)` / shrinking font sizes for accessibility-large fonts.

**🟢 P2 — Bottom dialog is reachable.** The End Turn dialog is centered at y=1376 on a 2400 px screen — easily thumb-reachable, including on phones with one-handed reach. No issue, just confirming.

---

## 6. Play Store launch readiness

### 6.1 P0 — must fix before submission

| # | Issue | Evidence | Fix |
|---|---|---|---|
| **P0-1** | **Real keystore committed in repo root** (`Q:\Repos\SubTerrania\keystore`, 2,692 bytes). | File exists. | (a) Rotate the upload key (generate new keystore). (b) Remove from git history (`git filter-repo` or BFG), force-push, ask collaborators to re-clone. (c) Move new keystore outside repo. (d) Add `keystore` to `.gitignore`. (e) Document the new path in the README under "Building a release". |
| **P0-2** | **Release `signingConfig` is commented out** (`app/build.gradle.kts:38–46`). Release builds will be unsigned by default — `bundleRelease` will fail or produce an unsignable bundle. | `build.gradle.kts` review. | Re-enable, parameterize via `~/.gradle/gradle.properties` (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`) or environment variables. Verify with `./gradlew bundleRelease`. |
| **P0-3** | **`targetSdk = 34`** but Google Play has required API 35+ for new app submissions since 2025-08-31 and for updates since 2025-11. | `build.gradle.kts:11-17`, `pm dump` output. | Bump `compileSdk` and `targetSdk` to 35 (Android 15). Test for [behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15) — the relevant ones are foreground service types, edge-to-edge enforcement, and `OnBackInvokedCallback`. |
| **P0-4** | **Compose BOM is 2023.08.00** (`gradle/libs.versions.toml:6`) — released ~2 years ago. Likely contains known security & stability fixes that have shipped since. | `libs.versions.toml`. | Bump to the most recent stable BOM (2025-09 line at minimum). Run a full QA pass, especially on the modal dialogs and the hex map's `LazyRow`/`LazyColumn` usage. |
| **P0-5** | **Active `GameState` is not persisted across process death** (only meta-progression is). | Verified live: force-stop → game lost. | Serialize `GameState` to DataStore (or `application_support/save.json`) on every `endTurn`. On `MainActivity` `onCreate`, attempt to load and offer "Resume" on the difficulty screen. |
| **P0-6** | **No content rating**. The IARC questionnaire has not been answered (assumption — please verify in Play Console). | n/a | Complete IARC. SubTerrania has no real-world violence/sex/drugs/gambling — should rate Everyone (PEGI 3 / ESRB E). |
| **P0-7** | **Play Console "Data Safety" form readiness**. Privacy policy says "no data collection," which is great, but the Data Safety form must explicitly check "No data collected" for every category, otherwise Play will reject. | `docs/privacy-policy.html`. | Walk the form before submission, declaring zero collection. Confirm telemetry never leaves the device — already true per code review (`GameTelemetry.kt` only writes Logcat). |

### 6.2 P1 — strongly recommended

| # | Issue | Fix |
|---|---|---|
| **P1-1** | `allowBackup="true"` + present `dataExtractionRules` allow `adb backup` to extract the SharedPreferences (`subterranea_meta`). Currently low-stakes (just achievement progress), but if you ever store auth tokens or purchase history, this is a leak vector. | Either set `allowBackup="false"` or audit `backup_rules.xml` to exclude any future sensitive prefs. |
| **P1-2** | No crash reporting. If a user hits an `OutOfMemoryError` or a `NullPointerException` post-launch, you'll never know. | Add Firebase Crashlytics with privacy-friendly opt-in (default opt-out so the privacy policy stays accurate), OR integrate Sentry, OR rely on Play Console crash reports only (acceptable but slower feedback loop). |
| **P1-3** | ProGuard rules are over-broad: `-keep class androidx.compose.** { *; }` defeats much of R8's tree-shaking. APK is 26.87 MB debug — release with effective shrinking should be under 15 MB. | Remove the `-keep class androidx.compose.**` rule. The Compose compiler plugin already adds the necessary keep rules through `consumerProguardFiles`. Test a release build to verify no missing classes. |
| **P1-4** | All drawables are PNGs (31 in xxhdpi). | Migrate icons to vector drawables. Will shrink APK and improve density support. |
| **P1-5** | Cold start 5+ seconds (debug). | Add `androidx.profileinstaller` + a baseline profile. Run a Macrobenchmark from Android Studio if you can. |
| **P1-6** | Limited Compose accessibility. Only ~9 `contentDescription` / `semantics` matches in 76 KB of `GameScreen.kt`. | Audit every `Modifier.clickable` and add a semantic label. Especially: action buttons (Roll/Build/Clear/Trade/End), structure cards in build menu, hex tiles. |
| **P1-7** | The "Builder/Optimizer" playstyle wins 46% — see §3.2. | See §3.2 recommendations. |
| **P1-8** | Easy is too easy (96% win, 4% close finishes) — see §3.2. | See §3.2 recommendations. |
| **P1-9** | Players forget abilities (21% usage). | Add an Ability button to the bottom action bar; surface a "Try your ability!" tutorial hint after Turn 5 if unused. |
| **P1-10** | Trade collapses on Hard/Nightmare. | See §2.2 recommendations. |
| **P1-11** | Not yet verified: store listing assets (1024×500 feature graphic, 2–8 phone screenshots, optional 7"/10" tablet screenshots, 512×512 hi-res icon, short description ≤ 80 chars, full description ≤ 4,000 chars). | Audit `docs/` and Play Console. |

### 6.3 P2 — post-launch backlog

| # | Issue | Fix |
|---|---|---|
| **P2-1** | `Player.addResource` and `removeResources` lack negative-result clamps (`Player.kt:22-34`). No current bug because all callers pass positive ints, but defensive gap. | Add `coerceAtLeast(0)` on the result. |
| **P2-2** | `BoardGenerator` uses static mutable singleton state (`shuffledTerrainMap`/`shuffledNumberMap`). Works for single-player but fragile if you ever add tests or multi-instance scenarios. | Move to instance state on `BoardGenerator` if it becomes a class, or pass through the `GameState`. |
| **P2-3** | `state.structures` and `Player.structuresBuilt` track structures redundantly. The Excavator-replaces-Outpost path leaves the Outpost in `Player.structuresBuilt` but removes it from `state.structures`. VP arithmetic happens to work (3 VP for Outpost+Excavator), but a future refactor could drift them. | Consolidate to one source of truth, derive the other if needed. |
| **P2-4** | `Player.victoryPoints` field appears to be a "bonus VP buffer" added to `calculateVictoryPoints()` in `state.totalVPFor()`, but it's only set in one path (`AncientCache OPEN → +1 VP`). Could either be folded into the achievement system or documented inline as the "interactive event reward channel." | Add a Kotlin doc comment on the field. |
| **P2-5** | Achievement `First Explorer` uses `anyoneHasIt` flag — looks like multiplayer-ready code in a single-player game. | Either delete the multiplayer scaffolding or mark it `// TODO: multiplayer` for clarity. |
| **P2-6** | `Surface` zone's terrain pool excludes `CRYSTAL_GROTTO` and double-includes `LICHEN_FIELD` (intentional per code comment). | Confirmed by `Crystal Baron 9%` — fine, but worth re-evaluating if you raise late-game pacing. |
| **P2-7** | Compose BOM update — see P0-4. After bumping, opportunistically migrate to Material3 components you're not yet using (NavigationBar, Snackbar with action). | Post-update sweep. |
| **P2-8** | The action bar has 5 buttons (Roll/Build/Clear/Trade/End). On a 360 dp narrow phone with `font_scale 1.5`, this will overflow. | Add `Modifier.weight(1f)` or use a `BottomAppBar` with `IconButton`s. |
| **P2-9** | `signingConfig` for `release` is commented; verify whether `buildTypes.release.shrinkResources` and `isMinifyEnabled` propagate correctly once you re-enable signing. | Manual verify on a `bundleRelease`. |
| **P2-10** | Snowball Index 1.52 on Nightmare. | Rebalance Nightmare hazard cadence + comeback drops. |

---

## 7. Prioritized action plan

### 7.1 Before submitting to Play Store (P0)

1. Remove and rotate the `keystore` file (P0-1).
2. Re-enable release `signingConfig` (P0-2).
3. Bump `targetSdk` to 35 (P0-3).
4. Update Compose BOM (P0-4).
5. Persist active `GameState` (P0-5).
6. Complete IARC content rating questionnaire (P0-6).
7. Complete Data Safety form, declare zero collection (P0-7).

### 7.2 Strongly recommended pre-launch (P1)

1. Disable or audit `allowBackup` (P1-1).
2. Decide on crash reporting strategy (P1-2).
3. Tighten ProGuard rules + verify release shrinks below 15 MB APK (P1-3).
4. Migrate primary icons to vector drawables (P1-4).
5. Add a baseline profile (P1-5).
6. Accessibility/semantics pass on all clickable Composables (P1-6).
7. Buff late-game structures (Excavator, Refinery, Anchor) so Builder profile crosses 65%+ win rate (P1-7).
8. Make Easy meaningfully easy-but-not-trivial — bump VP target or slow starting resources (P1-8).
9. Add Ability button to bottom bar; surface a hint after Turn 5 if unused (P1-9).
10. Trade-rate floor or bonus structure to keep Hard/Nightmare trade usage > 1.0/game (P1-10).
11. Audit Play Console listing assets (P1-11).

### 7.3 Post-launch backlog (P2)

See table in §6.3.

---

## Appendix A — Raw playtest data

Source: `app/build/test-results/testDebugUnitTest/TEST-com.atlyn.subterranea.AutoPlaytest.xml`, `TEST-com.atlyn.subterranea.FunFactorPlaytest.xml`. Trimmed summaries archived at `<session>/files/playtest/`.

**1. Core balance:**
```
  Easy       Win:24/25 (96%)  AvgTurns:13.0  AvgVP:12.2/12  DeadRoll:40.4%
  Normal     Win:22/25 (88%)  AvgTurns:14.0  AvgVP:14.1/14  DeadRoll:45.6%
  Hard       Win:20/25 (80%)  AvgTurns:17.4  AvgVP:18.8/19  DeadRoll:33.5%
  Nightmare  Win:7/25  (28%)  AvgTurns:25.0  AvgVP:15.3/18  DeadRoll:38.8%
```

**2. Engagement (selected):**
```
  VP momentum (target 0.8–1.5): Easy 1.02  Normal 1.15  Hard 1.18  Nightmare 0.67
  Aha moments/game:             Easy 3.3   Normal 4.1   Hard 5.2   Nightmare 4.4
  Decision richness % turns:    Easy 64.8  Normal 59.7  Hard 65.0  Nightmare 50.4
  Multi-tile rolls % rolls:     Easy 21.7  Normal 27.3  Hard 30.8  Nightmare 27.4
```

**3. Variety:**
```
  Structure entropy:  Easy 0.90 (3.0)  Normal 1.10 (3.4)  Hard 1.49 (5.0)  Nightmare 1.12 (3.6)
  Structure type frequency (% of games / avg built):
    Lantern Post     100% / 3.8
    Mining Outpost    71% / 1.5
    Fungal Farm       57% / 1.3
    Crystal Refinery  58% / 0.9
    Beetle Stable     55% / 1.1
    Deep Excavator    23% / 0.3
    Core Anchor       12% / 0.1
```

---

## Appendix D: Implementation Status (Post-Report)

After publishing this report, the following items were implemented in three follow-up commits on `master`:

### Phase A — `914a727` (security, balance, retention P0/P1 fixes)
- **P0-1 Keystore untracked**: `keystore` removed from git index, `.gitignore` updated. New `SECURITY.md` documents the manual key-rotation procedure (filter-repo + force-push + Play Console upload-key reset) — this remains a human follow-up because the secret is still present in historical commits.
- **P0-3 Signing config restored**: `app/build.gradle.kts` now reads `KEYSTORE_PASSWORD` / `KEY_PASSWORD` from environment variables; `signingConfigs.release` re-enabled.
- **P1-1 allowBackup audit**: `AndroidManifest.xml` now sets `android:allowBackup="false"` to prevent unintended user-data exfiltration via adb backup.
- **P1-2 Difficulty rebalance**: `Difficulty.kt` updated — Easy CRYSTAL starting resource set to 0; VP curve now 13 / 15 / 19 / 18 (Easy/Normal/Hard/Nightmare); trade ratios 2 / 3 / 3 / 4. `Player.kt` resource clamps added; `EXCAVATOR` cost, `CORE_ANCHOR` VP, and `MASTER_BUILDER` reward retuned.
- **P1-3 Master Builder check**: `StructureEngine.kt` now requires distinct structure types for the achievement instead of total count.
- **Empirical result** (100 games × 4 difficulties × Aggressive AI): Easy 88%, Normal 76%, Hard 72%, Nightmare 24% — smooth curve.

### Phase B — `755e686` (build & dependency upgrades)
- **P0-2 Stale Compose BOM**: `composeBom 2023.08.00 → 2024.12.01`; `coreKtx 1.12.0 → 1.15.0`; `lifecycleRuntimeKtx 2.7.0 → 2.8.7`; `activityCompose 1.8.2 → 1.9.3`; `junitVersion 1.1.5 → 1.2.1`; `espressoCore 3.5.1 → 3.6.1`.
- **P0-4 targetSdk bump**: `compileSdk` and `targetSdk` 34 → 35 (Play Store policy from August 2025); `platforms;android-35` and `build-tools;35.0.0` added to local SDK.
- **P1-12 ProGuard tightening**: removed broad `-keep class androidx.compose.**` and `-keep class androidx.lifecycle.**` rules. Kept Kotlin metadata, coroutines, model data classes, ViewModels, and `Log.*` strip rules.
- **Build verification**: `:app:testDebugUnitTest` BUILD SUCCESSFUL; `:app:minifyReleaseWithR8` BUILD SUCCESSFUL (`assembleRelease` only fails at packaging when `KEYSTORE_PASSWORD` is unset, which is expected/environmental).
- **Known cosmetic warnings** (deferred): `Divider → HorizontalDivider` deprecation, `Window.statusBarColor` / `navigationBarColor` deprecated on API 35 — both compile and run, accepted for launch.

### Phase C — `b4de404` (P0-5 persistence + P1-9 ability button + initial a11y)
- **P0-5 GameState persistence** (launch blocker confirmed via earlier force-stop test): new `domain/persistence/GameStatePersistence.kt` uses `org.json` to serialize the full `GameState` (board, players, structures, resources, turn metadata, achievements, event log, dice/production history) to a SharedPreferences entry. Schema versioned (`v=1`); volatile sealed-class fields (`lastExplorationEvent`, `pendingInteractiveEvent`, `pendingEventCoord`) intentionally excluded as modal UI overlays the user re-triggers. `deserialize` returns `null` on parse failure or version mismatch (treated as no-saved-game). `GameViewModel` saves on every successful `endTurn()`, in `onCleared()`, at game start, and on `MainActivity.onPause()`. Save cleared on game-over, reset, discard, or new-game start. **Difficulty selection screen** now shows green Resume button + Discard CTA when a saved game exists.
- **P1-9 Ability button**: new conditional Ability action-bar button (yellow, with count badge `Ability·N` when N > 1), only rendered when usable abilities exist and `turnPhase == MAIN_ACTION`. New `AbilityMenu` Composable lists usable abilities with content descriptions. Previously, abilities required tapping the structure tile and were used in only 21/100 simulated games.
- **P1-6 Accessibility (initial pass)**: `ActionButton` now accepts a `contentDescription` parameter and applies `Modifier.semantics`. All six action-bar buttons (Roll / Explore / Build / Clear / Trade / Ability / End) carry descriptive labels with disabled-state suffix. Resume / Discard / Ability menu items also have semantics. Full a11y audit (structure cards in `BuildMenu`, hex tiles in `HexMap.kt`, dice display, resource readout, modal dialogs) is deferred to a post-launch polish pass.
- **Test infrastructure**: `org.json:json:20240303` added as `testImplementation` so JVM unit tests can exercise the JSONObject path. Five new tests in `GameStatePersistenceTest` cover round-trip for fresh and mid-game states, invalid JSON, schema-version rejection, and parseable output.
- **Verification**: `:app:testDebugUnitTest` BUILD SUCCESSFUL (existing `AutoPlaytest` + `FunFactorPlaytest` still pass + 5 new persistence tests pass); `:app:minifyReleaseWithR8` BUILD SUCCESSFUL.

### Items deferred to human follow-up
- **Key rotation** (security-critical): the keystore is removed from `HEAD` but remains in historical commits. Run `git filter-repo --path keystore --invert-paths`, force-push, and reset the Play Console upload key. See `SECURITY.md`.
- **P0-6 IARC questionnaire**: requires a human to complete the in-console form.
- **P0-7 Data Safety form**: requires human declaration in Play Console.
- **P1-7 Crashlytics**: integration decision deferred; recommended before public launch.
- **P1-4 Vector drawables**: PNG → SVG conversion deferred to post-launch.
- **P1-10 Edge-to-edge migration** (API 35): `Window.statusBarColor` / `navigationBarColor` deprecation cleanup.
- **Play Console listing assets**: icon densities, feature graphic, screenshots, description copy.
- **Full a11y audit on emulator**: TalkBack walkthrough, large-font scaling, color-only-info checks.
- **Open balance items**: First Explorer 99% attainment (still too common); Nightmare snowball index 1.61. Re-run `FunFactorPlaytest` after the Ability button surfaces abilities — the 21/100 ability-usage stat should improve materially.

### Build status as of last commit
| Task | Result |
|---|---|
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:app:minifyReleaseWithR8` | BUILD SUCCESSFUL |
| `:app:assembleRelease` | minify path passes; packaging requires `KEYSTORE_PASSWORD` env var |

  Action types used:  Easy 3.2  Normal 2.6  Hard 2.6  Nightmare 2.2 (of 5 available)
  Trade usage:        Easy 2.6  Normal 0.4  Hard 0.2  Nightmare 0.2 trades/game
  Achievements:       First Explorer 99%, Master Builder 92%, Deep Delver 81%,
                      Core Seeker 80%, The Illuminator 80%, Crystal Baron 9%
```

**4. Pacing:**
```
  Snowball index:     Easy 1.16  Normal 1.44  Hard 1.26  Nightmare 1.52
  Comeback potential: Easy 0.15  Normal 0.27  Hard 0.19  Nightmare 0.39
  Close finishes:     Easy 4%    Normal 28%   Hard 44%   Nightmare 36%
  Resource efficiency Easy 130%  Normal 110%  Hard 112%  Nightmare 89%
```

**5. AI profile comparison:**
```
  Cautious Newbie     Win:19/24 (79%)
  Balanced Player     Win:20/28 (71%)
  Aggressive Explorer Win:23/24 (96%)
  Builder/Optimizer   Win:11/24 (46%)
```

**6. Hazards:**
```
  GasLeak    102 / 100 games
  CaveIn      78 / 100 games
  Tremor      53 / 100 games
  MagmaBurst  35 / 100 games
```

**7. Fun-factor guardrails (FunFactorPlaytest):** 23 / 24 PASS.
- **Fail:** Normal early engagement ≥ 1.5 (actual 1.4).

---

## Appendix B — Smoke test observations

Build under test: `app-debug.apk` (26.87 MB, versionCode 2, versionName 1.0.1, targetSdk 34, minSdk 24).

Emulator: Pixel 6 AVD, API 34, 1080×2400 @ 420 dpi, software-rendered (SwiftShader; no hardware acceleration available on the host).

| Step | Result |
|---|---|
| Cold start (1st launch) | 5132 ms — `am start -W` `TotalTime` |
| Cold start (post force-stop) | 4402 ms |
| Logcat at launch | `Choreographer: Skipped 138 frames!` (~5 s mark), then 49 / 65 / 40 frames during composition + first input. No FATAL/ANR/StrictMode violations from app code. |
| Memory at Turn 1 | TOTAL PSS 89 MB, Native Heap 39 MB, Java Heap 13 MB. Reasonable for a debug build. |
| `gfxinfo` 4399 frames | 50p 89 ms, 90p 109 ms, 95p 121 ms, 99p 150 ms. 94.57% janky. (Note: SwiftShader makes absolute numbers misleading; the **shape** — work concentrated on UI thread + draw command issuance — points at heavy Compose composition rather than GPU.) |
| Difficulty selection screen | Reads cleanly. All 4 difficulty cards visible with VP target, action count, trade rate. Has `text` labels but **no `content-desc` on cards themselves** (only resource icons later). |
| Tap "Hard" | Goes directly into Turn 1, ROLL DICE phase, character pre-selected as "The Explorer". No character-selection step appeared on this path. Worth confirming whether character selection is meant to gate by tier. |
| Roll on Turn 1 | Rolled 6+6=12. "Nothing produced — scavenged +1 Iron Ore, +1 Luminary Crystal" — generous fallback. UI updates correctly. |
| Continue to Turn 4 (Hard) | "Lucky 7! Choose your consolation..." dialog with three options (Scavenge / Hustle / Barter) appeared as expected. |
| Rotation (portrait → landscape) | State preserved. Resource counts, turn number, consolation dialog all carried over. |
| Force-stop + relaunch | **Game lost. Returned to difficulty selection. No "Resume" option.** |
| Font scale 1.3 | No layout breakage observed on difficulty screen. Did not test in-game at 1.3 due to time. |
| Rapid-restart bug repro (Feb 2026) | **Did not reproduce.** Tap Hard → force-stop → relaunch → tap Hard cleanly returned to difficulty screen and then to Turn 1. The modal-priority chain at `GameScreen.kt:121-133` appears to have fixed this. |
| End-turn confirmation dialog | Appears with "End turn now? You still have 2 actions left." Two clear buttons. Good UX. |

Screenshots & UI dumps in `<session>/files/smoke-test/`.

---

## Appendix C — Older improvement docs: status

The repository already contains `improvement_report.md`, `IMPROVEMENT_IDEAS.md`, `playtest_report_feb2026.md`, and a few `revalidation_*` reports. Spot-check of their recommendations against the current codebase:

| Older recommendation | Status |
|---|---|
| Difficulty selection screen | ✅ Shipped (`GameScreen.kt:1883`) |
| Multiple character classes with abilities | ✅ Shipped (`MetaProgression.kt`) |
| Map presets (Crystal Rich, Hazardous, Organic Rich, Standard) | ✅ Shipped (`MetaProgression.kt`, `EventEngine.kt:15-37`) |
| Interactive events (BeetleSwarm, UnstableGround, AncientCache, LostMinerEncounter) | ✅ Shipped (`EventEngine.kt`) |
| Lucky 7 / consolation choice | ✅ Shipped — observed live |
| Achievements with VP rewards | ✅ Shipped — empirically attaining |
| Tutorial hint surfaced on first turn | ✅ Shipped — observed live |
| End-turn confirmation when actions remain | ✅ Shipped — observed live |
| ProGuard log-stripping in release | ✅ Shipped (`proguard-rules.pro`) |
| Modal priority / Feb-2026 overlay bug | ✅ Fixed (`GameScreen.kt:121-133`) — could not reproduce |
| Persistent meta-progression | ✅ Shipped (`SharedPreferences subterranea_meta`) |
| **Persistent active game state** | ❌ Not shipped — see P0-5 |
| **Crash reporting** | ❌ Not shipped — see P1-2 |
| **Vector drawables** | ❌ Not shipped — see P1-4 |
| **Accessibility pass** | 🟡 Partial — resource icons have `content-desc`, but most clickables don't |
| **API 35 target** | ❌ Not shipped — see P0-3 |

---

## Document metadata

- Generated by: deep pre-launch audit
- Date: this run (see `git log` for commit timestamp)
- Build under test: `app-debug.apk` v1.0.1 (versionCode 2)
- Empirical data: `AutoPlaytest` (100 games × 4 profiles), `FunFactorPlaytest`, emulator smoke test
- No code changes made for this report

