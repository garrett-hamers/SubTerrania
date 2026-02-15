package com.atlyn.subterranea.domain.model

data class HexTile(
    val coordinate: HexCoordinate,
    val zone: Zone,
    val isRevealed: Boolean = false,
    val terrain: TerrainType = TerrainType.UNKNOWN,
    val numberToken: Int? = null,
    val hasRubble: Boolean = false,
    val isIlluminated: Boolean = false,
    val presetHint: String? = null // Used for map preset event generation
) {
    fun produce(): Resource? {
        if (!isRevealed || hasRubble || !isIlluminated) return null
        return terrain.produces
    }
}

enum class TerrainType(val produces: Resource?) {
    UNKNOWN(null),
    FUNGAL_FOREST(Resource.MYCELIUM),
    BASALT_QUARRY(Resource.BASALT),
    BEETLE_FARM(Resource.CHITIN),
    LICHEN_FIELD(Resource.LICHEN),
    IRON_VEIN(Resource.IRON_ORE),
    CRYSTAL_GROTTO(Resource.CRYSTAL),
    MAGMA_FLOW(null), // Hazard/Geothermal
    BEDROCK(null); // Unminable

    fun displayName(): String = name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
