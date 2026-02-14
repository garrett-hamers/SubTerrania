package com.atlyn.subterranea.domain.model

/**
 * Events that can occur during exploration
 */
sealed class ExplorationEvent {
    abstract val name: String
    abstract val description: String
    
    // Good events - rewards
    data class TreasureCache(
        val resources: Map<Resource, Int>
    ) : ExplorationEvent() {
        override val name = "Treasure Cache"
        override val description = "You found an ancient cache! Gain resources."
    }
    
    data class CrystalVein(
        val amount: Int = 2
    ) : ExplorationEvent() {
        override val name = "Crystal Vein"
        override val description = "A vein of pure crystals! Gain $amount Crystals."
    }
    
    data class AncientArtifact(
        val victoryPoints: Int = 1
    ) : ExplorationEvent() {
        override val name = "Ancient Artifact"
        override val description = "A relic from a lost civilization. Gain $victoryPoints VP!"
    }
    
    data class BeetleNest(
        val chitinAmount: Int = 3
    ) : ExplorationEvent() {
        override val name = "Beetle Nest"
        override val description = "A nest of cave beetles! Gain $chitinAmount Chitin."
    }
    
    // Neutral events
    data object StableGround : ExplorationEvent() {
        override val name = "Stable Ground"
        override val description = "The tunnel is stable. Nothing special happens."
    }
    
    data class FungalBloom(
        val myceliumAmount: Int = 2
    ) : ExplorationEvent() {
        override val name = "Fungal Bloom"
        override val description = "Bioluminescent fungi! Gain $myceliumAmount Mycelium and the tile is illuminated."
    }
    
    // Bad events - hazards
    data object CaveIn : ExplorationEvent() {
        override val name = "Cave-In!"
        override val description = "The tunnel collapses! This tile has rubble and cannot produce until cleared."
    }
    
    data class GasLeak(
        val resourceLost: Resource? = null
    ) : ExplorationEvent() {
        override val name = "Gas Pocket"
        override val description = "Toxic gases! Lose 1 of each resource type you have."
    }
    
    data object MagmaBurst : ExplorationEvent() {
        override val name = "Magma Burst"
        override val description = "Molten rock erupts! This tile becomes impassable Magma."
    }
    
    data class Tremor(
        val affectedStructures: Int = 0
    ) : ExplorationEvent() {
        override val name = "Tremor"
        override val description = "The ground shakes! Skip your next build action."
    }
    
    // Rare events
    data object GeothermalVent : ExplorationEvent() {
        override val name = "Geothermal Vent"
        override val description = "A source of heat and power! This tile produces any resource you choose."
    }
    
    data class LostMiner(
        val bonusExploration: Boolean = true
    ) : ExplorationEvent() {
        override val name = "Lost Miner"
        override val description = "You rescue a lost miner! Gain an extra exploration action."
    }
}

/**
 * Represents a dice roll result for production
 */
data class DiceResult(
    val die1: Int,
    val die2: Int
) {
    val total: Int get() = die1 + die2
    
    val isDoubles: Boolean get() = die1 == die2
    
    /**
     * Get probability dots like Catan (more dots = more common)
     * 7 is most common (6 ways), 2 and 12 are least common (1 way each)
     */
    val probabilityDots: Int get() = when (total) {
        7 -> 6
        6, 8 -> 5
        5, 9 -> 4
        4, 10 -> 3
        3, 11 -> 2
        2, 12 -> 1
        else -> 0
    }
    
    fun getProbabilityDescription(): String = when (probabilityDots) {
        6 -> "Most common!"
        5 -> "Very likely"
        4 -> "Good odds"
        3 -> "Uncommon"
        2 -> "Rare"
        1 -> "Very rare!"
        else -> ""
    }
    
    companion object {
        fun roll(): DiceResult {
            return DiceResult(
                die1 = (1..6).random(),
                die2 = (1..6).random()
            )
        }
    }
}

/**
 * Actions a player can take during their turn
 */
enum class TurnPhase {
    ROLL_DICE,      // Must roll dice at start
    PRODUCTION,     // Resources are distributed based on roll
    MAIN_ACTION,    // Build, explore, or trade
    END_TURN        // Cleanup and pass to next player
}

enum class ActionType {
    ROLL_DICE,
    BUILD_STRUCTURE,
    EXPLORE_TILE,
    CLEAR_RUBBLE,
    TRADE_RESOURCES,
    END_TURN
}

/**
 * Represents a possible action the player can take
 */
sealed class GameAction {
    data object RollDice : GameAction()
    data class Build(val structureType: StructureType, val location: HexCoordinate) : GameAction()
    data class Explore(val location: HexCoordinate) : GameAction()
    data class ClearRubble(val location: HexCoordinate) : GameAction()
    data class Trade(val give: Map<Resource, Int>, val receive: Map<Resource, Int>) : GameAction()
    data object EndTurn : GameAction()
}

/**
 * Choices presented when rolling 7 (or any non-producing roll)
 */
enum class RollConsolation(
    val displayName: String,
    val emoji: String,
    val description: String
) {
    GAIN_RESOURCE(
        displayName = "Scavenge",
        emoji = "🎁",
        description = "Gain 1 random common resource"
    ),
    BONUS_ACTION(
        displayName = "Hustle",
        emoji = "⚡",
        description = "+1 bonus action this turn"
    ),
    DISCOUNT_TRADE(
        displayName = "Barter",
        emoji = "🤝",
        description = "Unlock one 2:1 trade this turn"
    )
}
