package com.atlyn.subterranea

import com.atlyn.subterranea.domain.logic.BoardGenerator
import com.atlyn.subterranea.domain.logic.GameEngine
import com.atlyn.subterranea.domain.model.*
import org.junit.Test

/**
 * Automated playtest: runs 100 games across all difficulties with smart AI,
 * collects detailed engagement/retention metrics, and prints analysis.
 */
class AutoPlaytest {

    data class GameResult(
        val gameNumber: Int,
        val difficulty: Difficulty,
        val character: GameCharacter,
        val won: Boolean,
        val turns: Int,
        val totalVP: Int,
        val vpTarget: Int,
        val structuresBuilt: Int,
        val tilesExplored: Int,
        val achievements: Set<Achievement>,
        val resourcesCollected: Map<Resource, Int>,
        val tradesMade: Int,
        val deadRolls: Int,
        val totalRolls: Int,
        val hazardsHit: Int,
        val actionsPerTurn: List<Int>,
        val vpPerTurn: List<Int>,
        val stuckTurns: Int,  // turns with 0 meaningful actions
        val turnWithFirstBuild: Int,
        val abilitiesUsed: Int,
        val rubbleCleared: Int,
        val maxResourcesSeen: Int  // peak resource count
    )

    // AI Strategy: prioritizes illumination → resource diversity → VP buildings
    private fun playGame(gameNumber: Int, difficulty: Difficulty, character: GameCharacter): GameResult {
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

        var tradesMade = 0
        var deadRolls = 0
        var totalRolls = 0
        var hazardsHit = 0
        var stuckTurns = 0
        var turnWithFirstBuild = -1
        var abilitiesUsed = 0
        var rubbleCleared = 0
        var maxResourcesSeen = 0
        val actionsPerTurn = mutableListOf<Int>()
        val vpPerTurn = mutableListOf<Int>()
        val totalResourcesCollected = Resource.entries.associateWith { 0 }.toMutableMap()
        val maxTurns = difficulty.maxTurns + 5  // safety cap beyond turn limit

        while (!state.gameOver && state.turnNumber <= maxTurns) {
            val turnStartActions = state.actionsThisTurn
            val turnNumber = state.turnNumber

            // Phase 1: Roll dice
            if (state.turnPhase == TurnPhase.ROLL_DICE) {
                state = GameEngine.rollDiceAndProduce(state)
                totalRolls++

                // Handle consolation
                if (state.pendingConsolation) {
                    // Smart consolation choice
                    val choice = pickConsolation(state)
                    state = GameEngine.resolveConsolation(state, choice)
                }

                // Track dead rolls (auto-consolation scavenged = dead roll)
                val produced = state.lastProduction
                if (produced.isEmpty() || (produced.size == 1 && produced.values.sum() == 1)) {
                    deadRolls++
                }

                // Track resources
                state.lastProduction.forEach { (res, amt) ->
                    totalResourcesCollected[res] = (totalResourcesCollected[res] ?: 0) + amt
                }

                // Track max resources
                val totalRes = state.currentPlayer.resources.values.sum()
                if (totalRes > maxResourcesSeen) maxResourcesSeen = totalRes
            }

            // Phase 2: Main actions
            var actionsTaken = 0
            var safety = 0
            while (state.turnPhase == TurnPhase.MAIN_ACTION &&
                state.actionsThisTurn < state.maxActionsPerTurn &&
                !state.gameOver && safety < 20
            ) {
                safety++
                val actionBefore = state.actionsThisTurn
                state = pickAndExecuteAction(state)

                if (state.actionsThisTurn > actionBefore) {
                    actionsTaken++
                }

                // Track first build
                if (turnWithFirstBuild == -1 && state.structures.isNotEmpty()) {
                    turnWithFirstBuild = turnNumber
                }

                // Count events
                val lastEvent = state.eventLog.firstOrNull() ?: ""
                if (lastEvent.contains("Cave-in") || lastEvent.contains("Gas leak") ||
                    lastEvent.contains("Magma burst") || lastEvent.contains("Tremor")
                ) {
                    hazardsHit++
                }

                if (lastEvent.contains("Traded") || lastEvent.contains("trade")) {
                    tradesMade++
                }
                if (lastEvent.contains("Overtime") || lastEvent.contains("Flare") ||
                    lastEvent.contains("Survey") || lastEvent.contains("Spore Burst")
                ) {
                    abilitiesUsed++
                }
                if (lastEvent.contains("Cleared rubble")) {
                    rubbleCleared++
                }

                // If action count didn't change despite trying, break
                if (state.actionsThisTurn == actionBefore && safety > 3) break
            }

            if (actionsTaken == 0 && state.turnPhase == TurnPhase.MAIN_ACTION) {
                stuckTurns++
            }

            actionsPerTurn.add(actionsTaken)
            vpPerTurn.add(state.totalVPFor(state.currentPlayer))

            // End turn
            if (!state.gameOver) {
                state = GameEngine.endTurn(state)
            }
        }

        val finalPlayer = state.currentPlayer
        return GameResult(
            gameNumber = gameNumber,
            difficulty = difficulty,
            character = character,
            won = state.gameOver && state.winner != null,
            turns = state.turnNumber,
            totalVP = state.totalVPFor(finalPlayer),
            vpTarget = state.victoryPointsToWin,
            structuresBuilt = finalPlayer.structuresBuilt.size,
            tilesExplored = finalPlayer.explorationCount,
            achievements = finalPlayer.achievements,
            resourcesCollected = totalResourcesCollected,
            tradesMade = tradesMade,
            deadRolls = deadRolls,
            totalRolls = totalRolls,
            hazardsHit = hazardsHit,
            actionsPerTurn = actionsPerTurn,
            vpPerTurn = vpPerTurn,
            stuckTurns = stuckTurns,
            turnWithFirstBuild = turnWithFirstBuild,
            abilitiesUsed = abilitiesUsed,
            rubbleCleared = rubbleCleared,
            maxResourcesSeen = maxResourcesSeen
        )
    }

    private fun pickConsolation(state: GameState): RollConsolation {
        val player = state.currentPlayer
        val totalRes = player.resources.values.sum()
        // Low resources → scavenge; otherwise hustle for more actions
        return when {
            totalRes < 4 -> RollConsolation.GAIN_RESOURCE
            state.actionsThisTurn == 0 -> RollConsolation.BONUS_ACTION
            else -> RollConsolation.DISCOUNT_TRADE
        }
    }

    private fun pickAndExecuteAction(state: GameState): GameState {
        val player = state.currentPlayer
        val actions = GameEngine.getAvailableActions(state)
        if (actions.isEmpty() || actions.all { it is GameAction.EndTurn }) return state

        // Strategy priority:
        // 1. Build Lantern if affordable and we have few (illumination is critical)
        // 2. Explore if available (discover tiles for production)
        // 3. Build other structures for VP
        // 4. Use structure abilities
        // 5. Trade to afford something useful
        // 6. Clear rubble if it blocks a good tile

        val buildActions = actions.filterIsInstance<GameAction.Build>()
        val exploreActions = actions.filterIsInstance<GameAction.Explore>()
        val clearActions = actions.filterIsInstance<GameAction.ClearRubble>()

        val lanternCount = player.structuresBuilt.count { it.type == StructureType.LANTERN }
        val structureCount = player.structuresBuilt.size

        // Priority 1: Build Lantern (up to 3)
        if (lanternCount < 3) {
            val lanternBuild = buildActions.filter { it.structureType == StructureType.LANTERN }
            if (lanternBuild.isNotEmpty()) {
                // Prefer placing lantern near unexplored tiles for max illumination
                val best = lanternBuild.maxByOrNull { action ->
                    val neighbors = action.location.neighbors()
                    val unexplored = neighbors.count { n -> state.board[n]?.isRevealed == false }
                    val unlit = neighbors.count { n ->
                        val t = state.board[n]
                        t != null && t.isRevealed && !t.isIlluminated
                    }
                    unexplored * 2 + unlit * 3
                }
                if (best != null) return GameEngine.buildStructure(state, best.structureType, best.location)
            }
        }

        // Priority 2: Explore (prefer tiles in Crust/Mantle for better resources)
        if (exploreActions.isNotEmpty()) {
            val best = exploreActions.maxByOrNull { action ->
                val tile = state.board[action.location]
                val zoneScore = when (tile?.zone) {
                    Zone.SURFACE -> 1
                    Zone.CRUST -> 3
                    Zone.MANTLE -> 5
                    Zone.CORE -> 4  // core is riskier
                    null -> 0
                }
                // Prefer tiles adjacent to illuminated tiles (will produce faster)
                val illuminatedNeighbors = action.location.neighbors().count { n ->
                    state.board[n]?.isIlluminated == true
                }
                zoneScore + illuminatedNeighbors
            }
            if (best != null) return GameEngine.exploreTile(state, best.location)
        }

        // Priority 3: Build VP structures
        // Core Anchor (4 VP) > Excavator (2 VP) > Crystal Refinery (2 VP) > others
        val vpStructures = buildActions.sortedByDescending { it.structureType.victoryPoints }
        for (build in vpStructures) {
            if (build.structureType == StructureType.LANTERN) continue // handled above
            // For Excavator, prefer tiles with good number tokens
            if (build.structureType == StructureType.EXCAVATOR) {
                val tile = state.board[build.location]
                if (tile?.numberToken in listOf(6, 7, 8)) {
                    return GameEngine.buildStructure(state, build.structureType, build.location)
                }
                continue
            }
            // Build on tiles with best number tokens
            val tile = state.board[build.location]
            val tokenScore = tile?.numberToken?.let {
                when (it) { 6, 8 -> 5; 5, 9 -> 4; 7 -> 3; 4, 10 -> 2; else -> 1 }
            } ?: 0
            if (tokenScore >= 2 || structureCount < 2) {
                return GameEngine.buildStructure(state, build.structureType, build.location)
            }
        }
        // If we have VP builds left, just take the best one
        if (vpStructures.isNotEmpty()) {
            val best = vpStructures.first()
            return GameEngine.buildStructure(state, best.structureType, best.location)
        }

        // Priority 4: Use abilities
        val usable = GameEngine.getUsableAbilities(state)
        if (usable.isNotEmpty()) {
            val ability = usable.first()
            return GameEngine.useStructureAbility(state, ability.location)
        }

        // Priority 5: Trade to afford Lantern (Crystal + Iron)
        if (lanternCount < 3 && !player.canAfford(StructureType.LANTERN.cost)) {
            if (GameEngine.canTrade(state)) {
                val tradable = GameEngine.getTradableResources(state)
                // Figure out what we need
                val needCrystal = player.getResourceCount(Resource.CRYSTAL) < 1
                val needIron = player.getResourceCount(Resource.IRON_ORE) < 1
                val target = when {
                    needCrystal -> Resource.CRYSTAL
                    needIron -> Resource.IRON_ORE
                    else -> null
                }
                if (target != null) {
                    val give = tradable.firstOrNull { it != target }
                    if (give != null) {
                        return GameEngine.tradeResources(state, give, target)
                    }
                }
            }
        }

        // Priority 6: Trade for any VP structure we're close to affording
        if (GameEngine.canTrade(state)) {
            val affordable = findNearlyAffordableStructure(state)
            if (affordable != null) {
                val (neededResource, giveResource) = affordable
                return GameEngine.tradeResources(state, giveResource, neededResource)
            }
        }

        // Priority 7: Clear rubble on illuminated tiles
        if (clearActions.isNotEmpty()) {
            val best = clearActions.firstOrNull { action ->
                state.board[action.location]?.isIlluminated == true
            } ?: clearActions.first()
            return GameEngine.clearRubble(state, best.location)
        }

        return state
    }

    private fun findNearlyAffordableStructure(state: GameState): Pair<Resource, Resource>? {
        val player = state.currentPlayer
        val tradable = GameEngine.getTradableResources(state)
        if (tradable.isEmpty()) return null

        // Check each structure type we can almost afford
        for (type in StructureType.entries.sortedByDescending { it.victoryPoints }) {
            val cost = type.cost
            val missing = cost.filter { (res, amt) -> player.getResourceCount(res) < amt }
            if (missing.size == 1) {
                val (neededRes, _) = missing.entries.first()
                val give = tradable.firstOrNull { it != neededRes }
                if (give != null) return Pair(neededRes, give)
            }
        }
        return null
    }

    @Test
    fun runHundredGames() {
        val results = mutableListOf<GameResult>()
        val difficulties = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE)
        val gamesPerDifficulty = 25

        println("=" .repeat(80))
        println("SUBTERRANEA AUTOMATED PLAYTEST — 100 GAMES")
        println("=" .repeat(80))
        println()

        for (diff in difficulties) {
            println("--- Playing ${diff.displayName} (${gamesPerDifficulty} games) ---")
            for (i in 1..gamesPerDifficulty) {
                val gameNum = results.size + 1
                val character = GameCharacter.EXPLORER // baseline character
                val result = playGame(gameNum, diff, character)
                results.add(result)
                val status = if (result.won) "WON" else "LOST"
                val turnStr = if (result.turns > 80) "80+" else "${result.turns}"
                print("  Game $gameNum: $status in $turnStr turns, ${result.totalVP}/${result.vpTarget} VP, ${result.structuresBuilt} structures")
                println(", ${result.tilesExplored} explored, ${result.deadRolls}/${result.totalRolls} dead rolls")
            }
            println()
        }

        // === ANALYSIS ===
        println("\n${"=".repeat(80)}")
        println("ENGAGEMENT & RETENTION ANALYSIS")
        println("=".repeat(80))

        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            analyzeGroup("${diff.emoji} ${diff.displayName}", diffResults)
        }

        println("\n${"=".repeat(80)}")
        println("OVERALL ANALYSIS (ALL 100 GAMES)")
        println("=".repeat(80))
        analyzeGroup("All Games", results)

        // === ENGAGEMENT DEEP DIVE ===
        println("\n${"=".repeat(80)}")
        println("ENGAGEMENT DEEP DIVE")
        println("=".repeat(80))

        // VP Progression curves
        println("\n📈 VP PROGRESSION (avg VP at each turn):")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val maxTurn = diffResults.maxOf { it.vpPerTurn.size }
            print("  ${diff.displayName.padEnd(10)}: ")
            for (t in 0 until minOf(maxTurn, 20)) {
                val avgVP = diffResults
                    .mapNotNull { it.vpPerTurn.getOrNull(t) }
                    .takeIf { it.isNotEmpty() }?.average() ?: 0.0
                print("${String.format("%.1f", avgVP)} ")
            }
            println()
        }

        // Dead roll analysis
        println("\n🎲 DEAD ROLL RATES:")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val avgDeadRate = diffResults.map { it.deadRolls.toDouble() / it.totalRolls }.average()
            val avgDeadRolls = diffResults.map { it.deadRolls }.average()
            println("  ${diff.displayName.padEnd(10)}: ${String.format("%.1f%%", avgDeadRate * 100)} " +
                    "(avg ${String.format("%.1f", avgDeadRolls)} dead out of ${String.format("%.1f", diffResults.map { it.totalRolls }.average())} rolls)")
        }

        // Stuck turns (0 meaningful actions) — frustration metric
        println("\n😤 STUCK TURNS (0 actions available):")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val avgStuck = diffResults.map { it.stuckTurns }.average()
            val stuckRate = diffResults.map { it.stuckTurns.toDouble() / it.turns }.average()
            println("  ${diff.displayName.padEnd(10)}: avg ${String.format("%.1f", avgStuck)} stuck turns " +
                    "(${String.format("%.1f%%", stuckRate * 100)} of turns)")
        }

        // Time to first build — onboarding metric
        println("\n🏗️ TIME TO FIRST BUILD (turn #):")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val avgFirst = diffResults.filter { it.turnWithFirstBuild > 0 }
                .map { it.turnWithFirstBuild }.average()
            val neverBuilt = diffResults.count { it.turnWithFirstBuild == -1 }
            println("  ${diff.displayName.padEnd(10)}: avg turn ${String.format("%.1f", avgFirst)}" +
                    if (neverBuilt > 0) " ($neverBuilt games never built)" else "")
        }

        // Actions per turn — activity metric
        println("\n⚡ ACTIONS PER TURN:")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val allActions = diffResults.flatMap { it.actionsPerTurn }
            val avg = if (allActions.isNotEmpty()) allActions.average() else 0.0
            println("  ${diff.displayName.padEnd(10)}: avg ${String.format("%.2f", avg)} actions/turn (max allowed: ${diff.maxActionsPerTurn})")
        }

        // Achievement frequency
        println("\n🏆 ACHIEVEMENT FREQUENCY:")
        for (ach in Achievement.entries) {
            val count = results.count { ach in it.achievements }
            println("  ${ach.displayName.padEnd(20)}: $count/100 games (${count}%)")
        }

        // Structure popularity
        println("\n🏗️ STRUCTURE POPULARITY:")
        for (type in StructureType.entries) {
            val totalBuilt = results.sumOf { r ->
                r.let { state ->
                    // Approximate from achievements and other data
                    0 // We'd need to track this differently
                }
            }
        }

        // Trade frequency
        println("\n🔄 TRADES PER GAME:")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val avgTrades = diffResults.map { it.tradesMade }.average()
            println("  ${diff.displayName.padEnd(10)}: avg ${String.format("%.1f", avgTrades)} trades")
        }

        // Hazard impact
        println("\n⚠️ HAZARDS ENCOUNTERED:")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val avgHazards = diffResults.map { it.hazardsHit }.average()
            println("  ${diff.displayName.padEnd(10)}: avg ${String.format("%.1f", avgHazards)} hazards")
        }

        // === RETENTION RISK FLAGS ===
        println("\n${"=".repeat(80)}")
        println("🚩 RETENTION RISK FLAGS")
        println("=".repeat(80))

        val flags = mutableListOf<String>()

        // Check: Games too short (< 5 turns)
        val tooShort = results.count { it.won && it.turns < 5 }
        if (tooShort > 5) flags.add("⚠️ $tooShort games won in < 5 turns — game may feel too easy/shallow")

        // Check: Games too long (> 40 turns)
        val tooLong = results.count { it.turns > 40 }
        if (tooLong > 10) flags.add("⚠️ $tooLong games took > 40 turns — pacing drags")

        // Check: High dead roll rate
        val overallDeadRate = results.map { it.deadRolls.toDouble() / it.totalRolls }.average()
        if (overallDeadRate > 0.5) flags.add("⚠️ ${String.format("%.0f%%", overallDeadRate * 100)} dead roll rate — players feel unproductive")

        // Check: Too many stuck turns
        val avgStuckAll = results.map { it.stuckTurns }.average()
        if (avgStuckAll > 3) flags.add("⚠️ avg ${String.format("%.1f", avgStuckAll)} stuck turns — frustrating for players")

        // Check: Win rate too high on Easy
        val easyWinRate = results.filter { it.difficulty == Difficulty.EASY }.count { it.won } / 25.0
        if (easyWinRate > 0.95) flags.add("⚠️ Easy win rate ${String.format("%.0f%%", easyWinRate * 100)} — too easy, no challenge")
        if (easyWinRate < 0.5) flags.add("⚠️ Easy win rate ${String.format("%.0f%%", easyWinRate * 100)} — Easy should be more winnable")

        // Check: Win rate too low on Normal
        val normalWinRate = results.filter { it.difficulty == Difficulty.NORMAL }.count { it.won } / 25.0
        if (normalWinRate < 0.3) flags.add("⚠️ Normal win rate ${String.format("%.0f%%", normalWinRate * 100)} — may frustrate average players")

        // Check: Nightmare never won
        val nightmareWinRate = results.filter { it.difficulty == Difficulty.NIGHTMARE }.count { it.won } / 25.0
        if (nightmareWinRate == 0.0) flags.add("ℹ️ Nightmare 0% win rate — may be good for prestige, but can feel impossible")
        if (nightmareWinRate > 0.5) flags.add("⚠️ Nightmare ${String.format("%.0f%%", nightmareWinRate * 100)} win rate — too easy for hardest mode")

        // Check: First build taking too long
        val avgFirstBuild = results.filter { it.turnWithFirstBuild > 0 }.map { it.turnWithFirstBuild }.average()
        if (avgFirstBuild > 5) flags.add("⚠️ First build avg turn ${String.format("%.1f", avgFirstBuild)} — slow start hurts engagement")

        // Check: Low exploration
        val avgExploration = results.map { it.tilesExplored }.average()
        if (avgExploration < 5) flags.add("⚠️ avg ${String.format("%.1f", avgExploration)} tiles explored — exploration system underused")

        // Check: Achievements too rare or too common
        for (ach in Achievement.entries) {
            val rate = results.count { ach in it.achievements } / 100.0
            if (rate < 0.05) flags.add("ℹ️ ${ach.displayName} earned ${String.format("%.0f%%", rate * 100)} — may be too hard to achieve")
            if (rate > 0.9) flags.add("ℹ️ ${ach.displayName} earned ${String.format("%.0f%%", rate * 100)} — too easy, not special")
        }

        // Check: Games where player did nothing
        val noStructureGames = results.count { it.structuresBuilt == 0 }
        if (noStructureGames > 0) flags.add("⚠️ $noStructureGames games with 0 structures built — players can't progress")

        if (flags.isEmpty()) {
            println("  ✅ No major retention risks detected!")
        } else {
            flags.forEach { println("  $it") }
        }

        // === IMPROVEMENT RECOMMENDATIONS ===
        println("\n${"=".repeat(80)}")
        println("📋 GAME-BY-GAME REFLECTIONS & IMPROVEMENT IDEAS")
        println("=".repeat(80))

        // Reflect on patterns
        val shortGames = results.filter { it.won && it.turns <= 6 }
        val longGames = results.filter { !it.won || it.turns > 30 }
        val frustratingGames = results.filter { it.stuckTurns > it.turns / 3.0 }

        if (shortGames.isNotEmpty()) {
            println("\n🏃 SHORT GAMES (won ≤6 turns): ${shortGames.size}")
            println("  These finish before players invest emotionally. Need more VP requirement or slower ramp.")
            shortGames.take(5).forEach { g ->
                println("    Game #${g.gameNumber} (${g.difficulty.displayName}): ${g.turns} turns, ${g.structuresBuilt} structures, ${g.tilesExplored} tiles explored")
            }
        }

        if (longGames.isNotEmpty()) {
            println("\n🐌 LONG/LOST GAMES (>30 turns or lost): ${longGames.size}")
            println("  These drag or feel hopeless. Need catch-up mechanics or resource smoothing.")
            longGames.take(5).forEach { g ->
                println("    Game #${g.gameNumber} (${g.difficulty.displayName}): ${g.turns} turns, ${g.totalVP}/${g.vpTarget} VP, ${g.deadRolls}/${g.totalRolls} dead rolls")
            }
        }

        if (frustratingGames.isNotEmpty()) {
            println("\n😤 FRUSTRATING GAMES (>33% stuck turns): ${frustratingGames.size}")
            println("  Players couldn't do anything meaningful too often. Need more action options.")
            frustratingGames.take(5).forEach { g ->
                println("    Game #${g.gameNumber} (${g.difficulty.displayName}): ${g.stuckTurns}/${g.turns} stuck turns, ${g.deadRolls} dead rolls")
            }
        }

        // Win rate summary
        println("\n📊 WIN RATE SUMMARY:")
        for (diff in difficulties) {
            val diffResults = results.filter { it.difficulty == diff }
            val wins = diffResults.count { it.won }
            val avgTurns = diffResults.filter { it.won }.takeIf { it.isNotEmpty() }
                ?.map { it.turns }?.average() ?: 0.0
            println("  ${diff.displayName.padEnd(10)}: ${wins}/${gamesPerDifficulty} (${wins * 100 / gamesPerDifficulty}%) " +
                    "avg win turn: ${String.format("%.1f", avgTurns)}")
        }

        println("\n${"=".repeat(80)}")
        println("PLAYTEST COMPLETE")
        println("=".repeat(80))
    }

    private fun analyzeGroup(label: String, results: List<GameResult>) {
        val wins = results.count { it.won }
        val losses = results.count { !it.won }
        val avgTurns = results.map { it.turns }.average()
        val avgVP = results.map { it.totalVP }.average()
        val avgStructures = results.map { it.structuresBuilt }.average()
        val avgExplored = results.map { it.tilesExplored }.average()
        val avgDeadRate = results.map { it.deadRolls.toDouble() / maxOf(1, it.totalRolls) }.average()

        println("\n--- $label ---")
        println("  Win/Loss: $wins/${losses} (${String.format("%.0f%%", wins.toDouble() / results.size * 100)} win rate)")
        println("  Avg turns: ${String.format("%.1f", avgTurns)}")
        println("  Avg VP: ${String.format("%.1f", avgVP)}")
        println("  Avg structures: ${String.format("%.1f", avgStructures)}")
        println("  Avg explored: ${String.format("%.1f", avgExplored)}")
        println("  Avg dead roll rate: ${String.format("%.1f%%", avgDeadRate * 100)}")
    }
}
