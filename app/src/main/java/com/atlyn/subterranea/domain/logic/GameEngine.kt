package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*

/**
 * Core game logic engine - high-level turn flow and system orchestration.
 */
object GameEngine {

    fun rollDiceAndProduce(state: GameState): GameState {
        val diceResult = DiceResult.roll()
        var newState = state.copy(
            lastDiceResult = diceResult,
            lastProduction = emptyMap(),
            turnPhase = TurnPhase.MAIN_ACTION
        ).addEvent("🎲 Rolled ${diceResult.die1} + ${diceResult.die2} = ${diceResult.total}")

        val producingTiles = state.board.values.filter { tile ->
            tile.matchesRoll(diceResult.total) &&
            tile.isRevealed &&
            tile.isIlluminated &&
            !tile.hasRubble &&
            tile.terrain.produces != null
        }

        if (diceResult.total == GameConstants.LUCKY_ROLL && producingTiles.isEmpty()) {
            return newState.copy(pendingConsolation = true)
                .addEvent("🍀 Lucky 7! Choose your consolation...")
        }

        if (producingTiles.isEmpty()) {
            val darkProducingTiles = state.board.values.filter { tile ->
                tile.matchesRoll(diceResult.total) &&
                tile.isRevealed &&
                !tile.isIlluminated &&
                !tile.hasRubble &&
                tile.terrain.produces != null
            }
            if (darkProducingTiles.isNotEmpty()) {
                val resources = darkProducingTiles.mapNotNull { it.terrain.produces?.displayName() }.distinct()
                return newState.addEvent("🌑 ${resources.joinToString(", ")} would produce if illuminated! Build a Lantern 🔦")
            }

            val player = newState.currentPlayer
            val bonus = Resource.entries.minByOrNull { player.getResourceCount(it) } ?: Resource.MYCELIUM
            val updatedPlayer = player.addResource(bonus, 1)
            return newState.updatePlayer(updatedPlayer)
                .copy(lastProduction = mapOf(bonus to 1))
                .addEvent("🔍 Nothing produced — scavenged +1 ${bonus.displayName()}")
        }

        val productionTotals = mutableMapOf<Resource, Int>()
        val difficulty = state.difficulty
        var player = newState.currentPlayer

        producingTiles.forEach { tile ->
            val resource = tile.terrain.produces!!
            val structure = newState.getStructureAt(tile.coordinate)
            val structureMultiplier = when (structure?.type) {
                StructureType.EXCAVATOR -> 2
                StructureType.CRYSTAL_REFINERY -> if (resource == Resource.CRYSTAL) 2 else 1
                StructureType.FUNGAL_FARM -> if (resource == Resource.MYCELIUM) 2 else 1
                else -> 1
            }

            var amount = structureMultiplier + difficulty.productionBonus
            if (difficulty.rareResourcePenalty && (resource == Resource.IRON_ORE || resource == Resource.CRYSTAL)) {
                amount = maxOf(1, amount - 1)
            }
            amount = maxOf(1, amount)

            player = player.addResource(resource, amount)
            productionTotals[resource] = (productionTotals[resource] ?: 0) + amount
            newState = newState.addEvent("⛏️ ${tile.terrain.displayName()} produces $amount ${resource.displayName()}")
        }

        if (player.getResourceCount(Resource.CRYSTAL) >= 10 && Achievement.CRYSTAL_BARON !in player.achievements) {
            player = player.copy(achievements = player.achievements + Achievement.CRYSTAL_BARON)
            newState = newState.addEvent("🏆 Achievement: Crystal Baron!")
        }

        return newState.copy(lastProduction = productionTotals).updatePlayer(player)
    }

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
                newState = newState.copy(maxActionsPerTurn = newState.maxActionsPerTurn + 1)
                    .addEvent("⚡ Hustle! +1 action this turn")
            }
            RollConsolation.DISCOUNT_TRADE -> {
                newState = newState.copy(discountTradeAvailable = true)
                    .addEvent("🤝 Barter! One 2:1 trade available this turn")
            }
        }

        return newState
    }

    fun exploreTile(state: GameState, coord: HexCoordinate): GameState =
        ExplorationEngine.exploreTile(state, coord)

    fun buildStructure(state: GameState, structureType: StructureType, location: HexCoordinate): GameState =
        StructureEngine.buildStructure(state, structureType, location)

    fun getAdjustedBuildCost(
        structureType: StructureType,
        difficulty: Difficulty,
        character: GameCharacter
    ): Map<Resource, Int> = StructureEngine.getAdjustedBuildCost(structureType, difficulty, character)

    fun clearRubble(state: GameState, location: HexCoordinate): GameState =
        StructureEngine.clearRubble(state, location)

    fun endTurn(state: GameState): GameState {
        val nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
        val newTurnNumber = if (nextPlayerIndex == 0) state.turnNumber + 1 else state.turnNumber

        val stateWithCooldowns = tickStructureCooldowns(state)
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
            discountTradeAvailable = false,
            pendingConsolation = false
        ).addEvent("➡️ Turn $newTurnNumber - ${state.players[nextPlayerIndex].name}'s turn")
            .let { checkTurnLimit(it) }
    }

    fun checkVictory(state: GameState): GameState {
        for (player in state.players) {
            val totalVP = state.totalVPFor(player)
            if (totalVP >= state.victoryPointsToWin) {
                return state.copy(gameOver = true, winner = player)
                    .addEvent("🎉 ${player.name} wins with $totalVP VP!")
            }
        }
        return state
    }

    private fun checkTurnLimit(state: GameState): GameState {
        if (state.gameOver) return state
        val maxTurns = state.difficulty.maxTurns
        if (state.turnNumber > maxTurns) {
            val player = state.currentPlayer
            val totalVP = state.totalVPFor(player)
            return state.copy(gameOver = true, winner = null)
                .addEvent("⏰ Time's up! Reached turn $maxTurns with $totalVP/${state.victoryPointsToWin} VP.")
        }
        return state
    }

    fun getAvailableActions(state: GameState): List<GameAction> {
        val actions = mutableListOf<GameAction>()

        when (state.turnPhase) {
            TurnPhase.ROLL_DICE -> {
                actions.add(GameAction.RollDice)
            }
            TurnPhase.MAIN_ACTION, TurnPhase.PRODUCTION -> {
                actions.add(GameAction.EndTurn)
                if (state.actionsThisTurn < state.maxActionsPerTurn) {
                    if (state.canExploreThisTurn) {
                        ExplorationEngine.getExplorableCoordinates(state).forEach { coord ->
                            actions.add(GameAction.Explore(coord))
                        }
                    }

                    StructureEngine.getBuildableStructures(state).forEach { (type, locations) ->
                        locations.forEach { loc ->
                            actions.add(GameAction.Build(type, loc))
                        }
                    }

                    StructureEngine.getClearableRubble(state).forEach { coord ->
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

    fun tradeResources(state: GameState, give: Resource, receive: Resource): GameState =
        TradeEngine.tradeResources(state, give, receive)

    fun canTrade(state: GameState): Boolean = TradeEngine.canTrade(state)

    fun getTradableResources(state: GameState): List<Resource> =
        TradeEngine.getTradableResources(state)

    fun useStructureAbility(state: GameState, structureLocation: HexCoordinate): GameState =
        StructureEngine.useStructureAbility(state, structureLocation)

    fun tickStructureCooldowns(state: GameState): GameState =
        StructureEngine.tickStructureCooldowns(state)

    fun getUsableAbilities(state: GameState): List<Structure> =
        StructureEngine.getUsableAbilities(state)

    fun maybeGenerateInteractiveEvent(state: GameState, coord: HexCoordinate): InteractiveEvent? =
        EventEngine.maybeGenerateInteractiveEvent(state, coord)

    fun resolveInteractiveEvent(
        state: GameState,
        event: InteractiveEvent,
        choiceId: InteractiveChoiceId,
        coord: HexCoordinate
    ): GameState = EventEngine.resolveInteractiveEvent(state, event, choiceId, coord)
}
