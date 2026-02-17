package com.atlyn.subterranea.domain.model

enum class Zone(
    val index: Int,
    val explorationTerrainPool: List<TerrainType>,
    val explorationNumberPool: List<Int>,
    val surveyNumberPool: List<Int>,
    val flareHintTerrains: List<String>,
    val interactiveEventChance: Int,
    val secondaryNumberPool: List<Int> = emptyList()
) {
    SURFACE(
        index = 0,
        explorationTerrainPool = listOf(
            TerrainType.LICHEN_FIELD,
            TerrainType.FUNGAL_FOREST,
            TerrainType.BASALT_QUARRY,
            TerrainType.BEETLE_FARM
        ),
        explorationNumberPool = listOf(3, 4, 5, 9, 10, 11),
        surveyNumberPool = listOf(4, 5, 9, 10),
        flareHintTerrains = listOf("Lichen Field", "Fungal Forest", "Quarry"),
        interactiveEventChance = 0
    ),
    CRUST(
        index = 1,
        explorationTerrainPool = listOf(
            TerrainType.LICHEN_FIELD,
            TerrainType.FUNGAL_FOREST,
            TerrainType.BASALT_QUARRY,
            TerrainType.BEETLE_FARM,
            TerrainType.IRON_VEIN,
            TerrainType.IRON_VEIN
        ),
        explorationNumberPool = listOf(4, 5, 6, 8, 9, 10),
        surveyNumberPool = listOf(5, 6, 8, 9),
        flareHintTerrains = listOf("Iron Vein", "Fungal Forest", "Quarry"),
        interactiveEventChance = 0,
        secondaryNumberPool = listOf(3, 4, 5, 9, 10, 11)
    ),
    MANTLE(
        index = 2,
        explorationTerrainPool = listOf(
            TerrainType.IRON_VEIN,
            TerrainType.IRON_VEIN,
            TerrainType.CRYSTAL_GROTTO,
            TerrainType.BASALT_QUARRY,
            TerrainType.MAGMA_FLOW,
            TerrainType.CRYSTAL_GROTTO
        ),
        explorationNumberPool = listOf(5, 6, 6, 8, 8, 9),
        surveyNumberPool = listOf(6, 6, 8, 8),
        flareHintTerrains = listOf("Crystal Grotto", "Iron Vein", "Magma"),
        interactiveEventChance = GameConstants.Probabilities.MANTLE_INTERACTIVE_EVENT_CHANCE
    ),
    CORE(
        index = 3,
        explorationTerrainPool = listOf(
            TerrainType.CRYSTAL_GROTTO,
            TerrainType.CRYSTAL_GROTTO,
            TerrainType.CRYSTAL_GROTTO,
            TerrainType.IRON_VEIN,
            TerrainType.MAGMA_FLOW,
            TerrainType.BEDROCK
        ),
        explorationNumberPool = listOf(6, 6, 7, 7, 8, 8),
        surveyNumberPool = listOf(6, 7, 8),
        flareHintTerrains = listOf("Crystal Grotto", "Ancient Chamber", "Magma"),
        interactiveEventChance = GameConstants.Probabilities.CORE_INTERACTIVE_EVENT_CHANCE
    ); // The objective

    companion object {
        fun fromDistance(distance: Int): Zone = when (distance) {
            0, 1 -> SURFACE
            2 -> CRUST
            3 -> MANTLE
            else -> CORE
        }
    }
}
