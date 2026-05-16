# Phase P Emulator Playbench

Autonomous bot harness that drives an Android emulator running SubTerrania, captures
per-turn UI state to SQLite + JSONL, and produces a queryable corpus of playthroughs.

## What this is

A black-box test harness — the bot interacts with the shipping app via
`adb` + `screencap` + `uiautomator dump` only, with NO code changes to SubTerrania
itself. Each game generates ~150-300 per-tap state snapshots (UI dump, screenshot,
parsed JSON state) plus an in-game event log, persisted to
`playtest-results/emulator/runs.db` (SQLite WAL).

## Why this exists alongside the JVM playtest harnesses

The repo already has three JVM-side playtest harnesses
(`app/src/test/java/com/atlyn/subterranea/AutoPlaytest.kt`,
`RealStrategicPlaytest.kt`, `FunFactorPlaytest.kt`) which simulate hundreds of games
against the same `GameEngine` shipping in production. Those are **the right tool for
balance / pacing analytics** — they finish in minutes, and the deterministic engine
gives ground-truth resource flows.

This emulator harness is for a different purpose: capture **real on-device UI
behaviour** — modal cadence, exploration discovery timing, ANR risk, draw stack
recovery — that the JVM simulator can't see. It is also the only path that yields
data labeled with the `bot_kind` cohort, so we can analyze how different player
proxies experience the game.

## Files

| File | Purpose |
|---|---|
| `playbench.py` | adb wrapper, state extraction, SQLite WAL writer, per-turn JSONL journal, game lifecycle (start_game / end_game / summary). |
| `corpus_runner.py` | v1 heuristic policy bot. Probes screen coords blindly. `bot_kind='heuristic_v1'` |
| `corpus_runner_v2.py` | v2 hex-aware bot. Replicates `HexMap.kt`'s `hexToPixel(q, r) = (size*sqrt(3)*q + size*sqrt(3)/2*r + offX, size*1.5*r + offY)` math (size=70, offX=450, offY=1115 calibrated empirically) to target specific (q, r) hexes. Tracks explored set via event-log parse `at (q, r)`. `bot_kind='heuristic_v2_hex'` |

## Usage

```powershell
# 1. Boot the subterranea_test AVD (Pixel-class, 1080x2400, API 34)
emulator -avd subterranea_test -no-snapshot-load -gpu swiftshader_indirect &

# 2. Install the AAB (from prior Phase G/L build)
java -jar ~/bundletool-all.jar build-apks --bundle=app/build/outputs/bundle/release/app-release.aab --output=app.apks --device-spec=spec.json
java -jar ~/bundletool-all.jar install-apks --apks=app.apks --adb=$ANDROID_SDK/platform-tools/adb.exe

# 3. Init the DB + check status
python tools/playbench/playbench.py init
python tools/playbench/playbench.py status

# 4. Run the bot (detached)
Start-Process python -ArgumentList "-u tools/playbench/corpus_runner_v2.py" -RedirectStandardOutput corpus.log

# 5. Inspect progress any time
python tools/playbench/playbench.py summary
type playtest-results/emulator/summary.md
sqlite3 playtest-results/emulator/runs.db "SELECT outcome, COUNT(*), AVG(final_vp) FROM runs GROUP BY outcome"
```

## Per-turn data shape

Each step writes a row to `turns(game_id, turn_idx, step_label, captured_at, state_json,
action_type, action_target, llm_rationale, screenshot_path, dump_path)`. `state_json`
includes:

- `screen`, `turn`, `phase` (ROLL_DICE / MAIN_ACTION)
- `vp` / `vp_target`
- `resources` (M/B/C/L/I/Cr counts)
- `actions` (Roll/Build/Clear/Trade/End enabled state + bounds)
- `selected_tile` (zone, structure name)
- `latest_event` (in-game event log, with parsed hex coord `latest_event_hex` when "at (q, r)" present)
- `game_over` (WIN/LOSS/None)
- `recenter_bounds`, `difficulty_buttons`, etc.

## Known gaps / TODO (status as of this commit)

1. **Tile selection still imperfect.** Recentering before turn helps but doesn't
   eliminate misses. Adding app-side `testTag("hex:q:r")` to `HexMap.kt` would
   trivialise this and replace 30+ probe taps per turn with a single deterministic
   selector. Out of scope for this commit (no app code change).
2. **More modal kinds keep surfacing.** Currently handled: build_picker,
   end_turn_confirm, use_ability, trade_dialog, consolation_choice ("No Production!"),
   single_ok, event_multi, generic_dismissable. Likely missing: settings menu,
   victory/defeat-specific layouts (when reached at scale), structure-detail dialogs.
3. **Strategy is naive.** Always builds the first affordable structure. Doesn't
   plan trades. Doesn't use Ability productively. Doesn't prioritise illumination.
   For real balance data, prefer running `./gradlew test --tests RealStrategicPlaytest`
   which has a utility-maximising bot.

## Cohort discipline

Every game record carries:
- `bot_kind` - e.g. `heuristic_v2_hex`, `llm_in_loop`
- `model_id` - for LLM cohort, the model that drove the play
- `prompt_version` - version of the policy/prompt used
- `app_version_code` - `8` (1.1.0) at commit time
- `avd_name` - `subterranea_test`

**Do not mix cohorts in analysis.** This dataset is NOT a substitute for human
playthroughs.
