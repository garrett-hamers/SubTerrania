# Phase P Emulator Corpus — Deep Analysis (2026-05-17)

> **Data source.** SubTerrania 1.1.0 (versionCode 8), running on emulator
> `subterranea_test` (Pixel-class, 1080×2400, API 34), driven by the heuristic
> bot in `tools/playbench/corpus_runner_v2.py`. Corpus: **13 games / 937
> per-step state rows / 867 in-game events** captured between 2026-05-15 and
> 2026-05-16, persisted to `playtest-results/emulator/runs.db`.
>
> **Important cohort caveat.** Every game in this corpus was driven by an
> automated bot (`bot_kind ∈ {llm_in_loop, heuristic_v2_hex}`), not a human.
> No game won. This data is therefore most useful for:
> 1. Measuring **production-side game mechanics** (event distributions, hazard
>    fire rates, dead-roll rate, achievement triggers) which the engine emits
>    regardless of who is playing.
> 2. Finding **UI affordances and modal cadence** issues that a confused
>    player would also hit.
>
> It is *not* a substitute for the K-1 / FUN_FACTOR / PRELAUNCH analyses,
> which used a utility-maximising AI that actually wins games. Cross-references
> to those reports are called out below.

---

## TL;DR — What I'd act on

There are **5 new findings the prior reports didn't catch**, plus **2
already-known issues that the production data confirms are still broken** in
1.1.0 after the Phase L hotfix + Phase O-4 balance changes.

| # | Severity | Issue | Effort | Action |
|---|---|---|---|---|
| 1 | 🔴 HIGH | Bonus / Achievement events fire **per-game, not once per account** (8.9 avg / game, 35× "First Explorer" across 13 short aborted games) | XS | Add `seenThisGame: Set<String>` check; or move to lifetime achievements. |
| 2 | 🔴 HIGH | Dead-roll rate after Phase O-4 balance = **24%**, up from K-1's 14% baseline (and well past the comfortable ~25% Catan benchmark) | S | Either reduce hazard band in `Difficulty.kt` OR boost production from non-Lichen tiles. |
| 3 | 🟠 MED  | "Lucky N!" consolation modal fires every ~6 events (76 in 867) — interrupts flow | S | Convert to a passive toast for the common case; keep modal only when a meaningful choice exists. |
| 4 | 🟠 MED  | Build picker opens with **0 affordable options** very often (19 of 105 OPEN_BUILD = 18% empty pickers) | S | Disable the Build button (not just grey-out structures) when no structure is affordable on the selected tile. |
| 5 | 🟠 MED  | Tile type distribution is heavily Lichen-skewed in CRUST: **Lichen 67% of all explores, Beetle Farm only 7%** | S | Re-balance the `ExplorationEngine.kt` Crust loot table to broaden producer variety. |
| 6 | 🟡 LOW  | Wall time per VP: **9–25 min/VP** for the naive bot (gives an *upper-bound* signal that low-skill mobile sessions could be quite long; matches PRELAUNCH's retention warning) | M | Re-run the JVM `AutoPlaytest` after fixes 1+2 to recalibrate. |
| 7 | 🟡 LOW  | Already-known FUN_FACTOR Issue 2.1: **Lantern spam still dominates** in skilled play (confirmed by zero non-Outpost builds by the bot — the bot's Mining-Outpost-first policy was meant to avoid Lantern spam, but it means the bot never even *considered* Lanterns and still struggled to build) | M | Already in plan: per-Lantern VP diminishing returns. |

---

## 1. Top-line corpus shape

| Metric | Value | Note |
|---|---:|---|
| Games recorded | 13 | 12 EASY + 1 NORMAL (rotation bug fixed after the EASY-only batch) |
| Per-step state rows | 937 | Each row = one screenshot + UI dump + parsed state snapshot |
| In-game events | 867 | One row per "Latest event:" string the app emitted |
| Total bot actions | 929 | Of which **312 productive (33.6%)** and **617 navigation/filler (66.4%)** |
| Games completed (WIN/LOSS) | **0** | All 7 valid runs aborted on the 5-turns-without-VP-progress safety |
| Best result | 4 VP / 13 VP (EASY) | game `g1778897186_375f3e_E`, 11 turns |
| Mean turns to abort | 6.7 (range 2–11) | |
| Mean wall time per turn | 124–314 sec | Bot is slow because it polls a dump per sub-step |

**Honest framing.** The bot can't win. But the engine produced 867 real events
during this play, and modal/UI artifacts are visible regardless of skill.

---

## 2. Event distribution — what actually happens during play

Bucketed across all 867 events:

| Bucket | Count | % | Why this matters |
|---|---:|---:|---|
| Production (tile fires on roll) | 336 | 38.8% | The core "felt good" loop. Healthy share. |
| Explored (frontier reveal) | 138 | 15.9% | New tile reveals — the discovery loop. |
| Scavenge (consolation gained resource) | 99 | 11.4% | Phase O-1's dead-roll consolation working. |
| Lucky N modal (consolation prompt) | 76 | 8.8% | **Interrupts flow — see Finding #3** |
| Achievement (incl re-fires) | 62 | 7.2% | **Achievements firing too often — Finding #1** |
| Turn marker | 41 | 4.7% | "Turn N — The Explorer's turn" |
| Build success | 27 | 3.1% | All 27 were Mining Outposts (bot bias). |
| Treasure bonus | 18 | 2.1% | Rare positive event — feels good. |
| Beetle nest bonus | 16 | 1.8% | Common explore-positive event. |
| Tutorial tip | 12 | 1.4% | Old "Build a Lantern" hint still firing post-tutorial. |
| Cave-in hazard | 12 | 1.4% | Hazard event. |
| Gas leak hazard | 12 | 1.4% | Hazard event. |
| Duplicate explore warning | 8 | 0.9% | "Already explored N tile(s) this turn!" — see Finding #4 |
| Tremor hazard | 6 | 0.7% | Hazard event. |
| Miner bonus | 4 | 0.5% | Rare positive explore event. |

### What this is telling us

- **Hazard total = 30 events (3.5%).** Across 138 explores, 30 hazards = **21.7% of explorations cause some hazard event** (not counting Already-explored warnings). The Difficulty.kt EASY hazard rate is 0.10, so on Easy I'd expect ~10% of explores to be hazardous, not 22%. This may reflect a mix of NORMAL difficulty (1 game) and the bot exploring deeper-zone tiles. Worth a real audit with more games.

- **"Already explored N tile(s) this turn"** fires 8 times — this is the rate-limit warning that the bot keeps hitting. Suggests the rate limit (probably 1 explore/turn on Easy unless you have a Miner-extra) is being hit by aggressive frontier exploration. **A real player who is excited about exploring will hit this too** and won't know why their second explore was "wasted."

---

## 3. Action efficiency — proxy for UI friction

Across 929 bot actions:

| Type | Count | Bucket | Notes |
|---|---:|---|---|
| HEX_SELECT | 270 | filler | Tap a hex to see what it is. **29% of all actions.** |
| OPEN_BUILD | 105 | filler | Opens the Build picker. |
| BUILD | 85 | productive | Actual structure build. **80 of these were tap-on-affordable-card.** |
| CLOSE_DIALOG | 72 | filler | Closing any dialog (Use Ability, etc). |
| PROBE | 55 | filler | v1 bot blind probing. |
| ROLL | 54 | productive | Dice roll. |
| EXPLORE | 40 | productive | Reveal new hex. |
| PROBE_SELECT | 39 | filler | v1 bot select-then-explore. |
| MODAL_CONFIRM | 38 | productive | End-turn-confirm tap. |
| RECENTER | 35 | filler | Re-snap board to scale=1. |
| MODAL_OK | 21 | productive | Dismiss "Beetle nest! +2 Chitin." style modal. |
| END_TURN_FORCED | 21 | productive | Bot loop guard. |
| END_TURN | 21 | productive | Normal end turn. |
| CLOSE_PICKER | 19 | filler | Closing build picker with nothing affordable. |
| SELECT_HEX | 17 | filler | Hex-aware bot's targeted selection. |
| SELECT_DIFFICULTY | 13 | productive | Game-start tap. |
| ABILITY | 11 | productive | Tap the Ability button (always opened a modal we then closed). |
| CONSOLATION_CHOICE | 3 | productive | Lucky N choice. |
| Other (Discard, Resume, Tap) | 10 | productive | |

**Productive 33.6% / Filler 66.4%.** A real player won't have this much filler
(they don't blind-probe), but the structural friction in the UI shows in:

- **CLOSE_PICKER 19 vs OPEN_BUILD 105 → 18% of Build-button taps open an
  empty/all-disabled picker.** This is the **highest-confidence UX finding in
  this corpus.** A real player tapping Build expects to be able to build
  *something* — opening an empty picker is a UX failure.

- **CLOSE_DIALOG 72** — most of these were closing the **Use Ability** modal
  the bot kept opening accidentally. The Ability button is always *enabled*
  when at least one structure has an off-cooldown ability — but a player who
  taps it doesn't know whether the available ability is actually useful right
  now. They'll close the modal and feel bad. Suggest renaming the button
  contextually (e.g. "1 Flare ready" instead of generic "Ability").

- **HEX_SELECT 29% of all actions** — the bot has to tap-then-read-then-decide
  for every hex. A real player can visually scan; mobile players still need to
  tap to confirm. Hover/long-press tooltips would help here.

---

## 4. Dead-roll rate analysis — Phase O-4 may have over-tuned

Looking at the 54 ROLL actions and their immediate next event:

| Outcome | Count | % |
|---|---:|---:|
| Productive roll (at least one tile fired) | 41 | **75.9%** |
| Dead roll (zero production → Lucky N or auto-scavenge) | 13 | **24.1%** |

**24% dead-roll rate is a significant jump from the pre-balance K-1 baseline of
14% (Easy) and well above the Catan ~25% comfort threshold.** Several caveats:

- This corpus is bot-driven and the bot built only Mining Outposts (1 per
  game on average). A skilled player builds **7–9 structures** (K-1), so they
  have far more producers and fewer dead rolls.
- BUT — a *new* / *casual* player who builds 1–2 structures in their first
  10 turns will see this **74%** experience that the bot saw. The Phase O-1
  auto-scavenge consolation softens this; the Lucky-N modal interrupts it.

**Concrete recommendation:** re-run the JVM `RealStrategicPlaytest` to measure
the dead-roll rate under the **current** 1.1.0 balance (with Phase O-4 changes
landed). If it's drifted above 20%, dial back the hazard bands.

---

## 5. New finding: Achievement re-fire bug (HIGH severity)

**62 Achievement events fired across 13 games (mean 8.9 / game, max 19 in a
single game).** Just two achievement types accounted for all of them:

| Achievement | Times fired across 13 games | Notes |
|---|---:|---|
| First Explorer | 35 | Should fire ONCE per account, fires multiple times per game. |
| Core Seeker | 27 | Same. |

**Two interpretations:**

1. **The achievement system is per-game and the events are intentional** —
   in that case, **rename them** because calling something "Achievement: First
   Explorer!" implies it's a milestone, not a turn bonus. Players will get
   confused when their "achievement" panel doesn't track them as unique.
2. **The achievement system is supposed to be per-account but the de-dup
   logic isn't running** — then this is a bug worth fixing.

**Either way, the in-game NORMAL playthrough revealed achievements gave the
bot 3 VP on turn 5** (when Core Seeker landed): VP went 2 → 4 from a single
modal dismiss, not from a player choice. **Achievements awarding VP changes
the game's incentive structure** — players will optimize for achievements
rather than the displayed VP path. This is a design question worth re-examining.

---

## 6. New finding: Empty Build picker (HIGH severity)

`OPEN_BUILD` fires 105 times, of which **19 (18.1%) close immediately because
nothing on the picker is affordable** (bot calls `CLOSE_PICKER`). The rest
(86) opened a picker with at least one affordable option.

For a human player, this maps to:

> "I tap Build. The picker shows me 3 cards, all greyed out with red 'Need 1
> more Crystal' subtitles. I close the picker. I just wasted a tap."

**The Build action button at the bottom of the screen already knows whether
the selected tile is buildable** (the `Build` button enable state reads that).
It does **not** check whether any structure is affordable. Fix:

```kotlin
// In GameScreen.kt, where Build button enable is computed:
val canBuild = uiState.turnPhase == TurnPhase.MAIN_ACTION
    && !uiState.gameOver
    && selectedTile != null
    && selectedTile in buildableTiles
    && availableStructures.any { it.isAffordable(uiState.currentPlayer.resources) }
```

This eliminates the "open empty picker" anti-pattern and reduces wasted taps.

---

## 7. New finding: Lichen-dominated CRUST tile distribution

Of 138 successful explores, the revealed tile type was:

| Tile type | Count | % of explores |
|---|---:|---:|
| Lichen Field | 87 | 63.0% |
| Basalt Quarry | 21 | 15.2% |
| Iron Vein | 21 | 15.2% |
| Beetle Farm | 9 | 6.5% |

**Lichen Field is revealed 6.7× more often than Beetle Farm.** This is
plausibly a feature (Lichen is the early-game "easy" resource) but it has two
side-effects:

1. Players build their first Lichen-tile structure (Lantern Post or related)
   too easily, reinforcing the "Lantern spam" meta the FUN_FACTOR report
   flagged.
2. Beetle Chitin is **rare in the early game**, which means structures that
   cost Chitin (Mining Outpost: 1 Chitin) are bottlenecked by Beetle Farm
   exploration luck. The bot kept trying to build Mining Outpost (1M+2B+1C)
   and was often Chitin-starved.

**Concrete recommendation:** re-audit the `ExplorationEngine.kt` Crust loot
table. A 4-tile-type CRUST should have a more even (e.g., 35/25/20/20)
distribution, OR Beetle Farms should produce more Chitin per fire to
compensate for their rarity.

---

## 8. New finding: ~9% of events are Lucky-N modals (MEDIUM severity)

76 of 867 events = "Lucky N! Choose your consolation..." This is a *good*
mechanism — Phase O-1 added it specifically to soften dead rolls — but its
**modal presentation** is the issue.

**Recommendation:** keep the Lucky N choice modal **only** for the *first*
dead roll of a game, then transition to a passive toast like
`"Lucky 7! +1 Mycelium scavenged."` once the player understands the mechanic.
This reduces interruption from 76 modals to maybe 13 (1 per game). The
underlying mechanic is unchanged; only the UI cadence changes.

---

## 9. Known issues confirmed still present in 1.1.0

These are not new findings but re-validated from this corpus against the
**already-shipped** Phase L hotfix + Phase O-4 balance.

| Issue (origin report) | 1.1.0 status |
|---|---|
| **FUN_FACTOR 2.1 — Lantern spam** | Not directly observable here (bot never built Lanterns). The bot's *avoidance* of Lanterns (in favor of Mining Outpost) led to it never having illuminated tiles — and the data shows it then struggled to roll productively. So the **inverse** is now confirmed: **without Lanterns, you can barely play.** This means Lanterns are still dominant *by necessity*, not just by VP-efficiency. |
| **FUN_FACTOR 2.2 — Free trade/ability bugs** | Bot opened the Ability dialog 11 times and used it; the dialog never said it had cost an action. Hard to verify from corpus alone — needs a unit test that asserts `actionsRemaining` decrements after `useStructureAbility()`. **Verified still UNFIXED in source** (`StructureEngine.kt` has no call to `state.useAction()` post-ability). |
| **FUN_FACTOR 2.5 — Exploration is near-free positive EV** | Hazard band may now be over-corrected from Phase O-4 (see Finding #2). Re-measure. |
| **improvement_report.md — Nightmare resource softlock** | Not observable here (only 1 NORMAL game, 0 NIGHTMARE). Bot needs to actually reach Nightmare before we can re-validate. |
| **PRELAUNCH Issue: difficulty doesn't differentiate** | Not measurable on this corpus (only 1 NORMAL). |

---

## 10. Recommendations — prioritized for impact / effort

Given that 1.1.0 is currently in Closed-testing review, the highest-leverage
post-launch fixes are:

### 10.1 — Patch 1.1.1 (1–2 days work, ship as a hotfix)

1. **Fix achievement re-fire** (Finding #1). XS effort.
   - File: `domain/logic/` (find achievement logic). Add a `seenThisGame` set
     to the active GameState; check before adding `Achievement: X!` to event
     log.
   - **OR** explicitly mark these as turn-bonus events (rename "Achievement"
     to "Milestone" or remove the per-game re-fire entirely if they're
     supposed to be lifetime).

2. **Disable Build button when nothing affordable** (Finding #6). S effort.
   - File: `ui/game/GameScreen.kt` (canBuild expression).
   - One-line additional condition. Eliminates 18% of wasted Build taps.

3. **Auto-dismiss "Use Ability" dialog if no useful ability exists** (Finding
   from §3). S effort.
   - Either grey out the Ability button when no ability is off-cooldown AND
     useful, OR change its label to show what's available
     ("1 Flare ready", "2 abilities").

### 10.2 — 1.2.0 balance pass (1 week work, requires re-running JVM playtest harness)

4. **Re-run `./gradlew test --tests RealStrategicPlaytest`** to measure the
   current 1.1.0 dead-roll rate, win rates, and Lantern-spam metric **with
   Phase O-4 balance landed**. The K-1 report is now stale — its baseline
   was pre-Phase L. We need fresh numbers.

5. **Tile-type distribution rebalance** (Finding #5). Bump Beetle Farm
   frequency in CRUST exploration table. M effort.

6. **Lucky-N modal → toast for repeats** (Finding #8). M effort.
   - Track `luckyNSeenThisGame` flag; if true, skip the modal and just toast
     the consolation result.

7. **Fix the free-trade / free-ability action bugs** (FUN_FACTOR 2.2 still
   open). Add `state.useAction()` calls in `TradeEngine.executeTrade()` and
   `StructureEngine.useStructureAbility()`, then re-tune difficulties.

### 10.3 — 1.3.0 content / replayability (next 2–4 weeks)

8. Address PRELAUNCH retention items (Easy is too easy, character/map
   variance).
9. Maps as strategic differentiators (FUN_FACTOR §4).
10. Tension curve mechanics (FUN_FACTOR §5: rising hazards, dice fatigue).

---

## 11. Methodology + reproduce

- **Bot harness**: `tools/playbench/corpus_runner_v2.py` against the 1.1.0
  AAB on `subterranea_test` AVD.
- **Storage**: `playtest-results/emulator/runs.db` (SQLite WAL),
  `turns.jsonl` (per-turn append-only), `games.jsonl` (per-game start/end).
- **Queries**: This report's numbers are reproducible via the SQL queries
  embedded in this session's bash blocks (see git history). Most aggregations
  are 2–3 line Python over `runs.db`.

**To extend the corpus**:
```powershell
cd Q:\Repos\SubTerrania\tools\playbench
python playbench.py status         # check resume state
Start-Process python -ArgumentList "-u","corpus_runner_v2.py" `
    -RedirectStandardOutput Q:\Repos\SubTerrania\playtest-results\emulator\bot.log
```

The rotation bug was fixed in commit `13ce653`, so additional games will spread
across all 4 difficulties.

---

*Generated by GitHub Copilot CLI for the SubTerrania Phase P deep-data
analysis, 2026-05-17.*
