
import os
import sys
import time
import subprocess
import re
import math
import xml.etree.ElementTree as ET
import random

# Configuration
ADB_PATH = r"C:\Users\Garrett\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ADB_DEVICE = "emulator-5554"
PACKAGE_NAME = "com.atlyn.subterranea"
ACTIVITY_NAME = ".MainActivity"
DIFFICULTIES = ["Easy", "Normal", "Hard", "Nightmare"]
GAMES_PER_DIFFICULTY = 10
MAX_TURNS = 100
LOG_FILE = "game_stats.csv"
DUMP_FILE = "window_dump.xml"

SCREEN_W = 1080
SCREEN_H = 2220

# Hex tiles at distance 2 (Crust layer, first explorable ring)
DIST2_TILES = [
    (2, 0), (1, 1), (0, 2), (-1, 2), (-2, 2), (-2, 1),
    (-2, 0), (-1, -1), (0, -2), (1, -2), (2, -2), (2, -1),
]

# Surface ring at distance 1 (revealed at start, good for building)
DIST1_TILES = [(1, 0), (0, 1), (-1, 1), (-1, 0), (0, -1), (1, -1)]

# Distance 3 tiles (Mantle layer)
DIST3_TILES = [
    (3, 0), (2, 1), (1, 2), (0, 3), (-1, 3), (-2, 3), (-3, 3),
    (-3, 2), (-3, 1), (-3, 0), (-2, -1), (-1, -2), (0, -3),
    (1, -3), (2, -3), (3, -3), (3, -2), (3, -1),
]

# Consolation overlay button labels
CONSOLATION_CHOICES = ["Scavenge", "Hustle", "Barter"]

# ---- ADB helpers ----

def run_adb(args):
    """Run an ADB command and return output."""
    cmd = [ADB_PATH, "-s", ADB_DEVICE] + args
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True, timeout=20)
        return result.stdout.strip()
    except subprocess.CalledProcessError:
        return ""
    except subprocess.TimeoutExpired:
        print("ADB Timeout")
        return ""

def tap(x, y):
    run_adb(["shell", "input", "tap", str(x), str(y)])

def swipe(x1, y1, x2, y2, duration=300):
    run_adb(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration)])

def dump_ui(retries=3):
    """Dump UI hierarchy to local file and parse it."""
    for i in range(retries):
        run_adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
        run_adb(["pull", "/sdcard/window_dump.xml", DUMP_FILE])
        if os.path.exists(DUMP_FILE) and os.path.getsize(DUMP_FILE) > 0:
            try:
                tree = ET.parse(DUMP_FILE)
                return tree.getroot()
            except ET.ParseError:
                pass
        time.sleep(1)
    return None

# ---- UI search helpers ----

def find_bounds(node, text=None, content_desc=None, resource_id=None, exact=True):
    """Find center coords of a node matching criteria.
    exact=True: text must match exactly (==).
    exact=False: text can be a substring.
    """
    if node is None:
        return None

    match = True
    if text is not None:
        node_text = node.attrib.get("text", "")
        if exact:
            if node_text != text:
                match = False
        else:
            if text not in node_text:
                match = False
    if content_desc is not None:
        node_cd = node.attrib.get("content-desc", "")
        if exact:
            if node_cd != content_desc:
                match = False
        else:
            if content_desc not in node_cd:
                match = False
    if resource_id is not None:
        if resource_id not in node.attrib.get("resource-id", ""):
            match = False

    if match and (text is not None or content_desc is not None or resource_id is not None):
        bounds_str = node.attrib.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2

    for child in node:
        res = find_bounds(child, text, content_desc, resource_id, exact)
        if res:
            return res
    return None


def find_all_by_text(node, text, exact=True, results=None):
    """Return list of (cx, cy) for all nodes matching text."""
    if results is None:
        results = []
    if node is None:
        return results
    node_text = node.attrib.get("text", "")
    matched = (node_text == text) if exact else (text in node_text)
    if matched:
        bounds_str = node.attrib.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            results.append(((x1 + x2) // 2, (y1 + y2) // 2))
    for child in node:
        find_all_by_text(child, text, exact, results)
    return results


def has_text(root, text, exact=True):
    """Check if any node contains the given text."""
    return find_bounds(root, text=text, exact=exact) is not None


def all_texts(node, texts=None):
    """Collect all text values in the UI tree."""
    if texts is None:
        texts = []
    if node is None:
        return texts
    t = node.attrib.get("text", "")
    if t:
        texts.append(t)
    for child in node:
        all_texts(child, texts)
    return texts

# ---- Hex coordinate math ----

def hex_to_screen_pixel(q, r, screen_w=SCREEN_W, screen_h=SCREEN_H):
    """Convert axial hex coords to screen pixel coords for tapping."""
    top_pad = 825   # 300dp * 2.75
    bot_pad = 687   # 250dp * 2.75
    canvas_h = screen_h - top_pad - bot_pad  # ~708
    canvas_w = screen_w  # 1080

    offset_x = canvas_w / 2.0   # 540
    offset_y = canvas_h / 2.0 - 50  # 304

    hex_size = 70.0
    x = hex_size * (math.sqrt(3) * q + math.sqrt(3) / 2.0 * r) + offset_x
    y = hex_size * (3.0 / 2.0 * r) + offset_y

    screen_x = int(x)
    screen_y = int(y + top_pad)
    return screen_x, screen_y

# ---- App control ----

def restart_app():
    run_adb(["shell", "am", "force-stop", PACKAGE_NAME])
    time.sleep(1)
    run_adb(["shell", "am", "start", "-n", PACKAGE_NAME + "/" + ACTIVITY_NAME])
    time.sleep(5)

def get_turn_number(root):
    for elem in root.iter():
        text = elem.attrib.get("text", "")
        if text.startswith("Turn "):
            try:
                return int(text.split(" ")[1])
            except (ValueError, IndexError):
                pass
    return 0

# ---- Tile selection helpers ----

def try_tap_tile_and_check(q, r, button_text, sleep_after=0.5):
    """Tap a hex tile at (q,r), wait, dump UI, check if button_text appeared."""
    sx, sy = hex_to_screen_pixel(q, r)
    if sx < 0 or sx > SCREEN_W or sy < 0 or sy > SCREEN_H:
        return None
    tap(sx, sy)
    time.sleep(sleep_after)
    root = dump_ui(retries=1)
    if root is None:
        return None
    btn = find_bounds(root, text=button_text, exact=True)
    if btn:
        return root, btn
    return None


def select_explorable_tile(tile_list):
    """Try tapping tiles from the list until Explore button appears.
    Returns (root, explore_btn_coords) or None."""
    for q, r in tile_list:
        result = try_tap_tile_and_check(q, r, "Explore")
        if result:
            return result
    return None


def select_buildable_tile(tile_list):
    """Try tapping revealed tiles until Build button appears.
    Returns (root, build_btn_coords) or None."""
    for q, r in tile_list:
        result = try_tap_tile_and_check(q, r, "Build")
        if result:
            return result
    return None

# ---- Main game loop ----

def play_game(difficulty, game_idx):
    print("Starting Game %d - %s" % (game_idx, difficulty))

    # Select difficulty
    root = dump_ui(retries=5)
    diff_btn = find_bounds(root, text=difficulty, exact=True)
    if not diff_btn:
        print("Waiting for difficulty screen...")
        for _ in range(5):
            time.sleep(2)
            root = dump_ui()
            diff_btn = find_bounds(root, text=difficulty, exact=True)
            if diff_btn:
                break

    if not diff_btn:
        print("Could not find difficulty button: %s" % difficulty)
        return None

    tap(*diff_btn)
    time.sleep(2)

    turn = 0
    start_time = time.time()
    explore_idx = 0  # cycles through explorable tiles
    all_explorable = DIST2_TILES + DIST3_TILES
    build_attempts_this_turn = 0
    max_build_attempts = 2

    while turn < MAX_TURNS:
        root = dump_ui(retries=2)
        if root is None:
            time.sleep(1)
            continue

        # ---- Check victory ----
        if has_text(root, "VICTORY!", exact=False):
            print("  -> VICTORY on turn %d" % turn)
            return {"result": "WIN", "turn": turn, "duration": time.time() - start_time}

        # ---- Check defeat ----
        play_again = find_bounds(root, text="Play Again", exact=True)
        if play_again and not has_text(root, "VICTORY!", exact=False):
            print("  -> DEFEAT on turn %d" % turn)
            return {"result": "LOSS", "turn": turn, "duration": time.time() - start_time}

        # ---- Update turn ----
        new_turn = get_turn_number(root)
        if new_turn > turn:
            turn = new_turn
            build_attempts_this_turn = 0
            print("  Turn %d" % turn)

        ui_texts = all_texts(root)
        texts_joined = " ".join(ui_texts)

        # ---- Phase: ROLL_DICE ----
        if "ROLL DICE" in texts_joined:
            roll_btn = find_bounds(root, text="Roll", exact=True)
            if roll_btn:
                print("  Rolling dice")
                tap(*roll_btn)
                time.sleep(1.0)
                continue

        # ---- Consolation overlay ----
        consolation_found = False
        for choice in CONSOLATION_CHOICES:
            btn = find_bounds(root, text=choice, exact=True)
            if btn:
                print("  Consolation: %s" % choice)
                tap(*btn)
                time.sleep(0.8)
                consolation_found = True
                break
        if consolation_found:
            continue

        # ---- Interactive event overlay ----
        # Events may show buttons like "OK", "Accept", "Decline", etc.
        for evt_btn_text in ["OK", "Accept", "Dismiss"]:
            evt_btn = find_bounds(root, text=evt_btn_text, exact=True)
            if evt_btn:
                print("  Event: tapped %s" % evt_btn_text)
                tap(*evt_btn)
                time.sleep(0.8)
                break
        else:
            evt_btn = None
        if evt_btn:
            continue

        # ---- Build menu already open ----
        if "Build Structure" in texts_joined:
            built_from_menu = False
            for s in ["Lantern Post", "Mining Outpost", "Fungal Farm",
                       "Beetle Stable", "Deep Excavator", "Crystal Refinery", "Core Anchor"]:
                s_btn = find_bounds(root, text=s, exact=False)
                if s_btn:
                    tap(*s_btn)
                    time.sleep(0.8)
                    root_check = dump_ui(retries=1)
                    if root_check and "Build Structure" not in " ".join(all_texts(root_check)):
                        print("  Built %s" % s)
                        built_from_menu = True
                        break
            if not built_from_menu:
                # Can't afford anything -- dismiss menu
                close_btn = find_bounds(root, text="\u2715", exact=True)
                if close_btn:
                    tap(*close_btn)
                else:
                    tap(50, SCREEN_H // 2)
                time.sleep(0.5)
            continue

        # ---- End-turn dialog already visible ----
        if "End turn now?" in texts_joined:
            confirm = find_bounds(root, text="End Turn", exact=True)
            if not confirm:
                confirm = find_bounds(root, text="End Turn", exact=False)
            if confirm:
                print("  Confirming end turn")
                tap(*confirm)
                time.sleep(1.0)
                continue

        # ---- Phase: MAIN_ACTION ----
        # Check if actions remain (look for "Actions: X" with X > 0)
        actions_left = True
        for t in ui_texts:
            if t.startswith("Actions:"):
                try:
                    count = int(t.split(":")[1].strip())
                    if count <= 0:
                        actions_left = False
                except (ValueError, IndexError):
                    pass

        if not actions_left:
            # No actions left -- end turn
            end_btn = find_bounds(root, text="End", exact=True)
            if end_btn:
                print("  Ending turn (no actions)")
                tap(*end_btn)
                time.sleep(1.0)
                for _ in range(3):
                    root2 = dump_ui(retries=1)
                    if root2:
                        confirm = find_bounds(root2, text="End Turn", exact=True)
                        if not confirm:
                            confirm = find_bounds(root2, text="End Turn", exact=False)
                        if confirm:
                            tap(*confirm)
                            time.sleep(1.0)
                            break
                    time.sleep(0.5)
            continue

        # Actions remain -- try to explore or build
        # Strategy: try exploring first, then building if nothing to explore

        # 1) Try to explore: cycle through explorable tile coords
        explored = False
        attempts = min(len(all_explorable), 6)  # try a few per turn
        for _ in range(attempts):
            q, r = all_explorable[explore_idx % len(all_explorable)]
            explore_idx += 1
            sx, sy = hex_to_screen_pixel(q, r)
            if sx < 10 or sx > SCREEN_W - 10 or sy < 10 or sy > SCREEN_H - 10:
                continue
            tap(sx, sy)
            time.sleep(0.5)
            root2 = dump_ui(retries=1)
            if root2 is None:
                continue
            exp_btn = find_bounds(root2, text="Explore", exact=True)
            if exp_btn:
                print("  Exploring tile (%d, %d)" % (q, r))
                tap(*exp_btn)
                time.sleep(0.8)
                explored = True
                break

        if explored:
            continue

        # 2) Try to build on a revealed surface tile (limit attempts)
        built = False
        if build_attempts_this_turn < max_build_attempts:
            build_attempts_this_turn += 1
            shuffled_surface = list(DIST1_TILES) + [(0, 0)]
            random.shuffle(shuffled_surface)
            for q, r in shuffled_surface:
                sx, sy = hex_to_screen_pixel(q, r)
                tap(sx, sy)
                time.sleep(0.5)
                root2 = dump_ui(retries=1)
                if root2 is None:
                    continue
                build_btn = find_bounds(root2, text="Build", exact=True)
                if build_btn:
                    tap(*build_btn)
                    time.sleep(0.8)
                    # Build menu should appear -- pick first available structure
                    root3 = dump_ui(retries=1)
                    if root3:
                        after_texts = " ".join(all_texts(root3))
                        if "Build Structure" in after_texts:
                            for s in ["Lantern Post", "Mining Outpost", "Fungal Farm",
                                       "Beetle Stable", "Deep Excavator", "Crystal Refinery", "Core Anchor"]:
                                s_btn = find_bounds(root3, text=s, exact=False)
                                if s_btn:
                                    tap(*s_btn)
                                    time.sleep(1.0)
                                    # Check if menu closed (build succeeded)
                                    root4 = dump_ui(retries=1)
                                    if root4 and "Build Structure" not in " ".join(all_texts(root4)):
                                        print("  Building %s at (%d, %d)" % (s, q, r))
                                        built = True
                                        break
                        # Dismiss build menu if still open
                        if not built:
                            close_btn = find_bounds(root3, text="\u2715", exact=True)
                            if close_btn:
                                tap(*close_btn)
                            else:
                                tap(50, SCREEN_H // 2)
                            time.sleep(0.5)
                    if built:
                        break

        if built or explored:
            continue

        # 3) Nothing worked -- end turn
        end_btn = find_bounds(root, text="End", exact=True)
        if end_btn:
            print("  Ending turn (nothing to do)")
            tap(*end_btn)
            time.sleep(1.0)
            # Handle confirmation dialog -- retry a few times
            for _ in range(3):
                root2 = dump_ui(retries=1)
                if root2:
                    # Check if dialog appeared
                    confirm = find_bounds(root2, text="End Turn", exact=True)
                    if confirm:
                        tap(*confirm)
                        time.sleep(1.0)
                        break
                    # Also try substring match
                    confirm2 = find_bounds(root2, text="End Turn", exact=False)
                    if confirm2:
                        tap(*confirm2)
                        time.sleep(1.0)
                        break
                time.sleep(0.5)
            continue

        # Fallback: tap center to dismiss any overlay
        tap(SCREEN_W // 2, SCREEN_H // 2)
        time.sleep(0.5)

    return {"result": "TIMEOUT", "turn": turn, "duration": time.time() - start_time}

# ---- Stats logging ----

def log_stats(difficulty, game_idx, stats):
    with open(LOG_FILE, "a") as f:
        f.write("%s,%d,%s,%d,%.2f\n" % (
            difficulty, game_idx, stats["result"], stats["turn"], stats["duration"]))

# ---- Main entry point ----

if __name__ == "__main__":
    if not os.path.exists(LOG_FILE):
        with open(LOG_FILE, "w") as f:
            f.write("Difficulty,Game,Result,Turns,Duration\n")

    for diff in DIFFICULTIES:
        restart_app()
        time.sleep(5)

        for i in range(1, GAMES_PER_DIFFICULTY + 1):
            stats = play_game(diff, i)
            if stats:
                log_stats(diff, i, stats)

            # Prepare for next game
            root = dump_ui()
            play_again = find_bounds(root, text="Play Again", exact=True)
            if play_again:
                tap(*play_again)
                time.sleep(3)
            else:
                restart_app()
                time.sleep(3)

