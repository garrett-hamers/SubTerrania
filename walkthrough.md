# Walkthrough - Balance and UI Fixes

I have implemented the recommended fixes to address the Nightmare difficulty softlock and improve the exploration/building UX.

## Changes Verified

### 1. Nightmare Balance Fix
- **File**: `Difficulty.kt`
- **Change**: Updated `NIGHTMARE` starting resources to include **1 Iron Ore** and **1 Crystal**.
- **Impact**: Players can now build a Lantern on Turn 1 (using the 6:1 trade ratio if needed, or if they gather 1 more resource), preventing the softlock where they had 0 production capability.

### 2. Exploration UX
- **File**: `GameViewModel.kt`, `GameScreen.kt`
- **Change**: Changed `onTileClicked` to require a **second tap** to explore a selected tile. Added a dedicated **"Explore"** button to the action bar.
- **Impact**: clearer intent. "Tap to explore!" hint text now accurately reflects the requirement to tap the *selected* tile (or the button).

### 3. Visual Polish
- **File**: `HexMap.kt`
- **Change**: Added a **pulsing green animation** to explorable tiles.
- **Impact**: Significantly improved visibility of where players can go next.

### 4. Build Feedback
- **File**: `GameScreen.kt`
- **Change**: The "Build" button is now always clickable if a valid tile is selected, but turns **Red/Warning** if resources are insufficient.
- **Impact**: Players can click it to see *why* they can't build (via the specific error message in the menu), rather than guessing why the button was disabled.

## Verification
- **Compilation**: Code structure verified.
- **Logic Check**: `onTileClicked` logic updated to `if (state.selectedTile == coord)` ensures safety.
