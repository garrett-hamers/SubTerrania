package com.atlyn.subterranea.domain.persistence

import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.model.Achievement
import com.atlyn.subterranea.domain.model.DiceResult
import com.atlyn.subterranea.domain.model.Difficulty
import com.atlyn.subterranea.domain.model.GameCharacter
import com.atlyn.subterranea.domain.model.GameState
import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.MapPreset
import com.atlyn.subterranea.domain.model.Player
import com.atlyn.subterranea.domain.model.Resource
import com.atlyn.subterranea.domain.model.Structure
import com.atlyn.subterranea.domain.model.StructureType
import com.atlyn.subterranea.domain.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStatePersistenceTest {

    @Test
    fun roundTrip_preservesCoreFieldsForFreshGame() {
        val original = GameState(
            board = BoardGenerator.generateBoard(MapPreset.STANDARD),
            players = listOf(
                Player(
                    id = 0,
                    name = "Test Player",
                    resources = mapOf(
                        Resource.MYCELIUM to 3,
                        Resource.BASALT to 2,
                        Resource.CHITIN to 1,
                        Resource.LICHEN to 0,
                        Resource.IRON_ORE to 4,
                        Resource.CRYSTAL to 1
                    ),
                    victoryPoints = 0,
                    explorationCount = 0,
                    achievements = emptySet()
                )
            ),
            difficulty = Difficulty.HARD,
            selectedCharacter = GameCharacter.SCOUT,
            mapPreset = MapPreset.IRON_DEPTHS,
            turnPhase = TurnPhase.MAIN_ACTION,
            turnNumber = 1,
            actionsThisTurn = 0,
            maxActionsPerTurn = 2,
            victoryPointsToWin = Difficulty.HARD.victoryPointsToWin,
            canExploreThisTurn = true,
            exploresThisTurn = 0,
            gameOver = false,
            pendingConsolation = false,
            discountTradeAvailable = false
        )

        val json = GameStatePersistence.serialize(original)
        val restored = GameStatePersistence.deserialize(json)

        assertNotNull("Round-tripped state should not be null", restored)
        restored!!
        assertEquals(original.difficulty, restored.difficulty)
        assertEquals(original.selectedCharacter, restored.selectedCharacter)
        assertEquals(original.mapPreset, restored.mapPreset)
        assertEquals(original.turnPhase, restored.turnPhase)
        assertEquals(original.victoryPointsToWin, restored.victoryPointsToWin)
        assertEquals(original.maxActionsPerTurn, restored.maxActionsPerTurn)
        assertEquals(original.players.size, restored.players.size)
        assertEquals(original.players[0].resources, restored.players[0].resources)
        assertEquals(original.board.size, restored.board.size)
        assertNull(restored.lastExplorationEvent)
        assertNull(restored.pendingInteractiveEvent)
        assertNull(restored.pendingEventCoord)
    }

    @Test
    fun roundTrip_preservesMidGameProgression() {
        val coord = HexCoordinate(1, -1)
        val structure = Structure(
            type = StructureType.LANTERN,
            location = coord,
            ownerId = 0,
            abilityCooldown = 1,
            tilesIlluminated = 4
        )
        val original = GameState(
            board = BoardGenerator.generateBoard(MapPreset.STANDARD),
            players = listOf(
                Player(
                    id = 0,
                    name = "Mid Game",
                    resources = Resource.entries.associateWith { 2 },
                    victoryPoints = 3,
                    explorationCount = 6,
                    achievements = setOf(Achievement.FIRST_EXPLORER, Achievement.ILLUMINATOR),
                    structuresBuilt = listOf(structure)
                )
            ),
            structures = listOf(structure),
            difficulty = Difficulty.NORMAL,
            selectedCharacter = GameCharacter.PROSPECTOR,
            mapPreset = MapPreset.CRYSTAL_CAVES,
            turnPhase = TurnPhase.MAIN_ACTION,
            turnNumber = 7,
            actionsThisTurn = 1,
            maxActionsPerTurn = 2,
            victoryPointsToWin = 15,
            lastDiceResult = DiceResult(3, 4),
            lastProduction = mapOf(Resource.MYCELIUM to 1, Resource.IRON_ORE to 2),
            eventLog = listOf("Turn 7 begins", "Rolled a 7"),
            canExploreThisTurn = false,
            exploresThisTurn = 1,
            gameOver = false,
            pendingConsolation = false,
            discountTradeAvailable = true
        )

        val json = GameStatePersistence.serialize(original)
        val restored = GameStatePersistence.deserialize(json)

        assertNotNull(restored)
        restored!!
        assertEquals(original.turnNumber, restored.turnNumber)
        assertEquals(original.actionsThisTurn, restored.actionsThisTurn)
        assertEquals(original.lastDiceResult?.die1, restored.lastDiceResult?.die1)
        assertEquals(original.lastDiceResult?.die2, restored.lastDiceResult?.die2)
        assertEquals(original.lastProduction, restored.lastProduction)
        assertEquals(original.eventLog, restored.eventLog)
        assertEquals(original.discountTradeAvailable, restored.discountTradeAvailable)
        assertEquals(original.canExploreThisTurn, restored.canExploreThisTurn)
        assertEquals(original.exploresThisTurn, restored.exploresThisTurn)
        assertEquals(original.players[0].achievements, restored.players[0].achievements)
        assertEquals(1, restored.structures.size)
        assertEquals(StructureType.LANTERN, restored.structures[0].type)
        assertEquals(coord, restored.structures[0].location)
        assertEquals(4, restored.structures[0].tilesIlluminated)
    }

    @Test
    fun deserialize_returnsNullForInvalidJson() {
        assertNull(GameStatePersistence.deserialize("not-json"))
        assertNull(GameStatePersistence.deserialize(""))
    }

    @Test
    fun deserialize_returnsNullForOlderSchemaVersion() {
        val payload = "{\"v\":0,\"difficulty\":\"NORMAL\"}"
        assertNull(GameStatePersistence.deserialize(payload))
    }

    @Test
    fun serialize_producesParseableJson() {
        val state = GameState(
            board = BoardGenerator.generateBoard(MapPreset.STANDARD),
            difficulty = Difficulty.EASY
        )
        val json = GameStatePersistence.serialize(state)
        assertTrue("Serialized payload should be non-empty", json.isNotBlank())
        assertTrue("Should contain version marker", json.contains("\"v\":1"))
        assertTrue("Should contain difficulty", json.contains("EASY"))
    }
}
