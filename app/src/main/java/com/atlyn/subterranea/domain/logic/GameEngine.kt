package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*

/**
 * Core game logic engine - handles all game rules and mechanics
 * Inspired by: Catan (resource production), Betrayal at House on the Hill (exploration),
 * Terraforming Mars (engine building), and Forbidden Desert (cooperative survival)
 */
object GameEngine {
    
    /**
     * Roll dice and distribute resources to all players based on number tokens
     */
    fun rollDiceAndProduce(state: GameState): GameState {
        val diceResult = DiceResult.roll()
        var newState = state.copy(
            lastDiceResult = diceResult,
            lastProduction = emptyMap(),
            turnPhase = TurnPhase.MAIN_ACTION
        ).addEvent("🎲 Rolled ${diceResult.die1} + ${diceResult.die2} = ${diceResult.total}")
        
        // Find all tiles with matching number tokens
        val producingTiles = state.board.values.filter { tile ->
            tile.numberToken == diceResult.total &&
            tile.isRevealed &&
            tile.isIlluminated &&
            !tile.hasRubble &&
            tile.terrain.produces != null
        }
        
        // LUCKY 7 BONUS: Rolling 7 gives a consolation choice
        if (diceResult.total == 7 && producingTiles.isEmpty()) {
            return newState.copy(pendingConsolation = true)
                .addEvent("🍀 Lucky 7! Choose your consolation...")
        }
        
        if (producingTiles.isEmpty()) {
            // Check for tiles that WOULD produce if illuminated (ghost production feedback)
            val darkProducingTiles = state.board.values.filter { tile ->
                tile.numberToken == diceResult.total &&
                tile.isRevealed &&
                !tile.isIlluminated &&
                !tile.hasRubble &&
                tile.terrain.produces != null
            }
            if (darkProducingTiles.isNotEmpty()) {
                val resources = darkProducingTiles.mapNotNull { it.terrain.produces?.displayName() }.distinct()
                return newState.addEvent("🌑 ${resources.joinToString(", ")} would produce if illuminated! Build a Lantern 🔦")
            }
            // Auto-consolation: gain 1 random common resource on any dead roll
            val commonResources = listOf(Resource.MYCELIUM, Resource.BASALT, Resource.CHITIN, Resource.LICHEN)
            val bonus = commonResources.random()
            var player = newState.currentPlayer
            player = player.addResource(bonus, 1)
            return newState.updatePlayer(player)
                .copy(lastProduction = mapOf(bonus to 1))
                .addEvent("🔍 Nothing produced — scavenged +1 ${bonus.displayName()}")
        }
        
        // Track total production for display
        val productionTotals = mutableMapOf<Resource, Int>()
        val difficulty = state.difficulty
        
        // For single player, give resources to current player
        // For multiplayer, would check structure ownership
        var player = newState.currentPlayer
        producingTiles.forEach { tile ->
            val resource = tile.terrain.produces!!
            
            // Check if player has an outpost/excavator on this tile
            val structure = newState.getStructureAt(tile.coordinate)
            val structureMultiplier = when (structure?.type) {
                StructureType.EXCAVATOR -> 2
                StructureType.CRYSTAL_REFINERY -> if (resource == Resource.CRYSTAL) 2 else 1
                StructureType.FUNGAL_FARM -> if (resource == Resource.MYCELIUM) 2 else 1
                else -> 1
            }
            
            // Apply difficulty production bonus
            var amount = structureMultiplier + difficulty.productionBonus
            
            // Apply rare resource penalty on Nightmare
            if (difficulty.rareResourcePenalty && 
                (resource == Resource.IRON_ORE || resource == Resource.CRYSTAL)) {
                amount = maxOf(1, amount - 1)
            }
            
            // Ensure at least 1 resource is produced
            amount = maxOf(1, amount)
            
            player = player.addResource(resource, amount)
            productionTotals[resource] = (productionTotals[resource] ?: 0) + amount
            newState = newState.addEvent("⛏️ ${tile.terrain.name} produces $amount ${resource.displayName()}")
        }
        
        // Check for crystal baron achievement
        if (player.getResourceCount(Resource.CRYSTAL) >= 10 && 
            Achievement.CRYSTAL_BARON !in player.achievements) {
            player = player.copy(achievements = player.achievements + Achievement.CRYSTAL_BARON)
            newState = newState.addEvent("🏆 Achievement: Crystal Baron!")
        }
        
        return newState.copy(lastProduction = productionTotals).updatePlayer(player)
    }
    
    /**
     * Resolve the player's consolation choice after a non-producing roll
     */
    fun resolveConsolation(state: GameState, choice: RollConsolation): GameState {
        if (!state.pendingConsolation) return state
        
        var newState = state.copy(pendingConsolation = false)
        var player = newState.currentPlayer
        
        when (choice) {
            RollConsolation.GAIN_RESOURCE -> {
                val commonResources = listOf(Resource.MYCELIUM, Resource.BASALT, Resource.CHITIN, Resource.LICHEN)
                val bonus = commonResources.random()
                player = player.addResource(bonus, 1)
                newState = newState.updatePlayer(player)
                    .addEvent("🎁 Scavenged +1 ${bonus.displayName()}")
                    .copy(lastProduction = mapOf(bonus to 1))
            }
            RollConsolation.BONUS_ACTION -> {
                newState = newState.copy(
                    maxActionsPerTurn = newState.maxActionsPerTurn + 1
                ).addEvent("⚡ Hustle! +1 action this turn")
            }
            RollConsolation.DISCOUNT_TRADE -> {
                newState = newState.copy(
                    discountTradeAvailable = true
                ).addEvent("🤝 Barter! One 2:1 trade available this turn")
            }
        }
        
        return newState
    }
    
    /**
     * Explore (reveal) an unrevealed tile adjacent to revealed tiles
     */
    fun exploreTile(state: GameState, coord: HexCoordinate): GameState {
        val tile = state.board[coord] ?: return state
        
        if (tile.isRevealed) {
            return state.addEvent("❌ Tile already revealed")
        }
        
        // Check explore limit based on difficulty
        val maxExplores = state.maxExploresPerTurn
        if (state.exploresThisTurn >= maxExplores) {
            return state.addEvent("❌ Already explored $maxExplores tile(s) this turn!")
        }
        
        // Must be adjacent to a revealed tile
        val hasRevealedNeighbor = coord.neighbors().any { neighbor ->
            state.board[neighbor]?.isRevealed == true
        }
        if (!hasRevealedNeighbor) {
            return state.addEvent("❌ Must explore adjacent to revealed tile")
        }
        
        // Generate terrain for the tile
        val (newTile, event) = generateExploredTile(tile, state.difficulty)
        
        val newBoard = state.board.toMutableMap()
        newBoard[coord] = newTile
        
        // Track explores this turn (Easy mode allows 2)
        val newExploresThisTurn = state.exploresThisTurn + 1
        val canStillExplore = newExploresThisTurn < maxExplores
        
        var newState = state.copy(
            board = newBoard,
            lastExplorationEvent = event,
            canExploreThisTurn = canStillExplore,
            exploresThisTurn = newExploresThisTurn,
            actionsThisTurn = state.actionsThisTurn + 1
        ).addEvent("🔦 Explored ${newTile.terrain.name} at (${coord.q}, ${coord.r})")
        
        // Apply exploration event effects
        newState = applyExplorationEvent(newState, event, coord)
        
        // Check if tile should be illuminated by nearby lanterns
        newState = checkIlluminationFromLanterns(newState, coord)
        
        // Update player exploration count
        var player = newState.currentPlayer.copy(
            explorationCount = newState.currentPlayer.explorationCount + 1
        )
        
        // Check achievements
        if (tile.zone == Zone.MANTLE && Achievement.FIRST_EXPLORER !in player.achievements) {
            val anyoneHasIt = newState.players.any { Achievement.FIRST_EXPLORER in it.achievements }
            if (!anyoneHasIt) {
                player = player.copy(achievements = player.achievements + Achievement.FIRST_EXPLORER)
                newState = newState.addEvent("🏆 Achievement: First Explorer!")
            }
        }
        
        if (tile.zone == Zone.CORE && Achievement.CORE_SEEKER !in player.achievements) {
            val anyoneHasIt = newState.players.any { Achievement.CORE_SEEKER in it.achievements }
            if (!anyoneHasIt) {
                player = player.copy(achievements = player.achievements + Achievement.CORE_SEEKER)
                newState = newState.addEvent("🏆 Achievement: Core Seeker!")
            }
        }
        
        if (player.explorationCount >= 10 && Achievement.DEEP_DELVER !in player.achievements) {
            player = player.copy(achievements = player.achievements + Achievement.DEEP_DELVER)
            newState = newState.addEvent("🏆 Achievement: Deep Delver!")
        }
        
        return newState.updatePlayer(player).let { checkVictory(it) }
    }
    
    /**
     * Check if a tile should be illuminated by nearby lanterns
     */
    private fun checkIlluminationFromLanterns(state: GameState, coord: HexCoordinate): GameState {
        val tile = state.board[coord] ?: return state
        if (tile.isIlluminated) return state
        
        // Check if any adjacent tile has a lantern
        val hasNearbyLantern = (listOf(coord) + coord.neighbors()).any { neighborCoord ->
            state.structures.any { 
                it.type == StructureType.LANTERN && 
                it.location.distanceTo(coord) <= 1
            }
        }
        
        if (hasNearbyLantern) {
            val newBoard = state.board.toMutableMap()
            newBoard[coord] = tile.copy(isIlluminated = true)
            return state.copy(board = newBoard).addEvent("💡 Tile illuminated by nearby Lantern!")
        }
        
        return state
    }
    
    private fun generateExploredTile(tile: HexTile, difficulty: Difficulty = Difficulty.NORMAL): Pair<HexTile, ExplorationEvent> {
        // Terrain distribution based on zone (deeper = rarer resources)
        val terrainOptions = when (tile.zone) {
            Zone.SURFACE -> listOf(
                TerrainType.LICHEN_FIELD, TerrainType.FUNGAL_FOREST,
                TerrainType.BASALT_QUARRY, TerrainType.BEETLE_FARM
            )
            Zone.CRUST -> listOf(
                TerrainType.LICHEN_FIELD, TerrainType.FUNGAL_FOREST,
                TerrainType.BASALT_QUARRY, TerrainType.BEETLE_FARM,
                TerrainType.IRON_VEIN, TerrainType.IRON_VEIN // More iron in crust
            )
            Zone.MANTLE -> listOf(
                TerrainType.IRON_VEIN, TerrainType.IRON_VEIN,
                TerrainType.CRYSTAL_GROTTO, TerrainType.BASALT_QUARRY,
                TerrainType.MAGMA_FLOW, TerrainType.CRYSTAL_GROTTO
            )
            Zone.CORE -> listOf(
                TerrainType.CRYSTAL_GROTTO, TerrainType.CRYSTAL_GROTTO,
                TerrainType.CRYSTAL_GROTTO, TerrainType.IRON_VEIN,
                TerrainType.MAGMA_FLOW, TerrainType.BEDROCK
            )
        }
        
        val terrain = terrainOptions.random()
        
        // Number tokens: Better numbers in deeper zones (risk/reward)
        // Surface explored tiles get medium numbers, deeper = better
        val numberPool = when (tile.zone) {
            Zone.SURFACE -> listOf(4, 5, 5, 9, 9, 10) // Medium numbers
            Zone.CRUST -> listOf(5, 5, 6, 8, 9, 9)    // Good numbers  
            Zone.MANTLE -> listOf(5, 6, 6, 8, 8, 9)   // Great numbers
            Zone.CORE -> listOf(6, 6, 7, 7, 8, 8)     // Best numbers in the core
        }
        val numberToken = if (terrain.produces != null) numberPool.random() else null
        
        // Generate exploration event based on zone and difficulty
        val event = generateExplorationEvent(tile.zone, difficulty)
        
        val hasRubble = event is ExplorationEvent.CaveIn
        val isMagma = event is ExplorationEvent.MagmaBurst
        val isIlluminated = event is ExplorationEvent.FungalBloom
        
        val finalTerrain = if (isMagma) TerrainType.MAGMA_FLOW else terrain
        
        val newTile = tile.copy(
            isRevealed = true,
            terrain = finalTerrain,
            numberToken = numberToken,
            hasRubble = hasRubble,
            isIlluminated = isIlluminated
        )
        
        return Pair(newTile, event)
    }
    
    private fun generateExplorationEvent(zone: Zone, difficulty: Difficulty = Difficulty.NORMAL): ExplorationEvent {
        val roll = (1..100).random()
        val hazardChance = (difficulty.hazardChance * 100).toInt()
        
        // First, check for hazard based on difficulty (higher difficulty = more hazards)
        val hazardRoll = (1..100).random()
        val isHazardForced = hazardRoll <= hazardChance
        
        return when (zone) {
            Zone.SURFACE -> {
                // Surface is very safe, but on high difficulty add minor hazards
                if (isHazardForced && roll > 70) {
                    when ((1..3).random()) {
                        1 -> ExplorationEvent.CaveIn
                        2 -> ExplorationEvent.GasLeak()
                        else -> ExplorationEvent.Tremor()
                    }
                } else when {
                    roll <= 50 -> ExplorationEvent.StableGround
                    roll <= 70 -> ExplorationEvent.FungalBloom()
                    roll <= 85 -> ExplorationEvent.BeetleNest()
                    else -> ExplorationEvent.TreasureCache(
                        mapOf(Resource.LICHEN to 2, Resource.MYCELIUM to 1)
                    )
                }
            }
            Zone.CRUST -> {
                // Crust hazard chances scale with difficulty
                val hazardStart = 80 - (hazardChance / 3)
                when {
                    roll <= 35 -> ExplorationEvent.StableGround
                    roll <= 50 -> ExplorationEvent.FungalBloom()
                    roll <= 65 -> ExplorationEvent.BeetleNest(chitinAmount = 2)
                    roll <= hazardStart -> ExplorationEvent.TreasureCache(
                        mapOf(Resource.BASALT to 2, Resource.IRON_ORE to 1)
                    )
                    roll <= hazardStart + 8 -> ExplorationEvent.CaveIn
                    roll <= hazardStart + 14 -> ExplorationEvent.GasLeak()
                    else -> ExplorationEvent.LostMiner()
                }
            }
            Zone.MANTLE -> {
                // Mantle has higher risk, scaled by difficulty
                val safeZone = 25 - (hazardChance / 4)
                val hazardStart = 65 - (hazardChance / 3)
                when {
                    roll <= maxOf(10, safeZone) -> ExplorationEvent.StableGround
                    roll <= 40 -> ExplorationEvent.CrystalVein()
                    roll <= 55 -> ExplorationEvent.TreasureCache(
                        mapOf(Resource.IRON_ORE to 2, Resource.CRYSTAL to 1)
                    )
                    roll <= hazardStart -> ExplorationEvent.AncientArtifact()
                    roll <= hazardStart + 10 -> ExplorationEvent.CaveIn
                    roll <= hazardStart + 20 -> ExplorationEvent.Tremor()
                    roll <= hazardStart + 27 -> ExplorationEvent.GasLeak()
                    roll <= hazardStart + 32 -> ExplorationEvent.MagmaBurst
                    else -> ExplorationEvent.GeothermalVent
                }
            }
            Zone.CORE -> {
                // Core is most dangerous, difficulty amplifies this
                val safeZone = 20 - (hazardChance / 3)
                val hazardStart = 65 - (hazardChance / 2)
                when {
                    roll <= maxOf(5, safeZone) -> ExplorationEvent.StableGround
                    roll <= 40 -> ExplorationEvent.CrystalVein(3)
                    roll <= 55 -> ExplorationEvent.AncientArtifact(2)
                    roll <= hazardStart -> ExplorationEvent.TreasureCache(
                        mapOf(Resource.CRYSTAL to 3, Resource.IRON_ORE to 2)
                    )
                    roll <= hazardStart + 10 -> ExplorationEvent.CaveIn
                    roll <= hazardStart + 17 -> ExplorationEvent.MagmaBurst
                    roll <= hazardStart + 25 -> ExplorationEvent.Tremor()
                    roll <= hazardStart + 30 -> ExplorationEvent.GasLeak()
                    else -> ExplorationEvent.GeothermalVent
                }
            }
        }
    }
    
    private fun applyExplorationEvent(state: GameState, event: ExplorationEvent, coord: HexCoordinate): GameState {
        var player = state.currentPlayer
        var newState = state
        
        when (event) {
            is ExplorationEvent.TreasureCache -> {
                event.resources.forEach { (resource, amount) ->
                    player = player.addResource(resource, amount)
                }
                newState = newState.addEvent("💎 Found treasure! ${event.resources}")
            }
            is ExplorationEvent.CrystalVein -> {
                player = player.addResource(Resource.CRYSTAL, event.amount)
                newState = newState.addEvent("💎 Crystal vein! +${event.amount} Crystals")
            }
            is ExplorationEvent.AncientArtifact -> {
                player = player.copy(victoryPoints = player.victoryPoints + event.victoryPoints)
                newState = newState.addEvent("🏺 Ancient artifact! +${event.victoryPoints} VP")
            }
            is ExplorationEvent.BeetleNest -> {
                player = player.addResource(Resource.CHITIN, event.chitinAmount)
                newState = newState.addEvent("🪲 Beetle nest! +${event.chitinAmount} Chitin")
            }
            is ExplorationEvent.FungalBloom -> {
                player = player.addResource(Resource.MYCELIUM, event.myceliumAmount)
                newState = newState.addEvent("🍄 Fungal bloom! +${event.myceliumAmount} Mycelium")
            }
            is ExplorationEvent.CaveIn -> {
                newState = newState.addEvent("💥 Cave-in! Tile has rubble.")
            }
            is ExplorationEvent.GasLeak -> {
                // Lose 1 of each resource
                val newResources = player.resources.mapValues { (_, v) -> maxOf(0, v - 1) }
                player = player.copy(resources = newResources)
                newState = newState.addEvent("☠️ Gas leak! Lost resources.")
            }
            is ExplorationEvent.MagmaBurst -> {
                newState = newState.addEvent("🌋 Magma burst! Tile is impassable.")
            }
            is ExplorationEvent.Tremor -> {
                newState = newState.addEvent("🌍 Tremor! Ground shakes.")
            }
            is ExplorationEvent.GeothermalVent -> {
                // Give bonus resources
                player = player.addResource(Resource.CRYSTAL, 1)
                player = player.addResource(Resource.IRON_ORE, 1)
                newState = newState.addEvent("♨️ Geothermal vent! Bonus resources!")
            }
            is ExplorationEvent.LostMiner -> {
                newState = newState.copy(canExploreThisTurn = true)
                    .addEvent("👷 Rescued a miner! Extra exploration!")
            }
            ExplorationEvent.StableGround -> {
                // Nothing happens
            }
        }
        
        return newState.updatePlayer(player)
    }
    
    /**
     * Build a structure at a location
     */
    fun buildStructure(state: GameState, structureType: StructureType, location: HexCoordinate): GameState {
        val tile = state.board[location] ?: return state.addEvent("❌ Invalid location")
        val player = state.currentPlayer
        
        // Validation
        if (!tile.isRevealed) {
            return state.addEvent("❌ Must reveal tile first")
        }
        
        if (tile.terrain == TerrainType.MAGMA_FLOW || tile.terrain == TerrainType.BEDROCK) {
            return state.addEvent("❌ Cannot build on ${tile.terrain.name}")
        }
        
        if (state.hasStructureAt(location) && structureType != StructureType.EXCAVATOR) {
            return state.addEvent("❌ Already has a structure")
        }
        
        // Excavator requires existing outpost
        if (structureType == StructureType.EXCAVATOR) {
            val existing = state.getStructureAt(location)
            if (existing?.type != StructureType.OUTPOST) {
                return state.addEvent("❌ Excavator requires an Outpost")
            }
        }
        
        if (!player.canAfford(structureType.cost)) {
            return state.addEvent("❌ Cannot afford ${structureType.displayName}")
        }
        
        // Build it!
        val structure = Structure(structureType, location, player.id)
        var newStructures = state.structures.toMutableList()
        
        // Remove outpost if upgrading to excavator
        if (structureType == StructureType.EXCAVATOR) {
            newStructures = newStructures.filter { it.location != location }.toMutableList()
        }
        newStructures.add(structure)
        
        var newPlayer = player.removeResources(structureType.cost)
            .copy(structuresBuilt = player.structuresBuilt + structure)
        
        var newState = state.copy(
            structures = newStructures,
            actionsThisTurn = state.actionsThisTurn + 1
        ).addEvent("🏗️ Built ${structureType.displayName} at (${location.q}, ${location.r})")
        
        // Check achievements
        if (newPlayer.structuresBuilt.size >= 5 && Achievement.MASTER_BUILDER !in newPlayer.achievements) {
            newPlayer = newPlayer.copy(achievements = newPlayer.achievements + Achievement.MASTER_BUILDER)
            newState = newState.addEvent("🏆 Achievement: Master Builder!")
        }
        
        val lanternCount = newPlayer.structuresBuilt.count { it.type == StructureType.LANTERN }
        if (lanternCount >= 3 && Achievement.ILLUMINATOR !in newPlayer.achievements) {
            newPlayer = newPlayer.copy(achievements = newPlayer.achievements + Achievement.ILLUMINATOR)
            newState = newState.addEvent("🏆 Achievement: The Illuminator!")
        }
        
        // Lanterns illuminate adjacent tiles
        if (structureType == StructureType.LANTERN) {
            newState = illuminateAdjacentTiles(newState, location)
        }
        
        return newState.updatePlayer(newPlayer).let { checkVictory(it) }
    }
    
    private fun illuminateAdjacentTiles(state: GameState, center: HexCoordinate): GameState {
        val newBoard = state.board.toMutableMap()
        
        // Illuminate the center tile and all neighbors
        val tilesToLight = listOf(center) + center.neighbors()
        var tilesLit = 0
        
        tilesToLight.forEach { coord ->
            val tile = newBoard[coord]
            if (tile != null && tile.isRevealed && !tile.isIlluminated) {
                newBoard[coord] = tile.copy(isIlluminated = true)
                tilesLit++
            }
        }
        
        // Track tiles illuminated on the lantern structure for VP bonus
        val updatedStructures = state.structures.map { s ->
            if (s.location == center && s.type == StructureType.LANTERN) {
                s.copy(tilesIlluminated = s.tilesIlluminated + tilesLit)
            } else s
        }
        
        var newState = state.copy(board = newBoard, structures = updatedStructures)
            .addEvent("💡 Area illuminated! ($tilesLit tiles)")
        
        // Award VP if lantern illuminated 4+ tiles
        if (tilesLit >= 4) {
            newState = newState.addEvent("🏆 Well-placed Lantern! +1 VP")
        }
        
        return newState
    }
    
    /**
     * Clear rubble from a tile (costs resources)
     */
    fun clearRubble(state: GameState, location: HexCoordinate): GameState {
        val tile = state.board[location] ?: return state
        val player = state.currentPlayer
        
        if (!tile.hasRubble) {
            return state.addEvent("❌ No rubble to clear")
        }
        
        // Costs 1 iron ore and 1 basalt to clear
        val cost = mapOf(Resource.IRON_ORE to 1, Resource.BASALT to 1)
        if (!player.canAfford(cost)) {
            return state.addEvent("❌ Need Iron + Basalt to clear rubble")
        }
        
        val newTile = tile.copy(hasRubble = false)
        val newBoard = state.board.toMutableMap()
        newBoard[location] = newTile
        
        val newPlayer = player.removeResources(cost)
        
        return state.copy(
            board = newBoard,
            actionsThisTurn = state.actionsThisTurn + 1
        ).updatePlayer(newPlayer).addEvent("🧹 Cleared rubble at (${location.q}, ${location.r})")
    }
    
    /**
     * End the current turn
     */
    fun endTurn(state: GameState): GameState {
        val nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
        val newTurnNumber = if (nextPlayerIndex == 0) state.turnNumber + 1 else state.turnNumber
        
        // Tick structure cooldowns
        val stateWithCooldowns = tickStructureCooldowns(state)
        
        // Reset max actions to base value (undo any bonus action from consolation)
        val baseMaxActions = state.selectedCharacter.modifyMaxActions(state.difficulty.maxActionsPerTurn)
        
        return stateWithCooldowns.copy(
            currentPlayerIndex = nextPlayerIndex,
            turnNumber = newTurnNumber,
            turnPhase = TurnPhase.ROLL_DICE,
            actionsThisTurn = 0,
            maxActionsPerTurn = baseMaxActions,
            canExploreThisTurn = true,
            exploresThisTurn = 0,
            lastDiceResult = null,
            lastExplorationEvent = null,
            selectedTile = null,
            showBuildMenu = false,
            discountTradeAvailable = false,
            pendingConsolation = false
        ).addEvent("➡️ Turn $newTurnNumber - ${state.players[nextPlayerIndex].name}'s turn")
    }
    
    /**
     * Check if a player has won
     */
    fun checkVictory(state: GameState): GameState {
        for (player in state.players) {
            val totalVP = state.totalVPFor(player)
            
            if (totalVP >= state.victoryPointsToWin) {
                return state.copy(
                    gameOver = true,
                    winner = player
                ).addEvent("🎉 ${player.name} wins with $totalVP VP!")
            }
        }
        return state
    }
    
    /**
     * Get list of valid actions for current player
     */
    fun getAvailableActions(state: GameState): List<GameAction> {
        val actions = mutableListOf<GameAction>()
        
        when (state.turnPhase) {
            TurnPhase.ROLL_DICE -> {
                actions.add(GameAction.RollDice)
            }
            TurnPhase.MAIN_ACTION, TurnPhase.PRODUCTION -> {
                // Can always end turn
                actions.add(GameAction.EndTurn)
                
                if (state.actionsThisTurn < state.maxActionsPerTurn) {
                    // Explore actions
                    if (state.canExploreThisTurn) {
                        getExplorableCoordinates(state).forEach { coord ->
                            actions.add(GameAction.Explore(coord))
                        }
                    }
                    
                    // Build actions
                    getBuildableStructures(state).forEach { (type, locations) ->
                        locations.forEach { loc ->
                            actions.add(GameAction.Build(type, loc))
                        }
                    }
                    
                    // Clear rubble
                    getClearableRubble(state).forEach { coord ->
                        actions.add(GameAction.ClearRubble(coord))
                    }
                }
            }
            TurnPhase.END_TURN -> {
                actions.add(GameAction.EndTurn)
            }
        }
        
        return actions
    }
    
    private fun getExplorableCoordinates(state: GameState): List<HexCoordinate> {
        val revealed = state.board.values.filter { it.isRevealed }.map { it.coordinate }.toSet()
        val explorable = mutableSetOf<HexCoordinate>()
        
        revealed.forEach { coord ->
            coord.neighbors().forEach { neighbor ->
                val tile = state.board[neighbor]
                if (tile != null && !tile.isRevealed) {
                    explorable.add(neighbor)
                }
            }
        }
        
        return explorable.toList()
    }
    
    private fun getBuildableStructures(state: GameState): Map<StructureType, List<HexCoordinate>> {
        val player = state.currentPlayer
        val result = mutableMapOf<StructureType, MutableList<HexCoordinate>>()
        
        StructureType.entries.forEach { type ->
            if (player.canAfford(type.cost)) {
                result[type] = mutableListOf()
                
                state.board.values.filter { tile ->
                    tile.isRevealed &&
                    tile.terrain != TerrainType.MAGMA_FLOW &&
                    tile.terrain != TerrainType.BEDROCK
                }.forEach { tile ->
                    val existing = state.getStructureAt(tile.coordinate)
                    
                    val canBuild = when {
                        type == StructureType.EXCAVATOR -> existing?.type == StructureType.OUTPOST
                        existing != null -> false
                        else -> true
                    }
                    
                    if (canBuild) {
                        result[type]?.add(tile.coordinate)
                    }
                }
            }
        }
        
        return result.filter { it.value.isNotEmpty() }
    }
    
    private fun getClearableRubble(state: GameState): List<HexCoordinate> {
        val player = state.currentPlayer
        val cost = mapOf(Resource.IRON_ORE to 1, Resource.BASALT to 1)
        
        if (!player.canAfford(cost)) return emptyList()
        
        return state.board.values
            .filter { it.hasRubble }
            .map { it.coordinate }
    }
    
    /**
     * Trade resources at ratio based on difficulty
     * Easy: 3:1, Normal: 4:1, Hard: 5:1, Nightmare: 6:1
     * If discountTradeAvailable, first trade is 2:1
     */
    fun tradeResources(state: GameState, give: Resource, receive: Resource): GameState {
        if (give == receive) {
            return state.addEvent("❌ Cannot trade same resource!")
        }
        
        val player = state.currentPlayer
        val useDiscount = state.discountTradeAvailable
        val tradeRatio = if (useDiscount) 2 else state.difficulty.tradeRatio
        
        if (player.getResourceCount(give) < tradeRatio) {
            return state.addEvent("❌ Need $tradeRatio ${give.displayName()} to trade!")
        }
        
        val newPlayer = player
            .addResource(give, -tradeRatio)
            .addResource(receive, 1)
        
        var newState = state.updatePlayer(newPlayer)
            .addEvent("🔄 Traded $tradeRatio ${give.displayName()} for 1 ${receive.displayName()}")
        
        // Consume the discount trade
        if (useDiscount) {
            newState = newState.copy(discountTradeAvailable = false)
                .addEvent("🤝 Discount trade used!")
        }
        
        return newState
    }
    
    /**
     * Check if player can make a trade based on difficulty trade ratio
     */
    fun canTrade(state: GameState): Boolean {
        val tradeRatio = if (state.discountTradeAvailable) 2 else state.difficulty.tradeRatio
        return state.currentPlayer.resources.any { (_, count) -> count >= tradeRatio }
    }
    
    /**
     * Get resources that can be given in a trade based on difficulty
     */
    fun getTradableResources(state: GameState): List<Resource> {
        val tradeRatio = if (state.discountTradeAvailable) 2 else state.difficulty.tradeRatio
        return state.currentPlayer.resources
            .filter { (_, count) -> count >= tradeRatio }
            .keys.toList()
    }
    
    /**
     * Use a structure's active ability
     */
    fun useStructureAbility(state: GameState, structureLocation: HexCoordinate): GameState {
        val structure = state.getStructureAt(structureLocation) 
            ?: return state.addEvent("❌ No structure at this location")
        
        val ability = structure.type.ability 
            ?: return state.addEvent("❌ This structure has no active ability")
        
        if (structure.abilityCooldown > 0) {
            return state.addEvent("❌ Ability on cooldown (${structure.abilityCooldown} turns)")
        }
        
        var newState = state
        var player = state.currentPlayer
        
        when (ability) {
            StructureAbility.FLARE -> {
                // Reveal terrain type of 1 adjacent unexplored tile
                val adjacentUnexplored = structureLocation.neighbors()
                    .mapNotNull { state.board[it] }
                    .filter { !it.isRevealed }
                    .randomOrNull()
                
                if (adjacentUnexplored != null) {
                    // Generate what the terrain would be (preview only)
                    val terrainOptions = when (adjacentUnexplored.zone) {
                        Zone.SURFACE -> listOf("Lichen Field", "Fungal Forest", "Quarry")
                        Zone.CRUST -> listOf("Iron Vein", "Fungal Forest", "Quarry")
                        Zone.MANTLE -> listOf("Crystal Grotto", "Iron Vein", "Magma")
                        Zone.CORE -> listOf("Crystal Grotto", "Ancient Chamber", "Magma")
                    }
                    val hint = terrainOptions.random()
                    newState = newState.addEvent("🔥 Flare reveals: Likely $hint ahead at (${adjacentUnexplored.coordinate.q}, ${adjacentUnexplored.coordinate.r})")
                } else {
                    newState = newState.addEvent("🔥 Flare: No unexplored tiles nearby")
                }
            }
            
            StructureAbility.OVERTIME -> {
                // Produce 1 resource from this tile regardless of dice
                val tile = state.board[structureLocation]
                val resource = tile?.terrain?.produces
                if (resource != null) {
                    player = player.addResource(resource, 1)
                    newState = newState.addEvent("⚒️ Overtime! +1 ${resource.displayName()}")
                } else {
                    newState = newState.addEvent("❌ This tile doesn't produce resources")
                }
            }
            
            StructureAbility.SURVEY -> {
                // Reveal number tokens on adjacent unexplored tiles
                val adjacentUnexplored = structureLocation.neighbors()
                    .mapNotNull { state.board[it] }
                    .filter { !it.isRevealed }
                    .take(3)
                
                if (adjacentUnexplored.isNotEmpty()) {
                    val hints = adjacentUnexplored.map { 
                        val numberPool = when (it.zone) {
                            Zone.SURFACE -> listOf(4, 5, 9, 10)
                            Zone.CRUST -> listOf(5, 6, 8, 9)
                            Zone.MANTLE -> listOf(6, 6, 8, 8)
                            Zone.CORE -> listOf(6, 7, 8)
                        }
                        "(${it.coordinate.q},${it.coordinate.r}): likely ${numberPool.random()}"
                    }
                    newState = newState.addEvent("🔍 Survey reveals: ${hints.joinToString(", ")}")
                } else {
                    newState = newState.addEvent("🔍 Survey: No unexplored tiles nearby")
                }
            }
            
            StructureAbility.SPORE_BURST -> {
                // Gain 2 Mycelium immediately
                player = player.addResource(Resource.MYCELIUM, 2)
                newState = newState.addEvent("🍄 Spore Burst! +2 Mycelium")
            }
        }
        
        // Put ability on cooldown
        val updatedStructures = state.structures.map { s ->
            if (s.location == structureLocation) {
                s.copy(abilityCooldown = ability.cooldown)
            } else s
        }
        
        return newState
            .copy(structures = updatedStructures)
            .updatePlayer(player)
    }
    
    /**
     * Reduce all structure cooldowns at end of turn
     */
    fun tickStructureCooldowns(state: GameState): GameState {
        val updatedStructures = state.structures.map { s ->
            if (s.abilityCooldown > 0) {
                s.copy(abilityCooldown = s.abilityCooldown - 1)
            } else s
        }
        return state.copy(structures = updatedStructures)
    }
    
    /**
     * Get structures with usable abilities
     */
    fun getUsableAbilities(state: GameState): List<Structure> {
        return state.structures.filter { s ->
            s.type.ability != null && s.abilityCooldown == 0
        }
    }
    
    /**
     * Generate an interactive event (player choice required)
     * Returns null if no interactive event occurs
     */
    fun maybeGenerateInteractiveEvent(state: GameState, coord: HexCoordinate): InteractiveEvent? {
        val tile = state.board[coord] ?: return null
        
        // Only trigger interactive events in deeper zones (Mantle/Core)
        if (tile.zone != Zone.MANTLE && tile.zone != Zone.CORE) return null
        
        // 25% chance of interactive event in Mantle, 40% in Core
        val eventChance = when (tile.zone) {
            Zone.MANTLE -> 25
            Zone.CORE -> 40
            else -> 0
        }
        
        if ((1..100).random() > eventChance) return null
        
        // Pick random interactive event, weighted by tile preset
        val events = when (tile.presetHint) {
            "crystal_rich" -> listOf(
                InteractiveEvent.AncientCache(),
                InteractiveEvent.AncientCache(),
                InteractiveEvent.UnstableGround()
            )
            "hazardous" -> listOf(
                InteractiveEvent.UnstableGround(),
                InteractiveEvent.BeetleSwarm(),
                InteractiveEvent.UnstableGround()
            )
            "organic_rich" -> listOf(
                InteractiveEvent.BeetleSwarm(),
                InteractiveEvent.LostMinerEncounter(),
                InteractiveEvent.BeetleSwarm()
            )
            else -> listOf(
                InteractiveEvent.BeetleSwarm(),
                InteractiveEvent.UnstableGround(),
                InteractiveEvent.AncientCache(),
                InteractiveEvent.LostMinerEncounter()
            )
        }
        
        return events.random()
    }
    
    /**
     * Resolve player choice for an interactive event
     */
    fun resolveInteractiveEvent(
        state: GameState, 
        event: InteractiveEvent, 
        choiceId: String,
        coord: HexCoordinate
    ): GameState {
        var newState = state.copy(pendingInteractiveEvent = null, pendingEventCoord = null)
        var player = newState.currentPlayer
        val character = newState.selectedCharacter
        
        when (event) {
            is InteractiveEvent.BeetleSwarm -> {
                when (choiceId) {
                    "fight" -> {
                        val win = (1..100).random() <= 50
                        if (win) {
                            player = player.addResource(Resource.CHITIN, 3)
                            newState = newState.addEvent("⚔️ Fought off beetles! +3 Chitin")
                        } else {
                            newState = newState.copy(actionsThisTurn = newState.actionsThisTurn + 1)
                                .addEvent("💢 Beetles overwhelmed you! Lost 1 action")
                        }
                    }
                    "sneak" -> {
                        // Mark tile as infested (no production bonus)
                        newState = newState.addEvent("🤫 Snuck past. Tile marked infested")
                    }
                    "retreat" -> {
                        newState = newState.addEvent("🏃 Retreated safely")
                    }
                }
            }
            
            is InteractiveEvent.UnstableGround -> {
                when (choiceId) {
                    "careful" -> {
                        newState = newState.copy(actionsThisTurn = newState.actionsThisTurn + 1)
                            .addEvent("🚶 Proceeded carefully. Extra action used")
                    }
                    "rush" -> {
                        val caveIn = (1..100).random() <= 30
                        // Check character hazard resistance
                        val resistChance = (character.hazardResistance() * 100).toInt()
                        val resisted = caveIn && (1..100).random() <= resistChance
                        
                        if (caveIn && !resisted) {
                            val board = newState.board.toMutableMap()
                            board[coord] = board[coord]!!.copy(hasRubble = true)
                            newState = newState.copy(board = board)
                                .addEvent("💥 Cave-in! Tile has rubble")
                        } else if (resisted) {
                            newState = newState.addEvent("🛡️ Cave-in blocked by ${character.displayName}'s resistance!")
                        } else {
                            newState = newState.addEvent("🏃 Rushed through safely!")
                        }
                    }
                    "reinforce" -> {
                        val cost = mapOf(Resource.BASALT to 2)
                        if (player.canAfford(cost)) {
                            player = player.removeResources(cost)
                            newState = newState.addEvent("🏗️ Reinforced tunnel. Tile is safe")
                        } else {
                            newState = newState.addEvent("❌ Not enough Basalt!")
                        }
                    }
                }
            }
            
            is InteractiveEvent.AncientCache -> {
                when (choiceId) {
                    "open" -> {
                        val roll = (1..100).random()
                        when {
                            roll <= 40 -> {
                                // Resources!
                                val resource = listOf(Resource.CRYSTAL, Resource.IRON_ORE, Resource.CHITIN).random()
                                val amount = (2..4).random()
                                player = player.addResource(resource, amount)
                                newState = newState.addEvent("📦 Cache contained $amount ${resource.displayName()}!")
                            }
                            roll <= 70 -> {
                                // VP bonus
                                player = player.copy(victoryPoints = player.victoryPoints + 1)
                                newState = newState.addEvent("🏆 Ancient artifact found! +1 VP")
                            }
                            else -> {
                                // Trap!
                                val chitinLoss = minOf(2, player.resources[Resource.CHITIN] ?: 0)
                                if (chitinLoss > 0) {
                                    player = player.removeResources(mapOf(Resource.CHITIN to chitinLoss))
                                }
                                newState = newState.addEvent("⚠️ Trap! Lost $chitinLoss Chitin")
                            }
                        }
                    }
                    "study" -> {
                        // Give a hint about contents
                        newState = newState.addEvent("🔍 Contents look valuable... (can open next turn)")
                    }
                    "leave" -> {
                        newState = newState.addEvent("🚫 Left cache alone")
                    }
                }
            }
            
            is InteractiveEvent.LostMinerEncounter -> {
                when (choiceId) {
                    "rescue" -> {
                        // Extra exploration this turn
                        newState = newState.copy(canExploreThisTurn = true, exploresThisTurn = 0)
                            .addEvent("🤝 Rescued miner! They show you a shortcut (extra explore)")
                    }
                    "trade" -> {
                        val cost = mapOf(Resource.LICHEN to 2)
                        if (player.canAfford(cost)) {
                            player = player.removeResources(cost)
                            player = player.addResource(Resource.IRON_ORE, 1)
                            newState = newState.addEvent("🔄 Traded 2 Lichen for 1 Iron")
                        } else {
                            newState = newState.addEvent("❌ Not enough Lichen!")
                        }
                    }
                    "directions" -> {
                        val returnsWithGift = (1..100).random() <= 25
                        if (returnsWithGift) {
                            player = player.addResource(Resource.CRYSTAL, 1)
                            newState = newState.addEvent("👆 Miner returns with a gift! +1 Crystal")
                        } else {
                            newState = newState.addEvent("👆 Gave directions. Miner thanks you")
                        }
                    }
                }
            }
        }
        
        return newState.updatePlayer(player)
    }
}
