package com.atlyn.subterranea

import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.model.Difficulty
import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.HexTile
import com.atlyn.subterranea.domain.model.MapPreset
import com.atlyn.subterranea.domain.model.Resource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase O-1 regression tests:
 *   - o1b: Normal difficulty starting resources are bumped enough to allow
 *     at least one affordable build on turn 1 (Outpost / Fungal Farm /
 *     Beetle Stable).
 *   - o1e: BoardGenerator produces the same board for the same explicit
 *     seed (so the end-game "Replay this seed" CTA actually replays).
 */
class PhaseO1Test {

    // --- o1b ----------------------------------------------------------------

    @Test
    fun `Normal starting resources unlock at least one buildable structure`() {
        val r = Difficulty.NORMAL.startingResources

        // Outpost = 2 Basalt + 1 Mycelium + 1 Chitin
        val canBuildOutpost = (r[Resource.BASALT] ?: 0) >= 2 &&
            (r[Resource.MYCELIUM] ?: 0) >= 1 &&
            (r[Resource.CHITIN] ?: 0) >= 1

        // Fungal Farm = 2 Mycelium + 2 Lichen
        val canBuildFungal = (r[Resource.MYCELIUM] ?: 0) >= 2 &&
            (r[Resource.LICHEN] ?: 0) >= 2

        // Beetle Stable = 2 Chitin + 2 Lichen
        val canBuildBeetle = (r[Resource.CHITIN] ?: 0) >= 2 &&
            (r[Resource.LICHEN] ?: 0) >= 2

        assertTrue(
            "Normal turn 1 should allow at least one of Outpost / Fungal Farm / Beetle Stable",
            canBuildOutpost || canBuildFungal || canBuildBeetle
        )
    }

    @Test
    fun `Normal starting resources still keep Lantern materials at zero`() {
        // Lantern is the dominant strategy we're trying NOT to encourage on T1.
        val r = Difficulty.NORMAL.startingResources
        assertEquals(0, r[Resource.IRON_ORE] ?: 0)
        assertEquals(0, r[Resource.CRYSTAL] ?: 0)
    }

    @Test
    fun `Normal is still meaningfully harder than Easy on starting resources`() {
        val n = Difficulty.NORMAL.startingResources
        val e = Difficulty.EASY.startingResources
        // Easy still has Iron Ore as a starting resource; Normal does not.
        assertTrue(
            "Easy should still be more generous than Normal on at least one rare resource",
            (e[Resource.IRON_ORE] ?: 0) > (n[Resource.IRON_ORE] ?: 0) ||
                (e[Resource.CRYSTAL] ?: 0) > (n[Resource.CRYSTAL] ?: 0)
        )
    }

    // --- o1e ----------------------------------------------------------------

    @Test
    fun `BoardGenerator with the same seed produces the same board`() {
        val a = BoardGenerator.generateBoard(MapPreset.STANDARD, seed = 42L)
        val b = BoardGenerator.generateBoard(MapPreset.STANDARD, seed = 42L)
        assertEquals(a.size, b.size)
        for ((coord, tileA) in a) {
            val tileB = b[coord]
            requireNotNull(tileB) { "missing tile at $coord in second board" }
            assertEquals("zone differs at $coord", tileA.zone, tileB.zone)
            assertEquals("terrain differs at $coord", tileA.terrain, tileB.terrain)
            assertEquals("number differs at $coord", tileA.numberToken, tileB.numberToken)
            assertEquals("secondary differs at $coord", tileA.secondaryNumberToken, tileB.secondaryNumberToken)
            assertEquals("revealed differs at $coord", tileA.isRevealed, tileB.isRevealed)
        }
    }

    @Test
    fun `BoardGenerator with different seeds produces different boards`() {
        val a = BoardGenerator.generateBoard(MapPreset.STANDARD, seed = 1L)
        val b = BoardGenerator.generateBoard(MapPreset.STANDARD, seed = 999_999_999L)
        // We only need *some* tile to differ — terrain or number — for the
        // "fresh seed" path to feel like a genuinely new map.
        val anyDiff = a.entries.any { (coord, tileA) ->
            val tileB = b[coord]
            tileB == null ||
                tileA.terrain != tileB.terrain ||
                tileA.numberToken != tileB.numberToken
        }
        assertTrue("Two different seeds should produce different boards", anyDiff)
    }

    @Test
    fun `BoardGenerator with null seed still produces a valid board`() {
        // Default behavior (no explicit seed) must keep working — that's the
        // path the regular game start uses before we capture the seed.
        val board = BoardGenerator.generateBoard(MapPreset.STANDARD, seed = null)
        assertTrue("default-seed board should not be empty", board.isNotEmpty())
        // Sanity: there should be at least one revealed Surface tile (the spawn ring).
        assertTrue(
            "default-seed board should contain at least one revealed Surface tile",
            board.values.any { it.isRevealed }
        )
    }

    // --- BoardGenerator size sanity (defensive) ----------------------------

    @Test
    fun `BoardGenerator produces a non-trivial board`() {
        val board = BoardGenerator.generateBoard(MapPreset.STANDARD, seed = 7L)
        // The hex grid currently spans radius 4 → 61 hexes. Don't pin to an
        // exact number (the radius could change), but assert "lots of tiles".
        assertTrue("expected a sizable board, got ${board.size}", board.size >= 30)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun ignored(t: HexTile, c: HexCoordinate) {
        // Keep imports happy if we delete a check above.
    }
}
