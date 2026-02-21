"""
fun_factor_pipeline.py — Parse fun-factor telemetry events and compute composite fun scores.

Reads fun_* telemetry events from device logs (or AutoPlaytest output),
computes Frustration/Engagement/Pacing/Overall fun scores per game,
and outputs CSVs to telemetry_output/.
"""

import csv
import json
import os
import re
import sys
import statistics
from collections import defaultdict

TELEMETRY_DIR = os.path.join(os.path.dirname(__file__), "telemetry_output")

# Scoring weights (tunable)
FRUSTRATION_WEIGHTS = {
    "dead_roll_rate": 30,
    "stuck_turn_streak": 10,
    "build_frustration": 8,
    "turn_limit_loss": 20,
    "cannot_afford": 5,
}

ENGAGEMENT_WEIGHTS = {
    "agency_categories": 0.3,  # multiplied by agency %
    "aha_moments": 15,
    "exploration_rewards": 20,
    "event_participation": 10,
}

PACING_WEIGHTS = {
    "early_vp": 25,
    "variance_bonus": 25,
    "late_game": 25,
    "early_engagement": 5,
}


def parse_telemetry_log(log_path):
    """Parse a telemetry log file (stdout or logcat format) for fun_* events."""
    events = []
    pattern = re.compile(r'GameTelemetry:\s*(\{.+\})')

    with open(log_path, "r") as f:
        for line in f:
            match = pattern.search(line)
            if match:
                try:
                    event = json.loads(match.group(1))
                    if event.get("event", "").startswith("fun_"):
                        events.append(event)
                except json.JSONDecodeError:
                    continue

    return events


def group_events_by_game(events):
    """Group fun events by game. Uses turn resets to detect game boundaries."""
    games = []
    current_game = []
    last_turn = 0

    for event in events:
        turn = event.get("turn", 0)
        if turn < last_turn and current_game:
            games.append(current_game)
            current_game = []
        current_game.append(event)
        last_turn = turn

    if current_game:
        games.append(current_game)

    return games


def compute_fun_scores(game_events):
    """Compute fun scores for a single game's events."""
    dead_rolls = [e for e in game_events if e["event"] == "fun_dead_roll"]
    build_frusts = [e for e in game_events if e["event"] == "fun_build_frustration"]
    stuck_turns = [e for e in game_events if e["event"] == "fun_stuck_turn"]
    aha_moments = [e for e in game_events if e["event"] == "fun_aha_moment"]
    exploration_rewards = [e for e in game_events if e["event"] == "fun_exploration_reward"]
    agency_snapshots = [e for e in game_events if e["event"] == "fun_agency_snapshot"]

    total_turns = max(1, max((e.get("turn", 1) for e in game_events), default=1))

    # Frustration score (0-100, lower = better)
    dead_roll_rate = len(dead_rolls) / total_turns
    max_stuck_streak = max(
        (e.get("details", {}).get("consecutiveStuckTurns", 0) for e in stuck_turns),
        default=0
    )
    build_frust_count = len(build_frusts)
    cannot_afford_count = sum(
        1 for e in game_events
        if e.get("details", {}).get("deficit")
    )

    frustration = (
        dead_roll_rate * FRUSTRATION_WEIGHTS["dead_roll_rate"] +
        max_stuck_streak * FRUSTRATION_WEIGHTS["stuck_turn_streak"] +
        build_frust_count * FRUSTRATION_WEIGHTS["build_frustration"] +
        cannot_afford_count * FRUSTRATION_WEIGHTS["cannot_afford"]
    )
    frustration = min(100, max(0, frustration))

    # Engagement score (0-100, higher = better)
    avg_categories = statistics.mean(
        [e.get("details", {}).get("categoryCount", 0) for e in agency_snapshots]
    ) if agency_snapshots else 0
    agency_pct = min(100, avg_categories / 4.0 * 100)

    engagement = (
        agency_pct * ENGAGEMENT_WEIGHTS["agency_categories"] +
        len(aha_moments) * ENGAGEMENT_WEIGHTS["aha_moments"] +
        (len(exploration_rewards) / max(1, total_turns)) * ENGAGEMENT_WEIGHTS["exploration_rewards"] * 100 +
        ENGAGEMENT_WEIGHTS["event_participation"] * 10  # placeholder without event data
    )
    engagement = min(100, max(0, engagement))

    # Pacing score (simplified without VP curve)
    vp_jumps = [e.get("details", {}).get("vpJump", 0) for e in aha_moments]
    has_early_vp = any(e.get("turn", 99) <= 3 for e in aha_moments)
    pacing = (
        (25 if has_early_vp else 10) +
        min(25, len(vp_jumps) * 8) +
        25  # baseline
    )
    pacing = min(100, max(0, pacing))

    # Overall
    overall = (engagement + pacing - frustration * 0.7) / 2.0 + 30
    overall = min(100, max(0, overall))

    return {
        "total_turns": total_turns,
        "dead_rolls": len(dead_rolls),
        "build_frustrations": build_frust_count,
        "stuck_events": len(stuck_turns),
        "max_stuck_streak": max_stuck_streak,
        "aha_moments": len(aha_moments),
        "exploration_rewards": len(exploration_rewards),
        "agency_snapshots": len(agency_snapshots),
        "avg_action_categories": round(avg_categories, 2),
        "frustration_score": round(frustration, 2),
        "engagement_score": round(engagement, 2),
        "pacing_score": round(pacing, 2),
        "overall_fun_score": round(overall, 2),
    }


def process_log_file(log_path):
    """Process a telemetry log file and output fun-factor CSVs."""
    print(f"Parsing {log_path}...")
    events = parse_telemetry_log(log_path)
    print(f"  Found {len(events)} fun_* events")

    if not events:
        print("  No fun-factor events found. Run the game with fun telemetry enabled.")
        return

    games = group_events_by_game(events)
    print(f"  Detected {len(games)} games")

    results = []
    for i, game_events in enumerate(games):
        scores = compute_fun_scores(game_events)
        scores["game_index"] = i + 1
        results.append(scores)

    # Write per-game CSV
    out_path = os.path.join(TELEMETRY_DIR, "fun_factor_live_scores.csv")
    fieldnames = ["game_index"] + [k for k in results[0].keys() if k != "game_index"]
    with open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)

    print(f"\n  Written: {out_path}")

    # Print summary
    print(f"\n  {'Metric':<25} {'Avg':>8} {'Min':>8} {'Max':>8}")
    print(f"  {'-'*50}")
    for key in ["frustration_score", "engagement_score", "pacing_score", "overall_fun_score"]:
        values = [r[key] for r in results]
        print(f"  {key:<25} {statistics.mean(values):>8.1f} {min(values):>8.1f} {max(values):>8.1f}")

    # Recommendations
    avg_frust = statistics.mean([r["frustration_score"] for r in results])
    avg_eng = statistics.mean([r["engagement_score"] for r in results])
    print(f"\n  Recommendations:")
    if avg_frust > 50:
        print(f"    ⚠ High frustration ({avg_frust:.0f}/100) — reduce dead-roll impact or increase consolation")
    if avg_eng < 40:
        print(f"    ⚠ Low engagement ({avg_eng:.0f}/100) — add more reward events or decision points")


def process_existing_report():
    """Process the existing fun_factor_report.csv to add composite scores."""
    report_path = os.path.join(TELEMETRY_DIR, "fun_factor_report.csv")
    if not os.path.exists(report_path):
        print("No fun_factor_report.csv found. Run fun_factor_analysis.py first.")
        return

    print(f"Processing {report_path} for composite scores...")
    with open(report_path, "r", newline="") as f:
        rows = list(csv.DictReader(f))

    if not rows:
        print("  Empty report file.")
        return

    scored_rows = []
    for row in rows:
        dead_roll_rate = float(row.get("dead_roll_rate", 0))
        max_dead_streak = int(row.get("max_dead_roll_streak", 0))
        wasted_rate = float(row.get("wasted_turn_rate", 0))
        lost_tl = int(row.get("lost_to_turn_limit", 0))
        agency = float(row.get("agency_rate", 0))
        aha = int(row.get("aha_moments", 0))
        early_eng = float(row.get("early_engagement", 0))
        first_vp = int(row.get("first_vp_turn", 0) or 0)

        # Frustration (0-100, lower=better)
        frust = (
            dead_roll_rate * 40 +
            max_dead_streak * 8 +
            wasted_rate * 30 +
            lost_tl * 20
        )
        frust = min(100, max(0, frust))

        # Engagement (0-100, higher=better)
        eng = agency * 60 + aha * 15
        eng = min(100, max(0, eng))

        # Pacing (0-100, higher=better)
        early_bonus = 25 if first_vp <= 3 else (15 if first_vp <= 5 else 5)
        pace = early_bonus + early_eng * 5 + 25
        pace = min(100, max(0, pace))

        # Overall
        overall = (eng + pace - frust * 0.7) / 2.0 + 30
        overall = min(100, max(0, overall))

        row["frustration_score"] = round(frust, 2)
        row["engagement_score"] = round(eng, 2)
        row["pacing_score"] = round(pace, 2)
        row["overall_fun_score"] = round(overall, 2)
        scored_rows.append(row)

    # Write scored report
    out_path = os.path.join(TELEMETRY_DIR, "fun_factor_scored.csv")
    fieldnames = list(scored_rows[0].keys())
    with open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(scored_rows)

    print(f"  Written: {out_path}")

    # Per-difficulty summary
    by_diff = defaultdict(list)
    for r in scored_rows:
        by_diff[r["difficulty"]].append(r)

    print(f"\n  {'Difficulty':<12} {'Frustration':>12} {'Engagement':>12} {'Pacing':>8} {'Overall':>9}")
    print(f"  {'-'*55}")
    for diff in ["easy", "normal", "hard", "nightmare"]:
        games = by_diff.get(diff, [])
        if not games:
            continue
        avg_f = statistics.mean([float(g["frustration_score"]) for g in games])
        avg_e = statistics.mean([float(g["engagement_score"]) for g in games])
        avg_p = statistics.mean([float(g["pacing_score"]) for g in games])
        avg_o = statistics.mean([float(g["overall_fun_score"]) for g in games])
        print(f"  {diff:<12} {avg_f:>12.1f} {avg_e:>12.1f} {avg_p:>8.1f} {avg_o:>9.1f}")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        # Process a raw telemetry log file
        process_log_file(sys.argv[1])
    else:
        # Process existing fun_factor_report.csv
        process_existing_report()
