# SubTerrania Game Improvement Ideas

*Generated from full playthrough on January 7, 2026*
*Victory achieved: 11 VP in ~74 turns*
*Updated with implementations: January 7, 2026*

---

## 🎯 Resource Balance Issues

### ✅ 1. Resource Generation Feels Slow (Early Game) - FIXED
- After 30 turns of basic roll/end turn cycles, resources accumulated slowly
- **Suggestion**: 
  - ✅ Boost starting resources more - **DONE: Now starts with 6 of each basic, 4 Iron/Crystal**
  - ✅ Make surface tiles produce more reliably - **DONE: Added Iron & Crystal to surface**
  - Add a "turns played" counter for player feedback *(already exists in UI)*

### ✅ 2. Crystal and Iron Are Too Scarce - FIXED
- After 70+ turns, player had 0 Iron and 0 Crystal despite exploring an Iron Vein
- **Suggestion**: 
  - ✅ Add trading mechanism (e.g., 4:1 bank rate like Catan) - **DONE: Added 🔄 Trade button**
  - ✅ Place more iron/crystal tiles in starting area - **DONE: Surface now has Iron Vein & Crystal Grotto**
  - Reduce costs for structures needing rare resources
  - Give small amounts through exploration events

### ✅ 3. Resource Imbalance - PARTIALLY FIXED
- Lichen accumulated to 22 while Basalt stayed at 1
- **Suggestion**: 
  - Rebalance number tokens across resource types
  - ✅ Add inter-resource trades - **DONE: 4:1 trade system**
  - Auto-convert excess resources option

### ✅ 4. Illumination Chicken-and-Egg Problem - FIXED
- Explored Iron Vein didn't produce because it needed Lantern, but Lantern needs Iron!
- **Suggestion**: 
  - ✅ Surface tiles should always be illuminated - **Already true, surface has isIlluminated=true**
  - ✅ Provide alternative ways to get rare resources - **DONE: Iron & Crystal on surface + trading**
  - Exploration events could give small amounts of rare resources

---

## 📢 Feedback & UX Improvements

### 5. Dice Roll Feedback Needed
- When the dice roll doesn't match any tiles, it's unclear why nothing happened
- **Suggestion**: 
  - ✅ Show "No production on rolled number X" message prominently - **Already exists in event log**
  - Highlight which number was rolled
  - Show which tiles WOULD produce on common numbers

### ✅ 6. Build Menu Feedback Could Be Clearer - FIXED
- "No structures available to build here" doesn't explain why
- **Suggestion**: Show specific reason:
  - ✅ "Tile already has structure" - **DONE: getBuildFailureReason() shows specific reasons**
  - ✅ "Tile not explored" - **DONE**
  - ✅ "Insufficient resources: need 2 Basalt" - **DONE: Shows most needed resource**

### 7. Exploration Event Log Clarity
- When exploring, it's not always clear which tile was explored vs which is selected
- **Suggestion**: 
  - ✅ Show coordinates more prominently in event messages - **Already shows coordinates**
  - Use different colors for "just explored" vs "selected"

---

## ✨ New Features to Consider

### 8. Quality of Life
- ✅ Add a prominent "Turn X" counter - **Already exists in top HUD**
- Show total structures built on UI
- Add undo button for misclicks
- ✅ Add tutorial hints for first-time players - **Already exists with showTutorial**

### ✅ 9. Trading System - IMPLEMENTED
- ✅ Resource trading at 4:1 bank rate (like Catan) - **DONE**
- ✅ Allow trading excess resources for needed ones - **DONE**
- Could be a building that enables trading

### 10. Progressive Difficulty
- ✅ Early game feels slow, late game is satisfying - **FIXED: Better starting resources**
- ✅ Consider "starter bonus" for first few turns - **DONE: 6 of each resource + Iron/Crystal on surface**
- Or guaranteed production on certain turns

---

## ✅ What Works Well

These elements should be preserved:

1. **Clear "Not illuminated" warning** with "Build a Lantern nearby!" suggestion
2. **Achievement system** adds excitement ("Master Builder!", "First Explorer!")
3. **Victory screen** with achievements is satisfying
4. **Event log** showing recent production is helpful
5. **Exploration events** (beetle nests, etc.) add variety
6. **Hex-based map** is visually appealing
7. **Structure variety** with different costs/benefits
8. **Zone progression** (Surface → Crust → Mantle → Core) creates depth

---

## 📊 Playtest Statistics

| Metric | Value |
|--------|-------|
| Turns to Victory | ~74 |
| Final VP | 11/10 |
| Structures Built | 8+ |
| Most Abundant Resource | Lichen (22) |
| Scarcest Resources | Iron (0), Crystal (0) |
| Achievements | Master Builder, First Explorer |

---

## 🔧 Priority Recommendations

### High Priority
1. ✅ Fix Crystal/Iron scarcity - **DONE: Surface tiles + starting resources**
2. ✅ Add "why can't I build" feedback - **DONE: getBuildFailureReason()**
3. ✅ Ensure starting area has all resource types accessible - **DONE: All 6 resource types on surface**

### Medium Priority
4. ✅ Add resource trading system - **DONE: 4:1 trade ratio**
5. ✅ Improve dice roll feedback - **Already shows "No tiles produce on X"**
6. ✅ Balance number token distribution - **DONE: Good numbers (5,6,8,9) on surface tiles**

### Low Priority (Polish)
7. ✅ Add turn counter - **Already exists**
8. ✅ Tutorial hints - **Already exists**
9. Undo functionality - *Not implemented*
