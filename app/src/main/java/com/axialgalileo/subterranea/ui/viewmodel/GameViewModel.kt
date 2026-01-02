package com.axialgalileo.subterranea.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.axialgalileo.subterranea.domain.logic.BoardGenerator
import com.axialgalileo.subterranea.domain.model.GameState
import com.axialgalileo.subterranea.domain.model.HexCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    init {
        initializeGame()
    }

    private fun initializeGame() {
        val newBoard = BoardGenerator.generateBoard()
        _uiState.update { it.copy(board = newBoard) }
    }

    fun onTileClicked(coord: HexCoordinate) {
        // Placeholder interaction
        val tile = _uiState.value.board[coord] ?: return
        if (!tile.isRevealed) {
            // "Reveal" mechaninc mock
            val newTile = tile.copy(isRevealed = true, isIlluminated = true)
            val newBoard = _uiState.value.board.toMutableMap().apply {
                put(coord, newTile)
            }
             _uiState.update { it.copy(board = newBoard) }
        }
    }
}
