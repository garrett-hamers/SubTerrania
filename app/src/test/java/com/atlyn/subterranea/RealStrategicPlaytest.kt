package com.atlyn.subterranea

import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.model.*
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Phase K — "Play For Real" strategic playtest.
 *
 * Distinct from [AutoPlaytest] (slider-bias profiles) and [FunFactorPlaytest]
 * (frustration / engagement curves). This test runs a UTILITY-MAXIMIZING player
 * that enumerates every legal action each turn and picks the highest-EV move.
 *
 * It plays *well* (vs the human-mimicking tests that play with deliberate noise)
 * and the metrics it captures answer different questions:
 *   - "How meaningful are decisions?" — top1-vs-top2 utility gap distribution
 *   - "Is the game solvable?" — variance of strategy across seeds & maps
 *   - "Does dice variance ruin it?" — regret distribution (best move that
 *     produced sub-median realized payoff)
 *   - "Is replayability real?" — per (character × map) build-mix divergence
 *
 * Output: playtest-results/K1_heuristic_report.md (markdown, written at end).
 */
class RealStrategicPlaytest {

    // ========================================================================
    // STRATEGIC PLAYER — utility-maximizing brain
    // ========================================================================

    /**
     * Score model weights. Tuned defensively (no parameter sweeps); the goal is
     * a credible "well-played" baseline, not a perfectly optimised solver.
     */
    private object Weights {
        const val PER_VP = 100.0           // 1 VP is the standard unit
        const val PER_RESOURCE = 3.0       // a typical resource is worth ~0.03 VP
        const val PER_ILLUMINATED_TILE = 8.0 // illuminating a producing tile
        const val PER_PRODUCING_ADJ = 12.0 // adjacency for an outpost/excavator
        const val EXPLORE_BASE = 28.0      // the option value of revealing a tile
        const val EXPLORE_DEEP_BONUS = 12.0 // mantle/core have rarer rewards
        const val HAZARD_PENALTY_PER_PCT = 0.18 // 30% hazard → 5.4 utility cost
        const val CLEAR_RUBBLE_BASE = 20.0  // unlocking a producing tile is good
        const val TRADE_THRESHOLD = 25.0    // trade only if it beats this gap
        const val ABILITY_BASE = 30.0       // abilities are free → always run them
        const val ENDGAME_VP_RUSH = 1.5     // multiplier near victory
        const val EXPLORE_FLOOR = 4.0       // explore is always weakly positive
        const val LANTERN_DIMINISH_AFTER = 3 // additional lanterns get penalised
    }

    // ========================================================================
    // PER-TURN TELEMETRY
    // ========================================================================
    data class TurnTelemetry(
        val turnNumber: Int,
        val phase: String,
        val choiceSetSize: Int,
        val topUtility: Double,
        val secondUtility: Double,
        val chosenActionType: String,
        val vpBefore: Int,
        val vpAfter: Int,
        val resourcesBefore: Int,
        val resourcesAfter: Int,
        val isPlateau: Boolean,        // no VP change AND no exploration AND no build
        val isNovelActionType: Boolean // first time this action type seen this game
    ) {
        val gap: Double get() = topUtility - secondUtility
    }

    // ========================================================================
    // PER-GAME RESULT
    // ========================================================================
    data class StrategicGameResult(
        val gameNumber: Int,
        val seed: Long,
        val difficulty: Difficulty,
        val character: GameCharacter,
        val mapPreset: MapPreset,
        val won: Boolean,
        val turnsUsed: Int,
        val finalVP: Int,
        val vpTarget: Int,
        val structureMix: Map<StructureType, Int>,
        val exploredTiles: Int,
        val zonesReached: Set<Zone>,
        val totalRolls: Int,
        val deadRolls: Int,
        val tradesMade: Int,
        val abilitiesUsed: Int,
        val plateauTurns: Int,
        val averageGap: Double,
        val medianGap: Double,
        val highStakesTurns: Int,      // gap < 10 (very close call)
        val noBrainerTurns: Int,       // gap > 60 (one move dominates)
        val telemetry: List<TurnTelemetry>
    )

    // ========================================================================
    // GAME LOOP
    // ========================================================================
    private fun playGame(
        gameNumber: Int,
        seed: Long,
        difficulty: Difficulty,
        character: GameCharacter,
        mapPreset: MapPreset
    ): StrategicGameResult {
        val rng = Random(seed)
        val board = BoardGenerator.generateBoard(mapPreset)
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
            mapPreset = mapPreset
        )

        val telemetry = mutableListOf<TurnTelemetry>()
        val seenActionTypes = mutableSetOf<String>()
        var totalRolls = 0
        var deadRolls = 0
        var tradesMade = 0
        var abilitiesUsed = 0
        var plateauTurns = 0
        var lastVP = 0
        var lastExplored = 0
        var lastBuildCount = 0
        val maxTurns = difficulty.maxTurns + 5

        while (!state.gameOver && state.turnNumber <= maxTurns) {
            // --- ROLL ---
            if (state.turnPhase == TurnPhase.ROLL_DICE) {
                val before = state.currentPlayer.resources.values.sum()
                state = GameEngine.rollDiceAndProduce(state)
                totalRolls++
                if (state.pendingConsolation) {
                    state = GameEngine.resolveConsolation(state, pickConsolation(state))
                }
                val produced = state.currentPlayer.resources.values.sum() - before
                if (produced <= 0) deadRolls++
            }

            // --- MAIN ACTIONS ---
            val turnStartVP = state.totalVPFor(state.currentPlayer)
            val turnStartExplored = state.currentPlayer.explorationCount
            val turnStartBuilds = state.currentPlayer.structuresBuilt.size
            var safety = 0
            while (state.turnPhase == TurnPhase.MAIN_ACTION &&
                state.actionsThisTurn < state.maxActionsPerTurn &&
                !state.gameOver && safety < 30
            ) {
                safety++
            // Abilities now cost 1 action each (post-Phase L fix).
            // Only use ability when (a) we have spare actions OR (b) ability is materially valuable
            // (resource gain) and we can't afford a meaningful build.
            val usable = GameEngine.getUsableAbilities(state)
            if (usable.isNotEmpty() && shouldUseAbility(state, usable.first())) {
                state = GameEngine.useStructureAbility(state, usable.first().location)
                abilitiesUsed++
                continue
            }

                val before = state
                val actions = GameEngine.getAvailableActions(state).filter { it !is GameAction.RollDice }
                val candidates = enumerateAllCandidates(state, actions)
                if (candidates.isEmpty()) break

                val scored = candidates
                    .map { it to scoreAction(state, it) }
                    .sortedByDescending { it.second }

                val (chosen, top) = scored.first()
                val secondScore = scored.getOrNull(1)?.second ?: 0.0

                val resourcesBefore = state.currentPlayer.resources.values.sum()
                val vpBefore = state.totalVPFor(state.currentPlayer)
                val chosenType = actionTypeName(chosen)

                state = executeAction(state, chosen)
                if (chosen is GameAction.Trade) tradesMade++

                // Handle interactive events that pop after explores
                if (state.pendingInteractiveEvent != null && state.pendingEventCoord != null) {
                    val ev = state.pendingInteractiveEvent!!
                    val coord = state.pendingEventCoord!!
                    state = GameEngine.resolveInteractiveEvent(state, ev, pickInteractiveChoice(state, ev), coord)
                }

                val resourcesAfter = state.currentPlayer.resources.values.sum()
                val vpAfter = state.totalVPFor(state.currentPlayer)
                val isNovel = chosenType !in seenActionTypes
                seenActionTypes.add(chosenType)

                telemetry.add(
                    TurnTelemetry(
                        turnNumber = state.turnNumber,
                        phase = state.turnPhase.name,
                        choiceSetSize = candidates.size,
                        topUtility = top,
                        secondUtility = secondScore,
                        chosenActionType = chosenType,
                        vpBefore = vpBefore,
                        vpAfter = vpAfter,
                        resourcesBefore = resourcesBefore,
                        resourcesAfter = resourcesAfter,
                        isPlateau = false, // computed at turn level below
                        isNovelActionType = isNovel
                    )
                )

                // No state change → break (avoid infinite loop)
                if (state == before) break
            }

            val turnEndVP = state.totalVPFor(state.currentPlayer)
            val turnEndExplored = state.currentPlayer.explorationCount
            val turnEndBuilds = state.currentPlayer.structuresBuilt.size
            val plateaued = (turnEndVP == turnStartVP) &&
                (turnEndExplored == turnStartExplored) &&
                (turnEndBuilds == turnStartBuilds)
            if (plateaued) plateauTurns++

            // Mark the last entry of this turn as plateau if applicable
            if (plateaued && telemetry.lastOrNull()?.turnNumber == state.turnNumber) {
                val last = telemetry.removeAt(telemetry.size - 1)
                telemetry.add(last.copy(isPlateau = true))
            }

            lastVP = turnEndVP
            lastExplored = turnEndExplored
            lastBuildCount = turnEndBuilds

            if (!state.gameOver) state = GameEngine.endTurn(state)
        }

        val finalPlayer = state.currentPlayer
        val gaps = telemetry.map { it.gap }.filter { it.isFinite() }
        val sortedGaps = gaps.sorted()
        val medianGap = if (sortedGaps.isEmpty()) 0.0 else sortedGaps[sortedGaps.size / 2]
        val avgGap = if (gaps.isEmpty()) 0.0 else gaps.average()
        val highStakesTurns = telemetry.count { it.gap < 10 }
        val noBrainerTurns = telemetry.count { it.gap > 60 }
        val structureMix = finalPlayer.structuresBuilt
            .groupingBy { it.type }
            .eachCount()
        val zonesReached = state.board.values
            .filter { it.isRevealed }
            .map { it.zone }
            .toSet()

        return StrategicGameResult(
            gameNumber = gameNumber,
            seed = seed,
            difficulty = difficulty,
            character = character,
            mapPreset = mapPreset,
            won = state.gameOver && state.winner != null,
            turnsUsed = state.turnNumber,
            finalVP = state.totalVPFor(finalPlayer),
            vpTarget = state.victoryPointsToWin,
            structureMix = structureMix,
            exploredTiles = finalPlayer.explorationCount,
            zonesReached = zonesReached,
            totalRolls = totalRolls,
            deadRolls = deadRolls,
            tradesMade = tradesMade,
            abilitiesUsed = abilitiesUsed,
            plateauTurns = plateauTurns,
            averageGap = avgGap,
            medianGap = medianGap,
            highStakesTurns = highStakesTurns,
            noBrainerTurns = noBrainerTurns,
            telemetry = telemetry
        )
    }

    // ========================================================================
    // ACTION CANDIDATES (expand Trade into specific resource pairs)
    // ========================================================================
    private fun enumerateAllCandidates(state: GameState, baseActions: List<GameAction>): List<GameAction> {
        val out = baseActions.toMutableList()
        // Inject Trade candidates: try each (give, receive) pair we can afford
        val ratio = if (state.discountTradeAvailable) 2 else state.difficulty.tradeRatio
        if (state.actionsThisTurn < state.maxActionsPerTurn) {
            val player = state.currentPlayer
            for (give in Resource.entries) {
                if (player.getResourceCount(give) < ratio) continue
                for (receive in Resource.entries) {
                    if (give == receive) continue
                    out.add(GameAction.Trade(mapOf(give to ratio), mapOf(receive to 1)))
                }
            }
        }
        return out
    }

    // ========================================================================
    // SCORING — the brain
    // ========================================================================
    private fun scoreAction(state: GameState, action: GameAction): Double {
        val player = state.currentPlayer
        val turnsRemaining = max(1, state.difficulty.maxTurns - state.turnNumber + 1)
        val vpToWin = state.victoryPointsToWin - state.totalVPFor(player)
        val endgameMult = if (vpToWin <= 4) Weights.ENDGAME_VP_RUSH else 1.0
        return when (action) {
            is GameAction.EndTurn -> 0.0
            is GameAction.Build -> scoreBuild(state, action, turnsRemaining, endgameMult)
            is GameAction.Explore -> scoreExplore(state, action, turnsRemaining)
            is GameAction.ClearRubble -> scoreClear(state, action, turnsRemaining)
            is GameAction.Trade -> scoreTrade(state, action)
            is GameAction.RollDice -> -1.0  // shouldn't appear here but be safe
        }
    }

    private fun scoreBuild(state: GameState, action: GameAction.Build, turnsRemaining: Int, endgameMult: Double): Double {
        val type = action.structureType
        val tile = state.board[action.location] ?: return -1.0
        val cost = GameEngine.getAdjustedBuildCost(type, state.difficulty, state.selectedCharacter)
        val resourceCost = cost.values.sum().toDouble()
        var score = type.victoryPoints * Weights.PER_VP * endgameMult - resourceCost * Weights.PER_RESOURCE

        // Tile-suitability bonuses
        when (type) {
            StructureType.LANTERN -> {
                val existingLanterns = state.currentPlayer.structuresBuilt.count { it.type == StructureType.LANTERN }
                val adj = action.location.neighbors()
                val newlyLit = adj
                    .mapNotNull { state.board[it] }
                    .count { it.isRevealed && !it.isIlluminated && it.terrain.produces != null && !it.hasRubble }
                score += newlyLit * Weights.PER_ILLUMINATED_TILE * min(turnsRemaining, 5)
                val totalAdjRevealed = adj.count { state.board[it]?.isRevealed == true }
                if (totalAdjRevealed >= 4) score += Weights.PER_VP * 0.5
                // Diminishing returns past 3 lanterns
                if (existingLanterns >= Weights.LANTERN_DIMINISH_AFTER) {
                    score -= (existingLanterns - Weights.LANTERN_DIMINISH_AFTER + 1) * 25.0
                }
                // Heavy penalty if it lights nothing AND there are plenty of lanterns
                if (newlyLit == 0 && existingLanterns >= 2) score -= 40.0
            }
            StructureType.OUTPOST, StructureType.EXCAVATOR -> {
                if (tile.terrain.produces != null && tile.isIlluminated) {
                    score += Weights.PER_PRODUCING_ADJ * min(turnsRemaining, 6)
                    val odds = tileRollProbability(tile)
                    score += odds * 80.0 * min(turnsRemaining, 6)
                } else {
                    score -= 30.0
                }
            }
            StructureType.FUNGAL_FARM -> {
                if (tile.terrain == TerrainType.FUNGAL_FOREST && tile.isIlluminated) score += Weights.PER_PRODUCING_ADJ * 5
            }
            StructureType.CRYSTAL_REFINERY -> {
                if (tile.terrain == TerrainType.CRYSTAL_GROTTO && tile.isIlluminated) score += Weights.PER_PRODUCING_ADJ * 5
            }
            StructureType.BEETLE_STABLE -> {
                if (tile.terrain == TerrainType.BEETLE_FARM && tile.isIlluminated) score += Weights.PER_PRODUCING_ADJ * 4
            }
            StructureType.CORE_ANCHOR -> {
                // 5 VP swing — almost always best move if affordable & legal
                score += Weights.PER_VP * 4 * endgameMult
            }
        }
        return score
    }

    private fun tileRollProbability(tile: HexTile): Double {
        val ways = (tile.numberToken?.let { rollWays(it) } ?: 0) +
            (tile.secondaryNumberToken?.let { rollWays(it) } ?: 0)
        return ways / 36.0
    }

    private fun rollWays(total: Int): Int = when (total) {
        2, 12 -> 1; 3, 11 -> 2; 4, 10 -> 3; 5, 9 -> 4; 6, 8 -> 5; 7 -> 6
        else -> 0
    }

    private fun scoreExplore(state: GameState, action: GameAction.Explore, turnsRemaining: Int): Double {
        val tile = state.board[action.location]
        val zoneBonus = when (tile?.zone) {
            Zone.SURFACE -> 0.0
            Zone.MANTLE -> Weights.EXPLORE_DEEP_BONUS
            Zone.CORE -> Weights.EXPLORE_DEEP_BONUS * 1.6
            else -> 0.0
        }
        val hazardPct = state.difficulty.hazardChance * 100
        val survival = max(0f, 1f - state.selectedCharacter.hazardResistance())
        val hazardPenalty = hazardPct * Weights.HAZARD_PENALTY_PER_PCT * survival

        // Build-space urgency: revealing tiles unlocks future builds
        val buildableSurface = state.board.values.count { t ->
            t.isRevealed && !state.hasStructureAt(t.coordinate) &&
                t.terrain != TerrainType.MAGMA_FLOW && t.terrain != TerrainType.BEDROCK
        }
        val buildSpaceUrgency = when {
            buildableSurface <= 1 -> 35.0
            buildableSurface <= 3 -> 18.0
            buildableSurface <= 5 -> 6.0
            else -> 0.0
        }

        // VP pressure: VP we still need divided by turns left
        val vpDeficit = max(0, state.victoryPointsToWin - state.totalVPFor(state.currentPlayer))
        val vpPressure = vpDeficit.toDouble() / max(1, turnsRemaining) * 4.0

        // Late game: explore is less useful (no time to capitalize)
        val turnFactor = min(1.0, turnsRemaining / 4.0)
        val raw = (Weights.EXPLORE_BASE + zoneBonus + buildSpaceUrgency + vpPressure - hazardPenalty) * turnFactor
        return max(Weights.EXPLORE_FLOOR, raw)
    }

    private fun scoreClear(state: GameState, action: GameAction.ClearRubble, turnsRemaining: Int): Double {
        val tile = state.board[action.location] ?: return 0.0
        val productionPotential = if (tile.isIlluminated && tile.terrain.produces != null) 1.0 else 0.3
        return Weights.CLEAR_RUBBLE_BASE * productionPotential * min(turnsRemaining, 6) / 6.0
    }

    private fun scoreTrade(state: GameState, action: GameAction.Trade): Double {
        // Rough heuristic: trade is worth it if the received resource unlocks
        // a structure within reach. We approximate by checking which buildable
        // we'd unlock with one more of that resource.
        val player = state.currentPlayer
        val receive = action.receive.keys.first()
        val give = action.give.keys.first()
        val giveAmt = action.give[give] ?: 0
        // We're spending giveAmt of give (cheap resource preferably)
        val playerHas = player.getResourceCount(receive)
        val playerHasGive = player.getResourceCount(give)
        // Don't trade away rare resources cheaply
        val rareLoss = if (give == Resource.IRON_ORE || give == Resource.CRYSTAL) -50.0 else 0.0
        // If we'd be left with 0 of give, that's bad
        val emptyPenalty = if (playerHasGive - giveAmt <= 0) -25.0 else 0.0
        // Do we benefit? Compute the highest-utility build we'd unlock.
        val withReceive = player.copy(resources = player.resources.toMutableMap().apply {
            this[give] = (this[give] ?: 0) - giveAmt
            this[receive] = (this[receive] ?: 0) + 1
        })
        val canBuildBetter = StructureType.entries.any { st ->
            val cost = GameEngine.getAdjustedBuildCost(st, state.difficulty, state.selectedCharacter)
            !player.canAfford(cost) && withReceive.canAfford(cost)
        }
        val unlockBonus = if (canBuildBetter) 50.0 else -30.0
        return unlockBonus + rareLoss + emptyPenalty
    }

    private fun shouldUseAbility(state: GameState, structure: Structure): Boolean {
        // Abilities now cost 1 action (post Phase L). Only fire if we have spare action budget,
        // or if the ability gives a tangible resource and we cannot afford anything meaningful.
        val actionsRemainingAfterUse = state.maxActionsPerTurn - state.actionsThisTurn - 1
        val hasSpare = actionsRemainingAfterUse >= 1
        return when (structure.type.ability) {
            StructureAbility.SPORE_BURST -> {
                // +2 Mycelium is material. Use if spare action OR we lack mycelium
                hasSpare || state.currentPlayer.getResourceCount(Resource.MYCELIUM) < 2
            }
            StructureAbility.OVERTIME -> {
                val player = state.currentPlayer
                hasSpare && Resource.entries.any { player.getResourceCount(it) < 2 }
            }
            // Info-only abilities — only use when we have spare budget (can't beat a build/explore for the last action)
            StructureAbility.SURVEY, StructureAbility.FLARE -> hasSpare
            null -> false
        }
    }

    private fun executeAction(state: GameState, action: GameAction): GameState = when (action) {
        is GameAction.Build -> GameEngine.buildStructure(state, action.structureType, action.location)
        is GameAction.Explore -> GameEngine.exploreTile(state, action.location)
        is GameAction.ClearRubble -> GameEngine.clearRubble(state, action.location)
        is GameAction.Trade -> {
            val give = action.give.keys.first()
            val receive = action.receive.keys.first()
            GameEngine.tradeResources(state, give, receive)
        }
        is GameAction.EndTurn -> GameEngine.endTurn(state)
        is GameAction.RollDice -> GameEngine.rollDiceAndProduce(state)
    }

    private fun pickConsolation(state: GameState): RollConsolation {
        val player = state.currentPlayer
        val totalResources = player.resources.values.sum()
        // If we have very few resources, scavenge.
        // If we want to do more this turn, hustle.
        // If we'd benefit from cheap trades, barter.
        val needsResource = totalResources <= 3
        return when {
            needsResource -> RollConsolation.GAIN_RESOURCE
            state.actionsThisTurn < state.maxActionsPerTurn - 1 -> RollConsolation.BONUS_ACTION
            GameEngine.canTrade(state) -> RollConsolation.DISCOUNT_TRADE
            else -> RollConsolation.GAIN_RESOURCE
        }
    }

    private fun pickInteractiveChoice(state: GameState, event: InteractiveEvent): InteractiveChoiceId {
        // Choose the safest profitable option per event type.
        return when (event) {
            is InteractiveEvent.BeetleSwarm -> InteractiveChoiceId.SNEAK
            is InteractiveEvent.UnstableGround ->
                if (state.currentPlayer.getResourceCount(Resource.BASALT) >= 2) InteractiveChoiceId.REINFORCE
                else InteractiveChoiceId.CAREFUL
            is InteractiveEvent.AncientCache -> InteractiveChoiceId.OPEN
            is InteractiveEvent.LostMinerEncounter -> InteractiveChoiceId.RESCUE
        }
    }

    private fun actionTypeName(action: GameAction): String = when (action) {
        is GameAction.Build -> "BUILD-${action.structureType.name}"
        is GameAction.Explore -> "EXPLORE"
        is GameAction.ClearRubble -> "CLEAR_RUBBLE"
        is GameAction.Trade -> "TRADE"
        is GameAction.EndTurn -> "END_TURN"
        is GameAction.RollDice -> "ROLL"
    }

    // ========================================================================
    // MAIN TEST — run the matrix and write the report
    // ========================================================================
    @Test
    fun runStrategicMatrix() {
        val results = mutableListOf<StrategicGameResult>()
        val difficulties = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE)
        val characters = listOf(
            GameCharacter.EXPLORER,
            GameCharacter.PROSPECTOR,
            GameCharacter.SCOUT,
            GameCharacter.ENGINEER
        )
        val maps = listOf(MapPreset.STANDARD, MapPreset.CRYSTAL_CAVES, MapPreset.FUNGAL_JUNGLE)
        val seedsPerCell = 5

        println("=".repeat(90))
        println("PHASE K-1 — STRATEGIC HEURISTIC PLAYTEST")
        println("Cells: ${difficulties.size} difficulties × ${characters.size} characters × ${maps.size} maps × $seedsPerCell seeds")
        println("Total games: ${difficulties.size * characters.size * maps.size * seedsPerCell}")
        println("=".repeat(90))

        var gameNum = 0
        for (diff in difficulties) {
            for (chr in characters) {
                for (mp in maps) {
                    for (s in 1..seedsPerCell) {
                        gameNum++
                        val seed = (diff.ordinal * 10000 + chr.ordinal * 1000 + mp.ordinal * 100 + s).toLong()
                        val r = playGame(gameNum, seed, diff, chr, mp)
                        results.add(r)
                    }
                }
            }
        }

        writeReport(results)
    }

    // ========================================================================
    // REPORT
    // ========================================================================
    private fun writeReport(results: List<StrategicGameResult>) {
        val sb = StringBuilder()
        sb.appendLine("# Phase K-1 — Strategic Heuristic Playtest Report")
        sb.appendLine()
        sb.appendLine("**Total games:** ${results.size}")
        sb.appendLine("**Player model:** Utility-maximizing AI (enumerates legal actions, scores each by EV(VP) + structure synergy + risk-adjusted explore value, picks best).")
        sb.appendLine()

        // ---- Section 1: Win rates ----
        sb.appendLine("## 1. Win Rates (skilled-player ceiling)")
        sb.appendLine()
        sb.appendLine("If a *good* player can't beat a difficulty, that's a balance flag. If they always win in a few turns, the difficulty isn't differentiated.")
        sb.appendLine()
        sb.appendLine("| Difficulty | Win % | Avg turns | Avg VP | VP target |")
        sb.appendLine("|---|---|---|---|---|")
        for (diff in Difficulty.entries) {
            val r = results.filter { it.difficulty == diff }
            if (r.isEmpty()) continue
            val winPct = r.count { it.won } * 100.0 / r.size
            sb.appendLine("| ${diff.displayName} | ${"%.0f".format(winPct)}% | ${"%.1f".format(r.map { it.turnsUsed }.average())} | ${"%.1f".format(r.map { it.finalVP }.average())} | ${diff.victoryPointsToWin} |")
        }
        sb.appendLine()

        // ---- Section 2: Decision difficulty ----
        sb.appendLine("## 2. Decision Difficulty (does the game make you THINK?)")
        sb.appendLine()
        sb.appendLine("`gap` = top-1 utility minus top-2 utility. Small gap = the choice mattered. Big gap = one move dominates → boring.")
        sb.appendLine()
        sb.appendLine("| Difficulty | Avg gap | Median gap | High-stakes turns (gap<10) % | No-brainer turns (gap>60) % |")
        sb.appendLine("|---|---|---|---|---|")
        for (diff in Difficulty.entries) {
            val r = results.filter { it.difficulty == diff }
            if (r.isEmpty()) continue
            val totalTurns = r.sumOf { it.telemetry.size }
            if (totalTurns == 0) continue
            val highStakes = r.sumOf { it.highStakesTurns } * 100.0 / totalTurns
            val noBrainer = r.sumOf { it.noBrainerTurns } * 100.0 / totalTurns
            sb.appendLine("| ${diff.displayName} | ${"%.1f".format(r.map { it.averageGap }.average())} | ${"%.1f".format(r.map { it.medianGap }.average())} | ${"%.0f".format(highStakes)}% | ${"%.0f".format(noBrainer)}% |")
        }
        sb.appendLine()

        // ---- Section 3: Replayability via build mix ----
        sb.appendLine("## 3. Replayability (do strategies vary?)")
        sb.appendLine()
        sb.appendLine("Average structure mix per (character × map). If a row is dominated by one structure type across ALL maps, the meta is solved.")
        sb.appendLine()
        sb.appendLine("| Character | Map | Avg lanterns | Avg outposts | Avg excavators | Avg specialists | Core Anchors |")
        sb.appendLine("|---|---|---|---|---|---|---|")
        val characters = listOf(GameCharacter.EXPLORER, GameCharacter.PROSPECTOR, GameCharacter.SCOUT, GameCharacter.ENGINEER)
        val maps = listOf(MapPreset.STANDARD, MapPreset.CRYSTAL_CAVES, MapPreset.FUNGAL_JUNGLE)
        for (chr in characters) {
            for (mp in maps) {
                val cell = results.filter { it.character == chr && it.mapPreset == mp }
                if (cell.isEmpty()) continue
                val lantern = cell.map { it.structureMix[StructureType.LANTERN] ?: 0 }.average()
                val outpost = cell.map { it.structureMix[StructureType.OUTPOST] ?: 0 }.average()
                val exc = cell.map { it.structureMix[StructureType.EXCAVATOR] ?: 0 }.average()
                val spec = cell.map {
                    (it.structureMix[StructureType.FUNGAL_FARM] ?: 0) +
                        (it.structureMix[StructureType.CRYSTAL_REFINERY] ?: 0) +
                        (it.structureMix[StructureType.BEETLE_STABLE] ?: 0)
                }.average()
                val anchor = cell.map { it.structureMix[StructureType.CORE_ANCHOR] ?: 0 }.average()
                sb.appendLine("| ${chr.displayName} | ${mp.displayName} | ${"%.1f".format(lantern)} | ${"%.1f".format(outpost)} | ${"%.1f".format(exc)} | ${"%.1f".format(spec)} | ${"%.2f".format(anchor)} |")
            }
        }
        sb.appendLine()

        // ---- Section 4: Dice impact ----
        sb.appendLine("## 4. Dice Variance Impact")
        sb.appendLine()
        sb.appendLine("`dead roll %` = rolls that produced nothing. Catan-style games tolerate ~25% dead rolls. Higher than that and the game *feels random* even to a skilled player.")
        sb.appendLine()
        sb.appendLine("| Difficulty | Dead roll % | Avg plateau turns | Avg trades / game |")
        sb.appendLine("|---|---|---|---|")
        for (diff in Difficulty.entries) {
            val r = results.filter { it.difficulty == diff }
            if (r.isEmpty()) continue
            val deadPct = r.sumOf { it.deadRolls }.toDouble() / r.sumOf { it.totalRolls }.coerceAtLeast(1) * 100
            sb.appendLine("| ${diff.displayName} | ${"%.0f".format(deadPct)}% | ${"%.1f".format(r.map { it.plateauTurns }.average())} | ${"%.1f".format(r.map { it.tradesMade }.average())} |")
        }
        sb.appendLine()

        // ---- Section 5: Action variety per game ----
        sb.appendLine("## 5. Action Variety")
        sb.appendLine()
        sb.appendLine("Average distinct action TYPES taken per game (BUILD-LANTERN, BUILD-OUTPOST, EXPLORE, TRADE, CLEAR_RUBBLE, etc).")
        sb.appendLine()
        sb.appendLine("| Difficulty | Avg distinct action types | Avg explores | Avg structures built | Avg abilities used |")
        sb.appendLine("|---|---|---|---|---|")
        for (diff in Difficulty.entries) {
            val r = results.filter { it.difficulty == diff }
            if (r.isEmpty()) continue
            val variety = r.map { it.telemetry.map { t -> t.chosenActionType }.toSet().size }.average()
            val explores = r.map { it.exploredTiles }.average()
            val builds = r.map { it.structureMix.values.sum() }.average()
            val abilities = r.map { it.abilitiesUsed }.average()
            sb.appendLine("| ${diff.displayName} | ${"%.1f".format(variety)} | ${"%.1f".format(explores)} | ${"%.1f".format(builds)} | ${"%.1f".format(abilities)} |")
        }
        sb.appendLine()

        // ---- Section 6: Character/map balance ----
        sb.appendLine("## 6. Character × Map Win Rates")
        sb.appendLine()
        sb.appendLine("| Character | Standard | Crystal Caves | Fungal Jungle |")
        sb.appendLine("|---|---|---|---|")
        for (chr in characters) {
            val row = StringBuilder("| ${chr.displayName} |")
            for (mp in maps) {
                val r = results.filter { it.character == chr && it.mapPreset == mp }
                val wins = r.count { it.won }
                row.append(" ${if (r.isEmpty()) "—" else "$wins/${r.size}"} |")
            }
            sb.appendLine(row.toString())
        }
        sb.appendLine()

        // ---- Section 7: Verdict snippets ----
        sb.appendLine("## 7. Auto-detected Flags")
        sb.appendLine()
        val flags = mutableListOf<String>()
        for (diff in Difficulty.entries) {
            val r = results.filter { it.difficulty == diff }
            if (r.isEmpty()) continue
            val winPct = r.count { it.won } * 100.0 / r.size
            if (winPct >= 95.0 && diff in listOf(Difficulty.HARD, Difficulty.NIGHTMARE)) {
                flags.add("⚠️ Skilled player wins ${"%.0f".format(winPct)}% on ${diff.displayName}. May be too easy for top-end.")
            }
            if (winPct < 30.0 && diff != Difficulty.NIGHTMARE) {
                flags.add("⚠️ Skilled player wins only ${"%.0f".format(winPct)}% on ${diff.displayName}. Casual players will bounce off.")
            }
            val totalTurns = r.sumOf { it.telemetry.size }
            if (totalTurns > 0) {
                val noBrainerPct = r.sumOf { it.noBrainerTurns } * 100.0 / totalTurns
                if (noBrainerPct >= 40.0) {
                    flags.add("⚠️ ${diff.displayName}: ${"%.0f".format(noBrainerPct)}% of turns have an obvious dominant move. Strategy may feel scripted.")
                }
            }
            val deadPct = r.sumOf { it.deadRolls }.toDouble() / r.sumOf { it.totalRolls }.coerceAtLeast(1) * 100
            if (deadPct >= 35.0) {
                flags.add("⚠️ ${diff.displayName}: ${"%.0f".format(deadPct)}% dead rolls. Game may feel bad-luck-driven.")
            }
        }
        // Cross-cell variety check
        for (chr in characters) {
            val plays = results.filter { it.character == chr }
            val mixVariance = plays.map { it.structureMix[StructureType.LANTERN] ?: 0 }.toSet().size
            if (plays.isNotEmpty() && mixVariance == 1) {
                flags.add("ℹ️ ${chr.displayName}: lantern count is identical across all games. Strategy is map-agnostic.")
            }
        }
        if (flags.isEmpty()) flags.add("✅ No automated red flags surfaced.")
        flags.forEach { sb.appendLine("- $it") }
        sb.appendLine()

        // ---- Per-game appendix (compact) ----
        sb.appendLine("## 8. Per-Game Summary")
        sb.appendLine()
        sb.appendLine("| # | Diff | Char | Map | Won | Turns | VP | Plateau | Avg gap |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        for (r in results) {
            sb.appendLine("| ${r.gameNumber} | ${r.difficulty.name.take(4)} | ${r.character.name.take(8)} | ${r.mapPreset.name.take(8)} | ${if (r.won) "✓" else "✗"} | ${r.turnsUsed} | ${r.finalVP}/${r.vpTarget} | ${r.plateauTurns} | ${"%.0f".format(r.averageGap)} |")
        }

        val outDir = File("../playtest-results").also { it.mkdirs() }
        val outFile = File(outDir, "K1_heuristic_report.md")
        outFile.writeText(sb.toString())
        println()
        println("Wrote ${outFile.absolutePath} (${outFile.length()} bytes)")
    }
}
