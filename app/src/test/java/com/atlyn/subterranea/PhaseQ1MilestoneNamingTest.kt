package com.atlyn.subterranea

import com.atlyn.subterranea.domain.logic.ExplorationEngine
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.logic.StructureEngine
import com.atlyn.subterranea.domain.model.Achievement
import com.atlyn.subterranea.domain.model.Difficulty
import com.atlyn.subterranea.domain.model.GameCharacter
import com.atlyn.subterranea.domain.model.GameState
import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.HexTile
import com.atlyn.subterranea.domain.model.MapPreset
import com.atlyn.subterranea.domain.model.MetaProgression
import com.atlyn.subterranea.domain.model.Player
import com.atlyn.subterranea.domain.model.Resource
import com.atlyn.subterranea.domain.model.Structure
import com.atlyn.subterranea.domain.model.StructureType
import com.atlyn.subterranea.domain.model.TerrainType
import com.atlyn.subterranea.domain.model.TurnPhase
import com.atlyn.subterranea.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase Q-1.1: milestone-naming regression tests.
 *
 * After Phase Q, per-game milestone events are prefixed with
 *   "🎯 Milestone: <thing>! +N VP"
 * and lifetime first-time unlocks emit a separate
 *   "🏆 Achievement Unlocked: <name> — <description>"
 * event. Legacy "🏆 Achievement:" strings (without "Unlocked") must NOT
 * appear in any production code path.
 */
class PhaseQ1MilestoneNamingTest {

    // --- helpers ---------------------------------------------------------

    private fun makePlayer(
        explorationCount: Int = 0,
        achievements: Set<Achievement> = emptySet(),
        resources: Map<Resource, Int> = Resource.entries.associateWith { 10 }
    ) = Player(id = 0, name = "Test", resources = resources, achievements = achievements,
        explorationCount = explorationCount)

    /**
     * Build a tiny board where (0,0) is a revealed Bedrock base and (1,0) is
     * an unrevealed tile of the given zone. The home base means there's a
     * revealed neighbor, so the unrevealed tile can be explored.
     */
    private fun makeBoardWithFrontier(zone: Zone): Map<HexCoordinate, HexTile> {
        val base = HexCoordinate(0, 0)
        val frontier = HexCoordinate(1, 0)
        return mapOf(
            base to HexTile(
                coordinate = base, terrain = TerrainType.BEDROCK, zone = Zone.SURFACE,
                isRevealed = true, isIlluminated = true
            ),
            frontier to HexTile(
                coordinate = frontier, terrain = TerrainType.LICHEN_FIELD, zone = zone,
                isRevealed = false, isIlluminated = false
            )
        )
    }

    private fun makeState(
        zone: Zone = Zone.MANTLE,
        player: Player = makePlayer(),
        difficulty: Difficulty = Difficulty.EASY
    ) = GameState(
        board = makeBoardWithFrontier(zone),
        players = listOf(player),
        currentPlayerIndex = 0,
        turnNumber = 3,
        turnPhase = TurnPhase.MAIN_ACTION,
        actionsThisTurn = 0,
        maxActionsPerTurn = 2,
        difficulty = difficulty,
        victoryPointsToWin = difficulty.victoryPointsToWin,
        selectedCharacter = GameCharacter.EXPLORER,
        mapPreset = MapPreset.STANDARD,
        canExploreThisTurn = true,
        exploresThisTurn = 0
    )

    // --- per-game Milestone events --------------------------------------

    @Test
    fun `Mantle explore emits Milestone event with +1 VP`() {
        val state = makeState(zone = Zone.MANTLE)
        val frontier = HexCoordinate(1, 0)

        val after = ExplorationEngine.exploreTile(state, frontier)

        val milestone = after.eventLog.find { it.startsWith("🎯 Milestone:") }
        assertNotNull("Expected a 🎯 Milestone event after first Mantle explore", milestone)
        assertTrue("Milestone should mention Mantle, got: $milestone",
            milestone!!.contains("Mantle", ignoreCase = true))
        assertTrue("Milestone should embed +1 VP, got: $milestone",
            milestone.contains("+1 VP"))

        val legacy = after.eventLog.filter { it.startsWith("🏆 Achievement:") }
        assertEquals("No legacy '🏆 Achievement:' events should be emitted",
            emptyList<String>(), legacy)
    }

    @Test
    fun `Core explore emits Milestone with Core and +2 VP`() {
        val state = makeState(zone = Zone.CORE)
        val frontier = HexCoordinate(1, 0)

        val after = ExplorationEngine.exploreTile(state, frontier)

        val milestone = after.eventLog.find { it.contains("Core") && it.startsWith("🎯 Milestone:") }
        assertNotNull("Expected a 🎯 Milestone event mentioning Core, got: ${after.eventLog}", milestone)
        assertTrue("Core milestone should embed +2 VP", milestone!!.contains("+2 VP"))
    }

    @Test
    fun `Deep Delver milestone fires after 10 explorations`() {
        // Player who has already done 9 explores; next one tips them to 10.
        val player = makePlayer(explorationCount = 9)
        val state = makeState(zone = Zone.MANTLE, player = player)
        val frontier = HexCoordinate(1, 0)

        val after = ExplorationEngine.exploreTile(state, frontier)

        val deepDelver = after.eventLog.find { it.contains("10 tiles revealed") }
        assertNotNull("Expected Deep Delver milestone, got: ${after.eventLog}", deepDelver)
        assertTrue("Deep Delver should be a 🎯 Milestone event",
            deepDelver!!.startsWith("🎯 Milestone:"))
        assertTrue("Deep Delver awards +1 VP", deepDelver.contains("+1 VP"))
    }

    @Test
    fun `milestone event does NOT fire when player already has the achievement`() {
        val player = makePlayer(achievements = setOf(Achievement.FIRST_EXPLORER))
        val state = makeState(zone = Zone.MANTLE, player = player)
        val frontier = HexCoordinate(1, 0)

        val after = ExplorationEngine.exploreTile(state, frontier)

        val firstExplorer = after.eventLog.find { it.contains("First into the Mantle") }
        assertNull("First Explorer milestone should NOT re-fire if already earned this game",
            firstExplorer)
    }

    // --- lifetime Achievement Unlocked computation ----------------------

    @Test
    fun `computeNewlyUnlockedAchievements returns set difference`() {
        val meta = MetaProgression(
            lifetimeAchievements = setOf(Achievement.FIRST_EXPLORER, Achievement.DEEP_DELVER)
        )
        val earnedThisGame = setOf(
            Achievement.FIRST_EXPLORER,    // already lifetime: no event
            Achievement.DEEP_DELVER,       // already lifetime: no event
            Achievement.CORE_SEEKER        // NEW lifetime: event!
        )

        val newly = meta.computeNewlyUnlockedAchievements(earnedThisGame)

        assertEquals(setOf(Achievement.CORE_SEEKER), newly)
    }

    @Test
    fun `computeNewlyUnlockedAchievements returns empty when nothing new`() {
        val meta = MetaProgression(
            lifetimeAchievements = setOf(Achievement.FIRST_EXPLORER, Achievement.CORE_SEEKER)
        )
        val earnedThisGame = setOf(Achievement.FIRST_EXPLORER)

        assertTrue(meta.computeNewlyUnlockedAchievements(earnedThisGame).isEmpty())
    }

    @Test
    fun `computeNewlyUnlockedAchievements returns all when lifetime empty`() {
        val meta = MetaProgression(lifetimeAchievements = emptySet())
        val earnedThisGame = setOf(
            Achievement.FIRST_EXPLORER,
            Achievement.DEEP_DELVER
        )

        assertEquals(earnedThisGame, meta.computeNewlyUnlockedAchievements(earnedThisGame))
    }

    // --- structure-driven milestones (Master Builder, Illuminator) ------

    @Test
    fun `Master Builder milestone fires on 5th distinct structure type`() {
        // Player who already built 4 distinct structures; building a 5th
        // distinct type should fire the milestone with +2 VP.
        val priorStructures = listOf(
            Structure(StructureType.OUTPOST,   HexCoordinate(0, -1), ownerId = 0),
            Structure(StructureType.EXCAVATOR, HexCoordinate(1, -1), ownerId = 0),
            Structure(StructureType.LANTERN,   HexCoordinate(0,  1), ownerId = 0),
            Structure(StructureType.FUNGAL_FARM, HexCoordinate(-1, 1), ownerId = 0)
        )
        val player = makePlayer().copy(structuresBuilt = priorStructures)
        // Frontier tile for the new build: revealed Lichen Field at (2, 0).
        val board = mapOf(
            HexCoordinate(0, 0) to HexTile(
                coordinate = HexCoordinate(0, 0), terrain = TerrainType.BEDROCK,
                zone = Zone.SURFACE, isRevealed = true, isIlluminated = true
            ),
            HexCoordinate(2, 0) to HexTile(
                coordinate = HexCoordinate(2, 0), terrain = TerrainType.LICHEN_FIELD,
                zone = Zone.SURFACE, isRevealed = true, isIlluminated = true
            )
        )
        val state = makeState(player = player).copy(board = board)

        // Build a 5th distinct type: Beetle Stable.
        val after = StructureEngine.buildStructure(
            state, StructureType.BEETLE_STABLE, HexCoordinate(2, 0)
        )

        val milestone = after.eventLog.find { it.contains("5 distinct structures") }
        assertNotNull("Expected Master Builder milestone, got: ${after.eventLog}", milestone)
        assertTrue(milestone!!.startsWith("🎯 Milestone:"))
        assertTrue("Master Builder awards +2 VP", milestone.contains("+2 VP"))
    }

    // --- no production code emits legacy strings ------------------------

    @Test
    fun `no source-of-truth event string starts with the legacy Achievement prefix`() {
        // Smoke test: walk a single Mantle explore + a Lantern build chain and
        // assert no event in the log starts with the deprecated prefix.
        var state = makeState(zone = Zone.MANTLE)
        state = ExplorationEngine.exploreTile(state, HexCoordinate(1, 0))

        val legacyEvents = state.eventLog.filter { it.startsWith("🏆 Achievement:") }
        assertEquals("Phase Q-1.1: no '🏆 Achievement:' (without 'Unlocked') in event log",
            emptyList<String>(), legacyEvents)
    }
}
