package com.axialgalileo.subterranea.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.axialgalileo.subterranea.domain.logic.BoardGenerator
import com.axialgalileo.subterranea.domain.logic.GameEngine
import com.axialgalileo.subterranea.domain.model.*
import com.axialgalileo.subterranea.ui.audio.GameSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "GameVM"

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()
    
    // Trade menu state
    private val _showTradeMenu = MutableStateFlow(false)
    val showTradeMenu: StateFlow<Boolean> = _showTradeMenu.asStateFlow()
    
    // Difficulty selection state (shown at start)
    private val _showDifficultyMenu = MutableStateFlow(true)
    val showDifficultyMenu: StateFlow<Boolean> = _showDifficultyMenu.asStateFlow()
    
    // Current difficulty
    private val _currentDifficulty = MutableStateFlow(Difficulty.NORMAL)
    val currentDifficulty: StateFlow<Difficulty> = _currentDifficulty.asStateFlow()
    
    // Sound effect events - observed by UI to play sounds
    private val _soundEvent = MutableStateFlow<GameSound?>(null)
    val soundEvent: StateFlow<GameSound?> = _soundEvent.asStateFlow()
    
    // Dice animation trigger
    private val _diceRollTrigger = MutableStateFlow(0L)
    val diceRollTrigger: StateFlow<Long> = _diceRollTrigger.asStateFlow()
    
    // Production animation trigger (resource floating up)
    private val _productionTrigger = MutableStateFlow(0L)
    val productionTrigger: StateFlow<Long> = _productionTrigger.asStateFlow()
    
    // Build animation trigger
    private val _buildTrigger = MutableStateFlow(0L)
    val buildTrigger: StateFlow<Long> = _buildTrigger.asStateFlow()
    
    fun clearSoundEvent() {
        _soundEvent.value = null
    }
    
    private fun playSound(sound: GameSound) {
        _soundEvent.value = sound
    }

    init {
        // Don't initialize game yet - wait for difficulty selection
    }
    
    /**
     * Start game with selected difficulty
     */
    fun startGameWithDifficulty(difficulty: Difficulty) {
        _currentDifficulty.value = difficulty
        _showDifficultyMenu.value = false
        initializeGame(difficulty)
        playSound(GameSound.BUTTON_TAP)
    }

    private fun initializeGame(difficulty: Difficulty = _currentDifficulty.value) {
        val newBoard = BoardGenerator.generateBoard()
        
        val player = Player(
            id = 0, 
            name = "Explorer",
            resources = difficulty.startingResources
        )
        
        _uiState.update { 
            GameState(
                board = newBoard, 
                players = listOf(player),
                difficulty = difficulty,
                maxActionsPerTurn = difficulty.maxActionsPerTurn,
                victoryPointsToWin = difficulty.victoryPointsToWin,
                showTutorial = difficulty.showTutorial
            ).addEvent("🎮 Game started on ${difficulty.emoji} ${difficulty.displayName}!")
             .addEvent("💡 Tip: Build a Lantern to light up dark tiles!")
        }
    }
    
    fun resetGame() {
        _showDifficultyMenu.value = true
        _uiState.update { GameState() }
    }

    fun onTileClicked(coord: HexCoordinate) {
        println("TILE_CLICK: coord=$coord, currentPhase=${_uiState.value.turnPhase}")
        val wasExplored = _uiState.value.board[coord]?.isRevealed == false
        
        _uiState.update { state ->
            val tile = state.board[coord]
            println("TILE_CLICK: tile exists=${tile != null}")
            if (tile == null) return@update state
            
            // If in main action phase and tile is not revealed, try to explore
            if (state.turnPhase == TurnPhase.MAIN_ACTION && !tile.isRevealed) {
                if (state.canExploreThisTurn && state.actionsThisTurn < state.maxActionsPerTurn) {
                    // Explore the tile and select it
                    println("TILE_CLICK: Exploring tile")
                    return@update GameEngine.exploreTile(state, coord).copy(
                        selectedTile = coord,
                        showBuildMenu = false
                    )
                }
            }
            
            // Otherwise just select the tile
            println("TILE_CLICK: Selecting tile $coord")
            state.copy(
                selectedTile = coord,
                showBuildMenu = false
            )
        }
        
        // Play appropriate sound
        val newTile = _uiState.value.board[coord]
        if (wasExplored && newTile?.isRevealed == true) {
            playSound(GameSound.EXPLORE)
        } else {
            playSound(GameSound.TILE_SELECT)
        }
    }
    
    fun rollDice() {
        println("ROLL_DICE: called, currentPhase=${_uiState.value.turnPhase}")
        if (_uiState.value.turnPhase != TurnPhase.ROLL_DICE) {
            println("ROLL_DICE: wrong phase")
            return
        }
        
        // Trigger dice roll animation and sound
        playSound(GameSound.DICE_ROLL)
        _diceRollTrigger.value = System.currentTimeMillis()
        
        _uiState.update { currentState ->
            val result = GameEngine.rollDiceAndProduce(currentState)
            println("ROLL_DICE: rolled ${result.lastDiceResult}")
            result
        }
        
        // If resources were gained, trigger production animation
        if (_uiState.value.lastProduction.isNotEmpty()) {
            playSound(GameSound.RESOURCE_GAIN)
            _productionTrigger.value = System.currentTimeMillis()
        }
        
        // Check for victory
        if (_uiState.value.gameOver) {
            playSound(GameSound.VICTORY)
        }
    }
    
    fun buildStructure(structureType: StructureType) {
        val state = _uiState.value
        val location = state.selectedTile
        println("BUILD: type=$structureType, location=$location")
        
        if (location == null) {
            println("BUILD FAILED: No tile selected!")
            playSound(GameSound.ERROR)
            return
        }
        
        val previousStructureCount = _uiState.value.structures.size
        
        _uiState.update { currentState ->
            val result = GameEngine.buildStructure(currentState, structureType, location)
            println("BUILD RESULT: structures=${result.structures.size}, VP=${result.currentPlayer.calculateVictoryPoints()}, gameOver=${result.gameOver}")
            result.copy(showBuildMenu = false, selectedTile = null)
        }
        
        // Play sound if structure was actually built
        if (_uiState.value.structures.size > previousStructureCount) {
            playSound(GameSound.BUILD_STRUCTURE)
            _buildTrigger.value = System.currentTimeMillis()
            
            // Check for victory after building
            if (_uiState.value.gameOver) {
                playSound(GameSound.VICTORY)
            }
        }
    }
    
    fun toggleBuildMenu() {
        val state = _uiState.value
        println("TOGGLE_BUILD: selectedTile=${state.selectedTile}, phase=${state.turnPhase}, actions=${state.actionsThisTurn}/${state.maxActionsPerTurn}")
        playSound(GameSound.BUTTON_TAP)
        _uiState.update { it.copy(showBuildMenu = !it.showBuildMenu) }
    }
    
    // Trading functions
    fun toggleTradeMenu() {
        playSound(GameSound.BUTTON_TAP)
        _showTradeMenu.value = !_showTradeMenu.value
    }
    
    fun tradeResources(give: Resource, receive: Resource) {
        playSound(GameSound.TRADE)
        _uiState.update { currentState ->
            GameEngine.tradeResources(currentState, give, receive)
        }
    }
    
    fun canTrade(): Boolean = GameEngine.canTrade(_uiState.value)
    
    fun getTradableResources(): List<Resource> = GameEngine.getTradableResources(_uiState.value)
    
    fun clearRubble() {
        val state = _uiState.value
        val location = state.selectedTile ?: return
        
        playSound(GameSound.BUILD_STRUCTURE)
        _uiState.update { currentState ->
            GameEngine.clearRubble(currentState, location)
        }
    }
    
    fun endTurn() {
        playSound(GameSound.TURN_END)
        _uiState.update { currentState ->
            GameEngine.endTurn(currentState)
        }
    }
    
    fun dismissEvent() {
        _uiState.update { it.copy(lastExplorationEvent = null) }
    }
    
    fun getAvailableStructures(): List<StructureType> {
        val state = _uiState.value
        val player = state.currentPlayer
        val location = state.selectedTile
        Log.d(TAG, "getAvailableStructures: selectedTile=$location")
        
        if (location == null) return emptyList()
        val tile = state.board[location] ?: return emptyList()
        
        Log.d(TAG, "Tile at $location: revealed=${tile.isRevealed}, terrain=${tile.terrain}")
        Log.d(TAG, "Player resources: ${player.resources}")
        
        if (!tile.isRevealed || 
            tile.terrain == TerrainType.MAGMA_FLOW || 
            tile.terrain == TerrainType.BEDROCK) {
            Log.d(TAG, "Tile not valid for building")
            return emptyList()
        }
        
        val existingStructure = state.getStructureAt(location)
        Log.d(TAG, "Existing structure: $existingStructure")
        
        val available = StructureType.entries.filter { type ->
            val canAfford = player.canAfford(type.cost)
            val canPlace = when {
                type == StructureType.EXCAVATOR -> existingStructure?.type == StructureType.OUTPOST
                existingStructure != null -> false
                else -> true
            }
            Log.d(TAG, "  $type: canAfford=$canAfford, canPlace=$canPlace")
            canAfford && canPlace
        }
        Log.d(TAG, "Available structures: $available")
        return available
    }
}
