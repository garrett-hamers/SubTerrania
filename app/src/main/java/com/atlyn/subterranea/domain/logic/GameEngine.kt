package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*
import com.atlyn.subterranea.domain.telemetry.GameTelemetry
import java.util.Locale

/**
 * Core game logic engine - high-level turn flow and system orchestration.
 */
object GameEngine {

    fun rollDiceAndProduce(state: GameState): GameState {
        val baseDetails = linkedMapOf<String, Any>()
        val diceResult = DiceResult.roll()
        baseDetails["die1"] = diceResult.die1
        baseDetails["die2"] = diceResult.die2
        baseDetails["diceTotal"] = diceResult.total

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
            val result = newState.copy(pendingConsolation = true)
                .addEvent("🍀 Lucky 7! Choose your consolation...")
            GameTelemetry.logTransition(
                event = "roll_result",
                before = state,
                after = result,
                outcome = GameTelemetry.Outcome.SUCCESS,
                details = baseDetails + mapOf(
                    "branch" to "lucky_consolation",
                    "producingTileCount" to 0
                )
            )
            return result
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
                val result = newState.addEvent(
                    "🌑 ${resources.joinToString(", ")} would produce if illuminated! Build a Lantern 🔦"
                )
                GameTelemetry.logTransition(
                    event = "roll_result",
                    before = state,
                    after = result,
                    outcome = GameTelemetry.Outcome.SUCCESS,
                    details = baseDetails + mapOf(
                        "branch" to "dark_tiles_present",
                        "producingTileCount" to 0,
                        "darkResources" to resources
                    )
                )
                return result
            }

            val player = newState.currentPlayer
            val bonus = Resource.entries.minByOrNull { player.getResourceCount(it) } ?: Resource.MYCELIUM
            val updatedPlayer = player.addResource(bonus, 1)
            val result = newState.updatePlayer(updatedPlayer)
                .copy(lastProduction = mapOf(bonus to 1))
                .addEvent("🔍 Nothing produced — scavenged +1 ${bonus.displayName()}")
            GameTelemetry.logTransition(
                event = "roll_result",
                before = state,
                after = result,
                outcome = GameTelemetry.Outcome.SUCCESS,
                details = baseDetails + mapOf(
                    "branch" to "scavenge_bonus",
                    "producingTileCount" to 0,
                    "bonusResource" to bonus.name
                )
            )
            return result
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

        val result = newState.copy(lastProduction = productionTotals).updatePlayer(player)
        GameTelemetry.logTransition(
            event = "roll_result",
            before = state,
            after = result,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = baseDetails + mapOf(
                "branch" to "production",
                "producingTileCount" to producingTiles.size,
                "productionTotals" to resourceMapByName(productionTotals)
            )
        )
        return result
    }

    fun resolveConsolation(state: GameState, choice: RollConsolation): GameState {
        if (!state.pendingConsolation) {
            GameTelemetry.logTransition(
                event = "consolation_choice_result",
                before = state,
                after = state,
                outcome = GameTelemetry.Outcome.REJECTED,
                reasonCode = "no_pending_consolation",
                details = mapOf("choice" to choice.name)
            )
            return state
        }

        var newState = state.copy(pendingConsolation = false)
        var player = newState.currentPlayer
        var branch = choice.name.lowercase(Locale.US)
        var bonusResource: String? = null

        when (choice) {
            RollConsolation.GAIN_RESOURCE -> {
                val commonResources = listOf(Resource.MYCELIUM, Resource.BASALT, Resource.CHITIN, Resource.LICHEN)
                val bonus = commonResources.random()
                bonusResource = bonus.name
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

        val result = newState
        GameTelemetry.logTransition(
            event = "consolation_choice_result",
            before = state,
            after = result,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = mapOf(
                "choice" to choice.name,
                "branch" to branch,
                "bonusResource" to bonusResource
            )
        )
        return result
    }

    fun exploreTile(state: GameState, coord: HexCoordinate): GameState {
        val result = ExplorationEngine.exploreTile(state, coord)
        val latestMessage = GameTelemetry.eventMessage(result)
        val success = result.actionsThisTurn > state.actionsThisTurn &&
            result.board[coord]?.isRevealed == true
        val reasonCode = if (success) {
            null
        } else {
            GameTelemetry.reasonCodeFromEventMessage(latestMessage) ?: "explore_rejected"
        }
        GameTelemetry.logTransition(
            event = "explore_result",
            before = state,
            after = result,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = mapOf(
                "location" to GameTelemetry.coordinatePayload(coord),
                "explorationEvent" to result.lastExplorationEvent?.name
            )
        )
        return result
    }

    fun buildStructure(state: GameState, structureType: StructureType, location: HexCoordinate): GameState {
        val adjustedCost = getAdjustedBuildCost(structureType, state.difficulty, state.selectedCharacter)
        val result = StructureEngine.buildStructure(state, structureType, location)
        val latestMessage = GameTelemetry.eventMessage(result)
        val success = latestMessage?.startsWith("🏗️ Built") == true && result.actionsThisTurn > state.actionsThisTurn
        val reasonCode = if (success) {
            null
        } else {
            GameTelemetry.reasonCodeFromEventMessage(latestMessage) ?: "build_rejected"
        }
        GameTelemetry.logTransition(
            event = "build_result",
            before = state,
            after = result,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = mapOf(
                "structureType" to structureType.name,
                "location" to GameTelemetry.coordinatePayload(location),
                "adjustedCost" to resourceMapByName(adjustedCost)
            )
        )
        return result
    }

    fun getAdjustedBuildCost(
        structureType: StructureType,
        difficulty: Difficulty,
        character: GameCharacter
    ): Map<Resource, Int> = StructureEngine.getAdjustedBuildCost(structureType, difficulty, character)

    fun clearRubble(state: GameState, location: HexCoordinate): GameState {
        val result = StructureEngine.clearRubble(state, location)
        val latestMessage = GameTelemetry.eventMessage(result)
        val success = latestMessage?.startsWith("🧹 Cleared rubble") == true &&
            result.actionsThisTurn > state.actionsThisTurn
        val reasonCode = if (success) {
            null
        } else {
            GameTelemetry.reasonCodeFromEventMessage(latestMessage) ?: "clear_rubble_rejected"
        }
        GameTelemetry.logTransition(
            event = "clear_rubble_result",
            before = state,
            after = result,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = mapOf("location" to GameTelemetry.coordinatePayload(location))
        )
        return result
    }

    fun endTurn(state: GameState): GameState {
        val nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
        val newTurnNumber = if (nextPlayerIndex == 0) state.turnNumber + 1 else state.turnNumber

        val stateWithCooldowns = tickStructureCooldowns(state)
        val baseMaxActions = state.selectedCharacter.modifyMaxActions(state.difficulty.maxActionsPerTurn)

        val result = stateWithCooldowns.copy(
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

        GameTelemetry.logTransition(
            event = "turn_end",
            before = state,
            after = result,
            outcome = GameTelemetry.Outcome.SUCCESS,
            details = mapOf(
                "previousPlayerId" to state.currentPlayer.id,
                "nextPlayerId" to result.currentPlayer.id,
                "turnAdvanced" to (result.turnNumber > state.turnNumber)
            )
        )
        if (!result.gameOver) {
            GameTelemetry.logState(
                event = "turn_start",
                state = result,
                outcome = GameTelemetry.Outcome.SUCCESS
            )
        }
        return result
    }

    fun checkVictory(state: GameState): GameState {
        if (state.gameOver) return state
        for (player in state.players) {
            val totalVP = state.totalVPFor(player)
            if (totalVP >= state.victoryPointsToWin) {
                val result = state.copy(gameOver = true, winner = player)
                    .addEvent("🎉 ${player.name} wins with $totalVP VP!")
                GameTelemetry.logTransition(
                    event = "game_end",
                    before = state,
                    after = result,
                    outcome = GameTelemetry.Outcome.SUCCESS,
                    details = mapOf(
                        "endReason" to "victory",
                        "winnerId" to player.id,
                        "winnerName" to player.name,
                        "difficulty" to result.difficulty.name,
                        "character" to result.selectedCharacter.name,
                        "mapPreset" to result.mapPreset.name,
                        "finalVP" to totalVP,
                        "vpTarget" to result.victoryPointsToWin,
                        "turnReached" to result.turnNumber,
                        "finalResources" to resourceMapByName(player.resources),
                        "structuresBuiltByType" to structureCountsForPlayer(result, player.id),
                        "revealedTiles" to result.board.values.count { it.isRevealed },
                        "illuminatedTiles" to result.board.values.count { it.isIlluminated },
                        "explorationCount" to player.explorationCount
                    )
                )
                return result
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
            val result = state.copy(gameOver = true, winner = null)
                .addEvent("⏰ Time's up! Reached turn $maxTurns with $totalVP/${state.victoryPointsToWin} VP.")
            GameTelemetry.logTransition(
                event = "game_end",
                before = state,
                after = result,
                outcome = GameTelemetry.Outcome.SUCCESS,
                details = mapOf(
                    "endReason" to "turn_limit",
                    "winnerId" to null,
                    "winnerName" to null,
                    "difficulty" to result.difficulty.name,
                    "character" to result.selectedCharacter.name,
                    "mapPreset" to result.mapPreset.name,
                    "finalVP" to totalVP,
                    "vpTarget" to result.victoryPointsToWin,
                    "turnReached" to result.turnNumber,
                    "finalResources" to resourceMapByName(player.resources),
                    "structuresBuiltByType" to structureCountsForPlayer(result, player.id),
                    "revealedTiles" to result.board.values.count { it.isRevealed },
                    "illuminatedTiles" to result.board.values.count { it.isIlluminated },
                    "explorationCount" to player.explorationCount
                )
            )
            return result
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

    fun tradeResources(state: GameState, give: Resource, receive: Resource): GameState {
        val tradeRatio = if (state.discountTradeAvailable) 2 else state.difficulty.tradeRatio
        val result = TradeEngine.tradeResources(state, give, receive)
        val latestMessage = GameTelemetry.eventMessage(result)
        val success = latestMessage?.startsWith("🔄 Traded") == true
        val reasonCode = if (success) {
            null
        } else {
            GameTelemetry.reasonCodeFromEventMessage(latestMessage) ?: "trade_rejected"
        }
        GameTelemetry.logTransition(
            event = "trade_result",
            before = state,
            after = result,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = mapOf(
                "giveResource" to give.name,
                "receiveResource" to receive.name,
                "tradeRatio" to tradeRatio,
                "usedDiscountTrade" to state.discountTradeAvailable
            )
        )
        return result
    }

    fun canTrade(state: GameState): Boolean = TradeEngine.canTrade(state)

    fun getTradableResources(state: GameState): List<Resource> =
        TradeEngine.getTradableResources(state)

    fun useStructureAbility(state: GameState, structureLocation: HexCoordinate): GameState {
        val result = StructureEngine.useStructureAbility(state, structureLocation)
        val latestMessage = GameTelemetry.eventMessage(result)
        val success = latestMessage != null && !GameTelemetry.isRejectedMessage(latestMessage)
        val reasonCode = if (success) {
            null
        } else {
            GameTelemetry.reasonCodeFromEventMessage(latestMessage) ?: "ability_rejected"
        }
        GameTelemetry.logTransition(
            event = "ability_result",
            before = state,
            after = result,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = mapOf("location" to GameTelemetry.coordinatePayload(structureLocation))
        )
        return result
    }

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
    ): GameState {
        val previousMessage = GameTelemetry.eventMessage(state)
        val result = EventEngine.resolveInteractiveEvent(state, event, choiceId, coord)
        val latestMessage = GameTelemetry.eventMessage(result)
        val messageChanged = latestMessage != previousMessage
        val success = messageChanged && !GameTelemetry.isRejectedMessage(latestMessage)
        val reasonCode = if (success) {
            null
        } else {
            GameTelemetry.reasonCodeFromEventMessage(latestMessage) ?: "interactive_choice_rejected"
        }
        GameTelemetry.logTransition(
            event = "interactive_event_choice_result",
            before = state,
            after = result,
            outcome = if (success) GameTelemetry.Outcome.SUCCESS else GameTelemetry.Outcome.REJECTED,
            reasonCode = reasonCode,
            details = mapOf(
                "eventType" to event.javaClass.simpleName,
                "choiceId" to choiceId.name,
                "location" to GameTelemetry.coordinatePayload(coord)
            )
        )
        return result
    }

    private fun resourceMapByName(resources: Map<Resource, Int>): Map<String, Int> {
        return resources.entries.associate { (resource, amount) ->
            resource.name.lowercase(Locale.US) to amount
        }
    }

    private fun structureCountsForPlayer(state: GameState, playerId: Int): Map<String, Int> {
        return state.structures
            .filter { it.ownerId == playerId }
            .groupingBy { it.type.name.lowercase(Locale.US) }
            .eachCount()
            .toSortedMap()
    }
}
