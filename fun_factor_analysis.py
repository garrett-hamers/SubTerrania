"""
fun_factor_analysis.py — Analyze existing telemetry data for fun-factor signals.

Reads telemetry_output/ CSVs and extracts frustration, pacing, agency, and reward
metrics. Outputs fun_factor_report.csv and prints a human-readable summary.
"""

import csv
import os
import statistics
from collections import defaultdict

TELEMETRY_DIR = os.path.join(os.path.dirname(__file__), "telemetry_output")

# --- Data Loading ---

def load_csv(filename):
    path = os.path.join(TELEMETRY_DIR, filename)
    with open(path, "r", newline="") as f:
        return list(csv.DictReader(f))

def load_all():
    games = load_csv("telemetry_games.csv")
    turns = load_csv("telemetry_turns.csv")
    actions = load_csv("telemetry_actions.csv")
    rejections = load_csv("telemetry_rejections.csv")
    return games, turns, actions, rejections

# --- Metric Computation ---

def compute_game_metrics(games, turns, actions, rejections):
    """Compute per-game fun-factor metrics."""
    turns_by_game = defaultdict(list)
    for t in turns:
        turns_by_game[t["game_id"]].append(t)

    actions_by_game_turn = defaultdict(lambda: defaultdict(list))
    for a in actions:
        actions_by_game_turn[a["game_id"]][a["turn"]].append(a)

    rejections_by_game = defaultdict(list)
    for r in rejections:
        rejections_by_game[r["game_id"]].append(r)

    results = []
    for game in games:
        gid = game["game_id"]
        difficulty = game.get("difficulty", "unknown")
        won = game.get("won", "false") == "true"
        turns_played = int(game.get("turns_played", 0))
        final_vp = int(game.get("final_vp", 0))
        vp_target = int(game.get("vp_target", 0))
        end_reason = game.get("end_reason", "")

        game_turns = sorted(turns_by_game[gid], key=lambda t: int(t["turn"]))
        game_rejections = rejections_by_game[gid]

        if not game_turns or turns_played == 0:
            continue

        # --- Frustration metrics ---

        # Build rejection rate: turns with at least one build rejection / total turns
        turns_with_build_reject = set()
        total_build_rejections = 0
        explore_cap_events = 0
        cannot_afford_events = 0
        trade_rejections = 0
        for r in game_rejections:
            if r["event"] == "build":
                turns_with_build_reject.add(r["turn"])
                total_build_rejections += int(r["count"])
            if r["reason_code"] == "explore_cap_reached":
                explore_cap_events += int(r["count"])
            if r["reason_code"] == "cannot_afford":
                cannot_afford_events += int(r["count"])
            if r["event"] == "trade":
                trade_rejections += int(r["count"])

        build_rejection_turn_rate = len(turns_with_build_reject) / turns_played if turns_played > 0 else 0

        # Dead roll streaks (roll that produced 0 resources, approximated by resources_delta_total == 0 or negative)
        dead_rolls = []
        current_dead_streak = 0
        max_dead_streak = 0
        for t in game_turns:
            res_delta = int(t.get("resources_delta_total", 0))
            if res_delta <= 0:
                current_dead_streak += 1
                max_dead_streak = max(max_dead_streak, current_dead_streak)
            else:
                if current_dead_streak > 0:
                    dead_rolls.append(current_dead_streak)
                current_dead_streak = 0
        if current_dead_streak > 0:
            dead_rolls.append(current_dead_streak)

        total_dead_roll_turns = sum(1 for t in game_turns if int(t.get("resources_delta_total", 0)) <= 0)
        dead_roll_rate = total_dead_roll_turns / turns_played if turns_played > 0 else 0

        # Wasted turns: turns with 0 VP progress and negative or zero resource delta
        wasted_turns = sum(
            1 for t in game_turns
            if int(t.get("vp_delta", 0)) == 0 and int(t.get("resources_delta_total", 0)) <= 0
        )
        wasted_turn_rate = wasted_turns / turns_played if turns_played > 0 else 0

        # Turn-limit loss
        lost_to_turn_limit = 1 if end_reason == "turn_limit" else 0

        # --- Pacing metrics ---

        # VP accumulation curve: VP per turn
        vp_per_turn = [int(t.get("vp_delta", 0)) for t in game_turns]

        # Turn of first VP gain
        first_vp_turn = None
        for t in game_turns:
            if int(t.get("vp_delta", 0)) > 0:
                first_vp_turn = int(t["turn"])
                break

        # VP in first third / middle third / last third
        third = max(1, turns_played // 3)
        early_vp = sum(int(t.get("vp_delta", 0)) for t in game_turns[:third])
        mid_vp = sum(int(t.get("vp_delta", 0)) for t in game_turns[third:2*third])
        late_vp = sum(int(t.get("vp_delta", 0)) for t in game_turns[2*third:])

        # Action density: average actions per turn
        action_counts = [int(t.get("actions_taken", 0)) for t in game_turns]
        avg_actions_per_turn = statistics.mean(action_counts) if action_counts else 0

        # --- Agency metrics ---

        # Turns with multiple action types
        action_types_per_turn = defaultdict(set)
        for a in actions:
            if a["game_id"] == gid and int(a.get("successes", 0)) > 0:
                action_types_per_turn[a["turn"]].add(a["event"])
        multi_action_turns = sum(1 for types in action_types_per_turn.values() if len(types) >= 2)
        agency_rate = multi_action_turns / turns_played if turns_played > 0 else 0

        # --- Reward metrics ---

        # Aha moments: turns with VP delta >= 2
        aha_moments = sum(1 for t in game_turns if int(t.get("vp_delta", 0)) >= 2)

        # VP velocity by phase
        early_vp_rate = early_vp / third if third > 0 else 0
        late_vp_rate = late_vp / max(1, turns_played - 2 * third)

        # Anticlimax: won with VP significantly over target
        overshoot = final_vp - vp_target if won and vp_target > 0 else 0

        # Early game engagement: actions in turns 1-3
        early_turns = [t for t in game_turns if int(t["turn"]) <= 3]
        early_engagement = statistics.mean([int(t.get("actions_taken", 0)) for t in early_turns]) if early_turns else 0

        # Late game: last 3 turns VP
        late_turns = game_turns[-3:] if len(game_turns) >= 3 else game_turns
        late_game_vp = sum(int(t.get("vp_delta", 0)) for t in late_turns)

        results.append({
            "game_id": gid,
            "difficulty": difficulty,
            "won": won,
            "turns_played": turns_played,
            "final_vp": final_vp,
            "vp_target": vp_target,
            "end_reason": end_reason,
            # Frustration
            "build_rejection_turn_rate": round(build_rejection_turn_rate, 3),
            "total_build_rejections": total_build_rejections,
            "max_dead_roll_streak": max_dead_streak,
            "dead_roll_rate": round(dead_roll_rate, 3),
            "wasted_turns": wasted_turns,
            "wasted_turn_rate": round(wasted_turn_rate, 3),
            "explore_cap_hits": explore_cap_events,
            "cannot_afford_events": cannot_afford_events,
            "trade_rejections": trade_rejections,
            "lost_to_turn_limit": lost_to_turn_limit,
            # Pacing
            "first_vp_turn": first_vp_turn,
            "early_vp": early_vp,
            "mid_vp": mid_vp,
            "late_vp": late_vp,
            "avg_actions_per_turn": round(avg_actions_per_turn, 2),
            # Agency
            "multi_action_type_turns": multi_action_turns,
            "agency_rate": round(agency_rate, 3),
            # Rewards
            "aha_moments": aha_moments,
            "early_vp_rate": round(early_vp_rate, 3),
            "late_vp_rate": round(late_vp_rate, 3),
            "overshoot_vp": overshoot,
            "early_engagement": round(early_engagement, 2),
            "late_game_vp": late_game_vp,
        })

    return results


def aggregate_by_difficulty(game_metrics):
    """Aggregate fun-factor metrics per difficulty."""
    by_diff = defaultdict(list)
    for m in game_metrics:
        by_diff[m["difficulty"]].append(m)

    summaries = []
    for diff in ["easy", "normal", "hard", "nightmare"]:
        games = by_diff.get(diff, [])
        if not games:
            continue
        n = len(games)

        def avg(key): return round(statistics.mean([g[key] for g in games]), 3)
        def med(key): return round(statistics.median([g[key] for g in games]), 1)
        def mx(key): return max(g[key] for g in games)

        win_rate = sum(1 for g in games if g["won"]) / n
        turn_limit_rate = sum(g["lost_to_turn_limit"] for g in games) / n

        first_vp_turns = [g["first_vp_turn"] for g in games if g["first_vp_turn"] is not None]

        summaries.append({
            "difficulty": diff,
            "games": n,
            "win_rate": round(win_rate, 3),
            "turn_limit_loss_rate": round(turn_limit_rate, 3),
            "avg_turns": avg("turns_played"),
            "avg_build_rejection_rate": avg("build_rejection_turn_rate"),
            "avg_dead_roll_rate": avg("dead_roll_rate"),
            "max_dead_roll_streak": mx("max_dead_roll_streak"),
            "avg_wasted_turn_rate": avg("wasted_turn_rate"),
            "avg_wasted_turns": avg("wasted_turns"),
            "median_first_vp_turn": round(statistics.median(first_vp_turns), 1) if first_vp_turns else None,
            "avg_agency_rate": avg("agency_rate"),
            "avg_aha_moments": avg("aha_moments"),
            "avg_early_engagement": avg("early_engagement"),
            "avg_explore_cap_hits": avg("explore_cap_hits"),
            "avg_cannot_afford": avg("cannot_afford_events"),
            "avg_early_vp": avg("early_vp"),
            "avg_mid_vp": avg("mid_vp"),
            "avg_late_vp": avg("late_vp"),
            "avg_overshoot": avg("overshoot_vp"),
        })

    return summaries


def print_summary(diff_summaries, game_metrics):
    """Print a human-readable fun-factor analysis."""
    print("=" * 70)
    print("  SUBTERRANEA FUN-FACTOR ANALYSIS REPORT")
    print("=" * 70)
    print()

    for s in diff_summaries:
        diff = s["difficulty"].upper()
        print(f"--- {diff} ({s['games']} games) ---")
        print(f"  Win rate:              {s['win_rate']*100:.1f}%")
        print(f"  Turn-limit loss rate:  {s['turn_limit_loss_rate']*100:.1f}%")
        print(f"  Avg turns:             {s['avg_turns']}")
        print()

        # Frustration
        print(f"  FRUSTRATION:")
        print(f"    Build rejection rate: {s['avg_build_rejection_rate']*100:.1f}% of turns")
        print(f"    Dead-roll rate:       {s['avg_dead_roll_rate']*100:.1f}% of turns")
        print(f"    Max dead-roll streak: {s['max_dead_roll_streak']} consecutive")
        print(f"    Wasted turn rate:     {s['avg_wasted_turn_rate']*100:.1f}%")
        print(f"    Explore cap hits/game:{s['avg_explore_cap_hits']}")
        print(f"    Can't-afford events:  {s['avg_cannot_afford']}")

        # Pacing
        print(f"  PACING:")
        print(f"    First VP on turn:     {s['median_first_vp_turn']}")
        print(f"    VP early/mid/late:    {s['avg_early_vp']}/{s['avg_mid_vp']}/{s['avg_late_vp']}")
        print(f"    Early engagement:     {s['avg_early_engagement']} actions/turn (turns 1-3)")

        # Agency
        print(f"  AGENCY:")
        print(f"    Multi-action turns:   {s['avg_agency_rate']*100:.1f}%")

        # Rewards
        print(f"  REWARDS:")
        print(f"    Aha moments/game:     {s['avg_aha_moments']}")
        print(f"    Win overshoot VP:     {s['avg_overshoot']}")
        print()

    # Cross-difficulty analysis
    print("=" * 70)
    print("  CROSS-DIFFICULTY INSIGHTS")
    print("=" * 70)
    print()

    if len(diff_summaries) >= 2:
        for i in range(1, len(diff_summaries)):
            prev = diff_summaries[i - 1]
            curr = diff_summaries[i]
            wr_drop = (prev["win_rate"] - curr["win_rate"]) * 100
            frust_jump = (curr["avg_build_rejection_rate"] - prev["avg_build_rejection_rate"]) * 100
            print(f"  {prev['difficulty'].upper()} → {curr['difficulty'].upper()}:")
            print(f"    Win rate drop:        {wr_drop:+.1f} pp")
            print(f"    Build frustration Δ:  {frust_jump:+.1f} pp")
            dead_diff = curr["avg_dead_roll_rate"] - prev["avg_dead_roll_rate"]
            print(f"    Dead-roll rate Δ:     {dead_diff*100:+.1f} pp")
            print()

    # Flag critical issues
    print("=" * 70)
    print("  CRITICAL FUN-FACTOR FLAGS")
    print("=" * 70)
    print()

    for s in diff_summaries:
        diff = s["difficulty"].upper()
        flags = []
        if s["turn_limit_loss_rate"] > 0.5:
            flags.append(f"⚠ {s['turn_limit_loss_rate']*100:.0f}% of games lost to turn limit (>50%)")
        if s["avg_build_rejection_rate"] > 0.3:
            flags.append(f"⚠ Build rejected on {s['avg_build_rejection_rate']*100:.0f}% of turns (>30%)")
        if s["max_dead_roll_streak"] > 3:
            flags.append(f"⚠ Max dead-roll streak of {s['max_dead_roll_streak']} consecutive turns (>3)")
        if s["avg_wasted_turn_rate"] > 0.25:
            flags.append(f"⚠ {s['avg_wasted_turn_rate']*100:.0f}% wasted turns (>25%)")
        if s["avg_aha_moments"] < 1.0:
            flags.append(f"⚠ Only {s['avg_aha_moments']:.1f} aha moments per game (<1)")
        if s["median_first_vp_turn"] and s["median_first_vp_turn"] > 4:
            flags.append(f"⚠ First VP gain not until turn {s['median_first_vp_turn']:.0f} (>4)")
        if s["win_rate"] == 0 and s["games"] >= 10:
            flags.append(f"🚫 0% win rate across {s['games']} games — difficulty may be unwinnable")

        if flags:
            print(f"  {diff}:")
            for f in flags:
                print(f"    {f}")
            print()

    if not any(True for s in diff_summaries for _ in range(1)):
        print("  No critical flags detected.\n")


def write_report_csv(game_metrics, diff_summaries):
    """Write per-game fun metrics CSV."""
    out_path = os.path.join(TELEMETRY_DIR, "fun_factor_report.csv")
    if not game_metrics:
        return

    fieldnames = list(game_metrics[0].keys())
    with open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(game_metrics)

    # Also write difficulty summary
    summary_path = os.path.join(TELEMETRY_DIR, "fun_factor_by_difficulty.csv")
    if diff_summaries:
        fieldnames = list(diff_summaries[0].keys())
        with open(summary_path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(diff_summaries)

    print(f"\nWritten: {out_path}")
    print(f"Written: {summary_path}")


if __name__ == "__main__":
    games, turns, actions, rejections = load_all()
    game_metrics = compute_game_metrics(games, turns, actions, rejections)
    diff_summaries = aggregate_by_difficulty(game_metrics)
    print_summary(diff_summaries, game_metrics)
    write_report_csv(game_metrics, diff_summaries)
