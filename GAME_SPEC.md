# SubTerranea — Game Design Specification

> Comprehensive game design document. All values sourced directly from the codebase.

## Overview

SubTerranea is a single-player, turn-based strategy game for Android. Players explore an underground hex-grid map across 4 depth zones, collect resources, build structures, and race to reach a victory point target before the turn limit. The game draws inspiration from Catan's dice-driven resource production, layered with exploration and risk/reward mechanics.

---

## Turn Flow

Each turn follows a fixed phase sequence:

1. **ROLL_DICE** — Roll 2d6 to determine resource production
2. **MAIN_ACTION** — Spend actions to explore, build, clear rubble, or use abilities. Trading is free and unlimited.
3. **END_TURN** — Reset action counters, tick ability cooldowns, check victory

**Actions per turn:** 2 (Easy/Normal/Hard), 1 (Nightmare)
**Trading does not consume an action.**

### End-of-Turn Reset
- `actionsThisTurn` → 0
- `exploresThisTurn` → 0
- `canExploreThisTurn` → true
- `discountTradeAvailable` → false
- `pendingConsolation` → false
- Ability cooldowns tick down by 1

---

## Dice Rolling & Production

### Roll Mechanics
- Roll 2d6 (each die 1–6), sum = 2–12
- **Lucky Roll = 7:** If nothing produces, player chooses a consolation reward

### Production Conditions
A tile produces resources when **all** of the following are true:
- Number token matches the dice total
- Tile is revealed
- Tile is **illuminated** (by a Lantern or Fungal Bloom)
- No rubble on the tile
- Terrain type produces a resource

### Production Multipliers

| Structure on tile | Effect |
|---|---|
| Excavator | ×2 production |
| Crystal Refinery | ×2 if Crystal, else ×1 |
| Fungal Farm | ×2 if Mycelium, else ×1 |

Nightmare applies a rare-resource penalty: Iron and Crystal production −1 (minimum 1).

### No-Production Scenarios

| Condition | Result |
|---|---|
| Lucky 7, nothing produces | Player chooses consolation (see below) |
| Non-7, dark tiles would produce | Hint message: "would produce if illuminated!" |
| Non-7, nothing at all | Scavenge: +1 of lowest resource, +1 of second-lowest |

### Consolation Choices (Lucky 7)

| Choice | Effect |
|---|---|
| 🎁 Scavenge | +1 random common resource (Mycelium, Basalt, Chitin, or Lichen) |
| ⚡ Hustle | +1 action this turn |
| 🤝 Barter | One 2:1 trade available this turn |

---

## Resources

| Resource | Display Name | Source Terrain |
|---|---|---|
| Mycelium | Mycelium Stalks | Fungal Forest |
| Basalt | Basalt Blocks | Basalt Quarry |
| Chitin | Beetle Chitin | Beetle Farm |
| Lichen | Cave Lichen | Lichen Field |
| Iron Ore | Iron Ore | Iron Vein |
| Crystal | Luminary Crystal | Crystal Grotto |

Mycelium, Basalt, Chitin, and Lichen are **common** resources (available on Surface).
Iron Ore and Crystal are **rare** resources (primarily in deeper zones).

---

## Hex Board & Zones

### Board Layout
- Hex grid using axial coordinates `(q, r)`
- Radius 4 centered at `(0,0)` — approximately 37 tiles
- Center tile `(0,0)` is always Bedrock (elevator shaft), revealed and illuminated

### Zone Distribution

| Zone | Distance from center | Resources available |
|---|---|---|
| Surface | 0–1 | Common: Lichen, Fungal, Basalt, Beetle |
| Crust | 2 | Common + Iron Vein |
| Mantle | 3 | Iron, Crystal, Basalt, Magma |
| Core | 4+ | Crystal (dominant), Iron, Magma, Bedrock |

Deeper zones have rarer resources but higher hazard rates and interactive events.

### Number Token Pools

| Zone | Primary Numbers | Secondary Numbers |
|---|---|---|
| Surface | 6, 8, 5, 9, 4, 10 | 3, 4, 5, 9, 10, 11 |
| Crust | 4, 5, 6, 8, 9, 10 | 3, 4, 5, 9, 10, 11 |
| Mantle | 5, 6, 6, 8, 8, 9 | — |
| Core | 6, 6, 7, 7, 8, 8 | — |

Core tiles cluster around 6–8 (high probability), rewarding deep exploration.

### Map Presets

| Preset | Surface Modification |
|---|---|
| Standard | Default terrain mix |
| Crystal Caves | 2× Crystal Grotto on surface |
| Iron Depths | 2× Iron Vein on surface |
| Fungal Jungle | 2× Fungal Forest, 2× Lichen Field |
| Volcanic Core | 2× Basalt, mixed types |
| Daily Challenge | Deterministic seed from date |

---

## Exploration

### Rules
- Must explore a tile **adjacent to a revealed tile**
- **1 explore per turn** (Scout character gets 2, costs 1 action)
- Costs 1 action
- Already-revealed tiles cannot be explored again

### Terrain Generation
When a tile is explored, terrain and number token are randomly selected from the tile's zone pool (see Board section).

### Exploration Events by Zone

Events are triggered by a d100 roll, modified by difficulty hazard chance.

#### Surface Events

| Roll | Event | Reward |
|---|---|---|
| 1–50 | Stable Ground | Nothing |
| 51–70 | Fungal Bloom | +2 Mycelium, tile illuminated |
| 71–85 | Beetle Nest | +3 Chitin |
| 86–100 | Treasure Cache | +2 Lichen, +1 Mycelium |

#### Crust Events

| Roll | Event | Reward |
|---|---|---|
| 1–35 | Stable Ground | Nothing |
| 36–50 | Fungal Bloom | +2 Mycelium, illuminates tile |
| 51–65 | Beetle Nest | +2 Chitin |
| 66–79 | Treasure Cache | +2 Basalt, +1 Iron |
| 80–87 | Cave-In | Rubble on tile |
| 88–94 | Gas Leak | Lose 1 of each resource |
| 95–100 | Lost Miner | Bonus explore |

#### Mantle Events

| Roll | Event | Reward |
|---|---|---|
| 1–25 | Stable Ground | Nothing |
| 26–40 | Crystal Vein | +2 Crystal |
| 41–55 | Treasure Cache | +2 Iron, +1 Crystal |
| 56–65 | Ancient Artifact | +1 VP |
| 66–75 | Cave-In | Rubble |
| 76–85 | Tremor | Skip next action |
| 86–92 | Gas Leak | Lose resources |
| 93–100 | Magma Burst / Geothermal Vent | Impassable tile / +1 Crystal +1 Iron |

#### Core Events

| Roll | Event | Reward |
|---|---|---|
| 1–20 | Stable Ground | Nothing |
| 21–40 | Crystal Vein | +3 Crystal |
| 41–55 | Ancient Artifact | +2 VP |
| 56–65 | Treasure Cache | +3 Crystal, +2 Iron |
| 66–75 | Cave-In | Rubble |
| 76–86 | Magma Burst | Impassable tile |
| 87–94 | Tremor | Skip action |
| 95–100 | Gas Leak / Geothermal Vent | Lose resources / +1 Crystal +1 Iron |

### Hazard Chance Scaling
The hazard portion of the event table expands with difficulty:
- Easy: 10%, Normal: 30%, Hard: 50%, Nightmare: 70%
- HAZARDOUS map preset adds +20% (capped at 90%)

---

## Structures

### Building Requirements
- Tile must be revealed
- Cannot build on Magma Flow or Bedrock
- Player must afford the cost (adjusted by difficulty multiplier)
- No duplicate structure on same tile

### Structure Table

| Structure | Base Cost | VP | Ability | Cooldown |
|---|---|---|---|---|
| Lantern | 1 Crystal, 1 Iron | 1 | Flare | 2 turns |
| Outpost | 2 Basalt, 1 Mycelium, 1 Chitin | 1 | Overtime | 3 turns |
| Excavator | 3 Iron, 2 Basalt | 2 | Survey | 1 turn |
| Fungal Farm | 2 Mycelium, 2 Lichen | 1 | Spore Burst | 3 turns |
| Beetle Stable | 2 Chitin, 2 Lichen | 1 | — | — |
| Crystal Refinery | 2 Crystal, 2 Iron, 1 Basalt | 2 | — | — |
| Core Anchor | 3 Crystal, 3 Iron, 2 Basalt, 2 Mycelium | 4 | — | — |

### Cost Adjustment Formula
```
adjustedCost = ceil(baseCost × difficulty.buildCostMultiplier)
If Engineer character: rare resources (Iron/Crystal) cost −1
```

| Difficulty | Multiplier |
|---|---|
| Easy | 0.8× |
| Normal | 1.0× |
| Hard | 1.0× |
| Nightmare | 1.2× |

### Structure Abilities

| Ability | Effect |
|---|---|
| **Flare** | Reveal terrain type of 1 adjacent unexplored tile |
| **Overtime** | Produce +1 resource from this tile regardless of dice roll |
| **Survey** | Reveal number tokens on up to 3 adjacent unexplored tiles |
| **Spore Burst** | Gain +2 Mycelium immediately |

Abilities do not cost an action. They are used during MAIN_ACTION phase when off cooldown.

### Illumination
- **Lanterns illuminate** the placed tile + all 6 adjacent tiles (range 1; Easy gets range 2)
- **Only illuminated tiles produce resources** — this is the core strategic constraint
- **Fungal Bloom** events auto-illuminate the explored tile
- **Lantern Bonus:** Illuminate 4+ tiles → +1 VP

### Rubble
- Created by Cave-In exploration events
- Blocks production on the tile
- Clear cost: 1 Iron Ore + 1 Basalt (1 action)

---

## Trading

| Difficulty | Trade Ratio |
|---|---|
| Easy | 2:1 |
| Normal | 3:1 |
| Hard | 4:1 |
| Nightmare | 5:1 |

- Cannot trade a resource for itself
- Player must hold ≥ trade ratio of the given resource
- Trading does not consume an action (unlimited trades per turn)
- **Discount trade** (from Lucky 7 consolation): 2:1 for one trade this turn

---

## Interactive Events

Triggered probabilistically when exploring Mantle (25% chance) or Core (40% chance) tiles.

### Beetle Swarm

| Choice | Outcome |
|---|---|
| **Fight** | 50% win: +3 Chitin · 50% lose: costs 1 extra action |
| **Sneak** | Tile marked infested, no reward |
| **Retreat** | Return safely, no reward |

### Unstable Ground

| Choice | Cost / Risk | Outcome |
|---|---|---|
| **Careful** | +1 action | Safe exploration |
| **Rush** | 30% cave-in risk | Success: free · Fail: rubble (Survivor resists 50%) |
| **Reinforce** | 2 Basalt | Tile permanently stabilized |

### Ancient Cache

| Choice | d100 Roll | Outcome |
|---|---|---|
| **Open** | 1–40 | +2–4 random resource (Crystal/Iron/Chitin) |
| | 41–70 | +1 VP |
| | 71–100 | −2 Chitin (trap) |
| **Study** | — | Delayed: can open next turn |
| **Leave** | — | No reward |

### Lost Miner

| Choice | Cost | Outcome |
|---|---|---|
| **Rescue** | Free | Reset explores this turn, gain +1 explore |
| **Trade** | 2 Lichen | +1 Iron Ore |
| **Directions** | Free | 25% chance: +1 Crystal gift |

### Event Selection by Map Preset

| Preset | Weighting |
|---|---|
| Crystal Caves | 66% Ancient Cache, 33% Unstable Ground |
| Hazardous | 66% Unstable Ground, 33% Beetle Swarm |
| Organic Rich | 66% Beetle Swarm, 33% Lost Miner |
| Standard/Other | 25% each |

---

## Difficulty Settings

| Parameter | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| VP to win | 12 | 14 | 19 | 18 |
| Max actions/turn | 2 | 2 | 2 | 1 |
| Max turns | 22 | 18 | 20 | 25 |
| Starting resources | 2 each common, 1 each rare | 1 each common | 1 each common | 1 Mycelium, 1 Basalt |
| Trade ratio | 2:1 | 3:1 | 4:1 | 5:1 |
| Hazard chance | 10% | 30% | 50% | 70% |
| Build cost multiplier | 0.8× | 1.0× | 1.0× | 1.2× |
| Lantern range | 2 | 1 | 1 | 1 |
| Rare resource penalty | No | No | No | Yes (−1 production) |
| Tutorial hints | Yes | Yes | No | No |

---

## Characters & Meta-Progression

### Characters

| Character | Bonus | Unlock Condition |
|---|---|---|
| Explorer | None (default) | Always available |
| Prospector | +1 Iron, +1 Crystal starting | Win on Normal |
| Scout | 2 explores/turn, −1 max action | Explore 50 tiles (lifetime) |
| Engineer | Structures cost −1 rare resource | Build 20 structures (lifetime) |
| Survivor | 50% hazard resistance | Win on Hard |

### Achievements

| Achievement | Condition | VP |
|---|---|---|
| First Explorer | Reveal a Mantle tile | 1 |
| Core Seeker | Reveal a Core tile | 2 |
| Master Builder | Build 5 structures | 1 |
| Crystal Baron | Collect 10 Crystal | 2 |
| Deep Delver | Explore 10 tiles | 1 |
| Illuminator | Build 3 Lanterns | 1 |

---

## Victory & Defeat

**Victory:** Reach the VP target for the current difficulty. VP is earned from structures (1–4 VP each), achievements (1–2 VP each), and the Lantern Bonus (+1 VP for illuminating 4+ tiles).

**Defeat:** Exceed the turn limit without reaching the VP target.

### VP Sources Summary

| Source | VP Range |
|---|---|
| Lantern | 1 |
| Outpost | 1 |
| Fungal Farm | 1 |
| Beetle Stable | 1 |
| Excavator | 2 |
| Crystal Refinery | 2 |
| Core Anchor | 4 |
| Lantern Bonus (4+ tiles lit) | 1 |
| Achievements | 1–2 each |

---

## UI & Feedback

- **Hex map** rendered on Canvas with terrain colors, structure icons, and fog-of-war
- **Tile states:** Unrevealed (fog), revealed but dark (dim), illuminated (full color), selected (gold outline)
- **Explorable tiles** pulse green; buildable tiles glow blue
- **Sound:** Haptic vibration patterns for dice roll, explore, build, hazard events (no audio files)
- **Animations:** Bounce, shake, pulse, glow, spin, pop-in modifiers via Compose
- **Fast mode:** Toggle to skip animations
- **Event log:** Last 20 game events displayed in scrollable list
