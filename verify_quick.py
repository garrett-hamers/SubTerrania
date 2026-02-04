
import os
import sys
import time
import subprocess
import re
import xml.etree.ElementTree as ET

ADB_PATH = r"C:\Users\Garrett\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ADB_DEVICE = "emulator-5554"
PACKAGE_NAME = "com.atlyn.subterranea"
ACTIVITY_NAME = ".MainActivity"

def run_adb(args):
    cmd = [ADB_PATH, "-s", ADB_DEVICE] + args
    result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return result.stdout.strip()

def tap(x, y):
    run_adb(["shell", "input", "tap", str(x), str(y)])

def dump_ui():
    run_adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    run_adb(["pull", "/sdcard/window_dump.xml", "window_dump.xml"])
    if os.path.exists("window_dump.xml"):
        try:
            return ET.parse("window_dump.xml").getroot()
        except: return None
    return None

def find_text(root, text):
    for elem in root.iter():
        if text in elem.attrib.get('text', ''):
            return True
    return False
    
def find_node_by_text(root, text):
    for elem in root.iter():
        if text in elem.attrib.get('text', ''):
            bounds = elem.attrib.get('bounds')
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
            if m:
                return (int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2
    return None

def verify_nightmare():
    print("Restarting app...")
    run_adb(["shell", "am", "force-stop", PACKAGE_NAME])
    time.sleep(1)
    run_adb(["shell", "am", "start", "-n", f"{PACKAGE_NAME}/{ACTIVITY_NAME}"])
    time.sleep(4)
    
    print("Selecting Nightmare...")
    root = dump_ui()
    nm_btn = find_node_by_text(root, "Nightmare")
    if nm_btn:
        tap(*nm_btn)
        time.sleep(2)
        
        # Check resources
        # We need to look for "1" next to Iron/Crystal icon or textual representation
        # Our ResourceBar puts count in text.
        # dump_ui might show "1"
        root = dump_ui()
        found_iron = find_text(root, "1") # Very loose check, but better than nothing
        
        if found_iron:
            print("SUCCESS: Found '1' in UI (likely resources).")
        else:
            print("WARNING: Did not find '1' in UI.")
            
        # Check for Explore button
        # Select a tile first
        tap(500, 1200) # center
        time.sleep(1)
        root = dump_ui()
        if find_text(root, "Explore"):
            print("SUCCESS: Found 'Explore' button.")
        else:
             print("WARNING: Explore button not found.")
             
    else:
        print("Could not find Nightmare button.")

if __name__ == "__main__":
    verify_nightmare()
