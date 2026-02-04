package com.atlyn.subterranea.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.atlyn.subterranea.R
import com.atlyn.subterranea.domain.model.Resource
import com.atlyn.subterranea.domain.model.StructureType
import com.atlyn.subterranea.domain.model.TerrainType

/**
 * Helper object for loading custom vector drawable icons in Compose.
 * Replaces emoji-based icons with crisp vector graphics.
 */
object IconHelper {
    
    /**
     * Get the drawable resource ID for a Resource type
     */
    fun getResourceIconRes(resource: Resource): Int {
        return when (resource) {
            Resource.MYCELIUM -> R.drawable.ic_mycelium
            Resource.BASALT -> R.drawable.ic_basalt
            Resource.CHITIN -> R.drawable.ic_chitin
            Resource.LICHEN -> R.drawable.ic_lichen
            Resource.IRON_ORE -> R.drawable.ic_iron_ore
            Resource.CRYSTAL -> R.drawable.ic_crystal
        }
    }
    
    /**
     * Get the drawable resource ID for a TerrainType
     */
    fun getTerrainIconRes(terrain: TerrainType): Int {
        return when (terrain) {
            TerrainType.FUNGAL_FOREST -> R.drawable.ic_terrain_fungal_forest
            TerrainType.BASALT_QUARRY -> R.drawable.ic_terrain_basalt_quarry
            TerrainType.BEETLE_FARM -> R.drawable.ic_terrain_beetle_farm
            TerrainType.LICHEN_FIELD -> R.drawable.ic_terrain_lichen_field
            TerrainType.IRON_VEIN -> R.drawable.ic_terrain_iron_vein
            TerrainType.CRYSTAL_GROTTO -> R.drawable.ic_terrain_crystal_grotto
            TerrainType.MAGMA_FLOW -> R.drawable.ic_terrain_magma_flow
            TerrainType.BEDROCK -> R.drawable.ic_terrain_bedrock
            TerrainType.UNKNOWN -> R.drawable.ic_terrain_unknown
        }
    }
    
    /**
     * Get the drawable resource ID for a StructureType
     */
    fun getStructureIconRes(structure: StructureType): Int {
        return when (structure) {
            StructureType.LANTERN -> R.drawable.ic_structure_lantern
            StructureType.OUTPOST -> R.drawable.ic_structure_outpost
            StructureType.EXCAVATOR -> R.drawable.ic_structure_excavator
            StructureType.FUNGAL_FARM -> R.drawable.ic_structure_fungal_farm
            StructureType.BEETLE_STABLE -> R.drawable.ic_structure_beetle_stable
            StructureType.CRYSTAL_REFINERY -> R.drawable.ic_structure_crystal_refinery
            StructureType.CORE_ANCHOR -> R.drawable.ic_structure_core_anchor
        }
    }
    
    /**
     * Composable helper to get a Painter for a Resource
     */
    @Composable
    fun resourcePainter(resource: Resource): Painter {
        return painterResource(id = getResourceIconRes(resource))
    }
    
    /**
     * Composable helper to get a Painter for a TerrainType
     */
    @Composable
    fun terrainPainter(terrain: TerrainType): Painter {
        return painterResource(id = getTerrainIconRes(terrain))
    }
    
    /**
     * Composable helper to get a Painter for a StructureType
     */
    @Composable
    fun structurePainter(structure: StructureType): Painter {
        return painterResource(id = getStructureIconRes(structure))
    }
}
