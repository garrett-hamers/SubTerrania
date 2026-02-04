
import subprocess
import sys

print("Python is working")
sys.stdout.flush()

adb_path = r"C:\Users\Garrett\AppData\Local\Android\Sdk\platform-tools\adb.exe"
try:
    print(f"Testing ADB at {adb_path}")
    result = subprocess.run([adb_path, "devices"], capture_output=True, text=True)
    print(f"ADB Exit Code: {result.returncode}")
    print(f"ADB Stdout: {result.stdout}")
    print(f"ADB Stderr: {result.stderr}")
except Exception as e:
    print(f"Error running ADB: {e}")
