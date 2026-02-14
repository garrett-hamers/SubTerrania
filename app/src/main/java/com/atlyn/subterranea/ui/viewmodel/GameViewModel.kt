package com.atlyn.subterranea.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.model.*
import com.atlyn.subterranea.ui.audio.GameSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "GameVM"
private const val PREFS_NAME = "subterranea_meta"

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()
    
    // Trade menu state
    private val _showTradeMenu = MutableStateFlow(false)
    val showTradeMenu: StateFlow<Boolean> = _showTradeMenu.asStateFlow()
    
    // Difficulty selection state (shown at start)
    private val _showDifficultyMenu = MutableStateFlow(true)
    val showDifficultyMenu: StateFlow<Boolean> = _showDifficultyMenu.asStateFlow()
    
    // Character selection state
    private val _showCharacterMenu = MutableStateFlow(false)
    val showCharacterMenu: StateFlow<Boolean> = _showCharacterMenu.asStateFlow()
    
    // Map preset selection state
    private val _showMapPresetMenu = MutableStateFlow(false)
    val showMapPresetMenu: StateFlow<Boolean> = _showMapPresetMenu.asStateFlow()
    
    // Current difficulty
    private val _currentDifficulty = MutableStateFlow(Difficulty.NORMAL)
    val currentDifficulty: StateFlow<Difficulty> = _currentDifficulty.asStateFlow()
    
    // Selected character
    private val _selectedCharacter = MutableStateFlow(GameCharacter.EXPLORER)
    val selectedCharacter: StateFlow<GameCharacter> = _selectedCharacter.asStateFlow()
    
    // Selected map preset
    private val _selectedMapPreset = MutableStateFlow(MapPreset.STANDARD)
    val selectedMapPreset: StateFlow<MapPreset> = _selectedMapPreset.asStateFlow()
    
    // Meta-progression (persisted across games)
    private val _metaProgression = MutableStateFlow(loadMetaProgression())
    val metaProgression: StateFlow<MetaProgression> = _metaProgression.asStateFlow()
    
    // Fast mode toggle
    private val _fastModeEnabled = MutableStateFlow(false)
    val fastModeEnabled: StateFlow<Boolean> = _fastModeEnabled.asStateFlow()
    
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
        // Skip sounds in fast mode except victory
        if (_fastModeEnabled.value && sound != GameSound.VICTORY) return
        _soundEvent.value = sound
    }

    init {
        // Don't initialize game yet - wait for difficulty selection
    }
    
    // Character selection
    fun showCharacterSelection() {
        _showCharacterMenu.value = true
        playSound(GameSound.BUTTON_TAP)
    }
    
    fun selectCharacter(character: GameCharacter) {
        if (character in _metaProgression.value.unlockedCharacters) {
            _selectedCharacter.value = character
            _showCharacterMenu.value = false
            playSound(GameSound.BUTTON_TAP)
        }
    }
    
    fun dismissCharacterMenu() {
        _showCharacterMenu.value = false
    }
    
    // Map preset selection
    fun showMapPresetSelection() {
        _showMapPresetMenu.value = true
        playSound(GameSound.BUTTON_TAP)
    }
    
    fun selectMapPreset(preset: MapPreset) {
        _selectedMapPreset.value = preset
        _showMapPresetMenu.value = false
        playSound(GameSound.BUTTON_TAP)
    }
    
    fun dismissMapPresetMenu() {
        _showMapPresetMenu.value = false
    }
    
    // Fast mode toggle
    fun toggleFastMode() {
        _fastModeEnabled.value = !_fastModeEnabled.value
        _metaProgression.update { it.copy(fastModeEnabled = _fastModeEnabled.value) }
        saveMetaProgression(_metaProgression.value)
        playSound(GameSound.BUTTON_TAP)
    }
    
    // Get unlocked characters
    fun getUnlockedCharacters(): List<GameCharacter> {
        return GameCharacter.entries.filter { it in _metaProgression.value.unlockedCharacters }
    }
    
    // Check if character is unlocked
    fun isCharacterUnlocked(character: GameCharacter): Boolean {
        return character in _metaProgression.value.unlockedCharacters
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
        val character = _selectedCharacter.value
        val metaProg = _metaProgression.value
        val mapPreset = _selectedMapPreset.value
        
        // Generate board based on map preset
        val newBoard = BoardGenerator.generateBoard(mapPreset)
        
        // Apply character bonuses to starting resources
        var startingResources = character.applyToResources(difficulty.startingResources)
        
        // Apply meta-progression bonuses
        val bonuses = metaProg.getStartingBonuses()
        startingResources = startingResources.toMutableMap().apply {
            this[Resource.CRYSTAL] = (this[Resource.CRYSTAL] ?: 0) + bonuses.bonusCrystal
            this[Resource.IRON_ORE] = (this[Resource.IRON_ORE] ?: 0) + bonuses.bonusIron
        }
        
        // Modify max actions based on character
        val maxActions = character.modifyMaxActions(difficulty.maxActionsPerTurn)
        
        // Can explore multiple times?
        val canExploreMultiple = character.canExploreMultiple() || difficulty.multipleExploresPerTurn
        
        val player = Player(
            id = 0, 
            name = character.displayName,
            resources = startingResources
        )
        
        _uiState.update { 
            GameState(
                board = newBoard, 
                players = listOf(player),
                difficulty = difficulty,
                maxActionsPerTurn = maxActions,
                victoryPointsToWin = difficulty.victoryPointsToWin,
                showTutorial = difficulty.showTutorial,
                selectedCharacter = character,
                fastModeEnabled = _fastModeEnabled.value,
                mapPreset = mapPreset,
                bonusActionsFirstTurn = bonuses.bonusActionsFirstTurn,
                firstStructureDiscount = bonuses.structureDiscount
            ).addEvent("🎮 Game started on ${difficulty.emoji} ${difficulty.displayName}!")
             .addEvent("${character.emoji} Playing as ${character.displayName}")
             .addEvent("💡 Tip: Build a Lantern to light up dark tiles!")
        }
    }
    
    fun resetGame() {
        // Fully reset all state to prevent UI overlay issues
        _showDifficultyMenu.value = true
        _showCharacterMenu.value = false
        _showMapPresetMenu.value = false
        _showTradeMenu.value = false
        _soundEvent.value = null
        _diceRollTrigger.value = 0L
        _productionTrigger.value = 0L
        _buildTrigger.value = 0L
        _uiState.value = GameState() // Use .value instead of update to force complete reset
    }
    
    /**
     * Record game end and update meta-progression
     */
    private fun recordGameEnd(won: Boolean) {
        val state = _uiState.value
        val player = state.currentPlayer
        val vpEarned = player.calculateVictoryPoints() + player.victoryPoints
        
        _metaProgression.update { current ->
            var updated = current.recordGameEnd(won, vpEarned, player.achievements)
            
            // Check for character unlocks
            if (won && state.difficulty == Difficulty.NORMAL && 
                GameCharacter.PROSPECTOR !in updated.unlockedCharacters) {
                updated = updated.unlockCharacter(GameCharacter.PROSPECTOR)
            }
            if (won && state.difficulty == Difficulty.HARD &&
                GameCharacter.SURVIVOR !in updated.unlockedCharacters) {
                updated = updated.unlockCharacter(GameCharacter.SURVIVOR)
            }
            
            // Unlock Scout after 50 total explorations
            val totalExplores = updated.lifetimeAchievements.count { it == Achievement.DEEP_DELVER } * 10 + 
                                player.explorationCount
            if (totalExplores >= 50 && GameCharacter.SCOUT !in updated.unlockedCharacters) {
                updated = updated.unlockCharacter(GameCharacter.SCOUT)
            }
            
            // Unlock Engineer after 20 structures
            val totalStructures = updated.lifetimeAchievements.count { it == Achievement.MASTER_BUILDER } * 5 +
                                  player.structuresBuilt.size
            if (totalStructures >= 20 && GameCharacter.ENGINEER !in updated.unlockedCharacters) {
                updated = updated.unlockCharacter(GameCharacter.ENGINEER)
            }
            
            updated
        }
        
        // Persist to disk
        saveMetaProgression(_metaProgression.value)
    }
    
    /**
     * Handle interactive event choice
     */
    fun handleEventChoice(choiceId: String) {
        val state = _uiState.value
        val event = state.pendingInteractiveEvent ?: return
        val coord = state.pendingEventCoord ?: return
        
        _uiState.update { currentState ->
            GameEngine.resolveInteractiveEvent(currentState, event, choiceId, coord)
        }
        playSound(GameSound.BUTTON_TAP)
    }
    
    /**
     * Dismiss interactive event (cancel)
     */
    fun dismissInteractiveEvent() {
        _uiState.update { it.copy(pendingInteractiveEvent = null, pendingEventCoord = null) }
    }
    
    /**
     * Handle consolation choice after a non-producing roll
     */
    fun handleConsolationChoice(choice: RollConsolation) {
        _uiState.update { currentState ->
            GameEngine.resolveConsolation(currentState, choice)
        }
        
        // Trigger production animation if resource was gained
        if (_uiState.value.lastProduction.isNotEmpty()) {
            playSound(GameSound.RESOURCE_GAIN)
            _productionTrigger.value = System.currentTimeMillis()
        } else {
            playSound(GameSound.BUTTON_TAP)
        }
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
                    // Safety check: Must be selected first to explore (or use Explore button)
                    if (state.selectedTile == coord) {
                        println("TILE_CLICK: Exploring tile")
                        return@update GameEngine.exploreTile(state, coord).copy(
                            selectedTile = coord,
                            showBuildMenu = false
                        )
                    }
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

    fun exploreSelectedTile() {
        val state = _uiState.value
        val coord = state.selectedTile ?: return
        val tile = state.board[coord] ?: return
        
        if (state.turnPhase == TurnPhase.MAIN_ACTION && !tile.isRevealed) {
             if (state.canExploreThisTurn && state.actionsThisTurn < state.maxActionsPerTurn) {
                playSound(GameSound.EXPLORE)
                _uiState.update { currentState ->
                    GameEngine.exploreTile(currentState, coord).copy(
                        showBuildMenu = false
                    )
                }
             }
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
            recordGameEnd(true)
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
                recordGameEnd(true)
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
    
    fun useStructureAbility(location: HexCoordinate) {
        playSound(GameSound.BUTTON_TAP)
        _uiState.update { currentState ->
            GameEngine.useStructureAbility(currentState, location)
        }
    }
    
    fun getUsableAbilities(): List<Structure> {
        return GameEngine.getUsableAbilities(_uiState.value)
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
    
    // --- Persistence ---
    
    private fun getPrefs() = getApplication<Application>()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private fun loadMetaProgression(): MetaProgression {
        val prefs = getPrefs()
        val achievementNames = prefs.getStringSet("achievements", emptySet()) ?: emptySet()
        val achievements = achievementNames.mapNotNull { name ->
            try { Achievement.valueOf(name) } catch (_: Exception) { null }
        }.toSet()
        
        val characterNames = prefs.getStringSet("characters", setOf("EXPLORER")) ?: setOf("EXPLORER")
        val characters = characterNames.mapNotNull { name ->
            try { GameCharacter.valueOf(name) } catch (_: Exception) { null }
        }.toSet()
        
        return MetaProgression(
            lifetimeAchievements = achievements,
            gamesPlayed = prefs.getInt("gamesPlayed", 0),
            gamesWon = prefs.getInt("gamesWon", 0),
            totalVPEarned = prefs.getInt("totalVPEarned", 0),
            unlockedCharacters = characters,
            fastModeEnabled = prefs.getBoolean("fastMode", false)
        )
    }
    
    private fun saveMetaProgression(meta: MetaProgression) {
        getPrefs().edit()
            .putStringSet("achievements", meta.lifetimeAchievements.map { it.name }.toSet())
            .putStringSet("characters", meta.unlockedCharacters.map { it.name }.toSet())
            .putInt("gamesPlayed", meta.gamesPlayed)
            .putInt("gamesWon", meta.gamesWon)
            .putInt("totalVPEarned", meta.totalVPEarned)
            .putBoolean("fastMode", meta.fastModeEnabled)
            .apply()
    }
}
