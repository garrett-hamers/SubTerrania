package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*

object ExplorationEngine {

    fun exploreTile(state: GameState, coord: HexCoordinate): GameState {
        val tile = state.board[coord] ?: return state

        if (tile.isRevealed) {
            return state.addEvent("❌ Tile already revealed")
        }

        val maxExplores = state.maxExploresPerTurn
        if (state.exploresThisTurn >= maxExplores) {
            return state.addEvent("❌ Already explored $maxExplores tile(s) this turn!")
        }

        val hasRevealedNeighbor = coord.neighbors().any { neighbor ->
            state.board[neighbor]?.isRevealed == true
        }
        if (!hasRevealedNeighbor) {
            return state.addEvent("❌ Must explore adjacent to revealed tile")
        }

        val (newTile, event) = generateExploredTile(tile, state.difficulty)
        val newBoard = state.board.toMutableMap()
        newBoard[coord] = newTile

        val newExploresThisTurn = state.exploresThisTurn + 1
        val canStillExplore = newExploresThisTurn < maxExplores

        var newState = state.copy(
            board = newBoard,
            lastExplorationEvent = event,
            canExploreThisTurn = canStillExplore,
            exploresThisTurn = newExploresThisTurn,
            actionsThisTurn = state.actionsThisTurn + 1
        ).addEvent("🔦 Explored ${newTile.terrain.displayName()} at (${coord.q}, ${coord.r})")

        newState = applyExplorationEvent(newState, event, coord)
        newState = checkIlluminationFromLanterns(newState, coord)

        var player = newState.currentPlayer.copy(
            explorationCount = newState.currentPlayer.explorationCount + 1
        )

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

        newState = newState.updatePlayer(player)

        val interactiveEvent = EventEngine.maybeGenerateInteractiveEvent(newState, coord)
        if (interactiveEvent != null) {
            newState = newState.copy(
                pendingInteractiveEvent = interactiveEvent,
                pendingEventCoord = coord
            )
        }

        return GameEngine.checkVictory(newState)
    }

    fun getExplorableCoordinates(state: GameState): List<HexCoordinate> {
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

    private fun checkIlluminationFromLanterns(state: GameState, coord: HexCoordinate): GameState {
        val tile = state.board[coord] ?: return state
        if (tile.isIlluminated) return state

        val hasNearbyLantern = (listOf(coord) + coord.neighbors()).any {
            state.structures.any { structure ->
                structure.type == StructureType.LANTERN && structure.location.distanceTo(coord) <= 1
            }
        }

        if (hasNearbyLantern) {
            val newBoard = state.board.toMutableMap()
            newBoard[coord] = tile.copy(isIlluminated = true)
            return state.copy(board = newBoard).addEvent("💡 Tile illuminated by nearby Lantern!")
        }

        return state
    }

    private fun generateExploredTile(
        tile: HexTile,
        difficulty: Difficulty = Difficulty.NORMAL
    ): Pair<HexTile, ExplorationEvent> {
        val terrain = tile.zone.explorationTerrainPool.random()
        val numberToken = if (terrain.produces != null) tile.zone.explorationNumberPool.random() else null

        val secondaryNumberToken = if (terrain.produces != null && tile.zone == Zone.CRUST) {
            tile.zone.secondaryNumberPool.random()
        } else null

        val event = generateExplorationEvent(tile.zone, difficulty, tile.presetHint)

        val hasRubble = event is ExplorationEvent.CaveIn
        val isMagma = event is ExplorationEvent.MagmaBurst
        val isIlluminated = event is ExplorationEvent.FungalBloom
        val finalTerrain = if (isMagma) TerrainType.MAGMA_FLOW else terrain

        val newTile = tile.copy(
            isRevealed = true,
            terrain = finalTerrain,
            numberToken = numberToken,
            secondaryNumberToken = secondaryNumberToken,
            hasRubble = hasRubble,
            isIlluminated = isIlluminated
        )

        return Pair(newTile, event)
    }

    private fun generateExplorationEvent(
        zone: Zone,
        difficulty: Difficulty = Difficulty.NORMAL,
        presetHint: MapPresetHint? = null
    ): ExplorationEvent {
        val roll = (1..GameConstants.RANDOM_ROLL_MAX).random()
        var hazardChance = (difficulty.hazardChance * 100).toInt()

        when (presetHint) {
            MapPresetHint.HAZARDOUS -> hazardChance = minOf(90, hazardChance + 20)
            // Phase O-4: bump CRYSTAL_RICH from 15 -> 30 so Crystal Caves
            // feels meaningfully Crystal-rich vs. the Standard map (K-1
            // showed map presets shifted the structure mix by <1 build).
            MapPresetHint.CRYSTAL_RICH -> if (roll <= 30 && zone != Zone.SURFACE) return ExplorationEvent.CrystalVein()
            MapPresetHint.IRON_RICH -> if (roll <= 10 && zone != Zone.SURFACE) {
                return ExplorationEvent.TreasureCache(mapOf(Resource.IRON_ORE to 2, Resource.BASALT to 1))
            }
            MapPresetHint.ORGANIC_RICH -> if (roll <= 15) {
                return if ((1..2).random() == 1) ExplorationEvent.FungalBloom() else ExplorationEvent.BeetleNest()
            }
            null -> Unit
        }

        val hazardRoll = (1..GameConstants.RANDOM_ROLL_MAX).random()
        val isHazardForced = hazardRoll <= hazardChance

        return when (zone) {
            Zone.SURFACE -> {
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
                    else -> ExplorationEvent.TreasureCache(mapOf(Resource.LICHEN to 2, Resource.MYCELIUM to 1))
                }
            }
            Zone.CRUST -> {
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

    private fun applyExplorationEvent(
        state: GameState,
        event: ExplorationEvent,
        coord: HexCoordinate
    ): GameState {
        var player = state.currentPlayer
        var newState = state

        when (event) {
            is ExplorationEvent.TreasureCache -> {
                event.resources.forEach { (resource, amount) ->
                    player = player.addResource(resource, amount)
                }
                newState = newState.addEvent(
                    "💎 Found treasure! ${event.resources.entries.joinToString(", ") { "${it.value} ${it.key.displayName()}" }}"
                )
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
                val newResources = player.resources.mapValues { (_, value) -> maxOf(0, value - 1) }
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
                player = player.addResource(Resource.CRYSTAL, 1)
                player = player.addResource(Resource.IRON_ORE, 1)
                newState = newState.addEvent("♨️ Geothermal vent! Bonus resources!")
            }
            is ExplorationEvent.LostMiner -> {
                newState = newState.copy(canExploreThisTurn = true)
                    .addEvent("👷 Rescued a miner! Extra exploration!")
            }
            ExplorationEvent.StableGround -> {
                // no-op
            }
        }

        return newState.updatePlayer(player)
    }
}
