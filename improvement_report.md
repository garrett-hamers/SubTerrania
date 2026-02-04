# SubTerrania Improvement Report

## Executive Summary
After analyzing the specific gameplay mechanics of *SubTerrania* and conducting automated playtests across all difficulties, several key areas have been identified to enhance player engagement and balance. The most critical finding is a potential resource "softlock" in Nightmare difficulty that may frustrate players immediately.

## 1. Balance & Difficulty

### 🚨 Critical: The "Nightmare" Softlock
**Observation**: In Nightmare difficulty, players start with:
- **Resources**: 2 Mycelium, 2 Basalt, 2 Chitin, 2 Lichen (Total: 8 Basic), 0 Iron, 0 Crystal.
- **Trade Ratio**: 6:1.
- **Lantern Cost**: 1 Iron + 1 Crystal.

**Issue**: Production requires illuminated tiles (Lanterns). To build the first Lantern (essential for production), a player needs 1 Iron and 1 Crystal.
- Trading for **1 Iron** costs 6 Basic resources.
- Trading for **1 Crystal** costs another 6 Basic resources.
- **Total Need**: 12 Basic resources.
- **Total Available**: 8 Basic resources.

**Result**: A player **cannot** build a Lantern on Turn 1. They are forced to rely on:
1.  **Exploration Events**: Trying to find treasure, but Nightmare has a **60% Hazard Chance**, often leading to resource loss or tile destruction.
2.  **Blind Luck**: Hoping for a "Treasure Cache" before running out of actions.

**Recommendation**:
- **Option A**: Grant 1 starting Iron *or* Crystal in Nightmare.
- **Option B**: Reduce Trade Ratio to 4:1 for Nightmare (or 5:1).
- **Option C**: Allow the "Base" tile to provide implicit illumination for adjacent tiles.

### Difficulty Curve
- **Easy vs Normal**: The jump from "Easy" (Bonus production +1) to "Normal" (+0) is significant but fair.
- **Event Hazards**: The 60% hazard chance in Nightmare is punishing. Consider scaling the *severity* of hazards rather than just the frequency, or provide a strictly "safe" exploration option with lower rewards.

## 2. UI/UX Enhancements

### Exploration Visibility
**Observation**: The "Tap to explore!" text in the `SelectedTileInfo` panel is informative but passive. Players focused on the map might miss that they need to re-tap the selected tile to explore.
**Recommendation**:
- Add a dedicated **"Explore" button** to the Action Bar (next to Build/Roll) that becomes active when an explorable tile is selected.
- Add a pulsing visual effect to explorable tiles on the HexMap.

### Build Feedback
**Observation**: The "Build" button logic correctly identifies when resources are missing, but the feedback is hidden behind the `BuildMenu` or requires opening the menu to see *why* options are greyed out.
**Recommendation**:
- If resources are insufficient for *any* structure, change the "Build" button style (e.g., greyed out with a warning icon) but allow clicking it to show a "Resource Shortage" toast or tooltip immediately, rather than opening an empty/disabled menu.

## 3. Maximizing Fun & Replayability

### Interaction Depth (Agency)
**Observation**: The current loop (`Explore -> Build -> Roll -> End`) is functional but repetitive.
**Recommendation**:
- **Active Structure Abilities**: Give structures active cooldown skills.
    - *Example*: "Overcharge Lantern" - Doubles illumination range for 1 turn (Cooldown: 3 turns).
    - *Example*: "Emergency Drill" - Get 1 random resource immediately (Cooldown: 5 turns).
- **Specialized Meeple**: Allow moving a "Surveyor" unit to boost exploration safety.

### Roguelite Elements (Replayability)
**Observation**: Victory screens show Achievements, which is great.
**Recommendation**:
- **Meta-Progression**: Allow Achievements to unlock "Starting Perks" for the next run.
    - *Unlocked "Deep Delver"?* -> Start next game with +1 Movement or +1 Vision.
    - *Unlocked "Crystal Baron"?* -> Start with 1 free Crystal.
- **Daily Challenges**: Generate a fixed seed map for a daily leaderboard to encourage community competition.

## 4. Automated Testing Insights
The automated testing script highlighted that the game loop can be slow if the player is unsure of the next action.
- **Turn Pacing**: Animations (Dice Shake, Production Flash) are polished but add up. Ensure a "Fast Mode" exists for experienced players.
- **Pathfinding**: The AI struggled to find "valid" build spots without Lanterns, reinforcing the need for the "Illumination" mechanic to be highlighted in the Tutorial.

## 5. Conclusion
*SubTerrania* has a solid foundation with high production values (animations, sound). Addressing the **Nightmare resource math** is the highest priority fix. Following that, enhancing **Exploration UI** and adding **Meta-progression** will significantly boost long-term replayability.
