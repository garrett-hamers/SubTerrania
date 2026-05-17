package com.atlyn.subterranea.ui.game

import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.TurnPhase

/**
 * Pure predicates for Action Bar button enable states.
 *
 * Phase Q-1.2 extracted these from the [ActionButtons] composable so they
 * can be unit-tested without a Compose runtime. The composable now delegates
 * to these functions; the only logic difference vs pre-Q is that
 * [canBuildAction] now requires `hasAvailableStructures = true` so the Build
 * button is disabled when the player can't afford anything on the selected
 * tile (eliminates the "open empty picker" UX anti-pattern surfaced in the
 * Phase P emulator corpus: 18% of OPEN_BUILD events opened a picker with
 * zero affordable structures).
 */

/**
 * True iff the Build action should be enabled.
 *
 * @param turnPhase                Current phase. Build only meaningful in MAIN_ACTION.
 * @param gameOver                 No actions allowed once the game is over.
 * @param selectedTile             A tile must be selected to build on.
 * @param actionsThisTurn          How many actions the player has already used.
 * @param maxActionsPerTurn        Per-difficulty action budget.
 * @param hasAvailableStructures   Whether at least one structure is affordable
 *                                 + buildable on the selected tile (Phase Q-1.2:
 *                                 added this check).
 */
fun canBuildAction(
    turnPhase: TurnPhase,
    gameOver: Boolean,
    selectedTile: HexCoordinate?,
    actionsThisTurn: Int,
    maxActionsPerTurn: Int,
    hasAvailableStructures: Boolean
): Boolean {
    return turnPhase == TurnPhase.MAIN_ACTION
        && !gameOver
        && selectedTile != null
        && actionsThisTurn < maxActionsPerTurn
        && hasAvailableStructures
}

/**
 * True iff the selected tile is in a valid "build context" — phase, game,
 * tile-selection, and action budget all OK — but ignoring whether any
 * structure is affordable. Used to colour the Build button distinctly in the
 * "tile selected, nothing affordable" case so the player sees the button as
 * in-scope but greyed-out, rather than fully hidden.
 */
fun isBuildContext(
    turnPhase: TurnPhase,
    gameOver: Boolean,
    selectedTile: HexCoordinate?,
    actionsThisTurn: Int,
    maxActionsPerTurn: Int
): Boolean {
    return turnPhase == TurnPhase.MAIN_ACTION
        && !gameOver
        && selectedTile != null
        && actionsThisTurn < maxActionsPerTurn
}
