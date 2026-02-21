# Copilot Instructions — SubTerranea

## Project Overview

SubTerranea is a single-player, turn-based Android strategy game (Kotlin, Jetpack Compose) set in underground caves. Players explore a hex-grid map across 4 depth zones (Surface → Crust → Mantle → Core), collect 6 resource types, build structures for victory points, and race to hit a VP target before the turn limit. Gameplay is Catan-inspired: 2d6 dice rolls drive resource production on hex tiles.

## Build & Run

```shell
# Debug build + install to connected device/emulator
./gradlew installDebug

# Release build (unsigned)
./gradlew assembleRelease

# Run unit tests (includes AutoPlaytest — 100 simulated games)
./gradlew test

# Run a single test class
./gradlew test --tests "com.atlyn.subterranea.AutoPlaytest"
```

The project uses Gradle version catalog (`gradle/libs.versions.toml`) for dependency management. JVM target is 21.

## Architecture

```
com.atlyn.subterranea
├── domain/
│   ├── model/       # Pure data: GameState, HexTile, Player, Resource, Zone, Difficulty, etc.
│   ├── logic/       # Stateless engines that transform GameState
│   │   ├── GameEngine          # Turn orchestrator — delegates to specialized engines
│   │   ├── BoardGenerator      # Hex grid creation, terrain/dice-number assignment
│   │   ├── ExplorationEngine   # Tile reveal, hazards, exploration events
│   │   ├── StructureEngine     # Building, abilities, cooldowns, illumination
│   │   ├── TradeEngine         # Resource exchange
│   │   └── EventEngine         # Interactive branching events
│   └── telemetry/   # JSON event logging for balance analytics
├── ui/
│   ├── viewmodel/GameViewModel  # MVVM hub — owns GameState + UIState as StateFlow
│   ├── game/GameScreen          # Main composable — dialogs, menus, action UI
│   ├── game/HexMap              # Canvas-drawn hex grid with touch→axial-coord mapping
│   ├── theme/                   # Color palette (cave/bioluminescent), typography
│   ├── animation/               # Reusable Compose animation modifiers
│   ├── audio/SoundManager       # Haptic feedback (vibration patterns, not audio files)
│   └── util/IconHelper          # Domain model → drawable resource mapping
└── MainActivity                 # Single-activity entry point
```

### Key design patterns

- **Immutable state + pure transforms**: All domain engines are stateless functions that take a `GameState` and return a new `GameState`. No mutation.
- **Single ViewModel**: `GameViewModel` is the only stateful component — it holds `GameState` and `GameUIState` as `StateFlow`, and all UI recomposes reactively.
- **Engine delegation**: `GameEngine` is the top-level orchestrator. It calls `ExplorationEngine`, `StructureEngine`, `TradeEngine`, and `EventEngine` for domain-specific logic. Don't put game logic directly in the ViewModel.

## Key Conventions

- **Hex coordinates**: The board uses axial coordinates (`HexCoordinate(q, r)`). Neighbor calculations and pixel↔hex conversions use standard axial math.
- **Difficulty scaling**: `Difficulty` enum (Beginner/Normal/Hard/Nightmare) controls starting resources, VP targets (12–20), action limits, hazard chances, and trade ratios. Balance constants live in `GameConstants.kt` and `Difficulty.kt`.
- **Zone system**: 4 depth zones (`Zone.SURFACE` → `CORE`) each have their own terrain pools and event probabilities. Deeper zones = rarer resources + higher risk.
- **Color naming**: Theme colors follow a semantic naming convention — `Terrain*`, `Resource*`, `Biolum*`, `Surface*`, `Accent*`, `Glow*` prefixes in `Color.kt`.

## Balance Testing

Game balance is validated via automated playtesting:

- `AutoPlaytest.kt` — JUnit test that simulates 100 games across all difficulties with 4 AI profiles (Cautious, Balanced, Aggressive, Builder). Run with `./gradlew test`.
- `hard_balance_calibration.py` — Runs AutoPlaytest, parses results, validates guardrails (e.g., Hard difficulty: 45–60% win rate).
- `playtest.ps1` — ADB-based script that plays 10 games on a real device and reports stats.
- `telemetry_analysis_pipeline.py` — Parses telemetry JSON logs into analytics CSVs.

When changing game constants, resource costs, or event probabilities, always run AutoPlaytest to verify balance isn't broken.
