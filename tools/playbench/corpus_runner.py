"""
corpus_runner.py — autonomous heuristic policy bot driving the emulator.

Goal: produce 30-100+ completed game records per session against SubTerrania 1.1.0.
Same SQLite schema as the LLM-in-the-loop cohort but rows are tagged
bot_kind='heuristic_v1' so the cohorts stay separate in analysis.

Decision tree per UI state:
    difficulty_select:
        - if Resume present -> tap Discard
        - else -> tap next round-robin difficulty
    main_action:
        - if Ability button enabled -> use it (free)
        - if a selected unexplored tile (Explore button visible) -> Explore
        - elif a selected revealed tile + Build enabled -> open picker, pick
          first affordable VP-producing structure (Mining Outpost > Lantern Post > others)
        - elif we still have actions and no recent progress -> probe a fresh
          frontier coord (cycle through a wider grid)
        - else -> End turn (handle confirmation dialog)
    roll_dice:
        - tap Roll
    modal:
        - single OK -> tap OK
        - multi-choice event -> tap first option
        - end-turn confirm (Keep Playing + End Turn) -> tap End Turn
        - build picker -> handled in main_action branch
    game_over:
        - read VICTORY / TIME'S UP -> end_game(WIN/LOSS) -> start next game
    app_dead / wrong-foreground:
        - relaunch + resume

Stop conditions:
    - Reached MAX_GAMES (default: 200)
    - Wall-clock timeout (default: 6 hours)
    - Repeated CRASHED outcomes (3 in a row -> abort)
"""
from __future__ import annotations
import sys, time, json, random, signal, traceback
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import playbench as pb

BOT_KIND = "heuristic_v1"
PROMPT_VERSION = "policy-v1"
MAX_GAMES = 200
WALL_TIMEOUT_SEC = 6 * 3600
MAX_CONSEC_CRASHES = 3
MAX_TURNS_PER_GAME = 30   # hard safety cap (Easy max=22, Nightmare max=25)
MAX_STUCK_TURNS = 5       # if no progress for this many turns, forfeit

# Frontier probe coords — wider grid than v0
PROBE_GRID = [
    (540, 1100), (450,  950), (380, 1180), (620, 1180), (340,  950), (660,  950),
    (540,  900), (540, 1300), (300, 1080), (780, 1080), (380,  830), (700,  830),
    (240, 1080), (840, 1080), (450, 1300), (640, 1300), (380, 1380), (660, 1380),
    (240, 1280), (840, 1280), (300,  830), (780,  830), (450,  790), (640,  790),
]

def short_state(s):
    parts = [
        f"sc={s.get('screen')}",
        f"t={s.get('turn')}/p={s.get('phase')}",
        f"vp={s.get('vp')}/{s.get('vp_target')}",
        f"go={s.get('game_over')}",
    ]
    return " ".join(parts)


def detect_modal(nodes):
    """Return modal kind: 'single_ok' | 'event_multi' | 'end_turn_confirm' | 'build_picker' | None."""
    texts = [(n.get("text") or "") for n in nodes]
    descs = [(n.get("desc") or "") for n in nodes]
    has_build_struct = any("Build Structure" == t for t in texts)
    has_end_turn_confirm = any("End turn now?" == t for t in texts) or (
        any("End Turn" == t for t in texts) and any("Keep Playing" == t for t in texts))
    has_ok = any("OK" == t for t in texts)
    cancel = any("Cancel" == t for t in texts)

    if has_build_struct: return "build_picker"
    if has_end_turn_confirm: return "end_turn_confirm"
    if has_ok and not cancel: return "single_ok"
    if has_ok and cancel: return "event_multi"
    return None


def find_text_bounds(nodes, text):
    for n in nodes:
        if (n.get("text") or "") == text:
            return n.get("bounds")
    return None


def find_desc_bounds(nodes, desc):
    for n in nodes:
        if (n.get("desc") or "") == desc:
            return n.get("bounds")
    return None


def parse_nodes_via_playbench():
    """Convenience: call playbench.step with no tap, just to get a fresh dump+state+nodes."""
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


def step(label, do_tap=None, delay=1.5, action_type=None, action_target=None, rationale=None, increment_turn=False):
    """Wrap playbench.step but parse + return nodes too."""
    state = pb.step(label, do_tap=do_tap, delay=delay,
                    action_type=action_type, action_target=action_target,
                    rationale=rationale, increment_turn=increment_turn)
    # Re-parse nodes from latest dump
    s = pb.load_state(); gid = s.get("current_game_id")
    if gid:
        dumps = sorted((pb.DUMPS_DIR / gid).glob("*.xml"), key=lambda p: p.stat().st_mtime)
        if dumps:
            nodes = pb.parse_dump(dumps[-1])
            return state, nodes
    return state, []


# ============================================================
# Per-state handlers
# ============================================================
def handle_difficulty_select(state, nodes):
    if state.get("resume_button"):
        b = state["discard_button"]
        if b:
            step("discard_save", do_tap=(b["cx"], b["cy"]), delay=1.2,
                 action_type="DISCARD_SAVE", action_target="resume_present", rationale="Clean fresh start")
        return None  # next loop iteration picks difficulty
    diff = pb.next_difficulty()
    btn = state["difficulty_buttons"].get(pb.DIFF_DISPLAY[diff])
    if not btn:
        return None
    info = pb.start_game(diff, model_id=BOT_KIND, prompt_version=PROMPT_VERSION)
    print(f"  >>> Started game {info['game_id']} ({diff})")
    # Tap card center (use card body, slightly below the text)
    step("select_diff", do_tap=(540, btn["cy"] + 60), delay=3.5,
         action_type="SELECT_DIFFICULTY", action_target=diff,
         rationale="Round-robin pick", increment_turn=True)
    return info["game_id"]


def handle_modal(state, nodes, modal_kind, runner=None):
    if modal_kind == "single_ok":
        b = find_text_bounds(nodes, "OK")
        if b:
            x, y = b["cx"], b["cy"]
            step("modal_ok", do_tap=(x, y), delay=1.5,
                 action_type="MODAL_OK", action_target="event", rationale="Dismiss")
        return
    if modal_kind == "end_turn_confirm":
        b = find_text_bounds(nodes, "End Turn")
        if b:
            x, y = b["cx"], b["cy"]
            step("end_confirm", do_tap=(x, y), delay=2.0,
                 action_type="MODAL_CONFIRM", action_target="end_turn", rationale="Confirm end")
        return
    if modal_kind == "event_multi":
        # Pick first OK/Yes-ish button. Fallback to OK.
        for label_text in ("OK", "Yes", "Confirm"):
            b = find_text_bounds(nodes, label_text)
            if b:
                step("modal_first_choice", do_tap=(b["cx"], b["cy"]), delay=1.5,
                     action_type="MODAL_CHOICE", action_target=label_text, rationale="Pick first")
                return
    if modal_kind == "build_picker":
        # Always set the flag once we touch the picker — prevents re-opening this turn
        # whether we successfully build or close it.
        if runner is not None:
            runner.turn_build_attempted_no_go = True
        # Pick first affordable VP-producing structure
        for struct in ("Mining Outpost", "Lantern Post", "Deep Excavator", "Beetle Stable"):
            sb = find_text_bounds(nodes, struct)
            if not sb: continue
            disabled = False
            for n in nodes:
                t = n.get("text") or ""
                if t.startswith("Need ") and "more" in t and n.get("bounds"):
                    if abs(n["bounds"]["y1"] - sb["y1"]) < 250:
                        disabled = True; break
                if t == "Cannot build here" and n.get("bounds"):
                    if abs(n["bounds"]["y1"] - sb["y1"]) < 250:
                        disabled = True; break
            if not disabled:
                step(f"build_{struct.replace(' ','_')}",
                     do_tap=(540, sb["cy"] + 100), delay=2.0,
                     action_type="BUILD", action_target=struct,
                     rationale="Cheapest affordable")
                return
        # No affordable -> close via X.
        x_btn = find_text_bounds(nodes, "✕")
        if x_btn:
            step("close_picker", do_tap=(x_btn["cx"], x_btn["cy"]), delay=1.2,
                 action_type="CLOSE_PICKER", action_target="build", rationale="Nothing affordable")
        else:
            step("close_picker_hard", do_tap=(869, 786), delay=1.2,
                 action_type="CLOSE_PICKER_HARD", action_target="build", rationale="X by coord")
        return


# Per-game memory: which probe coords have already been tried this turn
def handle_main_action(state, nodes, runner):
    """Choose next action given current main-action state + within-turn flags."""
    actions = state.get("actions", {})
    runner.turn_substep_count += 1

    # Force End turn if we've taken too many sub-steps this turn (loop guard)
    if runner.turn_substep_count > 12:
        end_b = find_text_bounds(nodes, "End")
        if end_b:
            step("force_end_loop", do_tap=(end_b["cx"], end_b["cy"]), delay=1.5,
                 action_type="END_TURN_FORCED", action_target="loop_guard",
                 rationale="Sub-step cap exceeded")
        return

    # Use Ability if enabled (free, never wastes a turn) — only once per turn though
    if not runner.turn_ability_used:
        abil_b = find_text_bounds(nodes, "Ability")
        if abil_b:
            abil_disabled = False
            for n in nodes:
                d = (n.get("desc") or "")
                if "Use" in d and "ability" in d.lower():
                    if "disabled" in d.lower(): abil_disabled = True
                    break
            if not abil_disabled:
                step("use_ability", do_tap=(abil_b["cx"], abil_b["cy"]), delay=2.0,
                     action_type="ABILITY", action_target="passive", rationale="Free action")
                runner.turn_ability_used = True
                return

    # If Explore button visible (an unexplored tile is selected), explore now.
    ex_b = find_text_bounds(nodes, "Explore")
    if ex_b:
        step("explore", do_tap=(ex_b["cx"], ex_b["cy"]), delay=2.5,
             action_type="EXPLORE", action_target="frontier", rationale="Reveal tile")
        runner.turn_explore_done = True
        return

    # If Build is enabled AND we haven't already failed Build this turn, try opening picker
    bld = actions.get("Build")
    if bld and bld.get("enabled") and not runner.turn_build_attempted_no_go:
        step("open_build", do_tap=(bld["bounds"]["cx"], bld["bounds"]["cy"]), delay=2.0,
             action_type="OPEN_BUILD", action_target="selected", rationale="Try to build")
        return

    # Probe a fresh frontier coord
    if runner.frontier_index < len(PROBE_GRID):
        sx, sy = PROBE_GRID[runner.frontier_index]
        step(f"probe_{sx}_{sy}", do_tap=(sx, sy), delay=0.8,
             action_type="PROBE", action_target=f"({sx},{sy})",
             rationale=f"Probe frontier #{runner.frontier_index}")
        runner.frontier_index += 1
        return

    # Out of probes — end turn
    end_b = find_text_bounds(nodes, "End")
    if end_b:
        step("end_turn", do_tap=(end_b["cx"], end_b["cy"]), delay=1.5,
             action_type="END_TURN", action_target="", rationale="Exhausted probes")


def handle_roll(state, nodes):
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
class CorpusRunner:
    def __init__(self):
        self.start_time = time.time()
        self.games_completed = 0
        self.consec_crashes = 0
        self.last_vp = 0
        self.no_progress_turns = 0
        self.frontier_index = 0
        self.last_turn_idx = 0
        # Within-turn state (reset every turn)
        self.turn_substep_count = 0
        self.turn_build_attempted_no_go = False
        self.turn_explore_done = False
        self.turn_ability_used = False

    def reset_turn_state(self):
        self.turn_substep_count = 0
        self.turn_build_attempted_no_go = False
        self.turn_explore_done = False
        self.turn_ability_used = False
        self.frontier_index = 0

    def reset_game_state(self):
        self.no_progress_turns = 0
        self.last_vp = 0
        self.last_turn_idx = 0
        self.reset_turn_state()

    def run(self):
        print(f"corpus_runner started @ {pb.now_iso()}")
        # On startup, mark orphans
        orphans = pb.mark_orphans()
        if orphans:
            print(f"  Marked {len(orphans)} orphan game(s) as ABORTED_RESUME")

        while self.games_completed < MAX_GAMES:
            if time.time() - self.start_time > WALL_TIMEOUT_SEC:
                print("Wall timeout reached; stopping.")
                break
            if self.consec_crashes >= MAX_CONSEC_CRASHES:
                print("Too many consecutive crashes; stopping.")
                break
            try:
                self.tick()
            except KeyboardInterrupt:
                print("Interrupted by user")
                break
            except Exception as ex:
                print(f"  !! Tick exception: {ex}\n{traceback.format_exc()}")
                time.sleep(2)
                self.consec_crashes += 1
                # Try to recover by relaunching
                pb.adb("shell", "am", "force-stop", pb.APP)
                time.sleep(1)
                pb.adb("shell", "am", "start", "-n", pb.APP_ACTIVITY)
                time.sleep(3)
                # End any in-flight game as CRASHED
                s = pb.load_state()
                if s.get("current_game_id"):
                    pb.end_game("CRASHED", None)
        # Final summary
        pb.regenerate_summary()
        print(f"\nDone. {self.games_completed} games completed.")
        return self.games_completed

    def tick(self):
        pb.ensure_subterrania_foreground()
        state, nodes = parse_nodes_via_playbench()
        s = pb.load_state()
        gid = s.get("current_game_id")
        # Detect modal first (modals override main UI)
        modal_kind = detect_modal(nodes)
        screen = state.get("screen")

        # Track no-progress
        if state.get("turn") and state["turn"] != self.last_turn_idx:
            # Turn advanced
            cur_vp = state.get("vp") or 0
            if cur_vp > self.last_vp:
                self.no_progress_turns = 0
            else:
                self.no_progress_turns += 1
            self.last_vp = cur_vp
            self.last_turn_idx = state["turn"]
            self.reset_turn_state()  # fresh turn -> reset within-turn flags

        # Forfeit if stuck
        if gid and self.no_progress_turns >= MAX_STUCK_TURNS:
            print(f"  ! No VP progress for {self.no_progress_turns} turns; ending as ABORTED_STUCK")
            pb.end_game("ABORTED_STUCK", state.get("vp"))
            self.games_completed += 1
            self.reset_game_state()
            return
        # Hard safety: don't spend forever in one game
        if gid and (state.get("turn") or 0) > MAX_TURNS_PER_GAME + 5:
            print(f"  ! Turn cap exceeded; ending as ABORTED_STUCK")
            pb.end_game("ABORTED_STUCK", state.get("vp"))
            self.games_completed += 1
            self.reset_game_state()
            return

        if modal_kind:
            print(f"  modal: {modal_kind}")
            handle_modal(state, nodes, modal_kind, runner=self)
            return

        if state.get("game_over"):
            handle_game_over(state, nodes)
            self.games_completed += 1
            self.reset_game_state()
            self.consec_crashes = 0
            # Refresh summary after every completed game
            pb.regenerate_summary()
            return

        if screen == "difficulty_select":
            handle_difficulty_select(state, nodes)
            return

        if state.get("phase") == "ROLL_DICE":
            print(f"  state: {short_state(state)} -> ROLL")
            handle_roll(state, nodes)
            return

        if state.get("phase") == "MAIN_ACTION":
            print(f"  state: {short_state(state)} -> main action (probe={self.frontier_index} sub={self.turn_substep_count} build_no_go={self.turn_build_attempted_no_go})")
            handle_main_action(state, nodes, self)
            return

        # Unknown screen
        print(f"  unknown screen: {short_state(state)}; waiting")
        time.sleep(2)


if __name__ == "__main__":
    CorpusRunner().run()
