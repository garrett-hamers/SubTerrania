"""
playbench.py — Phase P emulator-driven game corpus harness for SubTerrania.

Key design points (refined post-rubber-duck):
- Every turn is committed to SQLite (WAL) BEFORE the LLM picks the next action.
- Mirror append to per-turn JSONL journal so a hard crash leaves usable data.
- Foreground guard: every public action verifies the SubTerrania activity is up first;
  if not, force-stops competing apps and relaunches.
- Resumable: on construct, scan runs.db for orphan rows (ended_at IS NULL),
  mark outcome='ABORTED_RESUME', derive next game_id + next difficulty from
  micro-batch schedule.
- Screenshot/dump/summary in one call (`step()`) keyed by `step_label`.
- Hex coords are LEARNED, not computed: we read "Built X at (q, r)" / "Explored ... at (q, r)"
  from the event log so we don't need to solve the screen->hex math up front.

CLI surface:
    python playbench.py status              # print run-summary + next-up
    python playbench.py start_game [DIFF]   # start a new game (default: micro-batch round-robin)
    python playbench.py step LABEL [tap X Y] [delay S]
                                            # tap (or no-op), screencap, dump, summary, persist
    python playbench.py end_game OUTCOME [final_vp]
    python playbench.py summary             # regenerate summary.md
"""
from __future__ import annotations
import json
import os
import re
import sqlite3
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional, Any
from xml.etree import ElementTree as ET

ADB = r"C:\Android\Sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
APP = "com.atlyn.subterranea"
APP_ACTIVITY = f"{APP}/.MainActivity"
APP_VERSION_CODE = 8  # 1.1.0

PB_DIR = Path(r"C:\Users\ghamers\.copilot\session-state\77e8cfc3-761e-4389-8883-8b1486c326d1\files\playbench")
SHOTS_DIR = PB_DIR / "screenshots"
DUMPS_DIR = PB_DIR / "dumps"
SUMS_DIR  = PB_DIR / "summaries"
RES_DIR   = Path(r"Q:\Repos\SubTerrania\playtest-results\emulator")
DB_PATH   = RES_DIR / "runs.db"
TURN_JSONL = RES_DIR / "turns.jsonl"
GAME_JSONL = RES_DIR / "games.jsonl"
SUMMARY_MD = RES_DIR / "summary.md"
STATE_JSON = RES_DIR / "state.json"  # in-flight game pointer

DIFFICULTIES = ["EASY", "NORMAL", "HARD", "NIGHTMARE"]
DIFF_DISPLAY = {"EASY": "Easy", "NORMAL": "Normal", "HARD": "Hard", "NIGHTMARE": "Nightmare"}
MICRO_BATCH_SIZE = 5
TARGET_PER_DIFFICULTY = 100

# Difficulty -> VP target (must match Difficulty.kt)
VP_TARGET = {"EASY": 13, "NORMAL": 15, "HARD": 19, "NIGHTMARE": 22}
MAX_TURNS_BY_DIFF = {"EASY": 22, "NORMAL": 18, "HARD": 20, "NIGHTMARE": 25}

for d in (SHOTS_DIR, DUMPS_DIR, SUMS_DIR, RES_DIR):
    d.mkdir(parents=True, exist_ok=True)


# ============================================================
# adb helpers
# ============================================================
def adb(*args, capture_text=True, capture_bytes=False) -> Any:
    cmd = [ADB, "-s", DEV, *args]
    if capture_bytes:
        return subprocess.run(cmd, capture_output=True).stdout
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.stdout

def tap(x, y):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))

def key_back():
    adb("shell", "input", "keyevent", "KEYCODE_BACK")

def screencap(label: str, game_id: Optional[str] = None) -> Path:
    base = SHOTS_DIR / (game_id or "spike")
    base.mkdir(parents=True, exist_ok=True)
    out = base / f"{label}.png"
    out.write_bytes(adb("exec-out", "screencap", "-p", capture_bytes=True))
    return out

def ui_dump(label: str, game_id: Optional[str] = None) -> Path:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    base = DUMPS_DIR / (game_id or "spike")
    base.mkdir(parents=True, exist_ok=True)
    out = base / f"{label}.xml"
    adb("pull", "/sdcard/ui.xml", str(out))
    return out

def foreground_app() -> str:
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"topResumedActivity=ActivityRecord\{[^}]*\s(\S+/\S+)", out or "")
    return m.group(1) if m else ""

def ensure_subterrania_foreground():
    """If SubTerrania isn't in front, kill competitor + bring it back. Returns True if had to recover."""
    fg = foreground_app()
    if fg.startswith(APP):
        return False
    # Kill known competitor
    if fg.startswith("com.slidequest"):
        adb("shell", "am", "force-stop", "com.slidequest.app")
        time.sleep(1)
    adb("shell", "am", "start", "-n", APP_ACTIVITY)
    time.sleep(3)
    return True


# ============================================================
# UI dump parsing
# ============================================================
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

def parse_bounds(s):
    m = BOUNDS_RE.match(s or "")
    if not m: return None
    x1, y1, x2, y2 = map(int, m.groups())
    return {"x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "w": x2 - x1, "h": y2 - y1}

def parse_dump(path: Path):
    tree = ET.parse(path)
    nodes = []
    for n in tree.iter("node"):
        text = (n.attrib.get("text") or "").strip()
        desc = (n.attrib.get("content-desc") or "").strip()
        cls  = n.attrib.get("class", "").rsplit(".", 1)[-1]
        clk  = n.attrib.get("clickable", "false") == "true"
        en   = n.attrib.get("enabled", "false") == "true"
        bnds = parse_bounds(n.attrib.get("bounds"))
        if text or desc or clk:
            nodes.append({
                "text": text, "desc": desc, "cls": cls,
                "clickable": clk, "enabled": en, "bounds": bnds,
            })
    return nodes


def extract_state(nodes) -> dict:
    """Pull a structured game state from the parsed dump nodes."""
    state = {
        "screen": "unknown",
        "turn": None,
        "phase": None,    # ROLL_DICE | MAIN_ACTION | DIFFICULTY_SELECT | GAME_OVER | MODAL
        "vp": None, "vp_target": None,
        "resources": {},
        "actions": {},    # name -> {enabled, bounds}
        "selected_tile": None,
        "latest_event": None,
        "latest_event_hex": None,  # (q, r) parsed from event if present
        "game_over": None,  # 'WIN' | 'LOSS' | None
        "modal": None,     # text of any modal dialog
        "modal_buttons": [],  # list of {text, bounds}
        "recenter_bounds": None,
        "difficulty_buttons": {},  # 'Easy'/'Normal'/'Hard'/'Nightmare' -> bounds
        "resume_button": None,     # bounds
        "discard_button": None,    # bounds
    }
    res_keys = ["Mycelium Stalks", "Basalt Blocks", "Beetle Chitin/Tallow",
                "Cave Lichen", "Iron Ore", "Luminary Crystal"]
    res_short = {"Mycelium Stalks":"M","Basalt Blocks":"B","Beetle Chitin/Tallow":"C",
                 "Cave Lichen":"L","Iron Ore":"I","Luminary Crystal":"Cr"}

    for n in nodes:
        t, d = n["text"], n["desc"]
        bnds = n["bounds"]

        # Turn header
        m = re.match(r"^Turn (\d+)$", t)
        if m: state["turn"] = int(m.group(1))

        # VP "0/13"
        m = re.match(r"^(\d+)/(\d+)$", t)
        if m and bnds and bnds["x1"] > 700:  # VP is on the right
            state["vp"] = int(m.group(1))
            state["vp_target"] = int(m.group(2))

        # Phase markers (top-bar text)
        if t == "ROLL DICE": state["phase"] = "ROLL_DICE"
        elif t == "MAIN ACTION": state["phase"] = "MAIN_ACTION"

        # Difficulty selector screen
        if t == "Select Difficulty": state["screen"] = "difficulty_select"
        for diff_name in ("Easy", "Normal", "Hard", "Nightmare"):
            if t == diff_name and bnds:
                # Find the parent card bounds (a wider clickable View around the text)
                state["difficulty_buttons"][diff_name] = bnds

        if "Resume your previous game" in d: state["resume_button"] = bnds
        if "Discard the saved game" in d: state["discard_button"] = bnds

        # Resources: a content-desc match next to a number text
        for rk in res_keys:
            if d == rk and bnds:
                # Find the number text immediately right of this icon (within ~150px)
                for m_ in nodes:
                    if not m_["text"] or not m_["bounds"]: continue
                    mb = m_["bounds"]
                    if abs(mb["y1"] - bnds["y1"]) < 60 and bnds["x2"] - 10 <= mb["x1"] <= bnds["x2"] + 200:
                        if m_["text"].isdigit():
                            state["resources"][res_short[rk]] = int(m_["text"])
                            break

        # Action buttons
        if t in ("Roll", "Build", "Clear", "Trade", "End"):
            # Look for sibling content-desc node for enable state
            tx, ty = bnds["cx"], bnds["cy"]
            enabled = True
            for m_ in nodes:
                if m_["desc"] and m_["bounds"]:
                    mb = m_["bounds"]
                    if abs(mb["cx"] - tx) < 80 and abs(mb["cy"] - ty) < 80:
                        if "disabled" in m_["desc"].lower():
                            enabled = False
                        break
            state["actions"][t] = {"enabled": enabled, "bounds": bnds}

        # Recenter
        if d == "Recenter board": state["recenter_bounds"] = bnds

        # Latest event
        if d.startswith("Latest event:"):
            ev = d[len("Latest event:"):].strip()
            ev = ev.replace(" Tap to view full history.", "").strip()
            state["latest_event"] = ev
            # Try to parse "at (q, r)" hex coord from the event
            m_hex = re.search(r"at\s*\((-?\d+),\s*(-?\d+)\)", ev)
            if m_hex:
                state["latest_event_hex"] = (int(m_hex.group(1)), int(m_hex.group(2)))

        # Game-over screens
        if t in ("VICTORY", "VICTORY!"):
            state["game_over"] = "WIN"; state["screen"] = "game_over"
        if t == "TIME'S UP":
            state["game_over"] = "LOSS"; state["screen"] = "game_over"
        if d == "Replay this map with the same seed":
            state["modal_buttons"].append({"text":"Replay seed","bounds":bnds})

        # Selected tile info — usually has zone tag CRUST/MANTLE/CORE
        if t in ("CRUST", "MANTLE", "CORE"):
            state["selected_tile"] = state["selected_tile"] or {}
            state["selected_tile"]["zone"] = t
        # "Building on: X" tells us what's selected during build picker
        if t.startswith("Building on:"):
            state["selected_tile"] = state["selected_tile"] or {}
            state["selected_tile"]["building_on"] = t.split(":",1)[1].strip()

        # Build picker structure cards
        # Modal detection — heuristic: a clickable=true View containing a heading + OK button
        # We surface common dialog texts that we'll act on
        if t == "OK" and bnds:
            state["modal_buttons"].append({"text":"OK","bounds":bnds})
        if t in ("Cancel", "Confirm", "Yes", "No") and bnds:
            state["modal_buttons"].append({"text":t,"bounds":bnds})

    # Synthesize screen name
    if state["screen"] == "unknown":
        if state["actions"]:
            state["screen"] = "in_game"
        elif state["difficulty_buttons"]:
            state["screen"] = "difficulty_select"

    # If we see Build picker hint phrase, mark it
    return state


def write_summary(label: str, state: dict, game_id: Optional[str]):
    base = SUMS_DIR / (game_id or "spike")
    base.mkdir(parents=True, exist_ok=True)
    (base / f"{label}.json").write_text(json.dumps(state, indent=2), encoding="utf-8")
    return base / f"{label}.json"


# ============================================================
# Database
# ============================================================
SCHEMA = """
CREATE TABLE IF NOT EXISTS runs (
    game_id           TEXT PRIMARY KEY,
    difficulty        TEXT NOT NULL,
    started_at        TEXT NOT NULL,
    ended_at          TEXT,
    outcome           TEXT,
    final_vp          INTEGER,
    vp_target         INTEGER,
    total_turns       INTEGER,
    total_actions     INTEGER,
    wall_seconds      REAL,
    bot_kind          TEXT NOT NULL DEFAULT 'llm_in_loop',
    model_id          TEXT,
    prompt_version    TEXT,
    app_version_code  INTEGER,
    avd_name          TEXT
);
CREATE TABLE IF NOT EXISTS turns (
    game_id              TEXT NOT NULL,
    turn_idx             INTEGER NOT NULL,
    step_label           TEXT NOT NULL,
    captured_at          TEXT NOT NULL,
    state_json           TEXT NOT NULL,
    action_type          TEXT,
    action_target        TEXT,
    llm_rationale        TEXT,
    screenshot_path      TEXT,
    dump_path            TEXT,
    PRIMARY KEY (game_id, turn_idx, step_label)
);
CREATE TABLE IF NOT EXISTS events (
    game_id     TEXT NOT NULL,
    turn_idx    INTEGER NOT NULL,
    captured_at TEXT NOT NULL,
    event_text  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_turns_game ON turns(game_id);
CREATE INDEX IF NOT EXISTS idx_events_game ON events(game_id);
"""

def db_connect():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.executescript(SCHEMA)
    return conn

def now_iso():
    return time.strftime("%Y-%m-%dT%H:%M:%S")


# ============================================================
# Game lifecycle
# ============================================================
def load_state():
    if STATE_JSON.exists():
        return json.loads(STATE_JSON.read_text())
    return {"current_game_id": None, "current_turn_idx": 0, "step_counter": 0}

def save_state(s):
    STATE_JSON.write_text(json.dumps(s, indent=2))

def append_jsonl(path: Path, obj: dict):
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(obj) + "\n")

def mark_orphans():
    conn = db_connect()
    cur = conn.execute("SELECT game_id FROM runs WHERE ended_at IS NULL")
    orphans = [r[0] for r in cur.fetchall()]
    for gid in orphans:
        conn.execute(
            "UPDATE runs SET outcome='ABORTED_RESUME', ended_at=? WHERE game_id=?",
            (now_iso(), gid),
        )
    conn.commit()
    conn.close()
    return orphans

def per_difficulty_counts():
    """Count games per difficulty for the round-robin scheduler.
    Counts ANY terminal outcome (WIN/LOSS/ABORTED_*/CRASHED) so the scheduler
    advances even if the bot keeps aborting at one difficulty."""
    conn = db_connect()
    rows = conn.execute(
        "SELECT difficulty, COUNT(*) FROM runs WHERE outcome IS NOT NULL GROUP BY difficulty"
    ).fetchall()
    conn.close()
    counts = {d: 0 for d in DIFFICULTIES}
    for d, c in rows:
        counts[d] = c
    return counts

def next_difficulty():
    counts = per_difficulty_counts()
    # Micro-batch order: prefer the next difficulty in DIFFICULTIES that has the lowest
    # completed count. Tie-break by DIFFICULTIES order.
    return min(DIFFICULTIES, key=lambda d: (counts[d], DIFFICULTIES.index(d)))

def start_game(difficulty: Optional[str] = None, model_id: str = "claude-opus-4.7-1m-internal",
               prompt_version: str = "v1", bot_kind: str = "llm_in_loop") -> dict:
    # Defensive: if there's an in-flight game, end it as ABORTED_RELAUNCH first
    s = load_state()
    prev_gid = s.get("current_game_id")
    if prev_gid:
        try:
            end_game("ABORTED_RELAUNCH", None)
        except Exception:
            pass
    diff = (difficulty or next_difficulty()).upper()
    assert diff in DIFFICULTIES
    game_id = f"g{int(time.time())}_{uuid.uuid4().hex[:6]}_{diff[0]}"
    conn = db_connect()
    conn.execute(
        """INSERT INTO runs (game_id, difficulty, started_at, vp_target, bot_kind,
           model_id, prompt_version, app_version_code, avd_name)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (game_id, diff, now_iso(), VP_TARGET[diff], bot_kind, model_id, prompt_version,
         APP_VERSION_CODE, "subterranea_test"),
    )
    conn.commit(); conn.close()
    s = load_state()
    s["current_game_id"] = game_id
    s["current_turn_idx"] = 0
    s["step_counter"] = 0
    s["difficulty"] = diff
    s["started_at"] = now_iso()
    save_state(s)
    append_jsonl(GAME_JSONL, {"event":"start", "game_id":game_id, "difficulty":diff,
                              "started_at":now_iso(), "bot_kind":bot_kind})
    return {"game_id": game_id, "difficulty": diff}


def end_game(outcome: str, final_vp: Optional[int] = None):
    s = load_state()
    gid = s.get("current_game_id")
    if not gid: return
    conn = db_connect()
    started = conn.execute("SELECT started_at FROM runs WHERE game_id=?", (gid,)).fetchone()
    started_dt = time.mktime(time.strptime(started[0], "%Y-%m-%dT%H:%M:%S")) if started else time.time()
    wall = time.time() - started_dt
    total_turns = conn.execute(
        "SELECT MAX(turn_idx) FROM turns WHERE game_id=?", (gid,)
    ).fetchone()[0] or 0
    total_actions = conn.execute(
        "SELECT COUNT(*) FROM turns WHERE game_id=? AND action_type IS NOT NULL", (gid,)
    ).fetchone()[0]
    conn.execute(
        """UPDATE runs SET ended_at=?, outcome=?, final_vp=?, total_turns=?,
           total_actions=?, wall_seconds=? WHERE game_id=?""",
        (now_iso(), outcome, final_vp, total_turns, total_actions, wall, gid),
    )
    conn.commit(); conn.close()
    append_jsonl(GAME_JSONL, {"event":"end","game_id":gid,"outcome":outcome,
                              "final_vp":final_vp,"total_turns":total_turns,
                              "wall_seconds":wall,"ended_at":now_iso()})
    s["current_game_id"] = None
    save_state(s)


def step(label: str, do_tap: Optional[tuple] = None, delay: float = 1.5,
         action_type: Optional[str] = None, action_target: Optional[str] = None,
         rationale: Optional[str] = None, increment_turn: bool = False):
    """Execute one step:
      1. Foreground guard.
      2. Optional tap.
      3. Wait `delay` seconds.
      4. Screencap + ui dump.
      5. Parse + extract state.
      6. Persist to runs.db (turns table) + turns.jsonl + summary JSON file.
    Returns the parsed state.
    """
    s = load_state()
    gid = s.get("current_game_id")
    recovered = ensure_subterrania_foreground()
    if recovered:
        delay = max(delay, 2.0)

    if do_tap:
        x, y = do_tap
        tap(x, y)
    time.sleep(delay)

    full_label = f"{s.get('step_counter',0):03d}_{label}"
    s["step_counter"] = s.get("step_counter", 0) + 1
    if increment_turn:
        s["current_turn_idx"] = s.get("current_turn_idx", 0) + 1
    save_state(s)

    shot = screencap(full_label, gid)
    dump = ui_dump(full_label, gid)
    nodes = parse_dump(dump)
    state = extract_state(nodes)
    state["foreground_recovered"] = recovered
    summary_path = write_summary(full_label, state, gid)

    captured = now_iso()
    if gid:
        conn = db_connect()
        conn.execute(
            """INSERT OR REPLACE INTO turns
               (game_id, turn_idx, step_label, captured_at, state_json,
                action_type, action_target, llm_rationale, screenshot_path, dump_path)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (gid, s["current_turn_idx"], full_label, captured, json.dumps(state),
             action_type, action_target, rationale, str(shot), str(dump)),
        )
        if state["latest_event"]:
            conn.execute(
                "INSERT INTO events (game_id, turn_idx, captured_at, event_text) VALUES (?,?,?,?)",
                (gid, s["current_turn_idx"], captured, state["latest_event"]),
            )
        conn.commit(); conn.close()
        append_jsonl(TURN_JSONL, {
            "game_id": gid, "turn_idx": s["current_turn_idx"],
            "step_label": full_label, "captured_at": captured,
            "action_type": action_type, "action_target": action_target,
            "rationale": rationale, "state": state,
        })
    return state


# ============================================================
# Summary generator
# ============================================================
def regenerate_summary():
    conn = db_connect()
    rows = conn.execute(
        """SELECT difficulty,
                  SUM(CASE WHEN outcome='WIN' THEN 1 ELSE 0 END)        AS wins,
                  SUM(CASE WHEN outcome='LOSS' THEN 1 ELSE 0 END)       AS losses,
                  SUM(CASE WHEN outcome LIKE 'ABORTED_%' THEN 1 ELSE 0 END) AS aborts,
                  SUM(CASE WHEN outcome='CRASHED' THEN 1 ELSE 0 END)    AS crashes,
                  AVG(total_turns) AS avg_turns,
                  AVG(final_vp)    AS avg_vp,
                  AVG(wall_seconds)AS avg_wall
           FROM runs
           WHERE outcome IS NOT NULL
           GROUP BY difficulty"""
    ).fetchall()
    total_runs = conn.execute("SELECT COUNT(*) FROM runs").fetchone()[0]
    completed = conn.execute("SELECT COUNT(*) FROM runs WHERE outcome IS NOT NULL").fetchone()[0]
    conn.close()

    md = []
    md.append("# SubTerrania emulator playbench — Phase P\n")
    md.append(f"_Generated {now_iso()} • cohort: `llm_in_loop` • app versionCode {APP_VERSION_CODE} • AVD subterranea_test_\n")
    md.append(f"**Total game records:** {total_runs}  |  **Completed (WIN/LOSS/ABORTED):** {completed}\n")
    md.append("\n## Per-difficulty summary\n")
    md.append("| Difficulty | Wins | Losses | Aborts | Crashes | Avg turns | Avg VP | Avg wall sec |")
    md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for d in DIFFICULTIES:
        r = next((row for row in rows if row[0] == d), None)
        if r:
            md.append(f"| {d} | {r[1] or 0} | {r[2] or 0} | {r[3] or 0} | {r[4] or 0} | "
                      f"{(r[5] or 0):.1f} | {(r[6] or 0):.1f} | {(r[7] or 0):.1f} |")
        else:
            md.append(f"| {d} | 0 | 0 | 0 | 0 | - | - | - |")
    md.append("\n_Cohort note: this is **LLM-in-the-loop play** and is NOT a substitute for "
              "human-representative data. Conclusions about balance / pacing must be triangulated "
              "with the JVM AutoPlaytest / RealStrategicPlaytest harnesses (see `playtest-results/K1_*.md`)._\n")
    SUMMARY_MD.write_text("\n".join(md), encoding="utf-8")
    return SUMMARY_MD


# ============================================================
# CLI
# ============================================================
def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    cmd = sys.argv[1]

    if cmd == "status":
        s = load_state()
        counts = per_difficulty_counts()
        print(f"State: {json.dumps(s, indent=2)}")
        print(f"Per-difficulty completed counts: {counts}")
        print(f"Next difficulty: {next_difficulty()}")
        print(f"Foreground app: {foreground_app()}")
    elif cmd == "init":
        # First-time init: create db schema + mark orphans
        orphs = mark_orphans()
        print(f"Marked {len(orphs)} orphan rows")
        regenerate_summary()
        print(f"Wrote {SUMMARY_MD}")
    elif cmd == "start_game":
        diff = sys.argv[2].upper() if len(sys.argv) > 2 else None
        info = start_game(diff)
        print(json.dumps(info))
    elif cmd == "end_game":
        outcome = sys.argv[2]
        final_vp = int(sys.argv[3]) if len(sys.argv) > 3 else None
        end_game(outcome, final_vp)
        print("ended")
        regenerate_summary()
    elif cmd == "step":
        # step LABEL [tap X Y] [delay S] [--action TYPE] [--target X] [--why TEXT] [--turn]
        label = sys.argv[2]
        rest = sys.argv[3:]
        do_tap = None; delay = 1.5; action_type = None; action_target = None; rationale = None; inc = False
        i = 0
        while i < len(rest):
            a = rest[i]
            if a == "tap":
                do_tap = (float(rest[i+1]), float(rest[i+2])); i += 3
            elif a == "delay":
                delay = float(rest[i+1]); i += 2
            elif a == "--action":
                action_type = rest[i+1]; i += 2
            elif a == "--target":
                action_target = rest[i+1]; i += 2
            elif a == "--why":
                rationale = rest[i+1]; i += 2
            elif a == "--turn":
                inc = True; i += 1
            else:
                print(f"unknown arg: {a}"); sys.exit(2)
        st = step(label, do_tap, delay, action_type, action_target, rationale, inc)
        print(json.dumps({k: v for k, v in st.items() if k not in ("modal_buttons",)}, indent=2, default=str))
    elif cmd == "summary":
        path = regenerate_summary()
        print(f"wrote {path}")
    elif cmd == "fg":
        recovered = ensure_subterrania_foreground()
        print(f"recovered={recovered} fg={foreground_app()}")
    else:
        print(f"unknown cmd: {cmd}"); sys.exit(2)


if __name__ == "__main__":
    main()
