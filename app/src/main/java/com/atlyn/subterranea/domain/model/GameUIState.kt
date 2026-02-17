package com.atlyn.subterranea.domain.model

data class GameUIState(
    val selectedTile: HexCoordinate? = null,
    val showBuildMenu: Boolean = false,
    val showTutorial: Boolean = Difficulty.NORMAL.showTutorial,
    val fastModeEnabled: Boolean = false
)
