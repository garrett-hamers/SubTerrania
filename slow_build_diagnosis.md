# Slow Build Diagnosis & Fix

## The Problem
You asked why the build takes 12-14 minutes and why it got *slower* after optimization.

## The Root Cause: Hard Drive (HDD) Bottleneck
I inspected your system storage configuration:
- **F: Drive (Project Location)**: Seagate ST4000DM004 (**Mechanical HDD**)
- **C: Drive**: Samsung 970 EVO Plus (**NVMe SSD**)

**Impact**: 
- **Mechanical HDDs** are very slow at reading/writing thousands of small files (which Gradle does).
- **Parallel Builds** (which I enabled) hurt HDD performance because multiple threads try to access the disk at once, causing the physical head to "thrash" back and forth. This explains why it went from 12m to 14m.

## Immediate Action Taken
1.  **Reverted Parallel Builds**: I set `org.gradle.parallel=false` in `gradle.properties`. This stops the thrashing and should return build times to ~12 minutes.
2.  **Antivirus Exclusions**: These are still good to keep.

## Recommended Fix (Critical)
To get build times down to **1-2 minutes**, you **MUST move the project to your SSD**.

### Steps:
1.  Close Android Studio & Emulator.
2.  Move the folder `f:\code\SubTerrania` to `C:\code\SubTerrania` (or similar).
3.  Open the project from the new C: location.
4.  (Optional) Re-enable `org.gradle.parallel=true` in `gradle.properties` (Parallel builds fly on SSDs).

This is the single biggest performance upgrade you can make.
