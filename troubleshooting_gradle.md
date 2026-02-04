# Gradle Build Troubleshooting

## Where are the logs?
The user asked: *"Can you tell me where to get the logs for the builder to understand where the time is spent?"*

### 1. Build Profile (Best for "Time Spent")
This is the most user-friendly report. 
- **Command**: `./gradlew assembleDebug --profile`
- **Location**: `f:\code\SubTerrania\build\reports\profile\profile-YYYY-MM-DD-....html`
- **What it shows**: A breakdown of time spent in "Configuration", "Dependency Resolution", and "Task Execution".

### 2. Gradle Daemon Logs (Best for "Freezing/Crashing")
If the build hangs before it even starts (stuck at "Initializing"), check these logs.
- **Location**: `C:\Users\Garrett\.gradle\daemon\<gradle-version>\daemon-<PID>.out.log`
- **Note**: Look for the most recently modified file.

### 3. Verbose Console Output
- **Command**: `./gradlew assembleDebug --info` (or `--debug` for everything)
- **What it shows**: Real-time activity. If it stops printing, that's where it's stuck.

## Diagnosis of Current Issue
The build is hanging at:
`> Starting build in new daemon [memory: 8 GiB]`

This indicates the Java process for Gradle is starting but failing to initialize fully. Common causes:
1.  **Antivirus / Windows Defender**: Scanning the massive JARs or the new Java process. Add `C:\Users\Garrett\.gradle` and the project folder to exclusions.
2.  **Corrupt Cache**: The Gradle distribution might be corrupt.
3.  **File Locking**: Something is holding a lock on the daemon registry.

## Recommended Fixes
1.  **Stop Daemons**: `taskkill /F /IM java.exe` (Already done).
2.  **Add Exclusions**: Exclude project folder and `.gradle` from Antivirus.
3.  **Try Offline**: `./gradlew assembleDebug --offline` (if dependencies are downloaded).

## How to Add Exclusions (Windows Defender)

### Option A: PowerShell (Fastest - Run as Administrator)
Open PowerShell as Administrator and run these two commands:
```powershell
Add-MpPreference -ExclusionPath "C:\Users\Garrett\.gradle"
Add-MpPreference -ExclusionPath "f:\code\SubTerrania"
```

### Option B: Manual Steps (UI)
1.  Open **Windows Security** (search in Start menu).
2.  Go to **Virus & threat protection**.
3.  Under **Virus & threat protection settings**, click **Manage settings**.
4.  Scroll down to **Exclusions** and click **Add or remove exclusions**.
5.  Click **+ Add an exclusion** -> **Folder**.
6.  Select `C:\Users\Garrett\.gradle`.
7.  Repeat for `f:\code\SubTerrania`.
