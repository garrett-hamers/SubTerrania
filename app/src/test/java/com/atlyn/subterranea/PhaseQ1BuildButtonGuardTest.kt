package com.atlyn.subterranea

import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.TurnPhase
import com.atlyn.subterranea.ui.game.canBuildAction
import com.atlyn.subterranea.ui.game.isBuildContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase Q-1.2: regression tests for the Build action enable predicate.
 *
 * Before Q-1.2 the predicate did not check `hasAvailableStructures`, leading
 * to 18% of OPEN_BUILD taps opening a picker with no affordable structures
 * (per the Phase P emulator corpus). The new test cases enumerate each
 * disable reason in isolation.
 */
class PhaseQ1BuildButtonGuardTest {

    private val anyHex = HexCoordinate(0, 0)

    @Test
    fun `canBuild is false when no tile is selected`() {
        assertFalse(
            canBuildAction(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = null,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2,
                hasAvailableStructures = true
            )
        )
    }

    @Test
    fun `canBuild is false during ROLL_DICE phase`() {
        assertFalse(
            canBuildAction(
                turnPhase = TurnPhase.ROLL_DICE,
                gameOver = false,
                selectedTile = anyHex,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2,
                hasAvailableStructures = true
            )
        )
    }

    @Test
    fun `canBuild is false when the game is over`() {
        assertFalse(
            canBuildAction(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = true,
                selectedTile = anyHex,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2,
                hasAvailableStructures = true
            )
        )
    }

    @Test
    fun `canBuild is false when all actions have been used`() {
        assertFalse(
            canBuildAction(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = anyHex,
                actionsThisTurn = 2,
                maxActionsPerTurn = 2,
                hasAvailableStructures = true
            )
        )
    }

    @Test
    fun `canBuild is false when no structures are affordable - the Q-1_2 fix`() {
        // This is the new behaviour added in Phase Q-1.2.
        assertFalse(
            canBuildAction(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = anyHex,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2,
                hasAvailableStructures = false
            )
        )
    }

    @Test
    fun `canBuild is true with all conditions met`() {
        assertTrue(
            canBuildAction(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = anyHex,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2,
                hasAvailableStructures = true
            )
        )
    }

    @Test
    fun `canBuild is true with 1 action remaining and structures available`() {
        assertTrue(
            canBuildAction(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = anyHex,
                actionsThisTurn = 1,
                maxActionsPerTurn = 2,
                hasAvailableStructures = true
            )
        )
    }

    // --- isBuildContext: same checks WITHOUT affordability ----------------

    @Test
    fun `isBuildContext ignores affordability`() {
        // Tile + phase + actions are valid; affordability shouldn't matter.
        assertTrue(
            isBuildContext(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = anyHex,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2
            )
        )
    }

    @Test
    fun `isBuildContext is false when no tile selected even with affordability`() {
        // Affordability irrelevant; no tile means no context.
        assertFalse(
            isBuildContext(
                turnPhase = TurnPhase.MAIN_ACTION,
                gameOver = false,
                selectedTile = null,
                actionsThisTurn = 0,
                maxActionsPerTurn = 2
            )
        )
    }
}
