"""
corpus_runner_v2.py — hex-aware heuristic bot.

Key upgrades over v1 (corpus_runner.py):
  - Replicates HexMap.kt math (hexSize=70, offset 450/1115 empirically calibrated)
    so we can target specific (q, r) tiles deterministically.
  - Tracks explored hex set via event-log parse ("at (q, r)" patterns).
  - Recenters board at the start of every turn (scale=1, offset=0).
  - Maintains a frontier set: hexes adjacent to explored that are not yet explored.
  - Tries Explore on a frontier hex; falls back to Build on the most recently
    explored producing tile.

We re-use playbench.py's lifecycle + persistence; only the policy changes.
"""
from __future__ import annotations
import sys, math, time, traceback
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import playbench as pb

BOT_KIND = "heuristic_v2_hex"
PROMPT_VERSION = "policy-v2-hex"
MAX_GAMES = 200
WALL_TIMEOUT_SEC = 6 * 3600
MAX_CONSEC_CRASHES = 3
MAX_TURNS_PER_GAME = 30
MAX_STUCK_TURNS = 5

# HexMap.kt math: hexToPixel(q, r) = (size * (sqrt(3)*q + sqrt(3)/2*r) + offX, size * 1.5 * r + offY)
HEX_SIZE = 70.0
HEX_OFFX = 450.0
HEX_OFFY = 1115.0

SQRT3 = math.sqrt(3.0)
SQRT3_2 = SQRT3 / 2.0

# Six axial-coord neighbours for pointy-top hex grid
HEX_DIRS = [(+1, 0), (-1, 0), (0, +1), (0, -1), (+1, -1), (-1, +1)]


def hex_to_screen(q, r):
    x = HEX_SIZE * (SQRT3 * q + SQRT3_2 * r) + HEX_OFFX
    y = HEX_SIZE * 1.5 * r + HEX_OFFY
    return (round(x), round(y))


def neighbours(q, r):
    return [(q + dq, r + dr) for (dq, dr) in HEX_DIRS]


def short_state(s):
    return f"sc={s.get('screen')} t={s.get('turn')}/{s.get('phase')} vp={s.get('vp')}/{s.get('vp_target')}"


def detect_modal(nodes):
    texts = [(n.get("text") or "") for n in nodes]
    # Priority 1: choice dialogs that block other UI (handle BEFORE underlying modals)
    has_consolation = any("No Production!" == t for t in texts) or any("Choose your consolation:" == t for t in texts)
    if has_consolation: return "consolation_choice"
    has_build_struct = any("Build Structure" == t for t in texts)
    has_end_turn_confirm = (any("End turn now?" == t for t in texts)
                            or (any("End Turn" == t for t in texts) and any("Keep Playing" == t for t in texts)))
    has_use_ability = any("Use Ability" == t for t in texts)
    has_trade = any("Trade Resources" == t for t in texts) or any("Open trade menu" in (n.get("desc") or "") for n in nodes)
    has_ok = any("OK" == t for t in texts)
    cancel = any("Cancel" == t for t in texts)
    if has_build_struct: return "build_picker"
    if has_end_turn_confirm: return "end_turn_confirm"
    if has_use_ability: return "use_ability"
    if has_trade and any("✕" == t for t in texts): return "trade_dialog"
    if has_ok and not cancel: return "single_ok"
    if has_ok and cancel: return "event_multi"
    # Generic dialog detection: has ✕ close button at top-right area
    for n in nodes:
        if (n.get("text") or "") == "✕" and n.get("bounds"):
            b = n["bounds"]
            if b["cy"] < 900 and b["cx"] > 700:  # top-right of dialog
                return "generic_dismissable"
    return None


def find_close_x(nodes):
    """Find an X (close) button. Prefer dialog-style ones at top-right."""
    for n in nodes:
        if (n.get("text") or "") == "✕" and n.get("bounds"):
            return n["bounds"]
    # Fallback: contentDescription="Close"
    for n in nodes:
        if "close" in (n.get("desc") or "").lower() and n.get("bounds"):
            return n["bounds"]
    return None


def find_text_bounds(nodes, text):
    for n in nodes:
        if (n.get("text") or "") == text:
            return n.get("bounds")
    return None


def parse_state():
    """Take a fresh dump+screencap and return (state, nodes)."""
    s = pb.load_state()
    gid = s.get("current_game_id")
    label_n = s.get("step_counter", 0)
    full_label = f"{label_n:03d}_probe"
    s["step_counter"] = label_n + 1
    pb.save_state(s)
    pb.screencap(full_label, gid)
    dump = pb.ui_dump(full_label, gid)
    nodes = pb.parse_dump(dump)
    state = pb.extract_state(nodes)
    pb.write_summary(full_label, state, gid)
    return state, nodes


def step(label, do_tap=None, delay=1.5, action_type=None, action_target=None,
         rationale=None, increment_turn=False):
    state = pb.step(label, do_tap=do_tap, delay=delay, action_type=action_type,
                    action_target=action_target, rationale=rationale, increment_turn=increment_turn)
    s = pb.load_state(); gid = s.get("current_game_id")
    nodes = []
    if gid:
        dumps = sorted((pb.DUMPS_DIR / gid).glob("*.xml"), key=lambda p: p.stat().st_mtime)
        if dumps:
            nodes = pb.parse_dump(dumps[-1])
    return state, nodes


# ============================================================
# Per-game memory
# ============================================================
class GameMemory:
    def __init__(self):
        self.explored = {(0, 0)}    # home base is "explored" by default
        self.producers = {}          # (q, r) -> structure name (e.g., "Beetle Farm")
        self.built = {}              # (q, r) -> structure built
        self.last_built_hex = None
        self.frontier_attempted = set()  # (q, r) we've tried to explore this turn
        self.recenter_done = False

    def reset_turn(self):
        self.frontier_attempted.clear()
        self.recenter_done = False

    def record_event(self, event_text, hex_coord):
        """Update explored/producers/built sets based on event log."""
        if not event_text:
            return
        if hex_coord:
            # Mark the hex as explored
            self.explored.add(hex_coord)
            if "Built" in event_text:
                # e.g. "Built Mining Outpost at (1, -2)."
                m = event_text.split("Built", 1)[1].strip()
                struct = m.split(" at ")[0].strip()
                self.built[hex_coord] = struct
                self.last_built_hex = hex_coord
            elif "Explored" in event_text:
                # "Explored Lichen Field at (1, -2)." -> producer
                m = event_text.split("Explored", 1)[1].strip()
                struct = m.split(" at ")[0].strip()
                self.producers[hex_coord] = struct

    def frontier(self):
        """Set of (q, r) adjacent to explored, not yet explored, not yet attempted this turn."""
        f = set()
        for (q, r) in self.explored:
            for n in neighbours(q, r):
                if n not in self.explored and n not in self.frontier_attempted:
                    f.add(n)
        return f

    def producing_revealed_hexes(self):
        """Tiles we've explored that are producers but not yet built upon."""
        return [h for h in self.producers if h not in self.built]


# ============================================================
# Per-state handlers
# ============================================================
def handle_difficulty_select(state, nodes):
    if state.get("resume_button"):
        b = state["discard_button"]
        if b:
            step("discard_save", do_tap=(b["cx"], b["cy"]), delay=1.2,
                 action_type="DISCARD_SAVE", action_target="resume_present", rationale="Clean fresh start")
        return None
    diff = pb.next_difficulty()
    btn = state["difficulty_buttons"].get(pb.DIFF_DISPLAY[diff])
    if not btn:
        return None
    info = pb.start_game(diff, model_id="claude-bot", prompt_version=PROMPT_VERSION, bot_kind=BOT_KIND)
    print(f"  >>> Started game {info['game_id']} ({diff}) bot_kind={BOT_KIND}")
    step("select_diff", do_tap=(540, btn["cy"] + 60), delay=3.5,
         action_type="SELECT_DIFFICULTY", action_target=diff,
         rationale="Round-robin pick", increment_turn=True)
    return info["game_id"]


def handle_modal(state, nodes, modal_kind, runner):
    if modal_kind == "single_ok":
        b = find_text_bounds(nodes, "OK")
        if b:
            step("modal_ok", do_tap=(b["cx"], b["cy"]), delay=1.5,
                 action_type="MODAL_OK", action_target="event", rationale="Dismiss")
        return
    if modal_kind == "end_turn_confirm":
        b = find_text_bounds(nodes, "End Turn")
        if b:
            step("end_confirm", do_tap=(b["cx"], b["cy"]), delay=2.0,
                 action_type="MODAL_CONFIRM", action_target="end_turn", rationale="Confirm end")
        return
    if modal_kind == "event_multi":
        for label_text in ("OK", "Yes", "Confirm"):
            b = find_text_bounds(nodes, label_text)
            if b:
                step("modal_first_choice", do_tap=(b["cx"], b["cy"]), delay=1.5,
                     action_type="MODAL_CHOICE", action_target=label_text, rationale="Pick first")
                return
    if modal_kind == "build_picker":
        # Pick first affordable VP-producing structure
        for struct in ("Mining Outpost", "Lantern Post", "Deep Excavator", "Beetle Stable"):
            sb = find_text_bounds(nodes, struct)
            if not sb: continue
            disabled = False
            for n in nodes:
                t = n.get("text") or ""
                if (t.startswith("Need ") and "more" in t) or t == "Cannot build here":
                    if n.get("bounds") and abs(n["bounds"]["y1"] - sb["y1"]) < 250:
                        disabled = True; break
            if not disabled:
                step(f"build_{struct.replace(' ','_')}",
                     do_tap=(540, sb["cy"] + 100), delay=2.5,
                     action_type="BUILD", action_target=struct,
                     rationale="Cheapest affordable")
                return
        # No affordable -> close
        x_btn = find_close_x(nodes)
        if x_btn:
            step("close_picker", do_tap=(x_btn["cx"], x_btn["cy"]), delay=1.2,
                 action_type="CLOSE_PICKER", action_target="build", rationale="Nothing affordable")
        else:
            step("close_picker_hard", do_tap=(869, 786), delay=1.2,
                 action_type="CLOSE_PICKER_HARD", action_target="build", rationale="X by coord")
        return

    if modal_kind == "consolation_choice":
        # No Production! Lucky 7 consolation dialog. Has 3 colored cards: Scavenge / Hustle / Barter
        # Strategy: tap the first one (Scavenge = +1 random resource — safe choice).
        for label_text in ("Scavenge", "Hustle", "Barter"):
            b = find_text_bounds(nodes, label_text)
            if b:
                step(f"consolation_{label_text}", do_tap=(540, b["cy"]), delay=2.0,
                     action_type="CONSOLATION_CHOICE", action_target=label_text,
                     rationale="Lucky 7 / No Production consolation")
                return
        # Fallback: tap any visible Close
        x_btn = find_close_x(nodes)
        if x_btn:
            step("consolation_x", do_tap=(x_btn["cx"], x_btn["cy"]), delay=1.5,
                 action_type="CONSOLATION_CLOSE", action_target="x", rationale="Couldnt find choices")
        return

    if modal_kind in ("use_ability", "trade_dialog", "generic_dismissable"):
        # Just close any dismissable dialog we don't know how to use productively
        x_btn = find_close_x(nodes)
        if x_btn:
            step(f"close_{modal_kind}", do_tap=(x_btn["cx"], x_btn["cy"]), delay=1.2,
                 action_type="CLOSE_DIALOG", action_target=modal_kind,
                 rationale=f"Dismiss {modal_kind}")
        else:
            step(f"close_{modal_kind}_hard", do_tap=(869, 786), delay=1.2,
                 action_type="CLOSE_DIALOG_HARD", action_target=modal_kind,
                 rationale="X by coord fallback")
        # If we just dismissed Use Ability, mark ability as "used this turn"
        # so we don't reopen it.
        if modal_kind == "use_ability" and runner is not None:
            runner.turn_ability_used = True
        return


def handle_main_action(state, nodes, runner):
    actions = state.get("actions", {})
    mem = runner.mem
    runner.turn_substep_count += 1

    # Loop-guard: force end after too many sub-steps
    if runner.turn_substep_count > 14:
        end_b = find_text_bounds(nodes, "End")
        if end_b:
            step("force_end", do_tap=(end_b["cx"], end_b["cy"]), delay=1.5,
                 action_type="END_TURN_FORCED", action_target="loop_guard",
                 rationale="Sub-step cap")
        return

    # Recenter once per turn so hex math is reliable
    if not mem.recenter_done and state.get("recenter_bounds"):
        rb = state["recenter_bounds"]
        step("recenter", do_tap=(rb["cx"], rb["cy"]), delay=1.0,
             action_type="RECENTER", action_target="board",
             rationale="Reset transform for hex math")
        mem.recenter_done = True
        return

    # Use Ability if enabled (free)
    if not runner.turn_ability_used:
        abil_b = find_text_bounds(nodes, "Ability")
        if abil_b:
            disabled = False
            for n in nodes:
                d = (n.get("desc") or "")
                if "Use" in d and "ability" in d.lower():
                    if "disabled" in d.lower(): disabled = True
                    break
            if not disabled:
                step("use_ability", do_tap=(abil_b["cx"], abil_b["cy"]), delay=2.0,
                     action_type="ABILITY", action_target="passive", rationale="Free")
                runner.turn_ability_used = True
                return

    # If Explore button visible (an unexplored tile is selected) -> explore
    ex_b = find_text_bounds(nodes, "Explore")
    if ex_b:
        step("explore", do_tap=(ex_b["cx"], ex_b["cy"]), delay=2.5,
             action_type="EXPLORE", action_target="frontier", rationale="Reveal")
        runner.turn_explore_done = True
        return

    # If Build is enabled AND we haven't already failed Build this turn -> open picker
    bld = actions.get("Build")
    if bld and bld.get("enabled") and not runner.turn_build_attempted_no_go:
        # Prefer building on a known producer hex if possible (re-select it)
        producers = mem.producing_revealed_hexes()
        if producers and not runner.turn_producer_targeted:
            # Tap the hex we want to build on (most recently explored producer)
            target = producers[-1]
            sx, sy = hex_to_screen(*target)
            step(f"select_producer_{target[0]}_{target[1]}",
                 do_tap=(sx, sy), delay=0.8,
                 action_type="SELECT_HEX", action_target=f"producer({target[0]},{target[1]})",
                 rationale="Re-select known producer for build")
            runner.turn_producer_targeted = True
            return
        step("open_build", do_tap=(bld["bounds"]["cx"], bld["bounds"]["cy"]), delay=2.0,
             action_type="OPEN_BUILD", action_target="selected", rationale="Try build")
        runner.turn_build_attempted_no_go = True  # set immediately
        return

    # Try to target a specific frontier hex by coord
    frontier = list(mem.frontier())
    if frontier:
        # Pick the closest-to-origin frontier hex (cheap heuristic for early game)
        frontier.sort(key=lambda h: abs(h[0]) + abs(h[1]) + abs(h[0] + h[1]))
        target = frontier[0]
        mem.frontier_attempted.add(target)
        sx, sy = hex_to_screen(*target)
        step(f"target_frontier_{target[0]}_{target[1]}",
             do_tap=(sx, sy), delay=0.8,
             action_type="HEX_SELECT", action_target=f"({target[0]},{target[1]})",
             rationale=f"Frontier hex screen=({sx},{sy})")
        return

    # No frontier left to try -> end turn
    end_b = find_text_bounds(nodes, "End")
    if end_b:
        step("end_turn", do_tap=(end_b["cx"], end_b["cy"]), delay=1.5,
             action_type="END_TURN", action_target="", rationale="Frontier exhausted")


def handle_roll(state, nodes, runner):
    runner.mem.reset_turn()
    roll_b = find_text_bounds(nodes, "Roll")
    if roll_b:
        step("roll", do_tap=(roll_b["cx"], roll_b["cy"]), delay=2.5,
             action_type="ROLL", action_target="dice", rationale="Mandatory roll", increment_turn=True)


def handle_game_over(state, nodes):
    outcome = state.get("game_over") or "LOSS"
    final_vp = state.get("vp")
    print(f"  *** Game over: {outcome} VP={final_vp}")
    pb.end_game(outcome, final_vp)


# ============================================================
# Main loop
# ============================================================
class CorpusRunnerV2:
    def __init__(self):
        self.start_time = time.time()
        self.games_completed = 0
        self.consec_crashes = 0
        self.last_vp = 0
        self.no_progress_turns = 0
        self.last_turn_idx = 0
        self.mem = GameMemory()
        # Within-turn flags
        self.turn_substep_count = 0
        self.turn_build_attempted_no_go = False
        self.turn_explore_done = False
        self.turn_ability_used = False
        self.turn_producer_targeted = False

    def reset_turn_state(self):
        self.turn_substep_count = 0
        self.turn_build_attempted_no_go = False
        self.turn_explore_done = False
        self.turn_ability_used = False
        self.turn_producer_targeted = False

    def reset_game_state(self):
        self.no_progress_turns = 0
        self.last_vp = 0
        self.last_turn_idx = 0
        self.mem = GameMemory()
        self.reset_turn_state()

    def run(self):
        print(f"corpus_runner_v2 started @ {pb.now_iso()}")
        orphans = pb.mark_orphans()
        if orphans:
            print(f"  Marked {len(orphans)} orphan game(s) as ABORTED_RESUME")

        while self.games_completed < MAX_GAMES:
            if time.time() - self.start_time > WALL_TIMEOUT_SEC:
                print("Wall timeout reached.")
                break
            if self.consec_crashes >= MAX_CONSEC_CRASHES:
                print("Too many crashes.")
                break
            try:
                self.tick()
            except KeyboardInterrupt:
                print("Interrupted")
                break
            except Exception as ex:
                print(f"  !! Tick exception: {ex}\n{traceback.format_exc()}")
                time.sleep(2)
                self.consec_crashes += 1
                pb.adb("shell", "am", "force-stop", pb.APP)
                time.sleep(1)
                pb.adb("shell", "am", "start", "-n", pb.APP_ACTIVITY)
                time.sleep(3)
                s = pb.load_state()
                if s.get("current_game_id"):
                    pb.end_game("CRASHED", None)
        pb.regenerate_summary()
        print(f"\nDone. {self.games_completed} games completed.")
        return self.games_completed

    def tick(self):
        pb.ensure_subterrania_foreground()
        state, nodes = parse_state()
        s = pb.load_state(); gid = s.get("current_game_id")
        modal_kind = detect_modal(nodes)
        screen = state.get("screen")

        # Track event log -> update memory
        if state.get("latest_event") and state.get("latest_event_hex"):
            self.mem.record_event(state["latest_event"], state["latest_event_hex"])

        # Track turn change
        if state.get("turn") and state["turn"] != self.last_turn_idx:
            cur_vp = state.get("vp") or 0
            if cur_vp > self.last_vp:
                self.no_progress_turns = 0
            else:
                self.no_progress_turns += 1
            self.last_vp = cur_vp
            self.last_turn_idx = state["turn"]
            self.reset_turn_state()
            self.mem.reset_turn()

        # Forfeit if stuck
        if gid and self.no_progress_turns >= MAX_STUCK_TURNS:
            print(f"  ! No VP progress for {self.no_progress_turns} turns; ABORTED_STUCK")
            pb.end_game("ABORTED_STUCK", state.get("vp"))
            self.games_completed += 1
            self.reset_game_state()
            return
        if gid and (state.get("turn") or 0) > MAX_TURNS_PER_GAME + 5:
            print(f"  ! Turn cap exceeded; ABORTED_STUCK")
            pb.end_game("ABORTED_STUCK", state.get("vp"))
            self.games_completed += 1
            self.reset_game_state()
            return

        if modal_kind:
            print(f"  modal: {modal_kind}")
            handle_modal(state, nodes, modal_kind, self)
            return

        if state.get("game_over"):
            handle_game_over(state, nodes)
            self.games_completed += 1
            self.reset_game_state()
            self.consec_crashes = 0
            pb.regenerate_summary()
            return

        if screen == "difficulty_select":
            handle_difficulty_select(state, nodes)
            return

        if state.get("phase") == "ROLL_DICE":
            print(f"  state: {short_state(state)} -> ROLL")
            handle_roll(state, nodes, self)
            return

        if state.get("phase") == "MAIN_ACTION":
            mem = self.mem
            print(f"  state: {short_state(state)} -> main (sub={self.turn_substep_count} explored={len(mem.explored)} frontier={len(mem.frontier())} producers_unbuilt={len(mem.producing_revealed_hexes())})")
            handle_main_action(state, nodes, self)
            return

        print(f"  unknown screen: {short_state(state)}; trying to close any visible dialog")
        # Try to dismiss any visible X / Close button as a last-resort recovery
        x_btn = find_close_x(nodes)
        if x_btn:
            step("unknown_dismiss", do_tap=(x_btn["cx"], x_btn["cy"]), delay=1.2,
                 action_type="UNKNOWN_DISMISS", action_target="visible_x",
                 rationale="Recover from unknown screen")
            return
        time.sleep(2)


if __name__ == "__main__":
    CorpusRunnerV2().run()
