"""
fun_guardrails.py — Automated pass/fail checks for fun-factor metrics.

Reads fun_factor_report.csv (or fun_factor_scored.csv) and validates
that fun metrics meet per-difficulty thresholds. Returns exit code 1
if any guardrail fails.
"""

import csv
import os
import sys
import statistics
from collections import defaultdict

TELEMETRY_DIR = os.path.join(os.path.dirname(__file__), "telemetry_output")

# ============================================================
# GUARDRAIL THRESHOLDS (tunable)
# ============================================================
THRESHOLDS = {
    "easy": {
        "max_frustration_score": 40,
        "min_engagement_score": 40,
        "min_pacing_score": 50,
        "max_build_rejection_turn_rate": 0.35,
        "max_dead_roll_streak": 4,
        "max_turn_limit_loss_rate": 0.15,
        "min_early_engagement": 1.5,
        "min_aha_moments": 0.5,
        "max_wasted_turn_rate": 0.35,
    },
    "normal": {
        "max_frustration_score": 40,
        "min_engagement_score": 40,
        "min_pacing_score": 50,
        "max_build_rejection_turn_rate": 0.35,
        "max_dead_roll_streak": 4,
        "max_turn_limit_loss_rate": 0.25,
        "min_early_engagement": 1.5,
        "min_aha_moments": 0.5,
        "max_wasted_turn_rate": 0.30,
    },
    "hard": {
        "max_frustration_score": 60,
        "min_engagement_score": 35,
        "min_pacing_score": 40,
        "max_build_rejection_turn_rate": 0.40,
        "max_dead_roll_streak": 5,
        "max_turn_limit_loss_rate": 0.60,
        "min_early_engagement": 1.0,
        "min_aha_moments": 0.3,
        "max_wasted_turn_rate": 0.35,
    },
    "nightmare": {
        "max_frustration_score": 75,
        "min_engagement_score": 30,
        "min_pacing_score": 30,
        "max_build_rejection_turn_rate": 0.50,
        "max_dead_roll_streak": 6,
        "max_turn_limit_loss_rate": 1.0,
        "min_early_engagement": 1.0,
        "min_aha_moments": 0.2,
        "max_wasted_turn_rate": 0.40,
    },
}


def load_report():
    """Load the fun factor report CSV."""
    # Try scored version first, fall back to base report
    for filename in ["fun_factor_scored.csv", "fun_factor_report.csv"]:
        path = os.path.join(TELEMETRY_DIR, filename)
        if os.path.exists(path):
            with open(path, "r", newline="") as f:
                rows = list(csv.DictReader(f))
            print(f"Loaded {len(rows)} games from {filename}")
            return rows
    return None


def check_guardrails(rows):
    """Run all guardrail checks. Returns (passes, fails, results)."""
    by_diff = defaultdict(list)
    for r in rows:
        diff = r.get("difficulty", "").lower()
        if diff in THRESHOLDS:
            by_diff[diff].append(r)

    results = []

    for diff_name in ["easy", "normal", "hard", "nightmare"]:
        games = by_diff.get(diff_name, [])
        if not games:
            continue

        thresholds = THRESHOLDS[diff_name]
        n = len(games)
        diff_label = diff_name.upper()

        def avg_float(key):
            vals = [float(g.get(key, 0) or 0) for g in games]
            return statistics.mean(vals) if vals else 0

        def max_int(key):
            vals = [int(float(g.get(key, 0) or 0)) for g in games]
            return max(vals) if vals else 0

        # Build rejection rate
        val = avg_float("build_rejection_turn_rate")
        thresh = thresholds["max_build_rejection_turn_rate"]
        passed = val <= thresh
        results.append((f"{diff_label} build rejection rate ≤ {thresh*100:.0f}%", f"{val*100:.1f}%", passed))

        # Dead roll streak
        val = max_int("max_dead_roll_streak")
        thresh = thresholds["max_dead_roll_streak"]
        passed = val <= thresh
        results.append((f"{diff_label} max dead-roll streak ≤ {thresh}", str(val), passed))

        # Turn-limit loss rate
        tl_losses = sum(1 for g in games if int(float(g.get("lost_to_turn_limit", 0))) == 1)
        tl_rate = tl_losses / n
        thresh = thresholds["max_turn_limit_loss_rate"]
        passed = tl_rate <= thresh
        results.append((f"{diff_label} turn-limit loss rate ≤ {thresh*100:.0f}%", f"{tl_rate*100:.0f}%", passed))

        # Early engagement
        val = avg_float("early_engagement")
        thresh = thresholds["min_early_engagement"]
        passed = val >= thresh
        results.append((f"{diff_label} early engagement ≥ {thresh}", f"{val:.1f}", passed))

        # Wasted turn rate
        val = avg_float("wasted_turn_rate")
        thresh = thresholds["max_wasted_turn_rate"]
        passed = val <= thresh
        results.append((f"{diff_label} wasted turn rate ≤ {thresh*100:.0f}%", f"{val*100:.1f}%", passed))

        # Aha moments
        val = avg_float("aha_moments")
        thresh = thresholds["min_aha_moments"]
        passed = val >= thresh
        results.append((f"{diff_label} aha moments ≥ {thresh}/game", f"{val:.1f}", passed))

        # Composite scores (if available in the data)
        if "frustration_score" in games[0]:
            val = avg_float("frustration_score")
            thresh = thresholds["max_frustration_score"]
            passed = val <= thresh
            results.append((f"{diff_label} frustration score ≤ {thresh}", f"{val:.1f}", passed))

        if "engagement_score" in games[0]:
            val = avg_float("engagement_score")
            thresh = thresholds["min_engagement_score"]
            passed = val >= thresh
            results.append((f"{diff_label} engagement score ≥ {thresh}", f"{val:.1f}", passed))

        if "pacing_score" in games[0]:
            val = avg_float("pacing_score")
            thresh = thresholds["min_pacing_score"]
            passed = val >= thresh
            results.append((f"{diff_label} pacing score ≥ {thresh}", f"{val:.1f}", passed))

    return results


def main():
    rows = load_report()
    if not rows:
        print("ERROR: No fun factor report found in telemetry_output/")
        print("  Run fun_factor_analysis.py first, then optionally fun_factor_pipeline.py")
        sys.exit(1)

    print()
    print("=" * 65)
    print("  FUN-FACTOR GUARDRAIL CHECKS")
    print("=" * 65)
    print()

    results = check_guardrails(rows)

    passes = 0
    fails = 0
    for check, value, passed in results:
        if passed:
            passes += 1
            status = "PASS ✅"
        else:
            fails += 1
            status = "FAIL ❌"
        print(f"  {status}  {check} (actual: {value})")

    print()
    print(f"  {passes} passed, {fails} failed out of {len(results)} guardrails")
    print()

    if fails > 0:
        print("  ⚠ Some guardrails failed. Review the fun factor report for details.")
        sys.exit(1)
    else:
        print("  ✅ All fun-factor guardrails passed!")
        sys.exit(0)


if __name__ == "__main__":
    main()
