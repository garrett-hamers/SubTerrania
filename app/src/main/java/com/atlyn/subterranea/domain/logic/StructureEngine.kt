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

        val adjustedCost = getAdjustedBuildCost(structureType, state.difficulty, state.selectedCharacter)
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

        if (newPlayer.structuresBuilt.size >= 5 && Achievement.MASTER_BUILDER !in newPlayer.achievements) {
            newPlayer = newPlayer.copy(achievements = newPlayer.achievements + Achievement.MASTER_BUILDER)
            newState = newState.addEvent("🏆 Achievement: Master Builder!")
        }

        val lanternCount = newPlayer.structuresBuilt.count { it.type == StructureType.LANTERN }
        if (lanternCount >= 3 && Achievement.ILLUMINATOR !in newPlayer.achievements) {
            newPlayer = newPlayer.copy(achievements = newPlayer.achievements + Achievement.ILLUMINATOR)
            newState = newState.addEvent("🏆 Achievement: The Illuminator!")
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

    fun getBuildableStructures(state: GameState): Map<StructureType, List<HexCoordinate>> {
        val player = state.currentPlayer
        val result = mutableMapOf<StructureType, MutableList<HexCoordinate>>()

        StructureType.entries.forEach { type ->
            val adjustedCost = getAdjustedBuildCost(type, state.difficulty, state.selectedCharacter)
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
                player = player.addResource(Resource.MYCELIUM, 2)
                newState = newState.addEvent("🍄 Spore Burst! +2 Mycelium")
            }
        }

        val updatedStructures = state.structures.map { existing ->
            if (existing.location == structureLocation) {
                existing.copy(abilityCooldown = ability.cooldown)
            } else existing
        }

        return newState.copy(structures = updatedStructures).updatePlayer(player)
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

        var newState = state.copy(board = newBoard, structures = updatedStructures)
            .addEvent("💡 Area illuminated! ($tilesLit ${if (tilesLit == 1) "tile" else "tiles"})")

        if (tilesLit >= GameConstants.LANTERN_BONUS_THRESHOLD) {
            newState = newState.addEvent("🏆 Well-placed Lantern! +1 VP")
        }

        return newState
    }
}
