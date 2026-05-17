package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*
import kotlin.math.ceil

object StructureEngine {

    fun buildStructure(state: GameState, structureType: StructureType, location: HexCoordinate): GameState {
        val tile = state.board[location] ?: return state.addEvent("❌ Invalid location")
        val player = state.currentPlayer

        if (!tile.isRevealed) {
            return state.addEvent("❌ Must reveal tile first")
        }

        if (tile.terrain == TerrainType.MAGMA_FLOW || tile.terrain == TerrainType.BEDROCK) {
            return state.addEvent("❌ Cannot build on ${tile.terrain.name}")
        }

        if (state.hasStructureAt(location) && structureType != StructureType.EXCAVATOR) {
            return state.addEvent("❌ Already has a structure")
        }

        if (structureType == StructureType.EXCAVATOR) {
            val existing = state.getStructureAt(location)
            if (existing?.type != StructureType.OUTPOST) {
                return state.addEvent("❌ Excavator requires an Outpost")
            }
        }

        if (structureType == StructureType.LANTERN && !lanternHasUtility(state, location)) {
            return state.addEvent("❌ Lantern would light no new area. Place near unlit or unrevealed tiles.")
        }

        val adjustedCost = getActualBuildCost(state, structureType)
        if (!player.canAfford(adjustedCost)) {
            return state.addEvent("❌ Cannot afford ${structureType.displayName}")
        }

        val structure = Structure(structureType, location, player.id)
        var newStructures = state.structures.toMutableList()
        if (structureType == StructureType.EXCAVATOR) {
            newStructures = newStructures.filter { it.location != location }.toMutableList()
        }
        newStructures.add(structure)

        var newPlayer = player.removeResources(adjustedCost)
            .copy(structuresBuilt = player.structuresBuilt + structure)

        var newState = state.copy(
            structures = newStructures,
            actionsThisTurn = state.actionsThisTurn + 1
        ).addEvent("🏗️ Built ${structureType.displayName} at (${location.q}, ${location.r})")

        if (newPlayer.structuresBuilt.map { it.type }.toSet().size >= 5 && Achievement.MASTER_BUILDER !in newPlayer.achievements) {
            newPlayer = newPlayer.copy(achievements = newPlayer.achievements + Achievement.MASTER_BUILDER)
            newState = newState.addEvent("🎯 Milestone: 5 distinct structures built! +2 VP")
        }

        val lanternCount = newPlayer.structuresBuilt.count { it.type == StructureType.LANTERN }
        if (lanternCount >= 3 && Achievement.ILLUMINATOR !in newPlayer.achievements) {
            newPlayer = newPlayer.copy(achievements = newPlayer.achievements + Achievement.ILLUMINATOR)
            newState = newState.addEvent("🎯 Milestone: 3 Lanterns built! +1 VP")
        }

        if (structureType == StructureType.LANTERN) {
            newState = illuminateAdjacentTiles(newState, location)
        }

        return GameEngine.checkVictory(newState.updatePlayer(newPlayer))
    }

    fun getAdjustedBuildCost(
        structureType: StructureType,
        difficulty: Difficulty,
        character: GameCharacter
    ): Map<Resource, Int> {
        val multiplier = difficulty.buildCostMultiplier
        val rareDiscount = character.structureRareDiscount()
        val rareResources = setOf(Resource.IRON_ORE, Resource.CRYSTAL)

        return structureType.cost.mapValues { (resource, baseCost) ->
            var adjusted = ceil(baseCost * multiplier).toInt()
            if (resource in rareResources) {
                adjusted = maxOf(0, adjusted - rareDiscount)
            }
            maxOf(0, adjusted)
        }.filter { it.value > 0 }
    }

    /**
     * Phase O-4: Lantern Spam Convergence v2.
     *
     * After Phase L hid 0-utility Lanterns, the K-1 240-game playtest still
     * showed Lantern as the dominant build for every character on every map
     * (Engineer averaged 4.2-4.3 Lanterns per game). To break that
     * convergence without removing the structure, the cost ramps with how
     * many Lanterns the player has already built:
     *
     *   1st Lantern: base cost (1 Crystal + 1 Iron) — unchanged.
     *   2nd Lantern: base + 1 Crystal (= 2 Crystal + 1 Iron).
     *   3rd+ Lantern: base + 1 Crystal + 1 Iron (= 2 Crystal + 2 Iron).
     *
     * Difficulty + character adjustments still stack (i.e. the rare-resource
     * discount is applied AFTER the scaling). Other structures are
     * unaffected.
     */
    fun getActualBuildCost(
        state: GameState,
        structureType: StructureType,
        playerId: Int = state.currentPlayer.id
    ): Map<Resource, Int> {
        val baseAdjusted = getAdjustedBuildCost(structureType, state.difficulty, state.selectedCharacter)
        if (structureType != StructureType.LANTERN) return baseAdjusted

        val owned = state.structures.count {
            it.type == StructureType.LANTERN && it.ownerId == playerId
        }
        // Surcharge stacks ON TOP of the difficulty/character-adjusted base.
        val surcharge = mutableMapOf<Resource, Int>()
        if (owned >= 1) surcharge[Resource.CRYSTAL] = (surcharge[Resource.CRYSTAL] ?: 0) + 1
        if (owned >= 2) surcharge[Resource.IRON_ORE] = (surcharge[Resource.IRON_ORE] ?: 0) + 1
        if (surcharge.isEmpty()) return baseAdjusted

        return (baseAdjusted.keys + surcharge.keys).associateWith { res ->
            (baseAdjusted[res] ?: 0) + (surcharge[res] ?: 0)
        }.filter { it.value > 0 }
    }

    fun getBuildableStructures(state: GameState): Map<StructureType, List<HexCoordinate>> {
        val player = state.currentPlayer
        val result = mutableMapOf<StructureType, MutableList<HexCoordinate>>()

        StructureType.entries.forEach { type ->
            val adjustedCost = getActualBuildCost(state, type)
            if (player.canAfford(adjustedCost)) {
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
                        type == StructureType.LANTERN -> lanternHasUtility(state, tile.coordinate)
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

    fun clearRubble(state: GameState, location: HexCoordinate): GameState {
        val tile = state.board[location] ?: return state
        val player = state.currentPlayer

        if (!tile.hasRubble) {
            return state.addEvent("❌ No rubble to clear")
        }

        val cost = GameConstants.RUBBLE_CLEAR_COST
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

    fun getClearableRubble(state: GameState): List<HexCoordinate> {
        if (!state.currentPlayer.canAfford(GameConstants.RUBBLE_CLEAR_COST)) return emptyList()

        return state.board.values
            .filter { it.hasRubble }
            .map { it.coordinate }
    }

    fun useStructureAbility(state: GameState, structureLocation: HexCoordinate): GameState {
        val structure = state.getStructureAt(structureLocation)
            ?: return state.addEvent("❌ No structure at this location")

        val ability = structure.type.ability
            ?: return state.addEvent("❌ This structure has no active ability")

        if (state.turnPhase != TurnPhase.MAIN_ACTION) {
            return state.addEvent("❌ Abilities only usable during the main action phase")
        }

        if (state.actionsThisTurn >= state.maxActionsPerTurn) {
            return state.addEvent("❌ No actions remaining — end turn or save the ability for later")
        }

        if (structure.abilityCooldown > 0) {
            return state.addEvent("❌ Ability on cooldown (${structure.abilityCooldown} turns)")
        }

        var newState = state
        var player = state.currentPlayer

        when (ability) {
            StructureAbility.FLARE -> {
                val adjacentUnexplored = structureLocation.neighbors()
                    .mapNotNull { state.board[it] }
                    .filter { !it.isRevealed }
                    .randomOrNull()

                if (adjacentUnexplored != null) {
                    val hint = adjacentUnexplored.zone.flareHintTerrains.random()
                    newState = newState.addEvent(
                        "🔥 Flare reveals: Likely $hint ahead at (${adjacentUnexplored.coordinate.q}, ${adjacentUnexplored.coordinate.r})"
                    )
                } else {
                    newState = newState.addEvent("🔥 Flare: No unexplored tiles nearby")
                }
            }

            StructureAbility.OVERTIME -> {
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
                val adjacentUnexplored = structureLocation.neighbors()
                    .mapNotNull { state.board[it] }
                    .filter { !it.isRevealed }
                    .take(3)

                if (adjacentUnexplored.isNotEmpty()) {
                    val hints = adjacentUnexplored.map {
                        "(${it.coordinate.q},${it.coordinate.r}): likely ${it.zone.surveyNumberPool.random()}"
                    }
                    newState = newState.addEvent("🔍 Survey reveals: ${hints.joinToString(", ")}")
                } else {
                    newState = newState.addEvent("🔍 Survey: No unexplored tiles nearby")
                }
            }

            StructureAbility.SPORE_BURST -> {
                // Phase O-4: Spore Burst yields more on Fungal Jungle so
                // building a Fungal Farm there meaningfully rewards the
                // map choice (K-1 showed map presets weren't differentiated).
                val mycBonus = if (state.mapPreset == MapPreset.FUNGAL_JUNGLE) 3 else 2
                val lichenBonus = if (state.mapPreset == MapPreset.FUNGAL_JUNGLE) 1 else 0
                player = player.addResource(Resource.MYCELIUM, mycBonus)
                if (lichenBonus > 0) player = player.addResource(Resource.LICHEN, lichenBonus)
                val msg = if (state.mapPreset == MapPreset.FUNGAL_JUNGLE)
                    "🍄 Spore Burst! +$mycBonus Mycelium, +$lichenBonus Lichen (Jungle bonus)"
                else
                    "🍄 Spore Burst! +$mycBonus Mycelium"
                newState = newState.addEvent(msg)
            }
        }

        val updatedStructures = state.structures.map { existing ->
            if (existing.location == structureLocation) {
                existing.copy(abilityCooldown = ability.cooldown)
            } else existing
        }

        return newState.copy(
            structures = updatedStructures,
            actionsThisTurn = newState.actionsThisTurn + 1
        ).updatePlayer(player)
    }

    fun tickStructureCooldowns(state: GameState): GameState {
        val updatedStructures = state.structures.map { structure ->
            if (structure.abilityCooldown > 0) {
                structure.copy(abilityCooldown = structure.abilityCooldown - 1)
            } else structure
        }
        return state.copy(structures = updatedStructures)
    }

    fun getUsableAbilities(state: GameState): List<Structure> {
        return state.structures.filter { structure ->
            structure.type.ability != null && structure.abilityCooldown == 0
        }
    }

    private fun illuminateAdjacentTiles(state: GameState, center: HexCoordinate): GameState {
        val newBoard = state.board.toMutableMap()
        val tilesToLight = listOf(center) + center.neighbors()
        var tilesLit = 0

        tilesToLight.forEach { coord ->
            val tile = newBoard[coord]
            if (tile != null && tile.isRevealed && !tile.isIlluminated) {
                newBoard[coord] = tile.copy(isIlluminated = true)
                tilesLit++
            }
        }

        val updatedStructures = state.structures.map { structure ->
            if (structure.location == center && structure.type == StructureType.LANTERN) {
                structure.copy(tilesIlluminated = structure.tilesIlluminated + tilesLit)
            } else structure
        }

        val message = when {
            tilesLit > 0 -> "💡 Area illuminated! ($tilesLit ${if (tilesLit == 1) "tile" else "tiles"})"
            else -> "💡 Lantern placed near unexplored area — future tiles will auto-light."
        }

        var newState = state.copy(board = newBoard, structures = updatedStructures)
            .addEvent(message)

        if (tilesLit >= GameConstants.LANTERN_BONUS_THRESHOLD) {
            newState = newState.addEvent("🏆 Well-placed Lantern! +1 VP")
        }

        return newState
    }

    /**
     * A Lantern Post has utility iff at least one of (self, neighbours) is either
     * unrevealed (so it will auto-illuminate when later explored — see
     * ExplorationEngine.checkIlluminationFromLanterns) OR revealed-but-unilluminated
     * (so it will be lit immediately by illuminateAdjacentTiles).
     */
    fun lanternHasUtility(state: GameState, location: HexCoordinate): Boolean {
        val candidates = listOf(location) + location.neighbors()
        return candidates.any { coord ->
            val tile = state.board[coord]
            tile != null && (!tile.isRevealed || !tile.isIlluminated)
        }
    }
}
