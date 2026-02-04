# SubTerrania Playtest & Improvement Report
## February 2026 Analysis

*Comprehensive analysis based on code review, existing playtest data, and live game observation*

---

## Executive Summary

SubTerrania is a well-crafted hex-based resource management game inspired by Catan, Betrayal at House on the Hill, and Terraforming Mars. After analyzing the codebase, existing playtest data, and observing live gameplay, this report identifies **key opportunities to enhance fun and replayability** while preserving what already works well.

**Key Findings:**
1. ✅ Core mechanics are solid and satisfying
2. ⚠️ Early game can feel slow due to illumination requirements  
3. ⚠️ Mid-game decision space is limited (explore → build → roll loop)
4. 🎯 Replayability would benefit from meta-progression and variety
5. 🐛 UI rendering issue: difficulty selection can overlay active game state

---

## 1. What's Working Well (Preserve These)

| Feature | Why It Works |
|---------|--------------|
| **Achievement System** | Creates exciting milestone moments ("Master Builder!", "Core Seeker!") |
| **Zone Progression** | Surface → Crust → Mantle → Core creates clear sense of depth |
| **Illumination Mechanic** | Strategic decision point for Lantern placement |
| **Event Log** | Keeps players informed about what happened |
| **Tutorial Hints** | Context-sensitive tips reduce new player friction |
| **Exploration Events** | Lost Miner, Beetle Nest, Treasure Cache add variety |
| **Difficulty Scaling** | Well-differentiated (8-15 VP, 3:1 to 6:1 trades, 1-3 actions) |

---

## 2. Fun & Engagement Improvements

### 2.1 The "Rolling 7" Problem
**Observation:** In my test session, I rolled 7 twice in a row on Turns 1-2 (4+3, 6+1). Since no tiles produce on 7, this felt like wasted turns.

**Recommendations:**
- **A) Lucky 7 Bonus**: When 7 is rolled, player gets a choice: draw 1 random resource OR peek at an adjacent unexplored tile
- **B) Consolation Prize**: "No production" rolls grant +1 action that turn
- **C) 7 = Free Trade**: Allow one 2:1 trade instead of the difficulty-based ratio

### 2.2 Active Structure Abilities
**Observation:** Once built, structures are passive. Players just wait for dice rolls.

**Recommendations:** Add activated abilities with cooldowns:
| Structure | Active Ability | Cooldown |
|-----------|---------------|----------|
| Lantern Post | "Flare" - Reveal terrain type of 1 adjacent fog tile | 3 turns |
| Mining Outpost | "Overtime" - Produce 1 resource regardless of dice | 4 turns |
| Deep Excavator | "Survey" - See number tokens on adjacent tiles | 2 turns |
| Fungal Farm | "Spore Burst" - Gain 2 Mycelium immediately | 5 turns |

### 2.3 Meaningful Choices During Exploration
**Observation:** Exploration events happen *to* the player. Adding choice increases engagement.

**Recommendation:** Some events offer choices:
```
🦗 Beetle Swarm Detected!
  [A] Fight (50% chance: gain Chitin x3, or lose 1 action)
  [B] Sneak past (safe, but tile marked as "Infested" - hazard for future)
  [C] Retreat (no exploration, keep action)
```

---

## 3. Replayability Enhancements

### 3.1 Meta-Progression System
**Current State:** Each game is independent. Achievements show on victory screen but don't carry over.

**Recommendation:** Unlock persistent bonuses:
| Achievement | Unlocks |
|-------------|---------|
| Crystal Baron | Start future games with +1 Crystal |
| Deep Delver | +1 Vision radius on first Lantern |
| Master Builder | First structure each game costs 1 less resource |
| Core Seeker | Surface tiles have 10% better number tokens |

### 3.2 Tile Randomization (Critical for Replayability)
**Current State:** Surface tiles have **fixed terrain and number token assignments** in `BoardGenerator.kt`:
```kotlin
// Every game is identical:
HexCoordinate(1, 0) → LICHEN_FIELD, number 6
HexCoordinate(0, 1) → FUNGAL_FOREST, number 8
HexCoordinate(-1, 0) → IRON_VEIN, number 6
// etc.
```

**Problem:** Players quickly memorize optimal opening moves. No strategic adaptation needed.

**Recommendations:**

**A) Randomize Surface Terrain Positions:**
```kotlin
// Instead of fixed mapping, shuffle terrain types:
val surfaceTerrains = listOf(
    TerrainType.LICHEN_FIELD, TerrainType.FUNGAL_FOREST,
    TerrainType.BASALT_QUARRY, TerrainType.IRON_VEIN,
    TerrainType.CRYSTAL_GROTTO, TerrainType.BEETLE_FARM
).shuffled()

// Assign to positions randomly
surfaceCoords.zip(surfaceTerrains).forEach { (coord, terrain) ->
    tiles[coord] = HexTile(coord, Zone.SURFACE, terrain = terrain, ...)
}
```

**B) Randomize Number Tokens:**
- Keep "good numbers" (5, 6, 8, 9) for surface, but **shuffle which tile gets which**
- Ensures Iron/Crystal aren't always on the best numbers
- Creates meaningful early-game decisions: "Iron is on a 4 this game - should I explore for another vein?"

**C) Variant: "Fog of Resources"**
- Surface tiles start revealed but terrain is hidden until first production
- Players see the number tokens but not what resource each tile produces
- Creates discovery moments even on the safe starting area

**D) Deep Tile Randomization:**
- Currently unexplored tiles get terrain assigned on exploration
- Ensure terrain distribution varies per game (seed-based)
- Some games might have more Crystal Grottos, others more hazard-prone tiles

**Implementation Priority:** High - this is a low-effort, high-impact change. Simply replacing the fixed `terrainByPosition` map with a shuffled list would immediately make every game feel fresh.

### 3.3 Map Variety
**Additional Recommendations:**
- **Map Presets**: "The Abyss" (narrow vertical), "Sprawling Caverns" (wide), "Crystal Caves" (more grottos)
- **Daily Challenge**: Seeded map with leaderboard - compete for fastest VP or most tiles explored
- **Scenario Mode**: Special objectives like "Rescue the Miners" (find 3 Lost Miner events) or "Core Rush" (reach Core in under 20 turns)

### 3.3 Character Selection
**Recommendation:** Add 3-4 starting characters with unique abilities:
- **The Prospector**: Starts with 1 extra Iron and Crystal
- **The Scout**: Can explore twice per turn (like Easy mode) but 1 fewer action
- **The Engineer**: Structures cost 1 less rare resource
- **The Survivor**: 50% hazard resistance on exploration events

---

## 4. Balance Observations

### 4.1 Nightmare Difficulty - Addressed ✅
Per existing reports, Nightmare now starts with 1 Iron + 1 Crystal, fixing the previous softlock.

### 4.2 Resource Flow Analysis
Based on code review of `Difficulty.kt` and `GameEngine.kt`:

| Difficulty | Starting Rare | Trade Ratio | First Lantern Feasible? |
|------------|--------------|-------------|------------------------|
| Easy | 6 Iron, 6 Crystal | 3:1 | ✅ Immediate |
| Normal | 4 Iron, 4 Crystal | 4:1 | ✅ Immediate |
| Hard | 2 Iron, 2 Crystal | 5:1 | ✅ Immediate |
| Nightmare | 1 Iron, 1 Crystal | 6:1 | ✅ Immediate (fixed) |

**Remaining Issue:** Hard difficulty may feel too similar to Normal. Consider:
- Hard: Starting Iron/Crystal reduced to 1 each (forces early exploration)
- Hard: Hazard events can destroy unprotected structures

### 4.3 Structure VP Balance
| Structure | Cost | VP | VP/Resource Ratio |
|-----------|------|-----|-------------------|
| Lantern Post | 2 rare | 0 | 0 (utility only) |
| Mining Outpost | 4 common | 1 | 0.25 |
| Deep Excavator | 5 mixed | 2 | 0.40 |
| Fungal Farm | 4 common | 1 | 0.25 |
| Core Anchor | 10 mixed | 4 | 0.40 |

**Observation:** Lantern feels mandatory but gives no VP. This is intentional but could feel unrewarding.

**Recommendation:** Lanterns grant 1 VP after illuminating 4+ tiles (tracked per lantern).

---

## 5. UI/UX Polish

### 5.1 Critical Bug Found
**Issue:** During testing, the difficulty selection screen rendered *on top of* an active Turn 1 game state. Both "Select Difficulty" text and "Turn 1" were visible simultaneously.

**Reproduction:** Occurred after rapid app restarts via ADB. May indicate Compose state not being properly cleared.

**Fix:** Ensure `GameState` is fully reset when navigating to difficulty selection. Check for race conditions in `GameViewModel` initialization.

### 5.2 Production Feedback
**Observation:** When dice match tiles but they're not illuminated, nothing visually happens.

**Recommendation:** Show ghost/dimmed production animation with "🌑 +2 Mycelium (not illuminated)" to teach the mechanic.

### 5.3 Tile Information Density
**Current:** Selected tile shows zone, terrain, number token, illumination status.

**Recommendation:** Add:
- Distance from nearest Lantern (e.g., "2 tiles from light")
- Production history ("Produced 3 times this game")

### 5.4 Fast Mode Toggle
**Observation:** Dice shake and production animations are polished but repetitive after many turns.

**Recommendation:** Settings toggle for "Fast Mode" - instant dice, abbreviated animations.

---

## 6. Prioritized Action Items

### 🔴 High Priority (Do First)
1. **Fix UI overlay bug** - Difficulty screen rendering over game state
2. **Randomize surface tiles** - Shuffle terrain positions and number tokens each game
3. **Add "Rolling 7" mitigation** - Choose: +1 resource, +1 action, or free 2:1 trade
4. **Illumination feedback** - Show what *would* produce if lit

### 🟡 Medium Priority (Next Sprint)
4. **Active structure abilities** - Start with Lantern "Flare" ability
5. **Meta-progression** - Achievements unlock starting bonuses
6. **Hard difficulty differentiation** - More distinct from Normal

### 🟢 Lower Priority (Polish)
7. **Character selection** - 3-4 characters with unique abilities
8. **Map presets/daily challenges**
9. **Exploration event choices**
10. **Fast mode toggle**

---

## 7. Summary

SubTerrania has excellent bones - satisfying resource management, clear progression, and good difficulty scaling. The main opportunities are:

1. **More player agency** - Active abilities, event choices, mitigation for bad rolls
2. **Reasons to replay** - Meta-progression, characters, map variety
3. **Polish** - Fix the UI bug, improve feedback for edge cases

Implementing even the high-priority items would significantly boost engagement and replayability.

---

*Report generated: February 4, 2026*
*Analysis based on: Code review, existing playtest data (IMPROVEMENT_IDEAS.md, improvement_report.md), live observation*
