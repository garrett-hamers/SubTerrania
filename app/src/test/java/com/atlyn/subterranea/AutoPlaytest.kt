package com.atlyn.subterranea

import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.model.*
import org.junit.Test
import kotlin.math.ln
import kotlin.math.abs

/**
 * Automated playtest: runs 100 games with human-like AI player profiles,
 * collecting deep engagement, variety, and replayability metrics.
 */
class AutoPlaytest {

    // ========================================================================
    // PLAYER PROFILES — simulate different human play styles
    // ========================================================================
    enum class PlayerProfile(
        val profileName: String,
        val exploreVsBuildBias: Double,  // 0.0 = always build first, 1.0 = always explore first
        val riskTolerance: Double,       // 0.0 = avoid core/mantle, 1.0 = rush deep zones
        val tradePropensity: Double,     // 0.0 = never trade, 1.0 = trade aggressively
        val abilityAwareness: Double,    // 0.0 = forget abilities exist, 1.0 = use them optimally
        val structureDiversity: Double,  // 0.0 = only lanterns, 1.0 = build variety
        val mistakeRate: Double,         // 0.0 = perfect play, 1.0 = random actions
        val lanternPriority: Int         // max lanterns before switching strategy
    ) {
        CAUTIOUS_NEWBIE(
            "Cautious Newbie",
            exploreVsBuildBias = 0.3,
            riskTolerance = 0.2,
            tradePropensity = 0.1,
            abilityAwareness = 0.2,
            structureDiversity = 0.3,
            mistakeRate = 0.15,
            lanternPriority = 4
        ),
        BALANCED_PLAYER(
            "Balanced Player",
            exploreVsBuildBias = 0.5,
            riskTolerance = 0.5,
            tradePropensity = 0.4,
            abilityAwareness = 0.6,
            structureDiversity = 0.6,
            mistakeRate = 0.05,
            lanternPriority = 3
        ),
        AGGRESSIVE_EXPLORER(
            "Aggressive Explorer",
            exploreVsBuildBias = 0.8,
            riskTolerance = 0.9,
            tradePropensity = 0.3,
            abilityAwareness = 0.4,
            structureDiversity = 0.4,
            mistakeRate = 0.08,
            lanternPriority = 2
        ),
        BUILDER_OPTIMIZER(
            "Builder/Optimizer",
            exploreVsBuildBias = 0.2,
            riskTolerance = 0.3,
            tradePropensity = 0.7,
            abilityAwareness = 0.9,
            structureDiversity = 0.8,
            mistakeRate = 0.03,
            lanternPriority = 3
        );
    }

    // ========================================================================
    // GAME RESULT — comprehensive per-game data
    // ========================================================================
    data class GameResult(
        val gameNumber: Int,
        val difficulty: Difficulty,
        val character: GameCharacter,
        val profile: PlayerProfile,
        val won: Boolean,
        val turns: Int,
        val totalVP: Int,
        val vpTarget: Int,
        // Structure tracking
        val structuresBuilt: Int,
        val structureTypes: Map<StructureType, Int>,
        val tilesExplored: Int,
        val achievements: Set<Achievement>,
        // Resource flow
        val resourcesProduced: Map<Resource, Int>,
        val resourcesSpent: Map<Resource, Int>,
        val tradesMade: Int,
        // Roll quality
        val deadRolls: Int,
        val productiveRolls: Int,
        val multiTileRolls: Int,      // rolls that hit 2+ tiles (exciting!)
        val totalRolls: Int,
        val consolationChoices: Map<RollConsolation, Int>,
        // Hazards & events
        val hazardsHit: Int,
        val hazardTypes: Map<String, Int>,
        val eventsTriggered: Int,
        // Per-turn detail
        val actionsPerTurn: List<Int>,
        val vpPerTurn: List<Int>,
        val resourcesPerTurn: List<Int>,   // total resources at end of each turn
        val stuckTurns: Int,
        val wastedActions: Int,            // actions that didn't advance VP
        // Engagement markers
        val turnWithFirstBuild: Int,
        val turnWithFirstLantern: Int,
        val turnWithFirstExplore: Int,
        val abilitiesUsed: Int,
        val rubbleCleared: Int,
        val maxResourcesSeen: Int,
        val ahaMoments: Int,               // turns where VP jumped by 2+
        val decisionPoints: Int,           // turns with 3+ meaningfully different actions
        val uniqueActionTypes: Set<String>,// different action categories used
        val zonesReached: Set<Zone>,       // how deep the player got
        val distinctTerrainsSeen: Set<TerrainType>,
        val closeFinish: Boolean           // won within 2 turns of limit or lost within 2 VP
    )

    // ========================================================================
    // GAME LOOP
    // ========================================================================
    private fun playGame(
        gameNumber: Int,
        difficulty: Difficulty,
        character: GameCharacter,
        profile: PlayerProfile
    ): GameResult {
        val board = BoardGenerator.generateBoard(MapPreset.STANDARD)
        val startingResources = character.applyToResources(difficulty.startingResources)
        val maxActions = character.modifyMaxActions(difficulty.maxActionsPerTurn)

        val player = Player(id = 0, name = character.displayName, resources = startingResources)
        var state = GameState(
            board = board,
            players = listOf(player),
            difficulty = difficulty,
            maxActionsPerTurn = maxActions,
            victoryPointsToWin = difficulty.victoryPointsToWin,
            showTutorial = false,
            selectedCharacter = character,
            mapPreset = MapPreset.STANDARD
        )

        // Tracking variables
        var tradesMade = 0
        var deadRolls = 0
        var productiveRolls = 0
        var multiTileRolls = 0
        var totalRolls = 0
        var hazardsHit = 0
        var eventsTriggered = 0
        var stuckTurns = 0
        var wastedActions = 0
        var turnWithFirstBuild = -1
        var turnWithFirstLantern = -1
        var turnWithFirstExplore = -1
        var abilitiesUsed = 0
        var rubbleCleared = 0
        var maxResourcesSeen = 0
        var ahaMoments = 0
        var decisionPoints = 0
        val actionsPerTurn = mutableListOf<Int>()
        val vpPerTurn = mutableListOf<Int>()
        val resourcesPerTurn = mutableListOf<Int>()
        val resourcesProduced = Resource.entries.associateWith { 0 }.toMutableMap()
        val resourcesSpent = Resource.entries.associateWith { 0 }.toMutableMap()
        val structureTypes = StructureType.entries.associateWith { 0 }.toMutableMap()
        val consolationChoices = RollConsolation.entries.associateWith { 0 }.toMutableMap()
        val hazardTypes = mutableMapOf<String, Int>()
        val uniqueActionTypes = mutableSetOf<String>()
        val zonesReached = mutableSetOf(Zone.SURFACE) // always start here
        val terrainsSeen = mutableSetOf<TerrainType>()
        val maxTurns = difficulty.maxTurns + 5

        // Track initial terrains
        state.board.values.filter { it.isRevealed }.forEach { terrainsSeen.add(it.terrain) }

        var lastVP = 0

        while (!state.gameOver && state.turnNumber <= maxTurns) {
            val turnNumber = state.turnNumber

            // Phase 1: Roll dice
            if (state.turnPhase == TurnPhase.ROLL_DICE) {
                val beforeResources = state.currentPlayer.resources.toMap()
                state = GameEngine.rollDiceAndProduce(state)
                totalRolls++

                // Handle consolation
                if (state.pendingConsolation) {
                    val choice = pickConsolation(state, profile)
                    consolationChoices[choice] = (consolationChoices[choice] ?: 0) + 1
                    state = GameEngine.resolveConsolation(state, choice)
                }

                // Classify roll quality
                val produced = state.lastProduction
                when {
                    produced.isEmpty() || (produced.size == 1 && produced.values.sum() <= 1) -> deadRolls++
                    produced.values.sum() >= 3 -> { productiveRolls++; multiTileRolls++ }
                    else -> productiveRolls++
                }

                // Track resources produced
                produced.forEach { (res, amt) ->
                    resourcesProduced[res] = (resourcesProduced[res] ?: 0) + amt
                }

                val totalRes = state.currentPlayer.resources.values.sum()
                if (totalRes > maxResourcesSeen) maxResourcesSeen = totalRes
            }

            // Count decision points (how many meaningfully different action categories are available)
            val availableActions = GameEngine.getAvailableActions(state)
            val actionCategories = availableActions.map { action ->
                when (action) {
                    is GameAction.Build -> "BUILD"
                    is GameAction.Explore -> "EXPLORE"
                    is GameAction.ClearRubble -> "CLEAR"
                    is GameAction.RollDice -> "ROLL"
                    is GameAction.EndTurn -> "END"
                    is GameAction.Trade -> "TRADE"
                }
            }.toSet()
            if (actionCategories.size >= 3) decisionPoints++

            // Phase 2: Main actions
            var actionsTaken = 0
            var vpBefore = state.totalVPFor(state.currentPlayer)
            var safety = 0
            while (state.turnPhase == TurnPhase.MAIN_ACTION &&
                state.actionsThisTurn < state.maxActionsPerTurn &&
                !state.gameOver && safety < 20
            ) {
                safety++
                val actionBefore = state.actionsThisTurn
                val resBefore = state.currentPlayer.resources.toMap()
                val vpBeforeAction = state.totalVPFor(state.currentPlayer)

                val eventCountBefore = state.eventLog.size
                state = pickAndExecuteAction(state, profile)

                val stateChanged = state.actionsThisTurn > actionBefore || state.eventLog.size > eventCountBefore
                if (stateChanged) {
                    if (state.actionsThisTurn > actionBefore) actionsTaken++

                    // Track resources spent
                    resBefore.forEach { (res, old) ->
                        val now = state.currentPlayer.getResourceCount(res)
                        if (now < old) {
                            resourcesSpent[res] = (resourcesSpent[res] ?: 0) + (old - now)
                        }
                    }

                    // Track what type of action was taken — check ALL recent events
                    // (builds can trigger secondary events like illumination)
                    val recentEvents = state.eventLog.take(5).joinToString(" ")
                    when {
                        recentEvents.contains("Built") -> {
                            uniqueActionTypes.add("BUILD")
                            if (turnWithFirstBuild == -1) turnWithFirstBuild = turnNumber
                            for (type in StructureType.entries) {
                                if (recentEvents.contains(type.displayName)) {
                                    structureTypes[type] = (structureTypes[type] ?: 0) + 1
                                    if (type == StructureType.LANTERN && turnWithFirstLantern == -1)
                                        turnWithFirstLantern = turnNumber
                                }
                            }
                        }
                        recentEvents.contains("Explored") -> {
                            uniqueActionTypes.add("EXPLORE")
                            if (turnWithFirstExplore == -1) turnWithFirstExplore = turnNumber
                            // Track zone and terrain
                            state.eventLog.firstOrNull()?.let { event ->
                                state.board.values.lastOrNull { it.isRevealed }?.let { tile ->
                                    zonesReached.add(tile.zone)
                                    terrainsSeen.add(tile.terrain)
                                }
                            }
                        }
                        recentEvents.contains("Traded") -> {
                            uniqueActionTypes.add("TRADE")
                            tradesMade++
                        }
                        recentEvents.contains("Cleared rubble") -> {
                            uniqueActionTypes.add("CLEAR")
                            rubbleCleared++
                        }
                        recentEvents.contains("Overtime") || recentEvents.contains("Flare") ||
                        recentEvents.contains("Survey") || recentEvents.contains("Spore Burst") -> {
                            uniqueActionTypes.add("ABILITY")
                            abilitiesUsed++
                        }
                    }

                    // Check for hazards
                    if (recentEvents.contains("Cave-in", ignoreCase = true)) {
                        hazardsHit++; hazardTypes["CaveIn"] = (hazardTypes["CaveIn"] ?: 0) + 1
                    }
                    if (recentEvents.contains("Gas", ignoreCase = true) && recentEvents.contains("leak", ignoreCase = true)) {
                        hazardsHit++; hazardTypes["GasLeak"] = (hazardTypes["GasLeak"] ?: 0) + 1
                    }
                    if (recentEvents.contains("Magma", ignoreCase = true) && recentEvents.contains("burst", ignoreCase = true)) {
                        hazardsHit++; hazardTypes["MagmaBurst"] = (hazardTypes["MagmaBurst"] ?: 0) + 1
                    }
                    if (recentEvents.contains("Tremor", ignoreCase = true)) {
                        hazardsHit++; hazardTypes["Tremor"] = (hazardTypes["Tremor"] ?: 0) + 1
                    }

                    // Wasted action = VP didn't change and no meaningful state change
                    val vpAfterAction = state.totalVPFor(state.currentPlayer)
                    if (vpAfterAction == vpBeforeAction && !recentEvents.contains("illuminated") &&
                        !recentEvents.contains("Explored")) {
                        wastedActions++
                    }

                    eventsTriggered++
                }

                if (state.actionsThisTurn == actionBefore && state.eventLog.size == eventCountBefore && safety > 3) break
            }

            if (actionsTaken == 0 && state.turnPhase == TurnPhase.MAIN_ACTION) stuckTurns++

            actionsPerTurn.add(actionsTaken)
            val currentVP = state.totalVPFor(state.currentPlayer)
            vpPerTurn.add(currentVP)
            resourcesPerTurn.add(state.currentPlayer.resources.values.sum())

            // Aha moment: VP jumped 2+ in a single turn
            if (currentVP - lastVP >= 2) ahaMoments++
            lastVP = currentVP

            if (!state.gameOver) state = GameEngine.endTurn(state)
        }

        val finalPlayer = state.currentPlayer
        val turnsUsed = state.turnNumber
        val closeFinish = (state.gameOver && state.winner != null && turnsUsed >= difficulty.maxTurns - 2) ||
            (state.gameOver && state.winner == null && state.totalVPFor(finalPlayer) >= state.victoryPointsToWin - 2)

        return GameResult(
            gameNumber = gameNumber,
            difficulty = difficulty,
            character = character,
            profile = profile,
            won = state.gameOver && state.winner != null,
            turns = turnsUsed,
            totalVP = state.totalVPFor(finalPlayer),
            vpTarget = state.victoryPointsToWin,
            structuresBuilt = finalPlayer.structuresBuilt.size,
            structureTypes = structureTypes.filter { it.value > 0 },
            tilesExplored = finalPlayer.explorationCount,
            achievements = finalPlayer.achievements,
            resourcesProduced = resourcesProduced,
            resourcesSpent = resourcesSpent,
            tradesMade = tradesMade,
            deadRolls = deadRolls,
            productiveRolls = productiveRolls,
            multiTileRolls = multiTileRolls,
            totalRolls = totalRolls,
            consolationChoices = consolationChoices.filter { it.value > 0 },
            hazardsHit = hazardsHit,
            hazardTypes = hazardTypes,
            eventsTriggered = eventsTriggered,
            actionsPerTurn = actionsPerTurn,
            vpPerTurn = vpPerTurn,
            resourcesPerTurn = resourcesPerTurn,
            stuckTurns = stuckTurns,
            wastedActions = wastedActions,
            turnWithFirstBuild = turnWithFirstBuild,
            turnWithFirstLantern = turnWithFirstLantern,
            turnWithFirstExplore = turnWithFirstExplore,
            abilitiesUsed = abilitiesUsed,
            rubbleCleared = rubbleCleared,
            maxResourcesSeen = maxResourcesSeen,
            ahaMoments = ahaMoments,
            decisionPoints = decisionPoints,
            uniqueActionTypes = uniqueActionTypes,
            zonesReached = zonesReached,
            distinctTerrainsSeen = terrainsSeen,
            closeFinish = closeFinish
        )
    }

    // ========================================================================
    // HUMAN-LIKE DECISION MAKING
    // ========================================================================
    private fun pickConsolation(state: GameState, profile: PlayerProfile): RollConsolation {
        val player = state.currentPlayer
        val totalRes = player.resources.values.sum()

        // Random mistake: pick randomly sometimes
        if (Math.random() < profile.mistakeRate) return RollConsolation.entries.random()

        return when {
            totalRes < 3 -> RollConsolation.GAIN_RESOURCE
            profile.tradePropensity > 0.5 && totalRes >= 4 -> RollConsolation.DISCOUNT_TRADE
            state.actionsThisTurn == 0 -> RollConsolation.BONUS_ACTION
            else -> RollConsolation.GAIN_RESOURCE
        }
    }

    private fun pickAndExecuteAction(state: GameState, profile: PlayerProfile): GameState {
        val player = state.currentPlayer
        val actions = GameEngine.getAvailableActions(state)
        if (actions.isEmpty() || actions.all { it is GameAction.EndTurn }) return state

        // Random mistake: take a random valid action
        if (Math.random() < profile.mistakeRate) {
            val nonEnd = actions.filter { it !is GameAction.EndTurn && it !is GameAction.RollDice }
            if (nonEnd.isNotEmpty()) {
                val random = nonEnd.random()
                return executeAction(state, random)
            }
        }

        val buildActions = actions.filterIsInstance<GameAction.Build>()
        val exploreActions = actions.filterIsInstance<GameAction.Explore>()
        val clearActions = actions.filterIsInstance<GameAction.ClearRubble>()

        val lanternCount = player.structuresBuilt.count { it.type == StructureType.LANTERN }
        val structureCount = player.structuresBuilt.size

        // Step 0: Use abilities proactively (they don't cost actions!)
        if (Math.random() < profile.abilityAwareness) {
            val usable = GameEngine.getUsableAbilities(state)
            if (usable.isNotEmpty()) {
                return GameEngine.useStructureAbility(state, usable.first().location)
            }
        }

        // Step 1: If we WANT to build but can't afford, try trading first
        val wantToBuild = lanternCount < profile.lanternPriority ||
            StructureType.entries.any { it.victoryPoints >= 2 }
        val canAffordAnything = buildActions.isNotEmpty()

        if (wantToBuild && !canAffordAnything && GameEngine.canTrade(state)) {
            val tradeResult = tryTrade(state, profile, lanternCount)
            if (tradeResult != null) return tradeResult
        }

        // Step 2: Explore vs Build based on profile bias
        val preferExplore = Math.random() < profile.exploreVsBuildBias

        if (preferExplore) {
            val exploreResult = tryExplore(state, exploreActions, profile)
            if (exploreResult != null) return exploreResult

            val buildResult = tryBuild(state, buildActions, profile, lanternCount, structureCount)
            if (buildResult != null) return buildResult
        } else {
            val buildResult = tryBuild(state, buildActions, profile, lanternCount, structureCount)
            if (buildResult != null) return buildResult

            val exploreResult = tryExplore(state, exploreActions, profile)
            if (exploreResult != null) return exploreResult
        }

        // Step 3: Opportunistic trading if nothing else to do
        if (Math.random() < profile.tradePropensity && GameEngine.canTrade(state)) {
            val tradeResult = tryTrade(state, profile, lanternCount)
            if (tradeResult != null) return tradeResult
        }

        // Step 4: Clear rubble
        if (clearActions.isNotEmpty()) {
            val best = clearActions.firstOrNull { state.board[it.location]?.isIlluminated == true }
                ?: clearActions.first()
            return GameEngine.clearRubble(state, best.location)
        }

        return state
    }

    private fun tryExplore(
        state: GameState,
        exploreActions: List<GameAction.Explore>,
        profile: PlayerProfile
    ): GameState? {
        if (exploreActions.isEmpty()) return null

        val best = exploreActions.maxByOrNull { action ->
            val tile = state.board[action.location]
            val zoneScore = when (tile?.zone) {
                Zone.SURFACE -> 1
                Zone.CRUST -> 3
                Zone.MANTLE -> if (Math.random() < profile.riskTolerance) 6 else 2
                Zone.CORE -> if (Math.random() < profile.riskTolerance) 7 else 1
                null -> 0
            }
            val illuminatedNeighbors = action.location.neighbors().count { n ->
                state.board[n]?.isIlluminated == true
            }
            zoneScore + illuminatedNeighbors + (Math.random() * 2).toInt() // slight randomness
        }
        return if (best != null) GameEngine.exploreTile(state, best.location) else null
    }

    private fun tryBuild(
        state: GameState,
        buildActions: List<GameAction.Build>,
        profile: PlayerProfile,
        lanternCount: Int,
        structureCount: Int
    ): GameState? {
        if (buildActions.isEmpty()) return null

        // Lantern priority
        if (lanternCount < profile.lanternPriority) {
            val lanternBuild = buildActions.filter { it.structureType == StructureType.LANTERN }
            if (lanternBuild.isNotEmpty()) {
                val best = lanternBuild.maxByOrNull { action ->
                    val neighbors = action.location.neighbors()
                    neighbors.count { n -> state.board[n]?.isRevealed == false } * 2 +
                    neighbors.count { n ->
                        val t = state.board[n]; t != null && t.isRevealed && !t.isIlluminated
                    } * 3
                }
                if (best != null) return GameEngine.buildStructure(state, best.structureType, best.location)
            }
        }

        // Diverse builder: consider variety
        val vpStructures = buildActions
            .filter { it.structureType != StructureType.LANTERN }
            .sortedByDescending { build ->
                val vpScore = build.structureType.victoryPoints * 10
                // Diversity bonus: prefer structures we haven't built yet
                val alreadyBuilt = state.currentPlayer.structuresBuilt.count { it.type == build.structureType }
                val diversityBonus = if (alreadyBuilt == 0 && Math.random() < profile.structureDiversity) 5 else 0
                // Tile quality
                val tile = state.board[build.location]
                val tileScore = tile?.numberToken?.let {
                    when (it) { 6, 8 -> 3; 5, 9 -> 2; 7 -> 2; else -> 1 }
                } ?: 0
                vpScore + diversityBonus + tileScore
            }

        if (vpStructures.isNotEmpty()) {
            val best = vpStructures.first()
            return GameEngine.buildStructure(state, best.structureType, best.location)
        }

        return null
    }

    private fun tryTrade(state: GameState, profile: PlayerProfile, lanternCount: Int): GameState? {
        val player = state.currentPlayer
        val tradable = GameEngine.getTradableResources(state)
        if (tradable.isEmpty()) return null

        // What do we need most?
        // Look at all structures we could build if we had the missing resource
        for (type in StructureType.entries.sortedByDescending { it.victoryPoints }) {
            val cost = type.cost
            val missing = cost.filter { (res, amt) -> player.getResourceCount(res) < amt }
            if (missing.size == 1) {
                val (neededRes, _) = missing.entries.first()
                val give = tradable.firstOrNull { it != neededRes }
                if (give != null) return GameEngine.tradeResources(state, give, neededRes)
            }
        }

        // Need a lantern? Trade for it
        if (lanternCount < 3 && !player.canAfford(StructureType.LANTERN.cost)) {
            val needCrystal = player.getResourceCount(Resource.CRYSTAL) < 1
            val needIron = player.getResourceCount(Resource.IRON_ORE) < 1
            val target = when {
                needCrystal -> Resource.CRYSTAL
                needIron -> Resource.IRON_ORE
                else -> null
            }
            if (target != null) {
                val give = tradable.firstOrNull { it != target }
                if (give != null) return GameEngine.tradeResources(state, give, target)
            }
        }

        return null
    }

    private fun executeAction(state: GameState, action: GameAction): GameState {
        return when (action) {
            is GameAction.Build -> GameEngine.buildStructure(state, action.structureType, action.location)
            is GameAction.Explore -> GameEngine.exploreTile(state, action.location)
            is GameAction.ClearRubble -> GameEngine.clearRubble(state, action.location)
            is GameAction.RollDice -> GameEngine.rollDiceAndProduce(state)
            is GameAction.EndTurn -> GameEngine.endTurn(state)
            is GameAction.Trade -> state // Trade requires give/receive params
        }
    }

    // ========================================================================
    // METRIC CALCULATIONS
    // ========================================================================
    private fun shannonEntropy(counts: Map<*, Int>): Double {
        val total = counts.values.sum().toDouble()
        if (total == 0.0) return 0.0
        return -counts.values.filter { it > 0 }.sumOf { count ->
            val p = count / total
            p * ln(p)
        }
    }

    private fun vpMomentum(vpPerTurn: List<Int>): Double {
        if (vpPerTurn.size < 2) return 0.0
        val deltas = vpPerTurn.zipWithNext().map { (a, b) -> b - a }
        return deltas.average()
    }

    private fun vpVariance(vpPerTurn: List<Int>): Double {
        if (vpPerTurn.size < 2) return 0.0
        val deltas = vpPerTurn.zipWithNext().map { (a, b) -> (b - a).toDouble() }
        val mean = deltas.average()
        return deltas.sumOf { (it - mean) * (it - mean) } / deltas.size
    }

    private fun comebackScore(vpPerTurn: List<Int>, vpTarget: Int): Double {
        if (vpPerTurn.size < 5) return 0.0
        val midpoint = vpPerTurn.size / 2
        val midVP = vpPerTurn[midpoint]
        val expectedMid = vpTarget * 0.5
        val finalVP = vpPerTurn.last()
        // Score how much the player recovered if they were behind at midpoint
        return if (midVP < expectedMid) {
            (finalVP - midVP).toDouble() / vpTarget
        } else 0.0
    }

    private fun snowballIndex(vpPerTurn: List<Int>): Double {
        // How much does each VP accelerate getting the next one?
        // Higher = more snowball = less interesting
        if (vpPerTurn.size < 4) return 0.0
        val firstHalf = vpPerTurn.take(vpPerTurn.size / 2)
        val secondHalf = vpPerTurn.drop(vpPerTurn.size / 2)
        val firstRate = if (firstHalf.size >= 2) (firstHalf.last() - firstHalf.first()).toDouble() / firstHalf.size else 0.0
        val secondRate = if (secondHalf.size >= 2) (secondHalf.last() - secondHalf.first()).toDouble() / secondHalf.size else 0.0
        return if (firstRate > 0) secondRate / firstRate else 1.0
    }

    // ========================================================================
    // MAIN TEST
    // ========================================================================
    @Test
    fun runHundredGames() {
        val results = mutableListOf<GameResult>()
        val difficulties = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE)
        val profiles = PlayerProfile.entries
        // 100 games: cycle through profiles evenly across difficulties
        // 25 per difficulty, ~6 per profile per difficulty
        val gamesPerDifficulty = 25

        println("=".repeat(90))
        println("SUBTERRANEA PLAYTEST — 100 GAMES × 4 PLAYER PROFILES")
        println("=".repeat(90))
        println()

        for (diff in difficulties) {
            println("--- ${diff.emoji} ${diff.displayName} (${gamesPerDifficulty} games) ---")
            for (i in 1..gamesPerDifficulty) {
                val gameNum = results.size + 1
                val profile = profiles[i % profiles.size]
                val result = playGame(gameNum, diff, GameCharacter.EXPLORER, profile)
                results.add(result)
                val status = if (result.won) "WON" else "LOST"
                print("  #$gameNum [${profile.profileName.take(8)}] $status T${result.turns} ${result.totalVP}/${result.vpTarget}VP")
                println(" S:${result.structuresBuilt} E:${result.tilesExplored} DR:${result.deadRolls}/${result.totalRolls} Tr:${result.tradesMade}")
            }
            println()
        }

        // ================================================================
        // SECTION 1: CORE BALANCE
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("1. CORE BALANCE")
        println("=".repeat(90))

        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val wins = dr.count { it.won }
            val avgT = dr.map { it.turns }.average()
            val avgVP = dr.map { it.totalVP }.average()
            val deadRate = dr.map { it.deadRolls.toDouble() / maxOf(1, it.totalRolls) }.average()
            println("  ${diff.displayName.padEnd(10)} Win:${wins}/${dr.size} (${wins*100/dr.size}%)  " +
                    "AvgTurns:${f1(avgT)}  AvgVP:${f1(avgVP)}/${diff.victoryPointsToWin}  " +
                    "DeadRoll:${f1(deadRate*100)}%")
        }

        // ================================================================
        // SECTION 2: ENGAGEMENT METRICS
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("2. ENGAGEMENT METRICS")
        println("=".repeat(90))

        println("\n  📈 VP MOMENTUM (avg VP gain per turn — should be 0.8-1.5):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val momentum = dr.map { vpMomentum(it.vpPerTurn) }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f2(momentum)} VP/turn")
        }

        println("\n  🎆 AHA MOMENTS (turns with VP jump ≥2 — excitement spikes):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgAha = dr.map { it.ahaMoments }.average()
            val ahaRate = dr.map { it.ahaMoments.toDouble() / maxOf(1, it.turns) }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgAha)} per game (${f1(ahaRate*100)}% of turns)")
        }

        println("\n  🤔 DECISION RICHNESS (turns with 3+ distinct action types available):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgDP = dr.map { it.decisionPoints }.average()
            val dpRate = dr.map { it.decisionPoints.toDouble() / maxOf(1, it.turns) }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgDP)} per game (${f1(dpRate*100)}% of turns)")
        }

        println("\n  🎲 MULTI-TILE ROLLS (dice hitting 2+ tiles — exciting production!):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgMulti = dr.map { it.multiTileRolls }.average()
            val multiRate = dr.map { it.multiTileRolls.toDouble() / maxOf(1, it.totalRolls) }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgMulti)} per game (${f1(multiRate*100)}% of rolls)")
        }

        println("\n  😤 FRUSTRATION INDEX (stuck turns + wasted actions):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgStuck = dr.map { it.stuckTurns }.average()
            val avgWasted = dr.map { it.wastedActions }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgStuck)} stuck + ${f1(avgWasted)} wasted actions")
        }

        println("\n  ⏱️ TIME TO FIRST MILESTONE (turn #):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val firstExplore = dr.filter { it.turnWithFirstExplore > 0 }.map { it.turnWithFirstExplore }.average()
            val firstBuild = dr.filter { it.turnWithFirstBuild > 0 }.map { it.turnWithFirstBuild }.average()
            val firstLantern = dr.filter { it.turnWithFirstLantern > 0 }.map { it.turnWithFirstLantern }.average()
            println("    ${diff.displayName.padEnd(10)}: Explore T${f1(firstExplore)}  Build T${f1(firstBuild)}  Lantern T${f1(firstLantern)}")
        }

        // ================================================================
        // SECTION 3: VARIETY & REPLAYABILITY
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("3. VARIETY & REPLAYABILITY")
        println("=".repeat(90))

        println("\n  🏗️ STRUCTURE DIVERSITY (Shannon entropy — higher = more variety):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgEntropy = dr.map { shannonEntropy(it.structureTypes) }.average()
            val avgTypes = dr.map { it.structureTypes.size }.average()
            println("    ${diff.displayName.padEnd(10)}: entropy ${f2(avgEntropy)} (${f1(avgTypes)} distinct types)")
        }

        println("\n  🏗️ STRUCTURE TYPE FREQUENCY (% of games each type is built):")
        for (type in StructureType.entries) {
            val count = results.count { (it.structureTypes[type] ?: 0) > 0 }
            val avgBuilt = results.map { it.structureTypes[type] ?: 0 }.average()
            println("    ${type.displayName.padEnd(20)}: ${count}% of games, avg ${f1(avgBuilt)} built")
        }

        println("\n  ⚡ ACTION TYPE VARIETY (distinct action categories used per game):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgTypes = dr.map { it.uniqueActionTypes.size }.average()
            val allTypesUsed = dr.flatMap { it.uniqueActionTypes }.toSet()
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgTypes)} types used (available: ${allTypesUsed.joinToString(",")})")
        }

        println("\n  🗺️ EXPLORATION DEPTH (zones reached per game):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgZones = dr.map { it.zonesReached.size }.average()
            val reachedCore = dr.count { Zone.CORE in it.zonesReached }
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgZones)} zones, ${reachedCore}/${dr.size} reached Core")
        }

        println("\n  🌈 TERRAIN VARIETY (distinct terrain types seen per game):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgTerrains = dr.map { it.distinctTerrainsSeen.size }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgTerrains)} / ${TerrainType.entries.size} types")
        }

        println("\n  🔄 TRADE USAGE:")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgTrades = dr.map { it.tradesMade }.average()
            val tradeGames = dr.count { it.tradesMade > 0 }
            println("    ${diff.displayName.padEnd(10)}: ${f1(avgTrades)} avg trades, ${tradeGames}/${dr.size} games used trading")
        }

        println("\n  🏆 ACHIEVEMENT DISTRIBUTION:")
        for (ach in Achievement.entries) {
            val count = results.count { ach in it.achievements }
            println("    ${ach.displayName.padEnd(20)}: ${count}%  (${ach.victoryPoints} VP)")
        }

        // ================================================================
        // SECTION 4: PACING & TENSION
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("4. PACING & TENSION")
        println("=".repeat(90))

        println("\n  🏔️ SNOWBALL INDEX (>1.5 = advantage compounds too fast, <0.5 = too flat):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgSnowball = dr.map { snowballIndex(it.vpPerTurn) }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f2(avgSnowball)}")
        }

        println("\n  📊 VP VARIANCE (turn-to-turn VP change volatility — higher = more dramatic):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgVar = dr.map { vpVariance(it.vpPerTurn) }.average()
            println("    ${diff.displayName.padEnd(10)}: ${f2(avgVar)}")
        }

        println("\n  🔄 COMEBACK POTENTIAL (recovery from behind at midpoint):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgComeback = dr.map { comebackScore(it.vpPerTurn, it.vpTarget) }.average()
            val comebackGames = dr.count { comebackScore(it.vpPerTurn, it.vpTarget) > 0.1 }
            println("    ${diff.displayName.padEnd(10)}: ${f2(avgComeback)} avg score, ${comebackGames}/${dr.size} comeback games")
        }

        println("\n  ⏰ CLOSE FINISHES (won near limit or lost close to winning):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val closeCount = dr.count { it.closeFinish }
            println("    ${diff.displayName.padEnd(10)}: ${closeCount}/${dr.size} (${closeCount*100/dr.size}%) — tension games")
        }

        println("\n  📉 RESOURCE FLOW (avg peak resources, efficiency):")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgPeak = dr.map { it.maxResourcesSeen }.average()
            val avgProduced = dr.map { it.resourcesProduced.values.sum() }.average()
            val avgSpent = dr.map { it.resourcesSpent.values.sum() }.average()
            val efficiency = if (avgProduced > 0) avgSpent / avgProduced else 0.0
            println("    ${diff.displayName.padEnd(10)}: peak ${f1(avgPeak)} res, produced ${f1(avgProduced)}, spent ${f1(avgSpent)}, efficiency ${f1(efficiency*100)}%")
        }

        // ================================================================
        // SECTION 5: PLAYER PROFILE COMPARISON
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("5. PLAYER PROFILE COMPARISON")
        println("=".repeat(90))

        for (prof in profiles) {
            val pr = results.filter { it.profile == prof }
            val wins = pr.count { it.won }
            val avgT = pr.map { it.turns }.average()
            val avgVP = pr.map { it.totalVP }.average()
            val avgStr = pr.map { it.structuresBuilt }.average()
            val avgTrades = pr.map { it.tradesMade }.average()
            val avgAbilities = pr.map { it.abilitiesUsed }.average()
            val avgExplored = pr.map { it.tilesExplored }.average()
            println("  ${prof.profileName.padEnd(22)} Win:${wins}/${pr.size}  T:${f1(avgT)}  VP:${f1(avgVP)}  " +
                    "S:${f1(avgStr)}  E:${f1(avgExplored)}  Tr:${f1(avgTrades)}  Ab:${f1(avgAbilities)}")
        }

        // ================================================================
        // SECTION 6: CONSOLATION SYSTEM
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("6. CONSOLATION & CATCH-UP MECHANICS")
        println("=".repeat(90))

        println("\n  🍀 CONSOLATION CHOICE DISTRIBUTION:")
        for (choice in RollConsolation.entries) {
            val totalPicks = results.sumOf { it.consolationChoices[choice] ?: 0 }
            println("    ${choice.displayName.padEnd(15)}: $totalPicks picks total")
        }

        println("\n  📊 HAZARD BREAKDOWN:")
        val allHazardTypes = results.flatMap { it.hazardTypes.keys }.toSet()
        for (type in allHazardTypes) {
            val total = results.sumOf { it.hazardTypes[type] ?: 0 }
            println("    ${type.padEnd(15)}: $total total across 100 games")
        }

        // ================================================================
        // SECTION 7: RETENTION RISK FLAGS
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("7. 🚩 RETENTION RISK FLAGS")
        println("=".repeat(90))

        val flags = mutableListOf<String>()

        // Game length
        val tooShort = results.count { it.won && it.turns < 5 }
        if (tooShort > 5) flags.add("⚠️ $tooShort games won in <5 turns — too shallow")

        val tooLong = results.count { it.turns > 35 }
        if (tooLong > 10) flags.add("⚠️ $tooLong games >35 turns — pacing drags")

        // Dead rolls
        val overallDeadRate = results.map { it.deadRolls.toDouble() / maxOf(1, it.totalRolls) }.average()
        if (overallDeadRate > 0.5) flags.add("⚠️ ${f0(overallDeadRate*100)}% dead rolls — unproductive feeling")
        if (overallDeadRate < 0.2) flags.add("ℹ️ ${f0(overallDeadRate*100)}% dead rolls — may be too generous")

        // Stuck turns
        val avgStuck = results.map { it.stuckTurns }.average()
        if (avgStuck > 3) flags.add("⚠️ ${f1(avgStuck)} avg stuck turns — frustrating")

        // Win rates
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val winRate = dr.count { it.won }.toDouble() / dr.size
            when (diff) {
                Difficulty.EASY -> {
                    if (winRate > 0.95) flags.add("ℹ️ Easy ${f0(winRate*100)}% win rate — maybe too easy")
                    if (winRate < 0.6) flags.add("⚠️ Easy ${f0(winRate*100)}% win rate — too hard for Easy")
                }
                Difficulty.NORMAL -> {
                    if (winRate < 0.3) flags.add("⚠️ Normal ${f0(winRate*100)}% — frustrating for average players")
                    if (winRate > 0.9) flags.add("ℹ️ Normal ${f0(winRate*100)}% — could be more challenging")
                }
                Difficulty.HARD -> {
                    if (winRate > 0.8) flags.add("ℹ️ Hard ${f0(winRate*100)}% — not hard enough")
                    if (winRate < 0.1) flags.add("⚠️ Hard ${f0(winRate*100)}% — feels impossible")
                }
                Difficulty.NIGHTMARE -> {
                    if (winRate > 0.5) flags.add("⚠️ Nightmare ${f0(winRate*100)}% — too easy for hardest")
                    if (winRate == 0.0) flags.add("ℹ️ Nightmare 0% — may need slight reprieve")
                }
            }
        }

        // Decision richness
        val avgDecisions = results.map { it.decisionPoints.toDouble() / maxOf(1, it.turns) }.average()
        if (avgDecisions < 0.3) flags.add("⚠️ Only ${f0(avgDecisions*100)}% turns have rich choices — needs more options")

        // Action variety
        val avgActionTypes = results.map { it.uniqueActionTypes.size }.average()
        if (avgActionTypes < 3) flags.add("⚠️ Only ${f1(avgActionTypes)} action types used — systems underutilized")

        // Trade usage
        val tradeGames = results.count { it.tradesMade > 0 }
        if (tradeGames < 20) flags.add("⚠️ Only $tradeGames/100 games used trading — trade system irrelevant")

        // Ability usage
        val abilityGames = results.count { it.abilitiesUsed > 0 }
        if (abilityGames < 30) flags.add("⚠️ Only $abilityGames/100 games used abilities — abilities forgotten")

        // Structure diversity
        val avgStructEntropy = results.map { shannonEntropy(it.structureTypes) }.average()
        if (avgStructEntropy < 0.5) flags.add("⚠️ Low structure entropy (${f2(avgStructEntropy)}) — players only build 1-2 types")

        // Snowball
        val avgSnowball = results.map { snowballIndex(it.vpPerTurn) }.average()
        if (avgSnowball > 2.0) flags.add("⚠️ High snowball (${f2(avgSnowball)}) — advantages compound too fast")

        // Aha moments
        val avgAha = results.map { it.ahaMoments }.average()
        if (avgAha < 1) flags.add("⚠️ Only ${f1(avgAha)} aha moments/game — needs more excitement spikes")

        // Achievements
        for (ach in Achievement.entries) {
            val rate = results.count { ach in it.achievements } / 100.0
            if (rate < 0.05) flags.add("ℹ️ ${ach.displayName} ${f0(rate*100)}% — too rare")
            if (rate > 0.9) flags.add("ℹ️ ${ach.displayName} ${f0(rate*100)}% — too common")
        }

        // Close finishes
        val closeGames = results.count { it.closeFinish }
        if (closeGames < 5) flags.add("ℹ️ Only $closeGames close finishes — games rarely feel tense")

        if (flags.isEmpty()) {
            println("  ✅ No major retention risks detected!")
        } else {
            flags.forEach { println("  $it") }
        }

        // ================================================================
        // SECTION 8: GAME LENGTH DISTRIBUTION
        // ================================================================
        println("\n${"=".repeat(90)}")
        println("8. GAME LENGTH HISTOGRAM")
        println("=".repeat(90))

        val buckets = listOf(1..4, 5..8, 9..12, 13..16, 17..20, 21..25, 26..30, 31..50)
        val bucketLabels = listOf("1-4", "5-8", "9-12", "13-16", "17-20", "21-25", "26-30", "31+")
        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            print("  ${diff.displayName.padEnd(10)}: ")
            for ((i, bucket) in buckets.withIndex()) {
                val count = dr.count { it.turns in bucket }
                print("${bucketLabels[i]}:$count  ")
            }
            println()
        }

        println("\n${"=".repeat(90)}")
        println("PLAYTEST COMPLETE — 100 GAMES × 4 PROFILES ANALYZED")
        println("=".repeat(90))
    }

    // Formatting helpers
    private fun f0(d: Double) = String.format("%.0f", d)
    private fun f1(d: Double) = String.format("%.1f", d)
    private fun f2(d: Double) = String.format("%.2f", d)
}
