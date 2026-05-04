# Phase K-2 — Claude Playthrough Narrative Review

> First-person review based on real games played on the Pixel-class emulator,
> running the Phase J debug APK (`com.atlyn.subterranea` v1.0.3, build 4).
> Each entry is what I noticed AS I played, not after-the-fact analysis.

---

## Game 1 — Normal, The Explorer, Standard Map

### Pre-game (difficulty select screen)

The launch screen reads "SubTerrania" in bold yellow. Each difficulty is colour-coded with three at-a-glance stat chips: VP target, actions/turn, trade ratio. **This is excellent UX.** I already know that Hard means 19 VP and 3:1 trades before tapping anything. The "Higher difficulty = more hazards…" footnote is honest. **Friction**: nothing on this screen tells you how *long* a game will be (turn cap), which is a major signal for someone deciding what to commit to. The "Hard" difficulty colour (orange) is identical to "Lichen" in the game itself — minor visual conflict.

### Turn 1 — Opening state

Resources: Mycelium 1, Basalt 1, Chitin 1, Lichen 1, Iron 0, Crystal 0. Six revealed tiles around a central black hex (looks like the spawn anchor — never explained on screen). Each tile carries a "X|Y" number pair, presumably the dice rolls on which it produces. Tile types are readable from icons but the numbers are tiny on a phone.

**Initial impression**: the board is visually striking. The dark unexplored hexes with "?" markers around the lit centre are immediately legible. **Friction point**: a first-time player has zero idea what `9|9`, `5|10` means without the tutorial telling them. The on-screen tip says "Build a Lantern to light up dark tiles" — but I have 0 Crystal and 0 Iron, which is exactly what a Lantern costs. The tip is leading me toward an action I literally cannot perform. **This is a fun-killer for new players.**

### Turn 1, action 1 — Roll

Dice: **4 + 4 = 8**. The Iron Vein (8|3) produces 1 Iron Ore. The board lights up briefly with a green pulse on the producing tile and the resource counter ticks up. Visually clear and satisfying. The dice card sits in the centre showing "4" "4" "= 8" with probability dots — nice touch.

### Turn 1, action 1 — Explore north

I tap a dark tile north of the Fungal Forest. **First confusion**: a single tap selects, double-tap explores. The on-screen hint actually told me this ("Double-tap a dark tile next to revealed tiles to explore!") — credit. New tile revealed: a **second** Basalt Quarry at (1, -2) with `10|10` — produces only on a roll of 10 (3/36 = 8.3% per roll). A frustrating result: this tile is a poor producer, and I can't even use it because it's "Not illuminated — Build a Lantern nearby!". I now have a lit tile I can build on but no resources to build the lantern that would light it. **The systems are well-coupled, but this circular dependency on turn 1 feels punishing.**

### Turn 1, action 2 — (forced) End

With 1 action left, I open the Build menu. It's well-designed — each structure card shows the cost as resource icons and the deficit ("Need 1 more Luminary Crystal"). **But it only shows 2 of the 7 structure types** (Lantern Post + Mining Outpost) — the other five (Excavator, Fungal Farm, Beetle Stable, Crystal Refinery, Core Anchor) are silently hidden. As a player, I have no way to learn what those structures even cost or why I can't build them. **This hides the meta from new players.**

I close the menu (and my Back-button press dumps me to the Android home screen instead of just dismissing the modal — **a Back-key UX bug worth fixing**). I relaunch and the "Resume Game" button works perfectly — Phase E persistence is solid. *But* — the resumed save is from a **different difficulty** (Easy) and a different board. The save is per-device, not per-session, and the difficulty selector doesn't show which save would be loaded. Either explicit "Resume Easy / Start New Normal" branching, or a "Currently saved: Easy, Turn 1" indicator under the Resume button, would prevent confusion.

I decide to continue with the resumed Easy game as the canonical playthrough; Normal gets its own pass below.

---

## Game 2 — Easy, The Explorer (resumed save)

### Turn 1 — Opening state

Resources: Mycelium 2, Basalt 2, Chitin 2, Lichen 2, Iron 1, Crystal 0. **Significantly more starting resources than Normal**. I can immediately afford **Outpost** (2B + 1M + 1C), **Fungal Farm** (2M + 2L), or **Beetle Stable** (2C + 2L) on turn 1 — three meaningful build options before I even roll. This is *vastly* better as an opening experience than Normal's "roll then end turn" sleepwalk.

There is also a **mystery Lantern Post pre-built at the top of the board labeled "9"**. I never built it. The on-screen state never explains it. After cross-referencing the source, I believe this is a starting bonus tied to the Explorer character, but the UI gives **zero indication** that this is what you're seeing. **Friction**: a player will spend the whole game wondering "did I sleep through a turn? did the AI place that?" — confusion is the enemy of strategy.

### Turns 2-5 — Engine building

The opening loop is: roll → harvest → trade away surplus → build a structure (usually Lantern, sometimes a Mining Outpost or Fungal Farm). The trade dialog is functional but has friction:

- **Horizontal scrolling is row-locked.** The "Receive" row only scrolls if you swipe at *its* Y. Swiping at the "Give" row Y does nothing. Took me 2 attempts every single trade. After 4 turns I learned the trick — but a new player won't.
- **Lum (Luminary Crystal) is hidden off-screen on the right.** It's the most strategically important receive option and it requires a swipe to reveal. **Bury the most-used choice off-screen — that is bad UX**.
- The dialog **auto-hides "Give" rows that have 0 of that resource**. Smart.

By T5 I had 5 VP and 3 Lanterns, all built on Crust tiles. Each Lantern build also adds **+1 Flare ability to the ability menu** (each Lantern has a Flare). This is interesting in theory — it means more lanterns = more reveals — but it's never explained in-game and the Flares all have identical effect, so it's effectively just `n_lanterns × free reveals`.

### Turns 6-8 — The Bug-Land Tour

This is where I discovered the structural problems one after another:

**Bug 1: "Free trades."** I traded 2 Iron for 1 Crystal on T7. My action counter stayed at 2. I then *also* built a structure that turn. Net actions used: 1, but I performed 2. I tested again on T9 and T10 — same result every time. Trades simply don't decrement the action counter despite the design clearly intending them to. Verified in source: `TradeEngine.executeTrade` just calls `mutate { ... }` without any `useAction()`.

**Bug 2: "Free abilities."** I tapped a Flare on T7, T8, T9. Each time, the action counter stayed at 2. Verified in `StructureEngine.useStructureAbility` — it sets a cooldown on the structure but never decrements the player's action count.

**Bug 3: "Tooltip lies."** The ability menu literally says **"Tap an ability to use it. Uses 1 action."** It does not. The text is a lie.

**Bug 4: "Interior Lantern Waste" (5 occurrences this game including 1 zero-illumination case).** When I build a Lantern Post at a tile whose neighbours are all already lit, the toast reads `"Area illuminated! (0 tiles)"`. The Lantern still gives me +1 VP, so it's not a wasted purchase — but the *advertised* benefit ("Illuminates adjacent tiles") doesn't trigger. A new player would feel cheated. The fact that you can place Lanterns where they do nothing illumination-wise but still claim VP is the core reason the game's optimal strategy collapses to "spam Lanterns."

### Turn 9 — Realisation

By T9 I was at 12/13 VP. My score had gone up almost entirely from Lanterns (each +1 VP). I could win simply by buying one more Lantern. I checked the ability menu: **only 4 Flares** — the Spore Burst from my Fungal Farm was on cooldown (3 turns; used T9 → next available T12, verified in `Player.kt:90`). Cooldowns are real even if action costs aren't.

I tried to chain Spore Burst → Trade → Build for an instant T9 win and the cooldown shut me down. **This is the only point in the entire game where I felt the rules push back on me.** Everything else was friction-free. That single 3-turn cooldown is doing more design work than the entire action system, which is broken.

### Turn 10 — The Win

Rolled 6 (3+3): +1 Beetle Chitin, +1 Iron from production tiles. State: M1 B0 C2 L1 I2 Cry0. Traded 2 Chitin → 1 Crystal (free, of course). Selected the rightmost Basalt Quarry tile ("5"). Built a Lantern Post for 1 Crystal + 1 Iron. **+1 VP → 13/13 → VICTORY.**

Final board log from the win screen reads `"Area illuminated! (0 tiles)"` — even the **winning** Lantern lit nothing. The win came from VP grinding alone.

Achievements unlocked: First Explorer, The Illuminator. Lifetime: 1W/1G • 13 VP. The "Play Again" button worked. **The closing loop is clean.**

### Side note: BACK button bug

Closing the trade dialog via the Android BACK button **exits the entire app** (drops to home screen). I had to relaunch and tap "Resume Game" to recover. Auto-save persistence is solid (mid-turn state including just-completed trade was preserved exactly), but the back-press behaviour is wrong: dismissable modals should absorb BACK and only the root screen should exit the app.

---

## Cross-game learnings (not just this Easy run)

- **Difficulty selector is excellent UX**, but doesn't reveal turn caps or estimated game length.
- **Tile X|Y dice-trigger labels are confusing on mobile** — the numbers are tiny and there's no on-screen tooltip explaining what they mean. New players will not connect "10|10" with "produces only on a roll of 10."
- **Hex selection precision is rough.** I miss-tapped 5+ times this session. Hex hit-boxes appear to be tighter than the visual graphic suggests.
- **Resume Game cleanly resumes mid-turn state.** Phase E persistence is rock-solid.
- **End Turn confirmation dialog with unused actions = good UX safeguard.** This is one of the polish wins of the game.
- **Trade dialog hides exhausted Give rows = good UX.** Small but kind.
- **Build menu hides un-affordable structures.** Mixed: prevents UI clutter, but also hides the meta from new players who don't know what's possible.

