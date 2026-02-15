package com.atlyn.subterranea.domain.model

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
    val exploresThisTurn: Int = 0,  // Track explores for Easy mode
    // New fields for meta-progression and interactive events
    val selectedCharacter: GameCharacter = GameCharacter.EXPLORER,
    val pendingInteractiveEvent: InteractiveEvent? = null,
    val pendingEventCoord: HexCoordinate? = null,
    val fastModeEnabled: Boolean = false,
    val mapPreset: MapPreset = MapPreset.STANDARD,
    val bonusActionsFirstTurn: Int = 0, // From meta-progression
    val firstStructureDiscount: Int = 0, // From meta-progression
    val hasUsedFirstStructureDiscount: Boolean = false,
    val pendingConsolation: Boolean = false, // Waiting for player to pick a roll consolation
    val discountTradeAvailable: Boolean = false // One-time 2:1 trade from consolation choice

) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    
    /**
     * Calculate total VP for a player, including lantern placement bonuses
     */
    fun totalVPFor(player: Player): Int {
        val baseVP = player.calculateVictoryPoints() + player.victoryPoints
        val lanternBonusVP = structures.count { s ->
            s.ownerId == player.id && s.type == StructureType.LANTERN && s.tilesIlluminated >= 4
        }
        return baseVP + lanternBonusVP
    }
    
    // Max explores based on difficulty
    val maxExploresPerTurn: Int get() = if (difficulty.multipleExploresPerTurn) 2 else 1
    
    /**
     * Get a contextual hint for the current game state
     */
    fun getCurrentHint(): String? {
        if (!showTutorial || gameOver) return null
        
        val currentVP = totalVPFor(currentPlayer)
        val hasLantern = structures.any { it.type == StructureType.LANTERN && it.ownerId == currentPlayer.id }
        val hasOutpost = structures.any { it.type == StructureType.OUTPOST && it.ownerId == currentPlayer.id }
        val illuminatedCount = board.values.count { it.isRevealed && it.isIlluminated }
        val revealedCount = board.values.count { it.isRevealed }
        
        return when {
            turnPhase == TurnPhase.ROLL_DICE -> "🎲 Tap 'Roll' to roll the dice and produce resources!"
            
            // Out of actions
            actionsThisTurn >= maxActionsPerTurn ->
                "➡️ You've used all actions. Tap 'End Turn' to continue."
            
            // Near victory
            currentVP >= victoryPointsToWin - 2 && currentVP < victoryPointsToWin ->
                "🏆 Almost there! ${victoryPointsToWin - currentVP} VP to victory!"
            
            // First turn - teach exploration
            turnNumber <= 2 && actionsThisTurn == 0 && turnPhase == TurnPhase.MAIN_ACTION -> 
                "🔦 Double-tap a dark tile next to revealed tiles to explore!"
            
            // No lanterns yet and can afford one - suggest building
            !hasLantern && currentPlayer.canAfford(StructureType.LANTERN.cost) && turnPhase == TurnPhase.MAIN_ACTION ->
                "💡 Build a Lantern to illuminate tiles and earn VP!"
            
            // Has lantern but no outpost - guide next step
            hasLantern && !hasOutpost && currentPlayer.canAfford(StructureType.OUTPOST.cost) ->
                "🏗️ Build an Outpost on a resource tile to earn VP!"
                
            // Few illuminated tiles — need more lanterns
            hasLantern && illuminatedCount < 4 && currentPlayer.canAfford(StructureType.LANTERN.cost) ->
                "💡 More Lanterns = more production. Place near unexplored areas!"
            
            // After exploring — suggest building
            !canExploreThisTurn && actionsThisTurn < maxActionsPerTurn && selectedTile != null ->
                "🏗️ Tap 'Build' to construct a structure on the selected tile."
            !canExploreThisTurn && actionsThisTurn < maxActionsPerTurn ->
                "🏗️ Select a revealed tile to build, or trade resources."
            
            // Mid-game variety
            revealedCount > 10 && turnPhase == TurnPhase.MAIN_ACTION ->
                "⛏️ Deeper zones have rarer resources but more hazards!"
            
            // General exploration tip
            canExploreThisTurn && turnPhase == TurnPhase.MAIN_ACTION ->
                "🔦 Explore to reveal tiles and find resources!"
            
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
