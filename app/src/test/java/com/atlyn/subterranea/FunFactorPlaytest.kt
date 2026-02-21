package com.atlyn.subterranea

import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.model.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fun-factor focused playtest: runs simulated games with metrics specifically
 * designed to measure player enjoyment signals — frustration, agency, pacing,
 * reward frequency, and engagement curves.
 */
class FunFactorPlaytest {

    // ========================================================================
    // PLAYER PROFILES — includes new fun-analysis profiles
    // ========================================================================
    enum class FunProfile(
        val profileName: String,
        val exploreVsBuildBias: Double,
        val riskTolerance: Double,
        val tradePropensity: Double,
        val abilityAwareness: Double,
        val structureDiversity: Double,
        val mistakeRate: Double,
        val lanternPriority: Int,
        val patienceThreshold: Int  // end turn early if resources below this
    ) {
        CAUTIOUS_NEWBIE(
            "Cautious Newbie", 0.3, 0.2, 0.3, 0.3, 0.3, 0.15, 4, patienceThreshold = 0
        ),
        BALANCED_PLAYER(
            "Balanced Player", 0.5, 0.5, 0.5, 0.7, 0.6, 0.05, 3, patienceThreshold = 0
        ),
        AGGRESSIVE_EXPLORER(
            "Aggressive Explorer", 0.8, 0.9, 0.4, 0.5, 0.4, 0.08, 2, patienceThreshold = 0
        ),
        BUILDER_OPTIMIZER(
            "Builder/Optimizer", 0.2, 0.3, 0.8, 0.9, 0.8, 0.03, 3, patienceThreshold = 0
        ),
        FRUSTRATED_QUITTER(
            "Frustrated Quitter", 0.5, 0.4, 0.3, 0.2, 0.3, 0.1, 3,
            patienceThreshold = 2  // gives up actions when resources < 2
        ),
        OPTIMAL_PLAYER(
            "Optimal Player", 0.4, 0.6, 0.7, 1.0, 0.7, 0.0, 3, patienceThreshold = 0
        );
    }

    // ========================================================================
    // FUN METRICS DATA CLASS
    // ========================================================================
    data class FunMetrics(
        val gameNumber: Int,
        val difficulty: Difficulty,
        val profile: FunProfile,
        val won: Boolean,
        val turns: Int,
        val finalVP: Int,
        val vpTarget: Int,
        val endReason: String,

        // Frustration signals
        val buildAttempts: Int,
        val buildSuccesses: Int,
        val buildFrustrationIndex: Double,      // attempts / max(1, successes)
        val maxDeadRollStreak: Int,              // longest run of 0-resource rolls
        val totalDeadRolls: Int,
        val stuckTurnStreak: Int,                // longest run of 0-VP-gain turns
        val totalStuckTurns: Int,
        val turnLimitLoss: Boolean,
        val exploreCapHits: Int,                 // wanted to explore but couldn't
        val cannotAffordEventChoices: Int,        // interactive events player couldn't engage with

        // Agency signals
        val agencyScore: Double,                 // % of turns with 2+ viable action categories
        val turnsWithChoice: Int,                // turns where multiple action types succeeded
        val turnsTotal: Int,

        // Pacing signals
        val vpPerTurnVariance: Double,           // low = boring steady, very high = feast/famine
        val earlyGameEngagement: Double,         // avg meaningful actions turns 1-3
        val lateGameVP: Int,                     // VP gained in final 3 turns
        val lateGameDesperation: Boolean,        // final 3 turns had 0 VP gain
        val firstVPTurn: Int,
        val firstBuildTurn: Int,

        // Reward signals
        val ahaMoments: Int,                     // turns with VP jump >= 2
        val explorationRewardRatio: Double,      // positive exploration events / total explorations
        val interactiveEventParticipation: Double, // events player could afford / total events
        val comebackPotential: Double,           // recovered from behind at midpoint
        val anticlimaxOvershoot: Int,            // VP over target when won (0 = perfect tension)

        // Composite scores (0-100)
        val frustrationScore: Double,
        val engagementScore: Double,
        val pacingScore: Double,
        val overallFunScore: Double
    )

    // ========================================================================
    // GAME LOOP — plays one game tracking fun metrics
    // ========================================================================
    private fun playGameForFun(
        gameNumber: Int,
        difficulty: Difficulty,
        profile: FunProfile
    ): FunMetrics {
        val board = BoardGenerator.generateBoard(MapPreset.STANDARD)
        val character = GameCharacter.EXPLORER
        val startingResources = character.applyToResources(difficulty.startingResources)
        val maxActions = character.modifyMaxActions(difficulty.maxActionsPerTurn)

        val player = Player(id = 0, name = character.displayName, resources = startingResources)
        var state = GameState(
            board = board,
            players = listOf(player),
            difficulty = difficulty,
            maxActionsPerTurn = maxActions,
            victoryPointsToWin = difficulty.victoryPointsToWin,
            selectedCharacter = character,
            mapPreset = MapPreset.STANDARD
        )

        // Tracking
        var buildAttempts = 0
        var buildSuccesses = 0
        var deadRollStreak = 0
        var maxDeadRollStreak = 0
        var totalDeadRolls = 0
        var stuckTurnStreak = 0
        var maxStuckTurnStreak = 0
        var totalStuckTurns = 0
        var exploreCapHits = 0
        var cannotAffordEvents = 0
        var turnsWithChoice = 0
        var ahaMoments = 0
        var totalExplorations = 0
        var positiveExplorations = 0
        var interactiveEventsTotal = 0
        var interactiveEventsAfforded = 0
        var firstVPTurn = -1
        var firstBuildTurn = -1
        val vpPerTurn = mutableListOf<Int>()
        val actionsPerTurn = mutableListOf<Int>()
        var lastVP = 0
        val maxTurns = difficulty.maxTurns + 5

        while (!state.gameOver && state.turnNumber <= maxTurns) {
            val turnNumber = state.turnNumber

            // Phase 1: Roll dice
            if (state.turnPhase == TurnPhase.ROLL_DICE) {
                val resBefore = state.currentPlayer.resources.values.sum()
                state = GameEngine.rollDiceAndProduce(state)

                // Handle consolation
                if (state.pendingConsolation) {
                    val choice = pickConsolation(state, profile)
                    state = GameEngine.resolveConsolation(state, choice)
                }

                val resAfter = state.currentPlayer.resources.values.sum()
                val produced = resAfter - resBefore
                if (produced <= 0) {
                    totalDeadRolls++
                    deadRollStreak++
                    maxDeadRollStreak = maxOf(maxDeadRollStreak, deadRollStreak)
                } else {
                    deadRollStreak = 0
                }
            }

            // Count available action categories for agency
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
            }.toSet() - setOf("END", "ROLL")
            if (actionCategories.size >= 2) turnsWithChoice++

            // Phase 2: Main actions
            var actionsTaken = 0
            val vpBefore = state.totalVPFor(state.currentPlayer)
            var safety = 0

            // Frustrated quitter: skip actions if resources too low
            val shouldQuit = profile.patienceThreshold > 0 &&
                state.currentPlayer.resources.values.sum() < profile.patienceThreshold &&
                state.actionsThisTurn > 0

            while (state.turnPhase == TurnPhase.MAIN_ACTION &&
                state.actionsThisTurn < state.maxActionsPerTurn &&
                !state.gameOver && safety < 20 && !shouldQuit
            ) {
                safety++
                val actionBefore = state.actionsThisTurn
                val eventCountBefore = state.eventLog.size
                val recentEvents = state.eventLog.take(3).joinToString(" ")

                // Track build attempts
                val buildActions = GameEngine.getAvailableActions(state).filterIsInstance<GameAction.Build>()
                val exploreActions = GameEngine.getAvailableActions(state).filterIsInstance<GameAction.Explore>()
                val wantsToBuild = Math.random() > profile.exploreVsBuildBias
                if (wantsToBuild && buildActions.isEmpty() && exploreActions.isNotEmpty()) {
                    buildAttempts++  // wanted to build but couldn't
                }

                state = pickAndExecuteAction(state, profile)

                // Handle interactive events
                if (state.pendingInteractiveEvent != null && state.pendingEventCoord != null) {
                    interactiveEventsTotal++
                    val event = state.pendingInteractiveEvent!!
                    val coord = state.pendingEventCoord!!

                    // Check if player can afford to participate meaningfully
                    val canAfford = when (event) {
                        is InteractiveEvent.UnstableGround ->
                            state.currentPlayer.getResourceCount(Resource.BASALT) >= 2
                        is InteractiveEvent.LostMinerEncounter ->
                            state.currentPlayer.getResourceCount(Resource.LICHEN) >= 2
                        else -> true
                    }
                    if (canAfford) interactiveEventsAfforded++ else cannotAffordEvents++

                    val choiceId = pickInteractiveEventChoice(event, profile)
                    state = GameEngine.resolveInteractiveEvent(state, event, choiceId, coord)
                }

                val stateChanged = state.actionsThisTurn > actionBefore || state.eventLog.size > eventCountBefore
                if (stateChanged) {
                    if (state.actionsThisTurn > actionBefore) actionsTaken++

                    val newEvents = state.eventLog.take(5).joinToString(" ")
                    if (newEvents.contains("Built")) {
                        buildSuccesses++
                        if (firstBuildTurn == -1) firstBuildTurn = turnNumber
                    }
                    if (newEvents.contains("Explored")) {
                        totalExplorations++
                        // Check if exploration was positive
                        if (newEvents.contains("Crystal") || newEvents.contains("Treasure") ||
                            newEvents.contains("Artifact") || newEvents.contains("Bloom") ||
                            newEvents.contains("Beetle Nest") || newEvents.contains("Geothermal") ||
                            newEvents.contains("found")
                        ) {
                            positiveExplorations++
                        }
                    }
                    if (newEvents.contains("explore_cap", ignoreCase = true) ||
                        (exploreActions.isEmpty() && Math.random() < profile.exploreVsBuildBias)
                    ) {
                        exploreCapHits++
                    }
                }

                if (state.actionsThisTurn == actionBefore && state.eventLog.size == eventCountBefore && safety > 3) break
            }

            val currentVP = state.totalVPFor(state.currentPlayer)
            vpPerTurn.add(currentVP)
            actionsPerTurn.add(actionsTaken)

            // Track VP milestones
            if (currentVP > lastVP) {
                if (firstVPTurn == -1) firstVPTurn = turnNumber
                if (currentVP - lastVP >= 2) ahaMoments++
                stuckTurnStreak = 0
            } else {
                stuckTurnStreak++
                totalStuckTurns++
                maxStuckTurnStreak = maxOf(maxStuckTurnStreak, stuckTurnStreak)
            }
            lastVP = currentVP

            if (!state.gameOver) state = GameEngine.endTurn(state)
        }

        val turnsPlayed = state.turnNumber
        val won = state.gameOver && state.winner != null
        val finalVP = state.totalVPFor(state.currentPlayer)
        val vpTarget = state.victoryPointsToWin
        val endReason = when {
            won -> "victory"
            turnsPlayed >= maxTurns -> "turn_limit"
            else -> "unknown"
        }

        // Compute composite metrics
        val buildFrustrationIndex = if (buildSuccesses > 0)
            buildAttempts.toDouble() / buildSuccesses else buildAttempts.toDouble()

        val agencyScore = if (turnsPlayed > 0)
            turnsWithChoice.toDouble() / turnsPlayed * 100 else 0.0

        val vpDeltas = if (vpPerTurn.size >= 2)
            vpPerTurn.zipWithNext().map { (a, b) -> (b - a).toDouble() }
        else listOf(0.0)
        val vpMean = vpDeltas.average()
        val vpVariance = if (vpDeltas.size > 1)
            vpDeltas.sumOf { (it - vpMean) * (it - vpMean) } / vpDeltas.size
        else 0.0

        val earlyTurns = actionsPerTurn.take(3)
        val earlyEngagement = if (earlyTurns.isNotEmpty()) earlyTurns.average() else 0.0

        val lateTurns = vpPerTurn.takeLast(3)
        val lateVP = if (lateTurns.size >= 2) lateTurns.last() - lateTurns.first() else 0
        val lateDesperation = lateVP <= 0 && !won

        val explorationRewardRatio = if (totalExplorations > 0)
            positiveExplorations.toDouble() / totalExplorations else 0.0

        val interactiveParticipation = if (interactiveEventsTotal > 0)
            interactiveEventsAfforded.toDouble() / interactiveEventsTotal else 1.0

        // Comeback: if behind at midpoint, how much did we recover?
        val comebackPotential = if (vpPerTurn.size >= 6) {
            val mid = vpPerTurn.size / 2
            val midVP = vpPerTurn[mid]
            val expectedMid = vpTarget * 0.5
            if (midVP < expectedMid) (finalVP - midVP).toDouble() / vpTarget else 0.0
        } else 0.0

        val overshoot = if (won) finalVP - vpTarget else 0

        // === COMPOSITE SCORES (0-100, higher = better except frustration) ===

        // Frustration (0-100, lower = better)
        val deadRollPenalty = (totalDeadRolls.toDouble() / maxOf(1, turnsPlayed)) * 30
        val buildFrustPenalty = buildFrustrationIndex * 10
        val stuckPenalty = maxStuckTurnStreak * 8.0
        val turnLimitPenalty = if (endReason == "turn_limit") 20.0 else 0.0
        val frustrationScore = (deadRollPenalty + buildFrustPenalty + stuckPenalty + turnLimitPenalty)
            .coerceIn(0.0, 100.0)

        // Engagement (0-100, higher = better)
        val agencyComponent = agencyScore * 0.3
        val ahaComponent = ahaMoments * 15.0
        val rewardComponent = explorationRewardRatio * 20
        val eventComponent = interactiveParticipation * 10
        val engagementScore = (agencyComponent + ahaComponent + rewardComponent + eventComponent)
            .coerceIn(0.0, 100.0)

        // Pacing (0-100, higher = better)
        val earlyBonus = if (firstVPTurn in 1..3) 25.0 else if (firstVPTurn in 4..5) 15.0 else 5.0
        val varianceBonus = if (vpVariance in 0.3..1.5) 25.0 else if (vpVariance < 0.3) 10.0 else 15.0
        val lateBonus = if (lateDesperation) 0.0 else 25.0
        val engagementBonus = earlyEngagement.coerceAtMost(5.0) * 5
        val pacingScore = (earlyBonus + varianceBonus + lateBonus + engagementBonus)
            .coerceIn(0.0, 100.0)

        // Overall fun = high engagement + high pacing - frustration
        val overallFunScore = ((engagementScore + pacingScore - frustrationScore * 0.7) / 2.0 + 30)
            .coerceIn(0.0, 100.0)

        return FunMetrics(
            gameNumber = gameNumber,
            difficulty = difficulty,
            profile = profile,
            won = won,
            turns = turnsPlayed,
            finalVP = finalVP,
            vpTarget = vpTarget,
            endReason = endReason,
            buildAttempts = buildAttempts,
            buildSuccesses = buildSuccesses,
            buildFrustrationIndex = round2(buildFrustrationIndex),
            maxDeadRollStreak = maxDeadRollStreak,
            totalDeadRolls = totalDeadRolls,
            stuckTurnStreak = maxStuckTurnStreak,
            totalStuckTurns = totalStuckTurns,
            turnLimitLoss = endReason == "turn_limit",
            exploreCapHits = exploreCapHits,
            cannotAffordEventChoices = cannotAffordEvents,
            agencyScore = round2(agencyScore),
            turnsWithChoice = turnsWithChoice,
            turnsTotal = turnsPlayed,
            vpPerTurnVariance = round2(vpVariance),
            earlyGameEngagement = round2(earlyEngagement),
            lateGameVP = lateVP,
            lateGameDesperation = lateDesperation,
            firstVPTurn = firstVPTurn,
            firstBuildTurn = firstBuildTurn,
            ahaMoments = ahaMoments,
            explorationRewardRatio = round2(explorationRewardRatio),
            interactiveEventParticipation = round2(interactiveParticipation),
            comebackPotential = round2(comebackPotential),
            anticlimaxOvershoot = overshoot,
            frustrationScore = round2(frustrationScore),
            engagementScore = round2(engagementScore),
            pacingScore = round2(pacingScore),
            overallFunScore = round2(overallFunScore)
        )
    }

    // ========================================================================
    // DECISION MAKING (reuses AutoPlaytest patterns)
    // ========================================================================
    private fun pickConsolation(state: GameState, profile: FunProfile): RollConsolation {
        val player = state.currentPlayer
        val totalRes = player.resources.values.sum()
        if (Math.random() < profile.mistakeRate) return RollConsolation.entries.random()
        return when {
            totalRes < 3 -> RollConsolation.GAIN_RESOURCE
            profile.tradePropensity > 0.5 && totalRes >= 4 -> RollConsolation.DISCOUNT_TRADE
            state.actionsThisTurn == 0 -> RollConsolation.BONUS_ACTION
            else -> RollConsolation.GAIN_RESOURCE
        }
    }

    private fun pickInteractiveEventChoice(event: InteractiveEvent, profile: FunProfile): InteractiveChoiceId {
        return when (event) {
            is InteractiveEvent.BeetleSwarm -> when {
                profile.riskTolerance > 0.6 -> InteractiveChoiceId.FIGHT
                profile.riskTolerance > 0.3 -> InteractiveChoiceId.SNEAK
                else -> InteractiveChoiceId.RETREAT
            }
            is InteractiveEvent.UnstableGround -> when {
                profile.riskTolerance > 0.7 -> InteractiveChoiceId.RUSH
                profile.riskTolerance > 0.3 -> InteractiveChoiceId.CAREFUL
                else -> InteractiveChoiceId.REINFORCE
            }
            is InteractiveEvent.AncientCache -> when {
                profile.riskTolerance > 0.5 -> InteractiveChoiceId.OPEN
                profile.riskTolerance > 0.2 -> InteractiveChoiceId.STUDY
                else -> InteractiveChoiceId.LEAVE
            }
            is InteractiveEvent.LostMinerEncounter -> when {
                profile.exploreVsBuildBias > 0.5 -> InteractiveChoiceId.RESCUE
                profile.tradePropensity > 0.5 -> InteractiveChoiceId.TRADE
                else -> InteractiveChoiceId.DIRECTIONS
            }
        }
    }

    private fun pickAndExecuteAction(state: GameState, profile: FunProfile): GameState {
        val player = state.currentPlayer
        val actions = GameEngine.getAvailableActions(state)
        if (actions.isEmpty() || actions.all { it is GameAction.EndTurn }) return state

        if (Math.random() < profile.mistakeRate) {
            val nonEnd = actions.filter { it !is GameAction.EndTurn && it !is GameAction.RollDice }
            if (nonEnd.isNotEmpty()) return executeAction(state, nonEnd.random())
        }

        // Use abilities first
        if (Math.random() < profile.abilityAwareness) {
            val usable = GameEngine.getUsableAbilities(state)
            if (usable.isNotEmpty()) return GameEngine.useStructureAbility(state, usable.first().location)
        }

        val buildActions = actions.filterIsInstance<GameAction.Build>()
        val exploreActions = actions.filterIsInstance<GameAction.Explore>()
        val clearActions = actions.filterIsInstance<GameAction.ClearRubble>()
        val lanternCount = player.structuresBuilt.count { it.type == StructureType.LANTERN }

        // Trade proactively
        if (GameEngine.canTrade(state) && Math.random() < profile.tradePropensity) {
            val tradeResult = tryTrade(state, profile, lanternCount)
            if (tradeResult != null) return tradeResult
        }

        // Trade if can't afford anything
        if (buildActions.isEmpty() && GameEngine.canTrade(state)) {
            val tradeResult = tryTrade(state, profile, lanternCount)
            if (tradeResult != null) return tradeResult
        }

        val preferExplore = Math.random() < profile.exploreVsBuildBias
        if (preferExplore) {
            tryExplore(state, exploreActions, profile)?.let { return it }
            tryBuild(state, buildActions, profile, lanternCount)?.let { return it }
        } else {
            tryBuild(state, buildActions, profile, lanternCount)?.let { return it }
            tryExplore(state, exploreActions, profile)?.let { return it }
        }

        // Opportunistic trade
        if (Math.random() < profile.tradePropensity && GameEngine.canTrade(state)) {
            tryTrade(state, profile, lanternCount)?.let { return it }
        }

        if (clearActions.isNotEmpty()) {
            val best = clearActions.firstOrNull { state.board[it.location]?.isIlluminated == true }
                ?: clearActions.first()
            return GameEngine.clearRubble(state, best.location)
        }

        return state
    }

    private fun tryExplore(state: GameState, exploreActions: List<GameAction.Explore>, profile: FunProfile): GameState? {
        if (exploreActions.isEmpty()) return null
        val takesRisk = Math.random() < profile.riskTolerance
        val best = exploreActions.maxByOrNull { action ->
            val tile = state.board[action.location]
            val zoneScore = when (tile?.zone) {
                Zone.SURFACE -> 1; Zone.CRUST -> 3
                Zone.MANTLE -> if (takesRisk) 6 else 2
                Zone.CORE -> if (takesRisk) 7 else 1; null -> 0
            }
            val illuminatedNeighbors = action.location.neighbors().count { n ->
                state.board[n]?.isIlluminated == true
            }
            zoneScore + illuminatedNeighbors
        }
        return if (best != null) GameEngine.exploreTile(state, best.location) else null
    }

    private fun tryBuild(state: GameState, buildActions: List<GameAction.Build>, profile: FunProfile, lanternCount: Int): GameState? {
        if (buildActions.isEmpty()) return null
        if (lanternCount < profile.lanternPriority) {
            val lanternBuild = buildActions.filter { it.structureType == StructureType.LANTERN }
            if (lanternBuild.isNotEmpty()) {
                val best = lanternBuild.maxByOrNull { action ->
                    action.location.neighbors().count { n -> state.board[n]?.isRevealed == false } * 2 +
                    action.location.neighbors().count { n ->
                        val t = state.board[n]; t != null && t.isRevealed && !t.isIlluminated
                    } * 3
                }
                if (best != null) return GameEngine.buildStructure(state, best.structureType, best.location)
            }
        }
        val vpStructures = buildActions
            .filter { it.structureType != StructureType.LANTERN }
            .sortedByDescending { it.structureType.victoryPoints * 10 }
        if (vpStructures.isNotEmpty()) {
            return GameEngine.buildStructure(state, vpStructures.first().structureType, vpStructures.first().location)
        }
        return null
    }

    private fun tryTrade(state: GameState, profile: FunProfile, lanternCount: Int): GameState? {
        val player = state.currentPlayer
        val tradable = GameEngine.getTradableResources(state)
        if (tradable.isEmpty()) return null

        for (type in StructureType.entries.sortedByDescending { it.victoryPoints }) {
            val cost = type.cost
            val missing = cost.filter { (res, amt) -> player.getResourceCount(res) < amt }
            if (missing.size == 1) {
                val (neededRes, _) = missing.entries.first()
                val give = tradable.firstOrNull { it != neededRes }
                if (give != null) return GameEngine.tradeResources(state, give, neededRes)
            }
        }

        val crystalCount = player.getResourceCount(Resource.CRYSTAL)
        if (crystalCount == 0 && tradable.isNotEmpty()) {
            val give = tradable.firstOrNull { it != Resource.CRYSTAL }
            if (give != null) return GameEngine.tradeResources(state, give, Resource.CRYSTAL)
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
            is GameAction.Trade -> state
        }
    }

    // ========================================================================
    // MAIN TEST
    // ========================================================================
    @Test
    fun runFunFactorAnalysis() {
        val results = mutableListOf<FunMetrics>()
        val difficulties = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE)
        val profiles = FunProfile.entries
        val gamesPerCombo = 5  // 5 games × 4 difficulties × 6 profiles = 120 games

        println("=".repeat(80))
        println("  SUBTERRANEA FUN-FACTOR PLAYTEST")
        println("  ${difficulties.size} difficulties × ${profiles.size} profiles × $gamesPerCombo games = ${difficulties.size * profiles.size * gamesPerCombo} games")
        println("=".repeat(80))
        println()

        for (diff in difficulties) {
            for (prof in profiles) {
                for (i in 1..gamesPerCombo) {
                    val result = playGameForFun(results.size + 1, diff, prof)
                    results.add(result)
                }
            }
        }

        // ================================================================
        // REPORT: PER-DIFFICULTY FUN SCORES
        // ================================================================
        println("\n${"=".repeat(80)}")
        println("  FUN SCORES BY DIFFICULTY")
        println("=".repeat(80))
        println()
        println("  ${"Difficulty".padEnd(12)} ${"Frustration".padEnd(14)} ${"Engagement".padEnd(13)} ${"Pacing".padEnd(10)} ${"Overall Fun".padEnd(13)} Win%")
        println("  ${"-".repeat(70)}")

        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgFrust = dr.map { it.frustrationScore }.average()
            val avgEng = dr.map { it.engagementScore }.average()
            val avgPace = dr.map { it.pacingScore }.average()
            val avgFun = dr.map { it.overallFunScore }.average()
            val winRate = dr.count { it.won } * 100.0 / dr.size
            val frustFlag = if (avgFrust > 50) " ⚠" else ""
            val engFlag = if (avgEng < 40) " ⚠" else ""
            println("  ${diff.displayName.padEnd(12)} ${f1(avgFrust).padEnd(14)}$frustFlag ${f1(avgEng).padEnd(13)}$engFlag ${f1(avgPace).padEnd(10)} ${f1(avgFun).padEnd(13)} ${f0(winRate)}%")
        }

        // ================================================================
        // REPORT: PER-PROFILE FUN SCORES
        // ================================================================
        println("\n${"=".repeat(80)}")
        println("  FUN SCORES BY PLAYER PROFILE")
        println("=".repeat(80))
        println()
        println("  ${"Profile".padEnd(22)} ${"Frustration".padEnd(14)} ${"Engagement".padEnd(13)} ${"Pacing".padEnd(10)} ${"Overall".padEnd(10)}")
        println("  ${"-".repeat(70)}")

        for (prof in profiles) {
            val pr = results.filter { it.profile == prof }
            val avgFrust = pr.map { it.frustrationScore }.average()
            val avgEng = pr.map { it.engagementScore }.average()
            val avgPace = pr.map { it.pacingScore }.average()
            val avgFun = pr.map { it.overallFunScore }.average()
            println("  ${prof.profileName.padEnd(22)} ${f1(avgFrust).padEnd(14)} ${f1(avgEng).padEnd(13)} ${f1(avgPace).padEnd(10)} ${f1(avgFun)}")
        }

        // ================================================================
        // REPORT: DETAILED FRUSTRATION BREAKDOWN
        // ================================================================
        println("\n${"=".repeat(80)}")
        println("  FRUSTRATION BREAKDOWN BY DIFFICULTY")
        println("=".repeat(80))

        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            println("\n  --- ${diff.displayName.uppercase()} ---")
            println("    Dead-roll rate:          ${f1(dr.map { it.totalDeadRolls.toDouble() / maxOf(1, it.turns) }.average() * 100)}%")
            println("    Max dead-roll streak:    ${dr.map { it.maxDeadRollStreak }.max()}")
            println("    Avg stuck-turn streak:   ${f1(dr.map { it.stuckTurnStreak }.average())}")
            println("    Build frustration index: ${f2(dr.map { it.buildFrustrationIndex }.average())}")
            println("    Turn-limit losses:       ${dr.count { it.turnLimitLoss }}/${dr.size}")
            println("    Explore cap hits/game:   ${f1(dr.map { it.exploreCapHits }.average())}")
            println("    Can't-afford events:     ${f1(dr.map { it.cannotAffordEventChoices }.average())}")
        }

        // ================================================================
        // REPORT: PACING DETAILS
        // ================================================================
        println("\n${"=".repeat(80)}")
        println("  PACING DETAILS BY DIFFICULTY")
        println("=".repeat(80))

        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            println("\n  --- ${diff.displayName.uppercase()} ---")
            println("    First VP on turn:        ${f1(dr.filter { it.firstVPTurn > 0 }.map { it.firstVPTurn }.average())}")
            println("    First build on turn:     ${f1(dr.filter { it.firstBuildTurn > 0 }.map { it.firstBuildTurn }.average())}")
            println("    VP variance:             ${f2(dr.map { it.vpPerTurnVariance }.average())}")
            println("    Early engagement:        ${f1(dr.map { it.earlyGameEngagement }.average())} actions/turn (T1-3)")
            println("    Late-game desperation:   ${dr.count { it.lateGameDesperation }}/${dr.size} games")
            println("    Comeback potential:      ${f2(dr.map { it.comebackPotential }.average())}")
            println("    Anticlimax overshoot:    ${f1(dr.filter { it.won }.map { it.anticlimaxOvershoot }.average())} VP over target")
        }

        // ================================================================
        // REPORT: REWARD SIGNALS
        // ================================================================
        println("\n${"=".repeat(80)}")
        println("  REWARD SIGNALS BY DIFFICULTY")
        println("=".repeat(80))

        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            println("\n  --- ${diff.displayName.uppercase()} ---")
            println("    Aha moments/game:        ${f1(dr.map { it.ahaMoments }.average())}")
            println("    Exploration reward ratio: ${f1(dr.map { it.explorationRewardRatio }.average() * 100)}%")
            println("    Event participation:     ${f1(dr.map { it.interactiveEventParticipation }.average() * 100)}%")
            println("    Agency score:            ${f1(dr.map { it.agencyScore }.average())}%")
        }

        // ================================================================
        // REPORT: GUARDRAIL CHECKS
        // ================================================================
        println("\n${"=".repeat(80)}")
        println("  FUN-FACTOR GUARDRAIL CHECKS")
        println("=".repeat(80))
        println()

        val guardrailResults = mutableListOf<Triple<String, String, Boolean>>()

        for (diff in difficulties) {
            val dr = results.filter { it.difficulty == diff }
            val avgFrust = dr.map { it.frustrationScore }.average()
            val avgEng = dr.map { it.engagementScore }.average()
            val avgPace = dr.map { it.pacingScore }.average()
            val maxDeadStreak = dr.map { it.maxDeadRollStreak }.max()
            val turnLimitRate = dr.count { it.turnLimitLoss }.toDouble() / dr.size
            val earlyEng = dr.map { it.earlyGameEngagement }.average()

            val frustThreshold = when (diff) {
                Difficulty.EASY, Difficulty.NORMAL -> 40.0
                Difficulty.HARD -> 60.0
                Difficulty.NIGHTMARE -> 75.0
            }
            val turnLimitThreshold = when (diff) {
                Difficulty.EASY, Difficulty.NORMAL -> 0.25
                Difficulty.HARD -> 0.50
                Difficulty.NIGHTMARE -> 1.0
            }

            guardrailResults.add(Triple("${diff.displayName} frustration ≤ ${f0(frustThreshold)}", f1(avgFrust), avgFrust <= frustThreshold))
            guardrailResults.add(Triple("${diff.displayName} engagement ≥ 40", f1(avgEng), avgEng >= 40))
            guardrailResults.add(Triple("${diff.displayName} pacing ≥ 50", f1(avgPace), avgPace >= 50))
            guardrailResults.add(Triple("${diff.displayName} dead-roll streak ≤ 3", "$maxDeadStreak", maxDeadStreak <= 3))
            guardrailResults.add(Triple("${diff.displayName} turn-limit loss ≤ ${f0(turnLimitThreshold * 100)}%", "${f0(turnLimitRate * 100)}%", turnLimitRate <= turnLimitThreshold))
            guardrailResults.add(Triple("${diff.displayName} early engagement ≥ 1.5", f1(earlyEng), earlyEng >= 1.5))
        }

        var passes = 0
        var fails = 0
        for ((check, value, passed) in guardrailResults) {
            val status = if (passed) { passes++; "PASS ✅" } else { fails++; "FAIL ❌" }
            println("  $status  $check (actual: $value)")
        }

        println("\n  ${passes} passed, ${fails} failed out of ${guardrailResults.size} guardrails")
        println("\n${"=".repeat(80)}")
        println("  FUN-FACTOR PLAYTEST COMPLETE")
        println("=".repeat(80))
    }

    // Formatting helpers
    private fun f0(d: Double) = String.format("%.0f", d)
    private fun f1(d: Double) = String.format("%.1f", d)
    private fun f2(d: Double) = String.format("%.2f", d)
    private fun round2(d: Double) = Math.round(d * 100) / 100.0
}
