# Task: Debugging Slow Gradle Builds

## Investigation
- [x] Locate and inspect existing Gradle Daemon logs for errors or GC thrashing <!-- id: 0 -->
- [x] Run a build with `--profile` to generate a local HTML timing report <!-- id: 1 -->
- [/] Run a build with `--info` to see if it's hanging on specific download/configure steps <!-- id: 2 -->
- [x] Analyze the Profile Report to pinpoint the bottleneck (indicated initialization hang) <!-- id: 3 -->

## Fixes
- [x] Apply targeted fixes based on profiling (Offline Mode test in progress) <!-- id: 4 -->
- [x] CLEANUP: Deleted corrupt `.gradle/daemon` registry (found "8 busy daemons" error) <!-- id: 5 -->
- [ ] Verify build time improvement <!-- id: 6 -->
