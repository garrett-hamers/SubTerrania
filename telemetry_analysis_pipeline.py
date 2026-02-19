from __future__ import annotations

import argparse
import csv
import json
import math
import re
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

TELEMETRY_PREFIX = "GameTelemetry:"

GAMES_COLUMNS = [
    "game_id",
    "start_ts",
    "end_ts",
    "difficulty",
    "character",
    "map_preset",
    "end_reason",
    "won",
    "turns_played",
    "final_vp",
    "vp_target",
    "final_rejection_count",
    "incomplete_game",
]

ACTIONS_COLUMNS = [
    "game_id",
    "turn",
    "event",
    "attempts",
    "successes",
    "rejections",
    "success_rate",
    "top_reason_code",
]

TURNS_COLUMNS = [
    "game_id",
    "turn",
    "roll_total",
    "actions_taken",
    "resources_delta_total",
    "vp_delta",
    "ended_turn",
]

REJECTIONS_COLUMNS = [
    "game_id",
    "turn",
    "event",
    "reason_code",
    "count",
    "share_of_turn_rejections",
]

RECOMMENDATIONS_COLUMNS = [
    "recommendation_id",
    "category",
    "trigger_metric",
    "current_value",
    "threshold",
    "suggested_change",
    "confidence",
    "impact_scope",
    "risk_class",
    "notes",
]

DIAGNOSTICS_COLUMNS = [
    "source_file",
    "total_lines",
    "telemetry_lines",
    "parsed_events",
    "malformed_events",
    "dropped_events",
    "incomplete_games",
    "duplicate_game_end_events",
]

ALLOWLIST_ACTION_BASES = {
    "game_start",
    "roll",
    "explore",
    "build",
    "trade",
    "clear_rubble",
    "ability",
    "turn_end",
    "interactive_event_choice",
    "consolation_choice",
}

ACTION_COUNT_BASES = {
    "roll",
    "explore",
    "build",
    "trade",
    "clear_rubble",
    "ability",
    "interactive_event_choice",
    "consolation_choice",
}


@dataclass
class ParseDiagnostics:
    source_file: str
    total_lines: int = 0
    telemetry_lines: int = 0
    parsed_events: int = 0
    malformed_events: int = 0
    dropped_events: int = 0
    incomplete_games: int = 0
    duplicate_game_end_events: int = 0

    def to_row(self) -> dict[str, Any]:
        return {
            "source_file": self.source_file,
            "total_lines": self.total_lines,
            "telemetry_lines": self.telemetry_lines,
            "parsed_events": self.parsed_events,
            "malformed_events": self.malformed_events,
            "dropped_events": self.dropped_events,
            "incomplete_games": self.incomplete_games,
            "duplicate_game_end_events": self.duplicate_game_end_events,
        }


@dataclass
class TelemetryEvent:
    event_index: int
    event: str
    ts: int
    turn: int
    phase: str
    outcome: str
    reason_code: str
    action_index: int
    max_actions: int
    vp: float
    vp_before: float
    vp_after: float
    vp_delta: float
    resources_delta: dict[str, float]
    details: dict[str, Any]
    payload: dict[str, Any]
    game_id: str = ""
    game_event_index: int = 0

    @property
    def resources_delta_total(self) -> float:
        return float(sum(self.resources_delta.values()))

    @property
    def source(self) -> str:
        source = self.details.get("source", "")
        return normalize_name(str(source))


@dataclass
class GameWindow:
    game_id: str
    started_with_game_start: bool
    start_ts: int
    start_event_index: int
    end_ts: int = 0
    end_event_index: int = 0
    ended: bool = False
    end_reason: str = ""
    incomplete_game: bool = False
    duplicate_game_end_events: int = 0
    last_ts: int = 0


def normalize_name(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "_", value.strip().lower())
    return normalized.strip("_")


def as_int(value: Any, default: int = 0) -> int:
    if value is None:
        return default
    if isinstance(value, bool):
        return int(value)
    try:
        return int(value)
    except (TypeError, ValueError):
        try:
            return int(float(value))
        except (TypeError, ValueError):
            return default


def as_float(value: Any, default: float = 0.0) -> float:
    if value is None:
        return default
    if isinstance(value, bool):
        return float(value)
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def to_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    if isinstance(value, (int, float)):
        return value != 0
    return str(value).strip().lower() in {"1", "true", "yes", "y"}


def extract_balanced_json(text: str, start_index: int) -> tuple[str | None, int]:
    if start_index < 0 or start_index >= len(text) or text[start_index] != "{":
        return None, start_index

    depth = 0
    in_string = False
    escape_next = False
    index = start_index

    while index < len(text):
        char = text[index]
        if in_string:
            if escape_next:
                escape_next = False
            elif char == "\\":
                escape_next = True
            elif char == "\"":
                in_string = False
        else:
            if char == "\"":
                in_string = True
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return text[start_index : index + 1], index + 1
        index += 1

    return None, len(text)


def parse_telemetry_events(input_path: Path) -> tuple[list[TelemetryEvent], ParseDiagnostics]:
    text = input_path.read_text(encoding="utf-8", errors="ignore")
    diagnostics = ParseDiagnostics(source_file=str(input_path))
    diagnostics.total_lines = text.count("\n") + (1 if text else 0)
    diagnostics.telemetry_lines = text.count(TELEMETRY_PREFIX)

    events: list[TelemetryEvent] = []
    cursor = 0
    event_index = 0

    while True:
        prefix_index = text.find(TELEMETRY_PREFIX, cursor)
        if prefix_index < 0:
            break

        payload_start = text.find("{", prefix_index)
        if payload_start < 0:
            diagnostics.malformed_events += 1
            cursor = prefix_index + len(TELEMETRY_PREFIX)
            continue

        payload_text, next_cursor = extract_balanced_json(text, payload_start)
        if payload_text is None:
            diagnostics.malformed_events += 1
            cursor = payload_start + 1
            continue

        cursor = next_cursor

        try:
            payload = json.loads(payload_text)
        except json.JSONDecodeError:
            diagnostics.malformed_events += 1
            continue

        event = build_event(payload=payload, event_index=event_index)
        if event is None:
            diagnostics.dropped_events += 1
            continue

        events.append(event)
        diagnostics.parsed_events += 1
        event_index += 1

    events.sort(key=lambda event: (event.ts, event.event_index))
    return events, diagnostics


def build_event(payload: dict[str, Any], event_index: int) -> TelemetryEvent | None:
    raw_event = payload.get("event")
    if raw_event is None:
        return None

    event_name = normalize_name(str(raw_event))
    if not event_name:
        return None

    raw_resources_delta = payload.get("resourcesDelta", {})
    resources_delta: dict[str, float] = {}
    if isinstance(raw_resources_delta, dict):
        for key, value in raw_resources_delta.items():
            normalized_key = normalize_name(str(key))
            if normalized_key:
                resources_delta[normalized_key] = as_float(value, 0.0)

    details = payload.get("details", {})
    if not isinstance(details, dict):
        details = {}

    return TelemetryEvent(
        event_index=event_index,
        event=event_name,
        ts=as_int(payload.get("ts"), event_index),
        turn=max(0, as_int(payload.get("turn"), 0)),
        phase=normalize_name(str(payload.get("phase", ""))),
        outcome=normalize_name(str(payload.get("outcome", ""))),
        reason_code=normalize_name(str(payload.get("reasonCode", ""))),
        action_index=as_int(payload.get("actionIndex"), 0),
        max_actions=as_int(payload.get("maxActions"), 0),
        vp=as_float(payload.get("vp"), 0.0),
        vp_before=as_float(payload.get("vpBefore"), 0.0),
        vp_after=as_float(payload.get("vpAfter"), 0.0),
        vp_delta=as_float(payload.get("vpDelta"), 0.0),
        resources_delta=resources_delta,
        details=details,
        payload=payload,
    )


def assign_game_windows(
    events: list[TelemetryEvent],
    diagnostics: ParseDiagnostics,
) -> dict[str, GameWindow]:
    game_windows: dict[str, GameWindow] = {}
    current_game_id: str | None = None
    game_counter = 0
    synthetic_counter = 0
    game_event_index = 0

    for event in events:
        if event.event == "game_start":
            if current_game_id is not None:
                previous = game_windows[current_game_id]
                if not previous.ended:
                    previous.incomplete_game = True
                    previous.end_reason = previous.end_reason or "unknown_incomplete"
                    previous.end_ts = previous.last_ts or previous.start_ts
                    current_game_id = None

            game_counter += 1
            current_game_id = f"game_{game_counter:04d}"
            game_windows[current_game_id] = GameWindow(
                game_id=current_game_id,
                started_with_game_start=True,
                start_ts=event.ts,
                start_event_index=event.event_index,
                last_ts=event.ts,
            )
            game_event_index = 0

        if current_game_id is None:
            synthetic_counter += 1
            current_game_id = f"synthetic_{synthetic_counter:04d}"
            game_windows[current_game_id] = GameWindow(
                game_id=current_game_id,
                started_with_game_start=False,
                start_ts=event.ts,
                start_event_index=event.event_index,
                incomplete_game=True,
                last_ts=event.ts,
            )
            game_event_index = 0

        game_event_index += 1
        event.game_id = current_game_id
        event.game_event_index = game_event_index

        window = game_windows[current_game_id]
        window.last_ts = event.ts
        window.end_event_index = event.event_index

        if event.event == "game_end":
            if window.ended:
                window.duplicate_game_end_events += 1
            else:
                window.ended = True
                window.end_ts = event.ts
                reason = event.details.get("endReason")
                if reason:
                    window.end_reason = normalize_name(str(reason))

            current_game_id = None
            game_event_index = 0

    if current_game_id is not None:
        window = game_windows[current_game_id]
        if not window.ended:
            window.incomplete_game = True
            window.end_reason = window.end_reason or "unknown_incomplete"
            window.end_ts = window.last_ts or window.start_ts

    diagnostics.incomplete_games = sum(1 for window in game_windows.values() if window.incomplete_game or not window.ended)
    diagnostics.duplicate_game_end_events = sum(window.duplicate_game_end_events for window in game_windows.values())

    for window in game_windows.values():
        if not window.end_ts:
            window.end_ts = window.last_ts or window.start_ts
        if not window.end_reason and not window.ended:
            window.end_reason = "unknown_incomplete"

    return game_windows


def parse_action_event_name(event_name: str) -> tuple[str, str] | None:
    if event_name.endswith("_attempt"):
        return event_name[: -len("_attempt")], "attempt"
    if event_name.endswith("_result"):
        return event_name[: -len("_result")], "result"
    return None


def select_preferred_source(events: list[TelemetryEvent]) -> list[TelemetryEvent]:
    has_viewmodel = any(event.source == "viewmodel" for event in events)
    if not has_viewmodel:
        return events
    return [event for event in events if event.source == "viewmodel"]


def aggregate_action_rows(
    events: list[TelemetryEvent],
) -> tuple[list[dict[str, Any]], dict[tuple[str, int, str], list[TelemetryEvent]]]:
    grouped: dict[tuple[str, int, str], dict[str, list[TelemetryEvent]]] = defaultdict(
        lambda: {"attempt": [], "result": []}
    )
    for event in events:
        parsed = parse_action_event_name(event.event)
        if parsed is None:
            continue
        base_event, kind = parsed
        if base_event not in ALLOWLIST_ACTION_BASES:
            continue
        grouped[(event.game_id, event.turn, base_event)][kind].append(event)

    rows: list[dict[str, Any]] = []
    selected_result_events: dict[tuple[str, int, str], list[TelemetryEvent]] = {}

    for key, bucket in grouped.items():
        game_id, turn, base_event = key
        selected_attempts = select_preferred_source(bucket["attempt"])
        selected_results = select_preferred_source(bucket["result"])
        selected_result_events[key] = selected_results

        attempts = len(selected_attempts)
        successes = sum(1 for event in selected_results if event.outcome == "success")
        rejections = sum(1 for event in selected_results if event.outcome == "rejected")
        reason_counts = Counter(
            (event.reason_code or "unknown")
            for event in selected_results
            if event.outcome == "rejected"
        )
        top_reason_code = reason_counts.most_common(1)[0][0] if reason_counts else ""
        denominator = attempts if attempts > 0 else (successes + rejections)
        success_rate = (successes / denominator) if denominator > 0 else 0.0

        rows.append(
            {
                "game_id": game_id,
                "turn": turn,
                "event": base_event,
                "attempts": attempts,
                "successes": successes,
                "rejections": rejections,
                "success_rate": round(success_rate, 4),
                "top_reason_code": top_reason_code,
            }
        )

    rows.sort(key=lambda row: (row["game_id"], row["turn"], row["event"]))
    return rows, selected_result_events


def aggregate_game_rows(
    events: list[TelemetryEvent],
    game_windows: dict[str, GameWindow],
) -> list[dict[str, Any]]:
    events_by_game: dict[str, list[TelemetryEvent]] = defaultdict(list)
    for event in events:
        events_by_game[event.game_id].append(event)

    rows: list[dict[str, Any]] = []
    for game_id in sorted(game_windows):
        game_events = events_by_game.get(game_id, [])
        if not game_events:
            continue
        window = game_windows[game_id]
        game_start_event = next((event for event in game_events if event.event == "game_start"), None)
        game_end_event = next((event for event in game_events if event.event == "game_end"), None)

        difficulty = ""
        character = ""
        map_preset = ""
        if game_start_event is not None:
            difficulty = normalize_name(str(game_start_event.details.get("difficulty", "")))
            character = normalize_name(str(game_start_event.details.get("character", "")))
            map_preset = normalize_name(str(game_start_event.details.get("mapPreset", "")))

        end_reason = window.end_reason or ""
        won = end_reason == "victory"
        if game_end_event is not None:
            detail_end_reason = normalize_name(str(game_end_event.details.get("endReason", "")))
            if detail_end_reason:
                end_reason = detail_end_reason
                won = detail_end_reason == "victory"
            if not difficulty:
                difficulty = normalize_name(str(game_end_event.details.get("difficulty", "")))
            if not character:
                character = normalize_name(str(game_end_event.details.get("character", "")))
            if not map_preset:
                map_preset = normalize_name(str(game_end_event.details.get("mapPreset", "")))

        turns_played = max((event.turn for event in game_events), default=0)
        final_vp = (
            as_float(game_end_event.details.get("finalVP"), math.nan)
            if game_end_event is not None
            else math.nan
        )
        if math.isnan(final_vp):
            candidate_vp_values = [event.vp_after for event in game_events if event.vp_after]
            if not candidate_vp_values:
                candidate_vp_values = [event.vp for event in game_events if event.vp]
            final_vp = candidate_vp_values[-1] if candidate_vp_values else 0.0

        vp_target = (
            as_float(game_end_event.details.get("vpTarget"), math.nan)
            if game_end_event is not None
            else math.nan
        )
        if math.isnan(vp_target):
            vp_target = 0.0

        final_rejection_count = sum(1 for event in game_events if event.outcome == "rejected")
        incomplete_game = window.incomplete_game or not window.ended or not window.started_with_game_start

        rows.append(
            {
                "game_id": game_id,
                "start_ts": window.start_ts,
                "end_ts": window.end_ts,
                "difficulty": difficulty,
                "character": character,
                "map_preset": map_preset,
                "end_reason": end_reason,
                "won": won,
                "turns_played": turns_played,
                "final_vp": round(final_vp, 4),
                "vp_target": round(vp_target, 4),
                "final_rejection_count": final_rejection_count,
                "incomplete_game": incomplete_game,
            }
        )

    rows.sort(key=lambda row: row["game_id"])
    return rows


def aggregate_turn_rows(
    events: list[TelemetryEvent],
    action_rows: list[dict[str, Any]],
    selected_result_events: dict[tuple[str, int, str], list[TelemetryEvent]],
) -> list[dict[str, Any]]:
    events_by_turn: dict[tuple[str, int], list[TelemetryEvent]] = defaultdict(list)
    for event in events:
        if event.turn > 0:
            events_by_turn[(event.game_id, event.turn)].append(event)

    actions_by_turn: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for row in action_rows:
        actions_by_turn[(row["game_id"], row["turn"])].append(row)

    rows: list[dict[str, Any]] = []
    for key in sorted(events_by_turn):
        game_id, turn = key
        turn_events = events_by_turn[key]
        turn_action_rows = actions_by_turn.get(key, [])

        action_successes = sum(
            row["successes"]
            for row in turn_action_rows
            if row["event"] in ACTION_COUNT_BASES
        )

        turn_selected_results: list[TelemetryEvent] = []
        for result_key, selected in selected_result_events.items():
            result_game_id, result_turn, _ = result_key
            if result_game_id == game_id and result_turn == turn:
                turn_selected_results.extend(selected)

        successful_results = [event for event in turn_selected_results if event.outcome == "success"]
        resources_delta_total = sum(event.resources_delta_total for event in successful_results)
        vp_delta = sum(event.vp_delta for event in successful_results)

        roll_total = 0
        roll_candidates = [
            event
            for event in turn_selected_results
            if event.event == "roll_result" and event.outcome == "success"
        ]
        if roll_candidates:
            selected_rolls = select_preferred_source(roll_candidates)
            roll_total = as_int(selected_rolls[0].details.get("diceTotal"), 0)

        ended_turn = any(
            event.event == "turn_end" and (event.outcome == "success" or not event.outcome)
            for event in turn_events
        )

        rows.append(
            {
                "game_id": game_id,
                "turn": turn,
                "roll_total": roll_total,
                "actions_taken": action_successes,
                "resources_delta_total": round(resources_delta_total, 4),
                "vp_delta": round(vp_delta, 4),
                "ended_turn": ended_turn,
            }
        )

    return rows


def aggregate_rejection_rows(
    selected_result_events: dict[tuple[str, int, str], list[TelemetryEvent]]
) -> list[dict[str, Any]]:
    rejection_counts: Counter[tuple[str, int, str, str]] = Counter()
    total_rejections_by_turn: Counter[tuple[str, int]] = Counter()

    for (game_id, turn, base_event), selected_results in selected_result_events.items():
        for event in selected_results:
            if event.outcome != "rejected":
                continue
            reason_code = event.reason_code or "unknown"
            rejection_counts[(game_id, turn, base_event, reason_code)] += 1
            total_rejections_by_turn[(game_id, turn)] += 1

    rows: list[dict[str, Any]] = []
    for (game_id, turn, base_event, reason_code), count in sorted(rejection_counts.items()):
        total_rejections = total_rejections_by_turn[(game_id, turn)]
        share = (count / total_rejections) if total_rejections else 0.0
        rows.append(
            {
                "game_id": game_id,
                "turn": turn,
                "event": base_event,
                "reason_code": reason_code,
                "count": count,
                "share_of_turn_rejections": round(share, 4),
            }
        )
    return rows


def confidence_from_ratio(current: float, threshold: float) -> float:
    if current <= threshold:
        return 0.0
    return min(0.95, 0.55 + (current - threshold) * 1.5)


def generate_recommendations(
    game_rows: list[dict[str, Any]],
    action_rows: list[dict[str, Any]],
    rejection_rows: list[dict[str, Any]],
    include_mechanic_recommendations: bool,
    min_confidence: float,
) -> list[dict[str, Any]]:
    recommendations: list[dict[str, Any]] = []
    recommendation_index = 1

    def add_recommendation(
        category: str,
        trigger_metric: str,
        current_value: float,
        threshold: float,
        suggested_change: str,
        confidence: float,
        impact_scope: str,
        risk_class: str,
        notes: str,
    ) -> None:
        nonlocal recommendation_index
        if confidence < min_confidence:
            return
        recommendations.append(
            {
                "recommendation_id": f"rec_{recommendation_index:03d}",
                "category": category,
                "trigger_metric": trigger_metric,
                "current_value": round(current_value, 4),
                "threshold": round(threshold, 4),
                "suggested_change": suggested_change,
                "confidence": round(confidence, 4),
                "impact_scope": impact_scope,
                "risk_class": risk_class,
                "notes": notes,
            }
        )
        recommendation_index += 1

    completed_games = [
        row
        for row in game_rows
        if str(row.get("end_reason", "")) not in {"", "unknown_incomplete"}
    ]
    total_games = len(completed_games)
    if total_games == 0:
        add_recommendation(
            category="observation",
            trigger_metric="total_games",
            current_value=0.0,
            threshold=1.0,
            suggested_change="Collect larger telemetry sample before balance changes",
            confidence=0.6,
            impact_scope="global",
            risk_class="low",
            notes="No complete games were detected in telemetry input.",
        )
        return recommendations

    turn_limit_losses = sum(1 for row in completed_games if row["end_reason"] == "turn_limit")
    turn_limit_loss_rate = turn_limit_losses / total_games
    turn_limit_threshold = 0.30

    if turn_limit_loss_rate > turn_limit_threshold:
        confidence = confidence_from_ratio(turn_limit_loss_rate, turn_limit_threshold)
        add_recommendation(
            category="numeric_tuning",
            trigger_metric="turn_limit_loss_rate",
            current_value=turn_limit_loss_rate,
            threshold=turn_limit_threshold,
            suggested_change="Increase max turns by +2 on impacted difficulties",
            confidence=confidence,
            impact_scope="difficulty",
            risk_class="low",
            notes="High turn-limit failure share indicates pacing pressure is too strict.",
        )
        if include_mechanic_recommendations:
            add_recommendation(
                category="mechanic_experiment",
                trigger_metric="turn_limit_loss_rate",
                current_value=turn_limit_loss_rate,
                threshold=turn_limit_threshold,
                suggested_change="Add late-turn momentum mechanic (bonus action/resource after late threshold)",
                confidence=max(min_confidence, confidence - 0.1),
                impact_scope="global",
                risk_class="medium",
                notes="Mechanic-level mitigation can reduce endgame stall without broad numeric inflation.",
            )

    all_rejections = sum(int(row["rejections"]) for row in action_rows)
    affordability_rejections = sum(
        int(row["count"])
        for row in rejection_rows
        if "afford" in str(row["reason_code"])
    )
    affordability_block_rate = (
        affordability_rejections / all_rejections if all_rejections else 0.0
    )
    affordability_threshold = 0.28

    if affordability_block_rate > affordability_threshold:
        confidence = confidence_from_ratio(affordability_block_rate, affordability_threshold)
        add_recommendation(
            category="numeric_tuning",
            trigger_metric="affordability_block_rate",
            current_value=affordability_block_rate,
            threshold=affordability_threshold,
            suggested_change="Reduce early structure rare-resource costs or improve trade ratio by 1",
            confidence=confidence,
            impact_scope="economy",
            risk_class="low",
            notes="Cannot-afford rejections dominate rejection mix.",
        )
        if include_mechanic_recommendations:
            add_recommendation(
                category="mechanic_experiment",
                trigger_metric="affordability_block_rate",
                current_value=affordability_block_rate,
                threshold=affordability_threshold,
                suggested_change="Introduce adaptive production smoothing after repeated affordability failures",
                confidence=max(min_confidence, confidence - 0.1),
                impact_scope="economy",
                risk_class="high",
                notes="Mechanic-level intervention can reduce dead turns when economy bottlenecks persist.",
            )

    explore_attempts = sum(
        int(row["attempts"]) for row in action_rows if row["event"] == "explore"
    )
    explore_rejections = sum(
        int(row["rejections"]) for row in action_rows if row["event"] == "explore"
    )
    explore_friction_rate = (
        explore_rejections / explore_attempts if explore_attempts else 0.0
    )
    explore_threshold = 0.22

    if explore_friction_rate > explore_threshold:
        confidence = confidence_from_ratio(explore_friction_rate, explore_threshold)
        add_recommendation(
            category="numeric_tuning",
            trigger_metric="explore_friction_rate",
            current_value=explore_friction_rate,
            threshold=explore_threshold,
            suggested_change="Increase explore flexibility (cap or action budget tuning)",
            confidence=confidence,
            impact_scope="exploration",
            risk_class="low",
            notes="Explore rejections are consuming a high share of exploration attempts.",
        )
        if include_mechanic_recommendations:
            add_recommendation(
                category="mechanic_experiment",
                trigger_metric="explore_friction_rate",
                current_value=explore_friction_rate,
                threshold=explore_threshold,
                suggested_change="Pilot relaxed exploration adjacency after repeated blocked explore attempts",
                confidence=max(min_confidence, confidence - 0.1),
                impact_scope="exploration",
                risk_class="medium",
                notes="Mechanic-level path can reduce exploration dead-ends without broad power gain.",
            )

    win_rate = sum(1 for row in completed_games if row["won"]) / total_games
    win_rate_threshold = 0.35
    if win_rate < win_rate_threshold:
        confidence = min(0.9, 0.55 + (win_rate_threshold - win_rate) * 1.3)
        add_recommendation(
            category="numeric_tuning",
            trigger_metric="overall_win_rate",
            current_value=win_rate,
            threshold=win_rate_threshold,
            suggested_change="Lower VP target by 1 for over-constrained mode segments",
            confidence=confidence,
            impact_scope="difficulty",
            risk_class="low",
            notes="Low global win rate suggests systemic over-tuning.",
        )

    if not recommendations:
        average_turns = statistics.mean(float(row["turns_played"]) for row in completed_games)
        add_recommendation(
            category="observation",
            trigger_metric="stable_balance_window",
            current_value=average_turns,
            threshold=0.0,
            suggested_change="No immediate balance change; continue telemetry collection",
            confidence=max(min_confidence, 0.55),
            impact_scope="global",
            risk_class="low",
            notes="Current thresholds are not exceeded on this sample.",
        )

    recommendations.sort(key=lambda row: row["recommendation_id"])
    return recommendations


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as output_file:
        writer = csv.DictWriter(output_file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            normalized_row: dict[str, Any] = {}
            for field in fieldnames:
                value = row.get(field, "")
                if isinstance(value, bool):
                    normalized_row[field] = "true" if value else "false"
                elif isinstance(value, float):
                    normalized_row[field] = f"{value:.6f}".rstrip("0").rstrip(".")
                else:
                    normalized_row[field] = value
            writer.writerow(normalized_row)


def parse_bool_arg(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized in {"true", "1", "yes", "y"}:
        return True
    if normalized in {"false", "0", "no", "n"}:
        return False
    raise argparse.ArgumentTypeError("Expected true/false")


def run_pipeline(
    input_path: Path,
    output_dir: Path,
    include_mechanic_recommendations: bool,
    min_confidence: float,
) -> int:
    events, diagnostics = parse_telemetry_events(input_path)
    game_windows = assign_game_windows(events, diagnostics)

    game_rows = aggregate_game_rows(events, game_windows)
    action_rows, selected_result_events = aggregate_action_rows(events)
    turn_rows = aggregate_turn_rows(events, action_rows, selected_result_events)
    rejection_rows = aggregate_rejection_rows(selected_result_events)
    recommendation_rows = generate_recommendations(
        game_rows=game_rows,
        action_rows=action_rows,
        rejection_rows=rejection_rows,
        include_mechanic_recommendations=include_mechanic_recommendations,
        min_confidence=min_confidence,
    )

    write_csv(output_dir / "telemetry_games.csv", GAMES_COLUMNS, game_rows)
    write_csv(output_dir / "telemetry_actions.csv", ACTIONS_COLUMNS, action_rows)
    write_csv(output_dir / "telemetry_turns.csv", TURNS_COLUMNS, turn_rows)
    write_csv(output_dir / "telemetry_rejections.csv", REJECTIONS_COLUMNS, rejection_rows)
    write_csv(
        output_dir / "telemetry_recommendations.csv",
        RECOMMENDATIONS_COLUMNS,
        recommendation_rows,
    )
    write_csv(
        output_dir / "telemetry_parse_diagnostics.csv",
        DIAGNOSTICS_COLUMNS,
        [diagnostics.to_row()],
    )

    top_rejections = Counter(
        f"{row['event']}:{row['reason_code']}" for row in rejection_rows for _ in range(int(row["count"]))
    )
    top_rejection_display = ", ".join(
        f"{name} ({count})" for name, count in top_rejections.most_common(5)
    )
    if not top_rejection_display:
        top_rejection_display = "none"

    print(f"Input: {input_path}")
    print(f"Output directory: {output_dir}")
    print(
        f"Parsed events: {diagnostics.parsed_events} "
        f"(malformed={diagnostics.malformed_events}, dropped={diagnostics.dropped_events})"
    )
    print(
        f"Games: {len(game_rows)} | Actions: {len(action_rows)} | "
        f"Turns: {len(turn_rows)} | Rejections: {len(rejection_rows)} | "
        f"Recommendations: {len(recommendation_rows)}"
    )
    print(f"Top rejection slices: {top_rejection_display}")

    if recommendation_rows:
        top = recommendation_rows[0]
        print(
            "Top recommendation: "
            f"{top['recommendation_id']} {top['category']} -> {top['suggested_change']} "
            f"(confidence={top['confidence']})"
        )

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Convert GameTelemetry logs into CSV metrics and balance recommendations."
    )
    parser.add_argument(
        "--input",
        type=Path,
        required=True,
        help="Path to telemetry source log file (logcat export or test output).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("telemetry_output"),
        help="Directory for generated CSV artifacts (default: telemetry_output).",
    )
    parser.add_argument(
        "--include-mechanic-recommendations",
        type=parse_bool_arg,
        default=True,
        help="Whether mechanic-level recommendations are allowed (true/false).",
    )
    parser.add_argument(
        "--min-confidence",
        type=float,
        default=0.45,
        help="Minimum confidence threshold for emitted recommendations.",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    if not args.input.exists():
        parser.error(f"Input file does not exist: {args.input}")
    if args.min_confidence < 0 or args.min_confidence > 1:
        parser.error("--min-confidence must be between 0 and 1")

    return run_pipeline(
        input_path=args.input,
        output_dir=args.output_dir,
        include_mechanic_recommendations=args.include_mechanic_recommendations,
        min_confidence=args.min_confidence,
    )


if __name__ == "__main__":
    raise SystemExit(main())
