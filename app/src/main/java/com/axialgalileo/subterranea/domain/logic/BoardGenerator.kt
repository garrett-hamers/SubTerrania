package com.axialgalileo.subterranea.domain.logic

import com.axialgalileo.subterranea.domain.model.*

object BoardGenerator {
    
    // Balanced number distribution for 2d6 probability
    // 7 is most common (6/36), 6&8 (5/36), 5&9 (4/36), 4&10 (3/36), 3&11 (2/36), 2&12 (1/36)
    private val goodNumbers = listOf(5, 6, 8, 9) // High probability
    private val mediumNumbers = listOf(4, 5, 9, 10) // Medium probability
    private val rareNumbers = listOf(3, 4, 10, 11) // Lower probability
    
    fun generateBoard(): Map<HexCoordinate, HexTile> {
        val tiles = mutableMapOf<HexCoordinate, HexTile>()
        
        // Generate coordinates in a spiral
        val maxRadius = 4 // Zone 3 extends to radius 4 approx
        
        for (q in -maxRadius..maxRadius) {
            val r1 = maxOf(-maxRadius, -q - maxRadius)
            val r2 = minOf(maxRadius, -q + maxRadius)
            for (r in r1..r2) {
                val coord = HexCoordinate(q, r)
                val dist = coord.distanceTo(HexCoordinate(0, 0))
                
                val zone = when(dist) {
                    0, 1 -> Zone.SURFACE
                    2 -> Zone.CRUST
                    3 -> Zone.MANTLE
                    else -> Zone.CORE
                }
                
                // Assign tiles
                val tile = if (zone == Zone.SURFACE) {
                     generateSurfaceTile(coord)
                } else {
                    HexTile(coord, zone, isRevealed = false)
                }
                
                tiles[coord] = tile
            }
        }
        return tiles
    }

    private fun generateSurfaceTile(coord: HexCoordinate): HexTile {
        // Center is the Elevator shaft - base of operations
        if (coord.q == 0 && coord.r == 0) {
            return HexTile(
                coord, 
                Zone.SURFACE, 
                isRevealed = true, 
                terrain = TerrainType.BEDROCK, // Elevator shaft
                isIlluminated = true
            )
        }
        
        // Surrounding 6 tiles get ALL resource types for balanced early game
        // FIXED: Now includes Iron Vein and Crystal Grotto on surface!
        val terrainByPosition = mapOf(
            HexCoordinate(1, 0) to TerrainType.LICHEN_FIELD,
            HexCoordinate(0, 1) to TerrainType.FUNGAL_FOREST,
            HexCoordinate(-1, 1) to TerrainType.BASALT_QUARRY,
            HexCoordinate(-1, 0) to TerrainType.IRON_VEIN,      // Iron on surface!
            HexCoordinate(0, -1) to TerrainType.CRYSTAL_GROTTO, // Crystal on surface!
            HexCoordinate(1, -1) to TerrainType.BEETLE_FARM
        )
        
        // Give surface tiles GOOD numbers so early game is productive
        val numberByPosition = mapOf(
            HexCoordinate(1, 0) to 6,
            HexCoordinate(0, 1) to 8,
            HexCoordinate(-1, 1) to 5,
            HexCoordinate(-1, 0) to 6,  // Iron on good number
            HexCoordinate(0, -1) to 8,  // Crystal on good number
            HexCoordinate(1, -1) to 9
        )
        
        val terrain = terrainByPosition[coord] ?: TerrainType.LICHEN_FIELD
        val number = numberByPosition[coord] ?: goodNumbers.random()
        
        return HexTile(
            coord, 
            Zone.SURFACE, 
            isRevealed = true, 
            terrain = terrain,
            numberToken = number,
            isIlluminated = true // Surface is always lit
        )
    }
}
