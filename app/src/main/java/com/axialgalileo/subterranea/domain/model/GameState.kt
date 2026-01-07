package com.axialgalileo.subterranea.domain.model

data class GameState(
    val board: Map<HexCoordinate, HexTile> = emptyMap(),
    val players: List<Player> = listOf(
        Player(
            id = 0, 
            name = "Player 1",
            // Default resources - will be overwritten by difficulty setting
            resources = Difficulty.NORMAL.startingResources
        )
    ),
    val currentPlayerIndex: Int = 0,
    val turnNumber: Int = 1,
    val turnPhase: TurnPhase = TurnPhase.ROLL_DICE,
    val lastDiceResult: DiceResult? = null,
    val lastExplorationEvent: ExplorationEvent? = null,
    val lastProduction: Map<Resource, Int> = emptyMap(), // What was produced this turn
    val structures: List<Structure> = emptyList(),
    val actionsThisTurn: Int = 0,
    val maxActionsPerTurn: Int = Difficulty.NORMAL.maxActionsPerTurn,
    val gameOver: Boolean = false,
    val winner: Player? = null,
    val victoryPointsToWin: Int = Difficulty.NORMAL.victoryPointsToWin,
    val eventLog: List<String> = emptyList(),
    val selectedTile: HexCoordinate? = null,
    val availableActions: List<GameAction> = emptyList(),
    val showBuildMenu: Boolean = false,
    val canExploreThisTurn: Boolean = true,
    val showTutorial: Boolean = Difficulty.NORMAL.showTutorial,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val exploresThisTurn: Int = 0  // Track explores for Easy mode
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    
    // Max explores based on difficulty
    val maxExploresPerTurn: Int get() = if (difficulty.multipleExploresPerTurn) 2 else 1
    
    /**
     * Get a contextual hint for the current game state
     */
    fun getCurrentHint(): String? {
        if (!showTutorial) return null
        
        // Calculate VP progress
        val currentVP = currentPlayer.calculateVictoryPoints() + currentPlayer.victoryPoints
        
        return when {
            turnPhase == TurnPhase.ROLL_DICE -> "🎲 Tap 'Roll' to roll the dice and produce resources!"
            
            // First turn guidance
            turnNumber == 1 && actionsThisTurn == 0 && turnPhase == TurnPhase.MAIN_ACTION -> 
                "🔦 Tap on a dark tile next to revealed tiles to explore it!"
            
            // Building hints
            actionsThisTurn == 0 && structures.isEmpty() && currentPlayer.canAfford(StructureType.LANTERN.cost) ->
                "💡 Build a Lantern to reveal adjacent tiles and earn VP!"
            
            // After exploring
            !canExploreThisTurn && actionsThisTurn < maxActionsPerTurn && selectedTile != null ->
                "🏗️ Tap 'Build' to construct a structure on the selected tile."
            !canExploreThisTurn && actionsThisTurn < maxActionsPerTurn ->
                "🏗️ Select a revealed tile to build structures."
            
            // Out of actions
            actionsThisTurn >= maxActionsPerTurn ->
                "➡️ You've used all actions. Tap 'End Turn' to continue."
            
            // Progress hints
            currentVP >= victoryPointsToWin - 2 && currentVP < victoryPointsToWin ->
                "🏆 Almost there! ${victoryPointsToWin - currentVP} VP to victory!"
            
            // General exploration tip
            canExploreThisTurn && turnPhase == TurnPhase.MAIN_ACTION ->
                "🔦 Explore deeper for better resources! Darker tiles have rarer rewards."
            
            else -> null
        }
    }
    
    fun updatePlayer(player: Player): GameState {
        val newPlayers = players.toMutableList()
        val index = newPlayers.indexOfFirst { it.id == player.id }
        if (index >= 0) {
            newPlayers[index] = player
        }
        return copy(players = newPlayers)
    }
    
    fun addEvent(message: String): GameState {
        val newLog = (listOf(message) + eventLog).take(20) // Keep last 20 events
        return copy(eventLog = newLog)
    }
    
    fun getStructureAt(coord: HexCoordinate): Structure? {
        return structures.find { it.location == coord }
    }
    
    fun hasStructureAt(coord: HexCoordinate): Boolean {
        return structures.any { it.location == coord }
    }
}
