package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*
import java.time.LocalDate
import kotlin.random.Random

object BoardGenerator {
    
    // Balanced number distribution for 2d6 probability
    // 7 is most common (6/36), 6&8 (5/36), 5&9 (4/36), 4&10 (3/36), 3&11 (2/36), 2&12 (1/36)
    private val goodNumbers = listOf(5, 6, 8, 9) // High probability
    private val mediumNumbers = listOf(4, 5, 9, 10) // Medium probability
    private val rareNumbers = listOf(3, 4, 10, 11) // Lower probability
    
    fun generateBoard(preset: MapPreset = MapPreset.STANDARD): Map<HexCoordinate, HexTile> {
        val tiles = mutableMapOf<HexCoordinate, HexTile>()
        
        // Get random seed based on preset
        val random = when (preset) {
            MapPreset.DAILY_CHALLENGE -> Random(LocalDate.now().toEpochDay())
            else -> Random.Default
        }
        
        // Shuffle surface tiles for variety each game
        shuffleSurfaceTiles(preset, random)
        
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
                     generateSurfaceTile(coord, preset)
                } else {
                    // For non-surface tiles, apply preset modifiers
                    generateUnexploredTile(coord, zone, preset, random)
                }
                
                tiles[coord] = tile
            }
        }
        return tiles
    }

    // Surface terrains - no Crystal on surface to encourage trading/exploration
    private val surfaceTerrains = listOf(
        TerrainType.LICHEN_FIELD,
        TerrainType.FUNGAL_FOREST,
        TerrainType.BASALT_QUARRY,
        TerrainType.IRON_VEIN,
        TerrainType.LICHEN_FIELD,
        TerrainType.BEETLE_FARM
    )
    
    // Surface coordinates (ring around center)
    private val surfaceCoords = listOf(
        HexCoordinate(1, 0),
        HexCoordinate(0, 1),
        HexCoordinate(-1, 1),
        HexCoordinate(-1, 0),
        HexCoordinate(0, -1),
        HexCoordinate(1, -1)
    )
    
    // Shuffled mappings regenerated each game
    private var shuffledTerrainMap: Map<HexCoordinate, TerrainType> = emptyMap()
    private var shuffledNumberMap: Map<HexCoordinate, Int> = emptyMap()
    private var shuffledSecondaryNumberMap: Map<HexCoordinate, Int> = emptyMap()
    
    private fun getSurfaceTerrainsForPreset(preset: MapPreset): List<TerrainType> {
        return when (preset) {
            MapPreset.CRYSTAL_CAVES -> listOf(
                TerrainType.CRYSTAL_GROTTO,
                TerrainType.CRYSTAL_GROTTO,
                TerrainType.LICHEN_FIELD,
                TerrainType.FUNGAL_FOREST,
                TerrainType.BASALT_QUARRY,
                TerrainType.BEETLE_FARM
            )
            MapPreset.IRON_DEPTHS -> listOf(
                TerrainType.IRON_VEIN,
                TerrainType.IRON_VEIN,
                TerrainType.BASALT_QUARRY,
                TerrainType.LICHEN_FIELD,
                TerrainType.FUNGAL_FOREST,
                TerrainType.BEETLE_FARM
            )
            MapPreset.FUNGAL_JUNGLE -> listOf(
                TerrainType.FUNGAL_FOREST,
                TerrainType.FUNGAL_FOREST,
                TerrainType.LICHEN_FIELD,
                TerrainType.LICHEN_FIELD,
                TerrainType.BASALT_QUARRY,
                TerrainType.BEETLE_FARM
            )
            MapPreset.VOLCANIC_CORE -> listOf(
                TerrainType.BASALT_QUARRY,
                TerrainType.BASALT_QUARRY,
                TerrainType.IRON_VEIN,
                TerrainType.CRYSTAL_GROTTO,
                TerrainType.LICHEN_FIELD,
                TerrainType.BEETLE_FARM
            )
            else -> surfaceTerrains
        }
    }
    
    private fun shuffleSurfaceTiles(preset: MapPreset, random: Random) {
        // Get terrains based on preset
        val terrainsForPreset = getSurfaceTerrainsForPreset(preset)
        
        // Shuffle terrains randomly
        val shuffledTerrains = terrainsForPreset.shuffled(random)
        shuffledTerrainMap = surfaceCoords.zip(shuffledTerrains).toMap()
        
        // Guarantee at least 2 tiles have a 6 or 8 for reliable early production
        val baseNumbers = listOf(6, 8, 5, 9, 4, 10).shuffled(random)
        val numberAssignment = surfaceCoords.zip(baseNumbers).toMap().toMutableMap()
        
        // Ensure at least one common resource tile gets a 6 or 8
        val commonTerrains = setOf(
            TerrainType.LICHEN_FIELD, TerrainType.FUNGAL_FOREST,
            TerrainType.BASALT_QUARRY, TerrainType.BEETLE_FARM
        )
        val commonCoords = shuffledTerrainMap.filter { it.value in commonTerrains }.keys
        val hasGoodNumberOnCommon = commonCoords.any { numberAssignment[it] in listOf(6, 8) }
        
        if (!hasGoodNumberOnCommon && commonCoords.isNotEmpty()) {
            // Swap: give a common resource tile a 6 or 8
            val targetCoord = commonCoords.random(random)
            val donorCoord = numberAssignment.entries
                .firstOrNull { it.value in listOf(6, 8) && it.key !in commonCoords }
                ?.key
            if (donorCoord != null) {
                val temp = numberAssignment[targetCoord]!!
                numberAssignment[targetCoord] = numberAssignment[donorCoord]!!
                numberAssignment[donorCoord] = temp
            }
        }
        
        shuffledNumberMap = numberAssignment
        
        // Secondary numbers from complementary range for better roll coverage
        val secondaryPool = listOf(3, 4, 5, 9, 10, 11).shuffled(random)
        shuffledSecondaryNumberMap = surfaceCoords.zip(secondaryPool).toMap()
    }
    
    private fun generateUnexploredTile(
        coord: HexCoordinate, 
        zone: Zone, 
        preset: MapPreset,
        random: Random
    ): HexTile {
        // Store preset hint for exploration event generation
        val presetHint = when (preset) {
            MapPreset.CRYSTAL_CAVES -> "crystal_rich"
            MapPreset.IRON_DEPTHS -> "iron_rich"
            MapPreset.FUNGAL_JUNGLE -> "organic_rich"
            MapPreset.VOLCANIC_CORE -> "hazardous"
            else -> null
        }
        
        return HexTile(
            coordinate = coord, 
            zone = zone, 
            isRevealed = false,
            presetHint = presetHint
        )
    }

    private fun generateSurfaceTile(coord: HexCoordinate, preset: MapPreset): HexTile {
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
        
        // Use shuffled terrain and number assignments for replayability
        val terrain = shuffledTerrainMap[coord] ?: surfaceTerrains.random()
        val number = shuffledNumberMap[coord] ?: goodNumbers.random()
        val secondaryNumber = shuffledSecondaryNumberMap[coord]
        
        return HexTile(
            coord, 
            Zone.SURFACE, 
            isRevealed = true, 
            terrain = terrain,
            numberToken = number,
            secondaryNumberToken = secondaryNumber,
            isIlluminated = true // Surface is always lit
        )
    }
}
