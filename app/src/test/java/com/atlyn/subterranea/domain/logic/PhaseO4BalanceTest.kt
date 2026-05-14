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
import com.atlyn.subterranea.domain.model.StructureAbility
import com.atlyn.subterranea.domain.model.StructureType
import com.atlyn.subterranea.domain.model.TerrainType
import com.atlyn.subterranea.domain.model.TurnPhase
import com.atlyn.subterranea.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase O-4 balance v2 regression tests:
 *  - Lantern cost scaling: 1st free, 2nd +1 Crystal, 3rd+ +1 Crystal +1 Iron.
 *  - Difficulty retune: Nightmare VP target raised; hazard chance bumped on
 *    Hard + Nightmare.
 *  - Map differentiation: Spore Burst yields more on Fungal Jungle.
 *  - Crystal Caves: CrystalVein exploration outcome more frequent (sanity
 *    check that the constant moved).
 */
class PhaseO4BalanceTest {

    private fun makePlayer(
        resources: Map<Resource, Int> = mapOf(
            Resource.MYCELIUM to 10,
            Resource.BASALT to 10,
            Resource.CHITIN to 10,
            Resource.LICHEN to 10,
            Resource.IRON_ORE to 10,
            Resource.CRYSTAL to 10
        )
    ) = Player(id = 0, name = "Test", resources = resources)

    private fun makeState(
        player: Player = makePlayer(),
        difficulty: Difficulty = Difficulty.NORMAL,
        character: GameCharacter = GameCharacter.EXPLORER,
        mapPreset: MapPreset = MapPreset.STANDARD,
        structures: List<Structure> = emptyList()
    ) = GameState(
        board = emptyMap(),
        players = listOf(player),
        currentPlayerIndex = 0,
        turnNumber = 5,
        turnPhase = TurnPhase.MAIN_ACTION,
        structures = structures,
        actionsThisTurn = 0,
        maxActionsPerTurn = 2,
        victoryPointsToWin = difficulty.victoryPointsToWin,
        difficulty = difficulty,
        selectedCharacter = character,
        mapPreset = mapPreset
    )

    private fun lantern(playerId: Int = 0, q: Int = 0, r: Int = 0) = Structure(
        type = StructureType.LANTERN,
        location = HexCoordinate(q, r),
        ownerId = playerId
    )

    // --- o4a: Lantern cost scaling ------------------------------------------

    @Test
    fun firstLanternCosts1Crystal1Iron() {
        val state = makeState()
        val cost = StructureEngine.getActualBuildCost(state, StructureType.LANTERN)
        assertEquals("First Lantern: 1 Crystal", 1, cost[Resource.CRYSTAL])
        assertEquals("First Lantern: 1 Iron",    1, cost[Resource.IRON_ORE])
    }

    @Test
    fun secondLanternAddsOneCrystal() {
        val state = makeState(structures = listOf(lantern(q = 0, r = 0)))
        val cost = StructureEngine.getActualBuildCost(state, StructureType.LANTERN)
        assertEquals("Second Lantern: 2 Crystal", 2, cost[Resource.CRYSTAL])
        assertEquals("Second Lantern: 1 Iron",    1, cost[Resource.IRON_ORE])
    }

    @Test
    fun thirdAndLaterLanternsAddOneCrystalAndOneIron() {
        val state = makeState(structures = listOf(
            lantern(q = 0, r = 0),
            lantern(q = 1, r = 0)
        ))
        val cost = StructureEngine.getActualBuildCost(state, StructureType.LANTERN)
        assertEquals("Third Lantern: 2 Crystal", 2, cost[Resource.CRYSTAL])
        assertEquals("Third Lantern: 2 Iron",    2, cost[Resource.IRON_ORE])

        // 5th Lantern should still be capped at +1 Crystal +1 Iron, not stacking forever.
        val state5 = makeState(structures = (0 until 4).map { lantern(q = it, r = 0) })
        val cost5 = StructureEngine.getActualBuildCost(state5, StructureType.LANTERN)
        assertEquals("Fifth Lantern: still 2 Crystal", 2, cost5[Resource.CRYSTAL])
        assertEquals("Fifth Lantern: still 2 Iron",    2, cost5[Resource.IRON_ORE])
    }

    @Test
    fun otherStructuresAreUnaffectedByLanternCount() {
        val state = makeState(structures = listOf(
            lantern(q = 0, r = 0), lantern(q = 1, r = 0), lantern(q = 2, r = 0)
        ))
        val outpostCost = StructureEngine.getActualBuildCost(state, StructureType.OUTPOST)
        assertEquals(2, outpostCost[Resource.BASALT])
        assertEquals(1, outpostCost[Resource.MYCELIUM])
        assertEquals(1, outpostCost[Resource.CHITIN])
        // Check absence — Iron / Crystal are not part of Outpost cost.
        assertEquals(null, outpostCost[Resource.IRON_ORE])
        assertEquals(null, outpostCost[Resource.CRYSTAL])
    }

    @Test
    fun otherPlayersLanternsDontCountForCurrentPlayer() {
        val theirs = Structure(
            type = StructureType.LANTERN,
            location = HexCoordinate(0, 0),
            ownerId = 99 // different player id
        )
        val state = makeState(structures = listOf(theirs))
        val cost = StructureEngine.getActualBuildCost(state, StructureType.LANTERN)
        assertEquals("Other player's Lanterns shouldn't surcharge mine", 1, cost[Resource.CRYSTAL])
    }

    // --- o4c: difficulty retune --------------------------------------------

    @Test
    fun nightmareVpTargetIsHigherThanHard() {
        // Phase L K-1 showed Nightmare easier than Hard (80% vs 72%). After
        // O-4 the VP target should restore the ordering.
        assertTrue(
            "NIGHTMARE.victoryPointsToWin (${Difficulty.NIGHTMARE.victoryPointsToWin}) " +
                "should exceed HARD.victoryPointsToWin (${Difficulty.HARD.victoryPointsToWin})",
            Difficulty.NIGHTMARE.victoryPointsToWin > Difficulty.HARD.victoryPointsToWin
        )
    }

    @Test
    fun hazardChanceMonotonicallyIncreases() {
        assertTrue(Difficulty.EASY.hazardChance < Difficulty.NORMAL.hazardChance)
        assertTrue(Difficulty.NORMAL.hazardChance < Difficulty.HARD.hazardChance)
        assertTrue(Difficulty.HARD.hazardChance < Difficulty.NIGHTMARE.hazardChance)
        // Nightmare should be at least 0.75 — meaningfully punishing.
        assertTrue(Difficulty.NIGHTMARE.hazardChance >= 0.75f)
    }

    // --- o4b: map differentiation ------------------------------------------

    @Test
    fun sporeBurstOnFungalJungleYieldsMore() {
        val tile = HexTile(
            coordinate = HexCoordinate(0, 0),
            zone = Zone.SURFACE,
            isRevealed = true,
            isIlluminated = true,
            terrain = TerrainType.FUNGAL_FOREST
        )
        val structure = Structure(
            type = StructureType.FUNGAL_FARM,
            location = HexCoordinate(0, 0),
            ownerId = 0,
            abilityCooldown = 0
        )

        val standardState = makeState(
            mapPreset = MapPreset.STANDARD,
            structures = listOf(structure),
            player = makePlayer(resources = mapOf(
                Resource.MYCELIUM to 0, Resource.LICHEN to 0,
                Resource.BASALT to 0, Resource.CHITIN to 0,
                Resource.IRON_ORE to 0, Resource.CRYSTAL to 0
            ))
        ).copy(board = mapOf(HexCoordinate(0, 0) to tile))

        val jungleState = standardState.copy(mapPreset = MapPreset.FUNGAL_JUNGLE)

        val afterStandard = StructureEngine.useStructureAbility(standardState, HexCoordinate(0, 0))
        val afterJungle = StructureEngine.useStructureAbility(jungleState, HexCoordinate(0, 0))

        val standardMyc = afterStandard.currentPlayer.resources[Resource.MYCELIUM] ?: 0
        val jungleMyc = afterJungle.currentPlayer.resources[Resource.MYCELIUM] ?: 0
        val jungleLichen = afterJungle.currentPlayer.resources[Resource.LICHEN] ?: 0

        assertEquals("Spore Burst on Standard yields +2 Mycelium", 2, standardMyc)
        assertEquals("Spore Burst on Jungle yields +3 Mycelium",   3, jungleMyc)
        assertEquals("Spore Burst on Jungle yields +1 Lichen",     1, jungleLichen)
    }
}
