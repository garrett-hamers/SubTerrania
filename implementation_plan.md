# IMPLEMENATION PLAN - SubTerrania Fixes

## Goal Description
Implement critical balance fixes for Nightmare difficulty and enhance UI/UX for Exploration and Building as recommended in the improvement report.

## User Review Required
> [!IMPORTANT]
> **Balance Change**: Nightmare Difficulty will now start with 1 Iron and 1 Crystal (previously 0) to prevent a "softlock" on Turn 1 where players cannot build a Lantern.

## Proposed Changes

### Domain Logic

#### [MODIFY] [Difficulty.kt](file:///f:/code/SubTerrania/app/src/main/java/com/axialgalileo/subterranea/domain/model/Difficulty.kt)
- Update `NIGHTMARE.startingResources` to include 1 Iron and 1 Crystal.

### UI Components

#### [MODIFY] [GameScreen.kt](file:///f:/code/SubTerrania/app/src/main/java/com/axialgalileo/subterranea/ui/game/GameScreen.kt)
- **TopHUD**: Add "Explore" Button or move it to ActionButtons.
- **ActionButtons**:
    - Add logic to show "Explore" button when a selected tile is explorable.
    - Update "Build" button to be enabled (clickable) even if resources are low, but show as "warning" color.
- **BuildMenu**: Ensure it clearly explains resource shortage (exists, but making the entry point better).

#### [MODIFY] [HexMap.kt](file:///f:/code/SubTerrania/app/src/main/java/com/axialgalileo/subterranea/ui/game/HexMap.kt)
- Add animation support for "pulsing" tiles (already supports static color, need to animate opacity/width).
- `explorableTiles` are already drawn with green border. Will enhance this with an animated pulse.

## Verification Plan

### Automated Tests
- Run `play_game.py` again (modified for new UI if needed) to verify Nightmare starts are viable.
- Verify "Explore" button appears/disappears correctly via adb dump or manual check.

### Manual Verification
- Start Nightmare game -> Confirm 1 Iron/1 Crystal.
- Select explorable tile -> Confirm "Explore" button appears.
- Select empty tile -> Click "Build" (with low resources) -> Confirm toast/popup message.
