package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.Difficulty
import com.atlyn.subterranea.domain.model.GameCharacter
import com.atlyn.subterranea.domain.model.GameState
import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.HexTile
import com.atlyn.subterranea.domain.model.MapPreset
import com.atlyn.subterranea.domain.model.Player
import com.atlyn.subterranea.domain.model.Resource
import com.atlyn.subterranea.domain.model.Structure
import com.atlyn.subterranea.domain.model.StructureType
import com.atlyn.subterranea.domain.model.TerrainType
import com.atlyn.subterranea.domain.model.TurnPhase
import com.atlyn.subterranea.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Phase L hotfix (FUN_FACTOR_REPORT items 1-3 + 6).
 *
 * Bug 1: TradeEngine.tradeResources didn't decrement actionsThisTurn → free trades.
 * Bug 2: StructureEngine.useStructureAbility didn't decrement actionsThisTurn → free abilities.
 * Bug 3: Lantern Posts could be built with zero illumination yield (still gave +1 VP).
 */
class PhaseLHotfixTest {

    private fun makePlayer(
        resources: Map<Resource, Int> = mapOf(
            Resource.MYCELIUM to 5,
            Resource.BASALT to 5,
            Resource.CHITIN to 5,
            Resource.LICHEN to 5,
            Resource.IRON_ORE to 5,
            Resource.CRYSTAL to 5
        )
    ) = Player(id = 0, name = "Test", resources = resources)

    private fun makeState(
        player: Player = makePlayer(),
        actionsThisTurn: Int = 0,
        maxActionsPerTurn: Int = 2,
        turnPhase: TurnPhase = TurnPhase.MAIN_ACTION,
        board: Map<HexCoordinate, HexTile> = emptyMap(),
        structures: List<Structure> = emptyList()
    ) = GameState(
        board = board,
        players = listOf(player),
        currentPlayerIndex = 0,
        turnNumber = 5,
        turnPhase = turnPhase,
        structures = structures,
        actionsThisTurn = actionsThisTurn,
        maxActionsPerTurn = maxActionsPerTurn,
        victoryPointsToWin = Difficulty.NORMAL.victoryPointsToWin,
        difficulty = Difficulty.NORMAL,
        selectedCharacter = GameCharacter.EXPLORER,
        mapPreset = MapPreset.STANDARD
    )

    // ---- Bug 1: free trades ----

    @Test
    fun trade_consumesOneAction() {
        val state = makeState(actionsThisTurn = 0, maxActionsPerTurn = 2)
        val after = TradeEngine.tradeResources(state, Resource.BASALT, Resource.CRYSTAL)
        assertEquals("Trade should cost exactly 1 action", 1, after.actionsThisTurn)
        assertTrue("Trade should record success event",
            after.eventLog.any { it.startsWith("🔄 Traded") })
    }

    @Test
    fun trade_rejectedWhenNoActionsRemaining() {
        val state = makeState(actionsThisTurn = 2, maxActionsPerTurn = 2)
        val after = TradeEngine.tradeResources(state, Resource.BASALT, Resource.CRYSTAL)
        assertEquals(
            "Trade attempt with 0 actions left should not consume an action",
            2, after.actionsThisTurn
        )
        assertEquals(
            "Resources should not change on rejected trade",
            state.currentPlayer.resources, after.currentPlayer.resources
        )
        assertTrue("Should emit no-actions-remaining error",
            after.eventLog.any { it.contains("No actions remaining") })
    }

    @Test
    fun trade_rejectedOutsideMainActionPhase() {
        val state = makeState(turnPhase = TurnPhase.ROLL_DICE)
        val after = TradeEngine.tradeResources(state, Resource.BASALT, Resource.CRYSTAL)
        assertEquals(
            "Trade outside MAIN_ACTION should not consume an action",
            0, after.actionsThisTurn
        )
        assertTrue(after.eventLog.any { it.contains("main action phase") })
    }

    // ---- Bug 2: free abilities ----

    @Test
    fun ability_consumesOneAction() {
        val coord = HexCoordinate(0, 0)
        val tile = HexTile(coord, Zone.CRUST, isRevealed = true, isIlluminated = true,
            terrain = TerrainType.FUNGAL_FOREST)
        val structure = Structure(StructureType.FUNGAL_FARM, coord, ownerId = 0,
            abilityCooldown = 0)
        val state = makeState(
            actionsThisTurn = 0,
            maxActionsPerTurn = 2,
            board = mapOf(coord to tile),
            structures = listOf(structure)
        )
        val after = StructureEngine.useStructureAbility(state, coord)
        assertEquals("Ability use should cost exactly 1 action", 1, after.actionsThisTurn)
        assertTrue("Spore burst should add 2 mycelium",
            after.currentPlayer.getResourceCount(Resource.MYCELIUM) ==
                state.currentPlayer.getResourceCount(Resource.MYCELIUM) + 2)
    }

    @Test
    fun ability_rejectedWhenNoActionsRemaining() {
        val coord = HexCoordinate(0, 0)
        val tile = HexTile(coord, Zone.CRUST, isRevealed = true, isIlluminated = true,
            terrain = TerrainType.FUNGAL_FOREST)
        val structure = Structure(StructureType.FUNGAL_FARM, coord, ownerId = 0,
            abilityCooldown = 0)
        val state = makeState(
            actionsThisTurn = 1,
            maxActionsPerTurn = 1,
            board = mapOf(coord to tile),
            structures = listOf(structure)
        )
        val after = StructureEngine.useStructureAbility(state, coord)
        assertEquals(
            "Ability with 0 actions left should not consume an action",
            1, after.actionsThisTurn
        )
        assertEquals(
            "Resources should not change on rejected ability",
            state.currentPlayer.resources, after.currentPlayer.resources
        )
    }

    @Test
    fun ability_rejectedOutsideMainActionPhase() {
        val coord = HexCoordinate(0, 0)
        val tile = HexTile(coord, Zone.CRUST, isRevealed = true, isIlluminated = true,
            terrain = TerrainType.FUNGAL_FOREST)
        val structure = Structure(StructureType.FUNGAL_FARM, coord, ownerId = 0,
            abilityCooldown = 0)
        val state = makeState(
            turnPhase = TurnPhase.ROLL_DICE,
            board = mapOf(coord to tile),
            structures = listOf(structure)
        )
        val after = StructureEngine.useStructureAbility(state, coord)
        assertEquals(0, after.actionsThisTurn)
        assertTrue(after.eventLog.any { it.contains("main action phase") })
    }

    // ---- Bug 3: Lantern Post utility validation ----

    @Test
    fun lantern_rejectedWhenAllNeighboursAreLitAndRevealed() {
        val center = HexCoordinate(0, 0)
        val board = (listOf(center) + center.neighbors()).associateWith { coord ->
            HexTile(coord, Zone.CRUST, isRevealed = true, isIlluminated = true,
                terrain = TerrainType.BASALT_QUARRY)
        }
        val state = makeState(board = board)
        assertFalse("Lantern surrounded by lit revealed tiles should not have utility",
            StructureEngine.lanternHasUtility(state, center))

        val after = StructureEngine.buildStructure(state, StructureType.LANTERN, center)
        assertEquals("Build should not consume an action when rejected",
            state.actionsThisTurn, after.actionsThisTurn)
        assertTrue(after.eventLog.any { it.contains("light no new area") })
    }

    @Test
    fun lantern_acceptedWhenNeighbourIsUnrevealed() {
        val center = HexCoordinate(0, 0)
        val neighbours = center.neighbors()
        val board = mutableMapOf<HexCoordinate, HexTile>()
        // Center revealed and lit (player picked it)
        board[center] = HexTile(center, Zone.CRUST, isRevealed = true, isIlluminated = true,
            terrain = TerrainType.BASALT_QUARRY)
        // 5 lit neighbours, 1 unrevealed → unrevealed gives utility for future auto-light
        neighbours.forEachIndexed { idx, c ->
            board[c] = HexTile(c, Zone.CRUST, isRevealed = idx > 0, isIlluminated = idx > 0,
                terrain = TerrainType.LICHEN_FIELD)
        }
        val state = makeState(board = board)
        assertTrue("Lantern next to >=1 unrevealed tile should have utility",
            StructureEngine.lanternHasUtility(state, center))
    }

    @Test
    fun lantern_acceptedWhenNeighbourIsRevealedButUnlit() {
        val center = HexCoordinate(0, 0)
        val neighbours = center.neighbors()
        val board = mutableMapOf<HexCoordinate, HexTile>()
        board[center] = HexTile(center, Zone.CRUST, isRevealed = true, isIlluminated = true,
            terrain = TerrainType.BASALT_QUARRY)
        // First neighbour revealed-but-unlit; rest lit
        neighbours.forEachIndexed { idx, c ->
            board[c] = HexTile(c, Zone.CRUST, isRevealed = true, isIlluminated = idx != 0,
                terrain = TerrainType.LICHEN_FIELD)
        }
        val state = makeState(board = board)
        assertTrue("Lantern next to revealed-unlit tile should have utility",
            StructureEngine.lanternHasUtility(state, center))
    }

    @Test
    fun lantern_filteredFromBuildableStructures_whenNoUtility() {
        val center = HexCoordinate(0, 0)
        val board = (listOf(center) + center.neighbors()).associateWith { coord ->
            HexTile(coord, Zone.CRUST, isRevealed = true, isIlluminated = true,
                terrain = TerrainType.BASALT_QUARRY)
        }
        val state = makeState(board = board)
        val buildable = StructureEngine.getBuildableStructures(state)
        val lanternLocations = buildable[StructureType.LANTERN].orEmpty()
        assertFalse("getBuildableStructures should exclude useless Lantern locations",
            lanternLocations.contains(center))
    }
}
