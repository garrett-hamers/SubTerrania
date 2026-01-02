package com.axialgalileo.subterranea.domain.model

data class GameState(
    val board: Map<HexCoordinate, HexTile> = emptyMap(),
    val turnNumber: Int = 1,
    // Add players, etc later
)
