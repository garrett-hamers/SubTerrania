
import os
import sys
import time
import subprocess
import re
import xml.etree.ElementTree as ET
import random

# Configuration
ADB_PATH = r"C:\Users\Garrett\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ADB_DEVICE = "emulator-5554"
PACKAGE_NAME = "com.atlyn.subterranea"
ACTIVITY_NAME = ".MainActivity"
DIFFICULTIES = ["Easy", "Normal", "Hard", "Nightmare"]
GAMES_PER_DIFFICULTY = 5
MAX_TURNS = 100
LOG_FILE = "game_stats.csv"

def run_adb(args):
    """Run an ADB command and return output."""
    cmd = [ADB_PATH, "-s", ADB_DEVICE] + args
    try:
        # Increased timeout for ADB commands
        result = subprocess.run(cmd, capture_output=True, text=True, check=True, timeout=20)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        # print(f"ADB Error: {e}") 
        return ""
    except subprocess.TimeoutExpired:
        print("ADB Timeout")
        return ""

def tap(x, y):
    run_adb(["shell", "input", "tap", str(x), str(y)])

def swipe(x1, y1, x2, y2, duration=300):
    run_adb(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration)])

def dump_ui(retries=3):
    """Dump UI hierarchy to local file."""
    for i in range(retries):
        # Dump to device sdcard
        run_adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
        # Pull to local
        run_adb(["pull", "/sdcard/window_dump.xml", "window_dump.xml"])
        
        if os.path.exists("window_dump.xml") and os.path.getsize("window_dump.xml") > 0:
            try:
                tree = ET.parse("window_dump.xml")
                return tree.getroot()
            except ET.ParseError:
                pass
        time.sleep(1)
    return None

def find_bounds(node, text=None, content_desc=None, resource_id=None):
    """Find bounds of a node matching criteria."""
    if node is None:
        return None
        
    # Check current node
    match = True
    if text and text not in node.attrib.get('text', ''):
        match = False
    if content_desc and content_desc not in node.attrib.get('content-desc', ''):
        match = False
    if resource_id and resource_id not in node.attrib.get('resource-id', ''):
        match = False
        
    if match and (text or content_desc or resource_id):
        bounds_str = node.attrib.get('bounds', '')
        # bounds format: [x1,y1][x2,y2]
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds_str)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2

    # Recurse
    for child in node:
        res = find_bounds(child, text, content_desc, resource_id)
        if res:
            return res
    return None

def find_node_by_text(root, text):
    return find_bounds(root, text=text)

def restart_app():
    run_adb(["shell", "am", "force-stop", PACKAGE_NAME])
    time.sleep(1)
    run_adb(["shell", "am", "start", "-n", f"{PACKAGE_NAME}/{ACTIVITY_NAME}"])
    time.sleep(5)

def get_turn_number(root):
    # Find text "Turn X"
    # Traversing all nodes to find text starting with "Turn "
    for elem in root.iter():
        text = elem.attrib.get('text', '')
        if text.startswith('Turn '):
            try:
                return int(text.split(' ')[1])
            except:
                pass
    return 0

def get_event_log(root):
    events = []
    # Just grab any text that looks like a log message or just all text in the log area if identifiable
    # Based on GameScreen.kt, EventLog is at the bottom.
    # We'll just collect all text for analysis? Too noisy.
    # Let's search for specific keywords from ExplorationEvent or Building.
    return events

def play_game(difficulty, game_idx):
    print(f"Starting Game {game_idx} - {difficulty}")
    
    # Needs to be on difficulty screen
    root = dump_ui(retries=5)
    diff_btn = find_node_by_text(root, difficulty)
    
    # Retry finding difficulty if failed (maybe animation)
    if not diff_btn:
        print(f"Waiting for difficulty screen...")
        for _ in range(5):
             time.sleep(2)
             root = dump_ui()
             diff_btn = find_node_by_text(root, difficulty)
             if diff_btn: break
             
    if not diff_btn:
        print(f"Could not find difficulty button: {difficulty}")
        return None

    tap(*diff_btn)
    time.sleep(1)
    
    turn = 0
    start_time = time.time()
    last_tap_pos = None
    
    # Fast loop
    no_action_count = 0
    
    while turn < MAX_TURNS:
        root = dump_ui(retries=1) # optimize retries
        if root is None:
            time.sleep(1)
            continue
            
        # Check Win/Loss
        if find_node_by_text(root, "VICTORY!"):
            print("Victory detected!")
            return {"result": "WIN", "turn": turn, "duration": time.time() - start_time}
        
        # Check if we died or lost (Game Over?) - code mentioned VictoryScreen, not sure about loss screen strictly
        # But let's assume "Play Again" button appears on game over.
        play_again = find_node_by_text(root, "Play Again")
        if play_again:
             print("Game Over detected!")
             # Determine if win or loss based on text?
             # Assuming if "VICTORY" not found but "Play Again" is, maybe loss?
             # Or maybe "VICTORY" is hidden.
             # Wait, VictoryScreen has "Play Again".
             return {"result": "UNKNOWN_END", "turn": turn, "duration": time.time() - start_time}

        new_turn = get_turn_number(root)
        if new_turn > turn:
            turn = new_turn
            print(f"Turn {turn}")
            
        # Priority 1: End Turn if Actions Used (Check "End" button enabled?)
        # Composable buttons don't always map to XML enabled state clearly, but text might be "End"
        
        # Priority 2: Clear Rubble
        clear_btn = find_node_by_text(root, "Clear") # "🧹 Clear"
        if clear_btn:
             # Need to check if we can click it? Just try.
             # In Compose, if disabled, it might still have text but not clickable?
             # Or color changed.
             tap(*clear_btn)
             time.sleep(1)

        # Priority 3: Build
        # Randomly tap map to select a tile
        # Map area roughly middle of screen.
        # dump_ui gives resolution? bounds of root.
        # root bounds usually [0,0][width,height]
        try:
             root_bounds = root.attrib.get('bounds') # [0,0][1080,2400]
             m = re.match(r'\[0,0\]\[(\d+),(\d+)\]', root_bounds)
             w, h = int(m.group(1)), int(m.group(2))
        except:
             w, h = 1080, 2400
        
        # Tap random hex in middle
        rx = random.randint(100, w-100)
        ry = random.randint(h//3, 2*h//3)
        
        # Check if we should explore (Priority: Check for "Explore" button)
        explore_btn = find_node_by_text(root, "Explore")
        if explore_btn:
             tap(*explore_btn)
             # print("Action: Clicked Explore Button")
             # time.sleep(0.2)
             no_action_count = 0
             
        # Tap random hex in middle if no obvious action
        else:
             tap(rx, ry)
             # time.sleep(0.2)
             no_action_count += 1
        
        # Priority: Roll Dice (Check every few loops or if found)
        # To be fast, check root
        
        # Check build button
        build_btn = find_node_by_text(root, "Build")
        if build_btn:
            tap(*build_btn)
            time.sleep(0.5)
            # Pick a structure - just tap generally in the center-ish or look for text
            # Lantern is usually at the top of the list
            tap(w//2, h//2) 
            time.sleep(0.5)
            no_action_count = 0
            
        roll_btn = find_node_by_text(root, "Roll")
        if roll_btn:
            tap(*roll_btn)
            time.sleep(1) 
            no_action_count = 0
            
        end_btn = find_node_by_text(root, "End")
        if end_btn:
            tap(*end_btn)
            time.sleep(0.5)
            no_action_count = 0

        # Dismiss events (Tap center)
        if no_action_count > 5:
             tap(w//2, h//2)
             no_action_count = 0
            
    return {"result": "TIMEOUT", "turn": turn, "duration": time.time() - start_time}

def log_stats(difficulty, game_idx, stats):
    with open(LOG_FILE, "a") as f:
        f.write(f"{difficulty},{game_idx},{stats['result']},{stats['turn']},{stats['duration']:.2f}\n")

if __name__ == "__main__":
    if not os.path.exists(LOG_FILE):
        with open(LOG_FILE, "w") as f:
            f.write("Difficulty,Game,Result,Turns,Duration\n")
            
    for diff in DIFFICULTIES:
        restart_app()
        # Wait for start
        time.sleep(5)
        
        for i in range(1, GAMES_PER_DIFFICULTY + 1):
            stats = play_game(diff, i)
            if stats:
                log_stats(diff, i, stats)
                
            # Prepare for next game
            # If we are at Victory screen, click "Play Again"
            root = dump_ui()
            play_again = find_node_by_text(root, "Play Again")
            if play_again:
                tap(*play_again)
            else:
                # Force restart if stuck
                restart_app()
            
            time.sleep(3)

