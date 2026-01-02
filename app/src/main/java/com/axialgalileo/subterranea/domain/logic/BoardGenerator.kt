package com.axialgalileo.subterranea.domain.logic

import com.axialgalileo.subterranea.domain.model.*

object BoardGenerator {
    
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
        // Fixed layout for Zone 0 (simplified for now)
        // Center is Elevator (Generic/Desert equivalent or special)
        if (coord.q == 0 && coord.r == 0) {
            return HexTile(
                coord, 
                Zone.SURFACE, 
                isRevealed = true, 
                terrain = TerrainType.BEDROCK, // Elevator shaft
                isIlluminated = true
            )
        }
        
        // Surrounding 6 tiles: Basic Resources
        // For prototype, random basic terrain
        val basicTerrains = listOf(
            TerrainType.LICHEN_FIELD, TerrainType.FUNGAL_FOREST, 
            TerrainType.BASALT_QUARRY, TerrainType.BEETLE_FARM
        )
        // Deterministic for stability based on hash or just random
        val type = basicTerrains.random() 
        // Numbers 2,3,11,12 are common here per GDD
        val number = listOf(2, 3, 11, 12).random()
        
        return HexTile(
            coord, 
            Zone.SURFACE, 
            isRevealed = true, 
            terrain = type,
            numberToken = number,
            isIlluminated = true // Surface is always lit
        )
    }
}
