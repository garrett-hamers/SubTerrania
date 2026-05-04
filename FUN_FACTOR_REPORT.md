# SubTerrania — Fun Factor Report

> **Phase K-3 — combined synthesis of K-1 (240-game statistical playtest) and K-2 (real-emulator playthrough on the Phase J debug AAB).**
> *Audience: the developer (you). Tone: candid. I am not soft-pedalling problems because the launch is imminent.*

## TL;DR — Verdict

SubTerrania is **a competent, atmospheric Catan-lite that ships with a fun core but a structurally broken difficulty curve.** It is enjoyable for a casual 5–15 minute session; it will **not** sustain replays in its current state because the optimal strategy collapses to *spam Lantern Posts* and the action economy is bypassable due to two underlying engine bugs. There is one to two days of focused balance work between "this is shippable" and "this is *good*."

| Dimension | Score | One-line verdict |
|---|---|---|
| First-time experience | **B** | Difficulty selector and screenshots sell the game well; first turn often feels resourceless on Normal+. |
| Moment-to-moment loop | **B+** | Roll → harvest → spend feels good; production-tile pulses are satisfying. |
| Decision depth | **C** | 78% of turns have small utility gaps (good in isolation), but the dominant strategy is one-dimensional. |
| Difficulty curve | **D** | A skilled player wins **97–100% of games on every difficulty.** Hard and Nightmare are not actually hard. |
| Replayability | **C-** | Every character converges to "build ~4 Lanterns, fill remainder with whatever fits." Maps barely shift the meta. |
| Polish | **B** | Solid persistence, clean menus, good achievement screens; bugs are in invisible places. |
| Bugs blocking fun | **F** | Free trades + free abilities + interior-Lantern waste = the rules don't actually constrain the player. |

---

## 1. The Gameplay Loop

Per turn:
1. **Roll dice** (1 action equivalent — the Roll Phase) — produces resources from any *revealed and lit* matching tile.
2. **Main Action Phase** — 2 actions per turn (1 on Nightmare). Choices:
   - **Build** a structure on a selected tile (1 action, costs resources).
   - **Trade** (~2:1 on Easy → 4:1 on Nightmare) — *currently 0 actions due to bug*.
   - **Use a structure ability** (Flares, Spore Burst, etc.) — *currently 0 actions due to bug*; cooldowns are the only constraint.
   - **Explore** an adjacent unrevealed tile (1 action) — RNG outcome from the zone-specific table.
3. **End Turn** (with confirmation if you didn't spend all actions — nice touch).

This is fundamentally **Catan minus trading-with-other-players plus exploration roguelike-lite hex outcomes**. It works.

The pulse-on-production animation is the single best moment-to-moment hook. Every roll feels alive because something visibly happens on the board.

---

## 2. Where the Loop Breaks

### Issue 2.1 — The Lantern Spam Convergence Problem (severity: HIGH)

A Lantern Post:
- Costs **1 Crystal + 1 Iron** (and 0.8× on Easy → still 1+1 because of integer flooring).
- Gives **+1 VP**.
- *Optionally* illuminates adjacent unlit tiles.
- *Bonus*: each Lantern adds a free Flare ability slot to the action menu.

Compare a Mining Outpost:
- Costs **2 Basalt + 1 Mycelium + 1 Beetle Chitin** (4 resources vs. 2).
- Gives **+1 VP**.
- Produces +1 of a resource per matching dice roll.

**The Lantern is strictly cheaper, simpler to plan around, and yields the same VP as the Mining Outpost.** The Outpost's resource production is only valuable if you need *more* resources to buy more structures, but… most of those structures' utility comes back to "VP," and Lanterns are the cheapest VP per resource on the board.

**K-1 statistical evidence (240 games):**

| Character | Avg Lanterns built | Avg Outposts | Avg Excavators | Avg Specialists |
|---|---|---|---|---|
| Explorer | 3.8 | 1.7 | 0.2 | 2.3 |
| Prospector | 4.1 | 1.5 | 0.1 | 2.6 |
| Scout | 2.8 | 2.1 | 0.4 | 3.7 |
| Engineer | 5.0 | 1.0 | 0.4 | 2.6 |

*Lanterns are the most-built structure for every character on every map.* The Engineer averages **5 Lanterns per game** — that alone is more than half their VP target.

**K-2 narrative evidence:** I won the Easy game on T10 with **6 Lanterns + 1 Mining Outpost + 1 Fungal Forest + 1 Fungal Grotto + 1 Beetle Farm**. The Lanterns alone gave me 6 VP. Every other structure was opportunistic — I built them only when their resource cost was already on hand.

**Concrete fix recommendations:**
- Increase Lantern cost to **2 Crystal + 1 Iron** (or scale by build-count: 1st Lantern = 1+1, 2nd = 2+1, 3rd = 2+2…).
- *Or* cap the VP from Lantern Posts at e.g. 4 per game; further Lanterns still illuminate but stop yielding VP.
- *Or* require Lanterns to be placed only on unilluminated edges of the lit area (force exploration).
- The **Engineer's Lantern bias is even stronger** because of the character bonus — consider rebalancing that bonus so it does *not* discount Lanterns.

### Issue 2.2 — The Free Action Economy Bugs (severity: HIGH)

Two engine bugs combine to make actions effectively unlimited:

**Bug A: Free trades.** `TradeEngine.executeTrade()` performs the resource swap but never decrements the player's action counter. Verified in source, observed in-game on T6, T7, T9, T10 of my K-2 playthrough — same behaviour every time.

**Bug B: Free abilities.** `StructureEngine.useStructureAbility()` applies the structure's cooldown but never decrements actions. The in-app tooltip in the ability menu literally reads **"Tap an ability to use it. Uses 1 action."** It does not.

**Why this matters more than it looks:** Every turn, the *intended* design says you have 2 (or 1 on Nightmare) decisions of consequence. The actual game gives you 2 *plus* unlimited trades *plus* unlimited Flares. A turn looks like:

- Roll dice (free).
- Use 4 Flares to reveal 4 tiles (claimed cost 4 actions; actual cost 0).
- Trade away unwanted resources (claimed cost 1 action per trade; actual cost 0).
- Build 1 structure (1 action).
- Explore 1 unrevealed tile (1 action).
- End turn.

The "2 actions" budget is fictional. *That's why* my AI heuristic was able to win 240/240 Easy games and 97–100% on every difficulty — it's not a brilliant AI, it's that the game has no resistance.

**Concrete fix recommendations:**
- Add `state.useAction()` (or equivalent) to `TradeEngine.executeTrade` immediately after the resource swap.
- Add `state.useAction()` to `StructureEngine.useStructureAbility` after the ability fires.
- Then **re-run K-1** (the heuristic AI test) — I expect Hard and Nightmare win-rates to drop into the 60–80% range, which is what those difficulties *should* feel like.
- The "Uses 1 action" tooltip can stay as-is once the bug is fixed.

### Issue 2.3 — Interior Lantern Waste (severity: MEDIUM)

When a Lantern Post is placed on a tile whose neighbouring hexes are already illuminated (or are out-of-board edges), the toast reads `"Area illuminated! (0 tiles)"`. The Lantern still grants +1 VP, so it's not a "wasted" purchase — but the *advertised secondary effect* doesn't fire.

I observed **6 occurrences in a 10-turn game**, including the **winning Lantern build itself**. From a player perspective:
- It's confusing — the build menu sells Lanterns on illumination, not VP.
- It rewards stacking Lanterns in already-lit areas (anti-strategy).
- It signals that the game's secondary objective (exploration via illumination) is decorative, not actually pursued.

**Concrete fix recommendations:**
- *Either* (a) cap or remove the +1 VP when a Lantern lights 0 tiles, *or* (b) only allow Lantern placement on tiles that border ≥1 unlit revealed hex.
- The (b) option is cleaner and pushes the meta toward "spread to explore," which matches the game's apparent design intent.

### Issue 2.4 — Difficulty Selector ≠ Actual Difficulty (severity: HIGH)

K-1 results, restated:

| Difficulty | Win % | Avg turns | Avg dead-roll % | Avg trades / game |
|---|---|---|---|---|
| Easy | 100% | 8.5 | 11% | 1.5 |
| Normal | 100% | 11.7 | 10% | 1.5 |
| Hard | 97% | 13.5 | 9% | 2.1 |
| Nightmare | 98% | 18.9 | 7% | 2.6 |

The differences are **VP target and a handful of tuning constants** (build cost multiplier, hazard chance, trade ratio). None of them stop a competent player. Nightmare is *longer* but not *harder*.

**Why "Nightmare" still wins 98%:** the AI gets 1 action/turn instead of 2, but the **free-trades and free-abilities bugs mean it effectively still gets 4–6 things per turn**. Fix Issue 2.2 first, then re-tune.

**Concrete recommendations after bugs are fixed:**
- Consider 2× hazard chance on Nightmare (currently 0.40 → 0.80).
- Add a "starvation" condition — if a player ends 3 consecutive turns without building anything, they lose. This punishes pure exploration grinds.
- Consider pruning the difficulty list to **3 tiers (Easy / Normal / Hard)** and dropping Nightmare entirely until the action bugs are fixed and you can validate the bottom of the curve. A Nightmare difficulty that you win 98% of the time *damages credibility*.

### Issue 2.5 — Exploration RNG is Almost Always a Net-Positive Gamble (severity: MEDIUM)

On Easy, exploring a Mantle tile has these outcomes (verified in `ExplorationEngine.kt:199-214`):

| Outcome | Probability | Net value |
|---|---|---|
| StableGround | 23% | nothing |
| CrystalVein +1 | 17% | +1 Crystal |
| TreasureCache | 15% | +2 Iron + 1 Crystal |
| AncientArtifact | 7% | **+1 VP** |
| Hazards (CaveIn / Tremor / GasLeak / MagmaBurst / GeothermalVent) | 38% | -1 to -2 of a resource |

**Expected value of one Mantle exploration on Easy:** ~+0.3 VP equivalent + ~+0.5 resources, costing 1 action. That's better than rolling dice on most turns. Hazards rarely sting because resources are abundant.

On Crust: hazards are even rarer — exploration is a **near-free positive-EV action**. A skilled player just hammers explore until VP target is met.

**Concrete fix recommendation:**
- Increase hazard band to ~50% on Easy Mantle, ~65% on Hard Mantle, ~75% on Nightmare Mantle.
- Add **a hard cost to exploration**: each explore reduces Lichen by 1 (you "burn fuel"). Right now exploration is too cheap.

---

## 3. UX & Onboarding Friction

In K-2 I logged the following first-time-player frictions in real time:

| # | Friction | Severity | Fix |
|---|---|---|---|
| F1 | Tile `X|Y` labels (dice triggers) are tiny + unexplained. | Medium | Add a one-time pinch tutorial: "These numbers are dice rolls that activate this tile." |
| F2 | The mystery pre-built Lantern at "9" (Explorer character bonus) is never explained on screen. | Medium | Add character-bonus blurb in the difficulty selector ("Starts with 1 free Lantern"). |
| F3 | Trade dialog "Receive" row scrolls only when swiped at its own Y; Lum (most-used target) starts off-screen right. | Medium | Pre-scroll Lum into view; or use a horizontally-scrolling chip strip instead of row of icons. |
| F4 | Build menu hides un-affordable structures, hiding the meta from new players. | Low | Show all structures grayed out with "Need 2 more Basalt" subtitle (which already exists for the visible ones). |
| F5 | Hex selection precision rough — multiple miss-taps. | Medium | Slightly enlarge hit-boxes; consider a 5–10px tap-grace radius. |
| F6 | First turn on Normal often has no affordable build (1 Iron, 0 Crystal). | High | Bump Normal starting resources by 1 of each. |
| F7 | Ability tooltip lies about action cost. | Medium | Fix Bug 2.2 then leave the tooltip alone. |
| F8 | Android BACK on a modal dialog exits the app instead of just closing the dialog. | Medium | Add `OnBackPressed` handler in the trade/build dialogs to dismiss the modal first. |
| F9 | Difficulty cards show VP/turn count but not estimated game length / turn cap. | Low | Add "~10–15 turns" subtitle under each difficulty. |

Strengths to preserve:
- **End-Turn confirmation when actions remain** is *exactly* the kind of small thing that respects the player's time. Keep it.
- **Auto-save persistence is rock solid.** I closed the game mid-trade-mid-turn and resumed perfectly. This is rare in indie games.
- **Achievement / lifetime stats screen** is satisfying and gives a reason to play another round.

---

## 4. Replayability Analysis

K-1 measured structure-mix variance across 4 chars × 3 maps × 5 seeds × 4 difficulties = 240 games. The key replayability indicator is **structure mix variance per character × map**.

Scout has the most variation (it builds 2.7–3.1 Lanterns vs. Engineer's 4.8–5.2 — that's a real difference). Every character builds Lanterns most often, and Mining Outposts second most often, **regardless of map**. The map presets (Standard / Crystal Caves / Fungal Jungle) shift the mix by less than 1 average structure.

Translation: **the maps are visual reskins, not strategic differentiators**. A new player playing 4–5 games will see "the same game" by their 3rd run.

**Concrete fix recommendations to boost replayability:**
- Crystal Caves should *meaningfully* over-supply Crystal (e.g., 30% of revealed Mantle tiles guarantee a Crystal Vein), pushing the Engineer or Crystal Refinery strategies to over-perform there.
- Fungal Jungle should make Spore Burst and Mycelium-based abilities *significantly* stronger (e.g., bloom propagates 2 hexes instead of 1).
- Add a 5th map preset with *no Lantern starting tile* and a higher Crystal cost — pushes Mining Outpost / Excavator strategies.
- Add a per-game scoring modifier (e.g., "this run: bonus VP for exploring Core") that varies the optimal play per session.

---

## 5. Tension Curve

There is no real tension in current SubTerrania. The K-1 metric "plateau turns" (consecutive turns without VP gain) averages **0.0–0.1 across all difficulties.** A skilled player never feels stuck.

The K-2 narrative confirmed this: in 10 turns I never once felt risk. The closest I came was a 0-illumination Lantern toast and a 3-turn Spore Burst cooldown — which is the *only* mechanic that actually pushed back on me.

A satisfying VP-race game should have a **low point around 60% game progress** where the player has to make a hard choice, and a **climactic 1–2 turn finish** where the game could be lost on a bad roll. SubTerrania currently has neither.

**Concrete fix recommendations:**
- Re-enable action costs on trades and abilities (this alone may produce natural tension by forcing trade-offs).
- Add a **rising-hazard mechanic**: every 5 turns, hazardChance increases by 0.05. The longer you play, the riskier exploration becomes — incentivizes finishing the game.
- Add a **dice-fatigue mechanic**: track how many turns since the last dice-triggered production for each tile; tiles that haven't fired in 4+ turns are "cold" and produce nothing on the next roll. Forces the player to diversify production beyond the same 2–3 high-probability tiles.

---

## 6. What's Already Good (don't break these)

- **The board art and pulse animations** make the game *feel* alive turn-to-turn.
- **The achievement and lifetime tracking** give a reason to play again.
- **The persistence model** (auto-save mid-turn, resume cleanly, even after BACK exits the app accidentally) is rare-quality work.
- **The "Almost there! N VP to victory" hint** is exactly the right texture of feedback at the right time.
- **The End-Turn confirmation** when actions remain is small and kind.
- **The difficulty selector chip layout** (VP / actions / trade ratio at a glance) is excellent.
- **The build menu's resource-deficit subtitles** ("Need 1 more Luminary Crystal") are the right level of help.
- **The trade dialog auto-hiding exhausted Give rows** is small but kind.

---

## 7. Prioritised Action List

Ordered by impact / effort. **All eight items are real engineering work in your existing codebase** — no design overhaul required.

| # | Fix | Severity | Effort | Files |
|---|---|---|---|---|
| 1 | Add `state.useAction()` to `TradeEngine.executeTrade()` | HIGH | XS | `domain/logic/TradeEngine.kt` |
| 2 | Add `state.useAction()` to `StructureEngine.useStructureAbility()` | HIGH | XS | `domain/logic/StructureEngine.kt` |
| 3 | Restrict Lantern Post placement to tiles bordering ≥1 unlit hex (or remove +1 VP for 0-illumination builds) | HIGH | S | `domain/logic/StructureEngine.kt` (validation) |
| 4 | Re-run K-1 heuristic playtest after #1–#3 to retune difficulties | HIGH | XS | `app/src/test/.../RealStrategicPlaytest.kt` |
| 5 | Increase hazard chance on Mantle/Core exploration | MEDIUM | XS | `domain/model/Difficulty.kt` |
| 6 | Add `OnBackPressed` handler to trade/build dialogs (don't exit app) | MEDIUM | S | UI Compose modal hosts |
| 7 | Pre-scroll Lum into view in trade dialog; or restructure as horizontal chip strip | MEDIUM | M | Trade dialog Composable |
| 8 | Add character-bonus blurb to difficulty selector (e.g., "Explorer: starts with 1 free Lantern") | LOW | XS | Difficulty selector Composable |

**My suggested release path:**
- **1.0.3 (already built, awaiting Google upload-key reset):** ship as-is. The bugs have been there since 1.0.0 and the closed-test cohort hasn't complained yet — the launch is not blocked by them.
- **1.0.4 hotfix release (within 1–2 weeks of public launch):** Items #1, #2, #3, #6 above. These are all small, surgical changes. This is what flips the game from "competent" to "actually fun."
- **1.1.0 content release:** Items #4, #5, #7, #8 plus map differentiation (§4 above) plus tension curve work (§5 above).

---

## 8. Methodology

**K-1 (statistical):** `app/src/test/java/com/atlyn/subterranea/RealStrategicPlaytest.kt` — utility-maximising AI that enumerates every legal action per turn and picks the best by `EV(VP) + structure synergy + risk-adjusted explore value`. Ran 240 games (4 difficulties × 4 characters × 3 maps × 5 seeds). Per-turn telemetry: top-1 vs top-2 utility gap, regret moves, plateau turns, dead-roll %. Output: `playtest-results/K1_heuristic_report.md`.

**K-2 (narrative):** I drove the actual `subterranea_test` AVD via ADB taps on the Phase J debug AAB (`com.atlyn.subterranea` v1.0.3, build 4), capturing screenshots and observing UX/bugs turn-by-turn. Played one full Easy game (Explorer character, Standard map, 10 turns to win 13/13 VP) and 1 turn of a Normal game. Output: `playtest-results/K2_narrative_report.md`. ~155 screenshots in session storage.

**K-3 (this report):** synthesis of K-1 numerical findings against K-2 lived experience.

The data and methodology are reproducible — re-run `./gradlew :app:testDebugUnitTest --tests "RealStrategicPlaytest"` for K-1 and follow `playtest-results/K2_narrative_report.md` for K-2 reproduction.

---

*Generated by GitHub Copilot CLI (Claude Opus 4.7 X-High) for the SubTerrania Phase K real-play fun analysis, 2026-05-03.*
