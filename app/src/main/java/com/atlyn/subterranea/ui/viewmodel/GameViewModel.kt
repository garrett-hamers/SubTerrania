package com.atlyn.subterranea.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.model.*
import com.atlyn.subterranea.domain.persistence.GameStatePersistence
import com.atlyn.subterranea.domain.telemetry.GameTelemetry
import com.atlyn.subterranea.ui.audio.GameSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "GameVM"
private const val PREFS_NAME = "subterranea_meta"
private const val ACTIVE_GAME_PREFS = "subterranea_active_game"
private const val ACTIVE_GAME_KEY = "state_json"

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()
    private val _gameUIState = MutableStateFlow(GameUIState())
    val gameUIState: StateFlow<GameUIState> = _gameUIState.asStateFlow()
    
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
    
    // Whether a saved active game exists from a prior session (process death recovery).
    // Surfaced to the difficulty-selection UI as a "Resume" affordance.
    private val _hasSavedGame = MutableStateFlow(activeGameExists())
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()
    
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

    private fun logActionAttempt(event: String, details: Map<String, Any?> = emptyMap()) {
        GameTelemetry.logState(
            event = "${event}_attempt",
            state = _uiState.value,
            outcome = GameTelemetry.Outcome.ATTEMPT,
            details = details + mapOf("source" to "viewmodel")
        )
    }

    private fun logActionRejected(
        event: String,
        reasonCode: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        val state = _uiState.value
        GameTelemetry.logTransition(
            event = "${event}_result",
            before = state,
            after = state,
            outcome = GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = details + mapOf("source" to "viewmodel")
        )
    }

    private fun logActionResult(
        event: String,
        before: GameState,
        after: GameState,
        success: Boolean,
        reasonCode: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        val resolvedReason = if (success) {
            null
        } else {
            reasonCode
                ?: GameTelemetry.reasonCodeFromEventMessage(GameTelemetry.eventMessage(after))
                ?: "action_rejected"
        }
        GameTelemetry.logTransition(
            event = "${event}_result",
            before = before,
            after = after,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = resolvedReason,
            details = details + mapOf("source" to "viewmodel")
        )
    }

    private fun resourceMapByName(resources: Map<Resource, Int>): Map<String, Int> {
        return resources.entries.associate { (resource, amount) -> resource.name to amount }
    }

    init {
        _fastModeEnabled.value = _metaProgression.value.fastModeEnabled
        _gameUIState.value = _gameUIState.value.copy(fastModeEnabled = _fastModeEnabled.value)
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
        _gameUIState.update { it.copy(fastModeEnabled = _fastModeEnabled.value) }
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
        logActionAttempt(
            event = "game_start",
            details = mapOf(
                "difficulty" to difficulty.name,
                "character" to _selectedCharacter.value.name,
                "mapPreset" to _selectedMapPreset.value.name
            )
        )
        // Starting fresh - drop any prior saved game and emit initial save.
        clearActiveGame()
        _currentDifficulty.value = difficulty
        initializeGame(difficulty)
        _showDifficultyMenu.value = false
        saveActiveGame()
        playSound(GameSound.BUTTON_TAP)
    }

    /**
     * Resume a previously-saved active game (recovered from process death or
     * user backgrounding). Returns true if a save was successfully restored.
     */
    fun resumeSavedGame(): Boolean {
        val restored = loadActiveGame() ?: return false
        _uiState.value = restored
        _currentDifficulty.value = restored.difficulty
        _selectedCharacter.value = restored.selectedCharacter
        _selectedMapPreset.value = restored.mapPreset
        _gameUIState.value = GameUIState(
            showTutorial = restored.difficulty.showTutorial,
            fastModeEnabled = _fastModeEnabled.value
        )
        _showDifficultyMenu.value = false
        _showCharacterMenu.value = false
        _showMapPresetMenu.value = false
        _showTradeMenu.value = false
        playSound(GameSound.BUTTON_TAP)
        GameTelemetry.logState(
            event = "game_resume",
            state = restored,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = mapOf(
                "difficulty" to restored.difficulty.name,
                "turnNumber" to restored.turnNumber
            )
        )
        return true
    }

    /**
     * Discard any saved active game. Called when the user explicitly chooses to
     * start fresh from the difficulty screen.
     */
    fun discardSavedGame() {
        clearActiveGame()
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
                selectedCharacter = character,
                mapPreset = mapPreset
            ).addEvent("🎮 Game started on ${difficulty.emoji} ${difficulty.displayName}!")
             .addEvent("${character.emoji} Playing as ${character.displayName}")
             .addEvent("💡 Tip: Build a Lantern to light up dark tiles!")
        }
        _gameUIState.value = GameUIState(
            showTutorial = difficulty.showTutorial,
            fastModeEnabled = _fastModeEnabled.value
        )
        GameTelemetry.logState(
            event = "game_start",
            state = _uiState.value,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = mapOf(
                "difficulty" to difficulty.name,
                "character" to character.name,
                "mapPreset" to mapPreset.name,
                "startingResources" to resourceMapByName(startingResources),
                "maxActionsPerTurn" to maxActions
            )
        )
    }
    
    fun resetGame() {
        val currentState = _uiState.value
        if (currentState.turnNumber > 1 || currentState.eventLog.isNotEmpty()) {
            GameTelemetry.logState(
                event = "game_reset",
                state = currentState,
                outcome = GameTelemetry.Outcome.SUCCESS,
                details = mapOf("reason" to "manual_reset")
            )
        }
        // Fully reset all state to prevent UI overlay issues
        clearActiveGame()
        _showDifficultyMenu.value = true
        _showCharacterMenu.value = false
        _showMapPresetMenu.value = false
        _showTradeMenu.value = false
        _soundEvent.value = null
        _diceRollTrigger.value = 0L
        _productionTrigger.value = 0L
        _buildTrigger.value = 0L
        _gameUIState.value = GameUIState(fastModeEnabled = _fastModeEnabled.value)
        _uiState.value = GameState() // Use .value instead of update to force complete reset
    }
    
    /**
     * Record game end and update meta-progression
     */
    private fun recordGameEnd(won: Boolean) {
        val state = _uiState.value
        val player = state.currentPlayer
        val vpEarned = player.calculateVictoryPoints() + player.victoryPoints
        val resourcesBefore = state.currentPlayer.resources
        
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
        GameTelemetry.logState(
            event = "meta_progression_recorded",
            state = _uiState.value,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = mapOf(
                "won" to won,
                "vpEarned" to vpEarned,
                "gamesPlayed" to _metaProgression.value.gamesPlayed,
                "gamesWon" to _metaProgression.value.gamesWon,
                "resourcesAtEnd" to resourceMapByName(resourcesBefore)
            )
        )
    }
    
    /**
     * Handle interactive event choice
     */
    fun handleEventChoice(choiceId: InteractiveChoiceId) {
        val state = _uiState.value
        logActionAttempt(
            event = "interactive_event_choice",
            details = mapOf("choiceId" to choiceId.name)
        )
        val event = state.pendingInteractiveEvent
        if (event == null) {
            logActionRejected(
                event = "interactive_event_choice",
                reasonCode = "no_pending_interactive_event",
                details = mapOf("choiceId" to choiceId.name)
            )
            return
        }
        val coord = state.pendingEventCoord
        if (coord == null) {
            logActionRejected(
                event = "interactive_event_choice",
                reasonCode = "missing_event_coordinate",
                details = mapOf("choiceId" to choiceId.name)
            )
            return
        }
        
        _uiState.update { currentState ->
            GameEngine.resolveInteractiveEvent(currentState, event, choiceId, coord)
        }
        val after = _uiState.value
        val success = !GameTelemetry.isRejectedMessage(GameTelemetry.eventMessage(after))
        logActionResult(
            event = "interactive_event_choice",
            before = state,
            after = after,
            success = success,
            details = mapOf(
                "choiceId" to choiceId.name,
                "eventType" to event::class.java.simpleName,
                "location" to GameTelemetry.coordinatePayload(coord)
            )
        )
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
        val before = _uiState.value
        logActionAttempt(
            event = "consolation_choice",
            details = mapOf("choice" to choice.name)
        )
        if (!before.pendingConsolation) {
            logActionRejected(
                event = "consolation_choice",
                reasonCode = "no_pending_consolation",
                details = mapOf("choice" to choice.name)
            )
            return
        }
        _uiState.update { currentState ->
            GameEngine.resolveConsolation(currentState, choice)
        }
        val after = _uiState.value
        logActionResult(
            event = "consolation_choice",
            before = before,
            after = after,
            success = true,
            details = mapOf("choice" to choice.name)
        )
        
        // Trigger production animation if resource was gained
        if (_uiState.value.lastProduction.isNotEmpty()) {
            playSound(GameSound.RESOURCE_GAIN)
            _productionTrigger.value = System.currentTimeMillis()
        } else {
            playSound(GameSound.BUTTON_TAP)
        }
    }

    fun onTileClicked(coord: HexCoordinate) {
        val state = _uiState.value
        val tile = state.board[coord]
        if (tile == null) {
            logActionRejected(
                event = "tile_click",
                reasonCode = "invalid_location",
                details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
            )
            return
        }
        val uiState = _gameUIState.value
        val wasUnrevealed = !tile.isRevealed
        logActionAttempt(
            event = "tile_click",
            details = mapOf(
                "location" to GameTelemetry.coordinatePayload(coord),
                "turnPhase" to state.turnPhase.name
            )
        )

        Log.d(TAG, "TILE_CLICK: coord=$coord, phase=${state.turnPhase}")

        val shouldExplore = state.turnPhase == TurnPhase.MAIN_ACTION &&
            !tile.isRevealed &&
            state.canExploreThisTurn &&
            state.actionsThisTurn < state.maxActionsPerTurn &&
            uiState.selectedTile == coord

        if (shouldExplore) {
            Log.d(TAG, "TILE_CLICK: Exploring tile")
            logActionAttempt(
                event = "explore",
                details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
            )
            _uiState.update { currentState -> GameEngine.exploreTile(currentState, coord) }
            val afterExplore = _uiState.value
            val explored = afterExplore.board[coord]?.isRevealed == true && wasUnrevealed
            logActionResult(
                event = "explore",
                before = state,
                after = afterExplore,
                success = explored,
                details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
            )
        } else {
            Log.d(TAG, "TILE_CLICK: Selecting tile $coord")
        }

        _gameUIState.update { it.copy(selectedTile = coord, showBuildMenu = false) }
        GameTelemetry.logState(
            event = "tile_select_result",
            state = _uiState.value,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
        )

        val newTile = _uiState.value.board[coord]
        if (wasUnrevealed && newTile?.isRevealed == true) {
            playSound(GameSound.EXPLORE)
        } else {
            playSound(GameSound.TILE_SELECT)
        }
    }

    fun exploreSelectedTile() {
        val before = _uiState.value
        val coord = _gameUIState.value.selectedTile
        logActionAttempt(
            event = "explore",
            details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
        )
        if (coord == null) {
            logActionRejected(
                event = "explore",
                reasonCode = "tile_not_selected"
            )
            return
        }
        val tile = before.board[coord]
        if (tile == null) {
            logActionRejected(
                event = "explore",
                reasonCode = "invalid_location",
                details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
            )
            return
        }
        
        if (before.turnPhase != TurnPhase.MAIN_ACTION) {
            logActionRejected(
                event = "explore",
                reasonCode = "wrong_phase",
                details = mapOf("turnPhase" to before.turnPhase.name)
            )
            return
        }
        if (tile.isRevealed) {
            logActionRejected(
                event = "explore",
                reasonCode = "tile_already_revealed",
                details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
            )
            return
        }
        if (!before.canExploreThisTurn || before.actionsThisTurn >= before.maxActionsPerTurn) {
            logActionRejected(
                event = "explore",
                reasonCode = if (!before.canExploreThisTurn) "explore_cap_reached" else "action_cap_reached",
                details = mapOf(
                    "canExploreThisTurn" to before.canExploreThisTurn,
                    "actionsThisTurn" to before.actionsThisTurn,
                    "maxActionsPerTurn" to before.maxActionsPerTurn
                )
            )
            return
        }

        playSound(GameSound.EXPLORE)
        _uiState.update { currentState ->
            GameEngine.exploreTile(currentState, coord)
        }
        _gameUIState.update { it.copy(showBuildMenu = false) }
        val after = _uiState.value
        val success = after.board[coord]?.isRevealed == true
        logActionResult(
            event = "explore",
            before = before,
            after = after,
            success = success,
            details = mapOf("location" to GameTelemetry.coordinatePayload(coord))
        )
    }
    
    fun rollDice() {
        val before = _uiState.value
        logActionAttempt(
            event = "roll",
            details = mapOf("turnPhase" to before.turnPhase.name)
        )
        Log.d(TAG, "ROLL_DICE: called, phase=${before.turnPhase}")
        if (before.turnPhase != TurnPhase.ROLL_DICE) {
            Log.d(TAG, "ROLL_DICE: wrong phase")
            logActionRejected(
                event = "roll",
                reasonCode = "wrong_phase",
                details = mapOf("turnPhase" to before.turnPhase.name)
            )
            return
        }
        
        // Trigger dice roll animation and sound
        playSound(GameSound.DICE_ROLL)
        _diceRollTrigger.value = System.currentTimeMillis()
        
        _uiState.update { currentState ->
            val result = GameEngine.rollDiceAndProduce(currentState)
            Log.d(TAG, "ROLL_DICE: rolled ${result.lastDiceResult}")
            result
        }
        val after = _uiState.value
        logActionResult(
            event = "roll",
            before = before,
            after = after,
            success = true,
            details = mapOf(
                "diceTotal" to after.lastDiceResult?.total,
                "pendingConsolation" to after.pendingConsolation
            )
        )
        
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
        val location = _gameUIState.value.selectedTile
        logActionAttempt(
            event = "build",
            details = mapOf(
                "structureType" to structureType.name,
                "location" to GameTelemetry.coordinatePayload(location)
            )
        )
        Log.d(TAG, "BUILD: type=$structureType, location=$location")
        
        if (location == null) {
            Log.d(TAG, "BUILD: No tile selected!")
            logActionRejected(
                event = "build",
                reasonCode = "tile_not_selected",
                details = mapOf("structureType" to structureType.name)
            )
            playSound(GameSound.ERROR)
            return
        }
        
        val before = _uiState.value
        val previousStructureCount = _uiState.value.structures.size
        
        _uiState.update { currentState ->
            val result = GameEngine.buildStructure(currentState, structureType, location)
            Log.d(TAG, "BUILD: structures=${result.structures.size}, VP=${result.currentPlayer.calculateVictoryPoints()}")
            result
        }
        val after = _uiState.value
        val built = after.actionsThisTurn > before.actionsThisTurn &&
            !GameTelemetry.isRejectedMessage(GameTelemetry.eventMessage(after))
        logActionResult(
            event = "build",
            before = before,
            after = after,
            success = built,
            details = mapOf(
                "structureType" to structureType.name,
                "location" to GameTelemetry.coordinatePayload(location)
            )
        )
        _gameUIState.update { it.copy(showBuildMenu = false, selectedTile = null) }
        
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
        val uiState = _gameUIState.value
        Log.d(TAG, "TOGGLE_BUILD: tile=${uiState.selectedTile}, actions=${state.actionsThisTurn}/${state.maxActionsPerTurn}")
        playSound(GameSound.BUTTON_TAP)
        _gameUIState.update { it.copy(showBuildMenu = !it.showBuildMenu) }
    }
    
    // Trading functions
    fun toggleTradeMenu() {
        playSound(GameSound.BUTTON_TAP)
        _showTradeMenu.value = !_showTradeMenu.value
    }
    
    fun tradeResources(give: Resource, receive: Resource) {
        val before = _uiState.value
        logActionAttempt(
            event = "trade",
            details = mapOf(
                "giveResource" to give.name,
                "receiveResource" to receive.name
            )
        )
        playSound(GameSound.TRADE)
        _uiState.update { currentState ->
            GameEngine.tradeResources(currentState, give, receive)
        }
        val after = _uiState.value
        val success = GameTelemetry.eventMessage(after)?.startsWith("🔄 Traded") == true
        logActionResult(
            event = "trade",
            before = before,
            after = after,
            success = success,
            details = mapOf(
                "giveResource" to give.name,
                "receiveResource" to receive.name
            )
        )
    }
    
    fun canTrade(): Boolean = GameEngine.canTrade(_uiState.value)
    
    fun getTradableResources(): List<Resource> = GameEngine.getTradableResources(_uiState.value)
    
    fun clearRubble() {
        val location = _gameUIState.value.selectedTile
        logActionAttempt(
            event = "clear_rubble",
            details = mapOf("location" to GameTelemetry.coordinatePayload(location))
        )
        if (location == null) {
            logActionRejected(
                event = "clear_rubble",
                reasonCode = "tile_not_selected"
            )
            return
        }
        val before = _uiState.value
        
        playSound(GameSound.BUILD_STRUCTURE)
        _uiState.update { currentState ->
            GameEngine.clearRubble(currentState, location)
        }
        val after = _uiState.value
        val success = GameTelemetry.eventMessage(after)?.startsWith("🧹 Cleared rubble") == true
        logActionResult(
            event = "clear_rubble",
            before = before,
            after = after,
            success = success,
            details = mapOf("location" to GameTelemetry.coordinatePayload(location))
        )
    }
    
    fun endTurn() {
        val before = _uiState.value
        logActionAttempt(
            event = "turn_end",
            details = mapOf(
                "actionsThisTurn" to before.actionsThisTurn,
                "maxActionsPerTurn" to before.maxActionsPerTurn
            )
        )
        playSound(GameSound.TURN_END)
        _uiState.update { currentState ->
            GameEngine.endTurn(currentState)
        }
        val after = _uiState.value
        logActionResult(
            event = "turn_end",
            before = before,
            after = after,
            success = true,
            details = mapOf(
                "previousTurn" to before.turnNumber,
                "currentTurn" to after.turnNumber,
                "currentPlayerId" to after.currentPlayer.id
            )
        )
        _gameUIState.update { it.copy(selectedTile = null, showBuildMenu = false) }
        
        // Check for game end after turn
        if (after.gameOver && !before.gameOver) {
            if (after.winner != null) {
                playSound(GameSound.VICTORY)
                recordGameEnd(true)
            } else {
                recordGameEnd(false)
            }
            clearActiveGame()
        } else {
            // Persist the post-turn state for process-death recovery.
            saveActiveGame()
        }
    }
    
    fun useStructureAbility(location: HexCoordinate) {
        val before = _uiState.value
        logActionAttempt(
            event = "ability",
            details = mapOf("location" to GameTelemetry.coordinatePayload(location))
        )
        playSound(GameSound.BUTTON_TAP)
        _uiState.update { currentState ->
            GameEngine.useStructureAbility(currentState, location)
        }
        val after = _uiState.value
        val success = !GameTelemetry.isRejectedMessage(GameTelemetry.eventMessage(after))
        logActionResult(
            event = "ability",
            before = before,
            after = after,
            success = success,
            details = mapOf("location" to GameTelemetry.coordinatePayload(location))
        )
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
        val location = _gameUIState.value.selectedTile
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
            val adjustedCost = GameEngine.getAdjustedBuildCost(type, state.difficulty, state.selectedCharacter)
            val canAfford = player.canAfford(adjustedCost)
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

    // ---------- Active-game persistence (process death recovery) ----------

    private fun activeGamePrefs() = getApplication<Application>()
        .getSharedPreferences(ACTIVE_GAME_PREFS, Context.MODE_PRIVATE)

    private fun activeGameExists(): Boolean {
        val raw = activeGamePrefs().getString(ACTIVE_GAME_KEY, null) ?: return false
        // Validate the payload deserializes; treat corrupted saves as absent.
        return GameStatePersistence.deserialize(raw) != null
    }

    /**
     * Persist the current [GameState] for resume-on-process-death.
     * Skips no-op states (game over or empty initial state).
     */
    fun saveActiveGame() {
        val state = _uiState.value
        if (state.gameOver) return
        if (state.board.isEmpty() && state.eventLog.isEmpty()) return
        try {
            val json = GameStatePersistence.serialize(state)
            activeGamePrefs().edit().putString(ACTIVE_GAME_KEY, json).apply()
            _hasSavedGame.value = true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to save active game", t)
        }
    }

    private fun loadActiveGame(): GameState? {
        val raw = activeGamePrefs().getString(ACTIVE_GAME_KEY, null) ?: return null
        return GameStatePersistence.deserialize(raw)
    }

    private fun clearActiveGame() {
        activeGamePrefs().edit().remove(ACTIVE_GAME_KEY).apply()
        _hasSavedGame.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // Final-chance save when the ViewModel is being destroyed mid-game so
        // the user can resume after process death.
        val state = _uiState.value
        if (!state.gameOver && state.board.isNotEmpty()) {
            saveActiveGame()
        }
    }
}
