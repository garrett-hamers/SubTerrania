package com.atlyn.subterranea.domain.model

/**
 * Game difficulty settings that affect various game parameters
 */
enum class Difficulty(
    val displayName: String,
    val description: String,
    val emoji: String
) {
    EASY(
        displayName = "Easy",
        description = "Relaxed pace with bonus resources",
        emoji = "🌱"
    ),
    NORMAL(
        displayName = "Normal", 
        description = "Balanced challenge",
        emoji = "⚖️"
    ),
    HARD(
        displayName = "Hard",
        description = "Scarce resources, more hazards",
        emoji = "🔥"
    ),
    NIGHTMARE(
        displayName = "Nightmare",
        description = "For expert subterranean explorers",
        emoji = "💀"
    );
    
    /**
     * Starting resources for each difficulty
     */
    val startingResources: Map<Resource, Int>
        get() = when (this) {
            EASY -> mapOf(
                Resource.MYCELIUM to 2,
                Resource.BASALT to 2,
                Resource.CHITIN to 2,
                Resource.LICHEN to 2,
                Resource.IRON_ORE to 1,
                Resource.CRYSTAL to 1
            )
            NORMAL -> mapOf(
                Resource.MYCELIUM to 1,
                Resource.BASALT to 1,
                Resource.CHITIN to 1,
                Resource.LICHEN to 1,
                Resource.IRON_ORE to 0,
                Resource.CRYSTAL to 0
            )
            HARD -> mapOf(
                Resource.MYCELIUM to 1,
                Resource.BASALT to 1,
                Resource.CHITIN to 1,
                Resource.LICHEN to 1,
                Resource.IRON_ORE to 0,
                Resource.CRYSTAL to 0
            )
            NIGHTMARE -> mapOf(
                Resource.MYCELIUM to 0,
                Resource.BASALT to 0,
                Resource.CHITIN to 0,
                Resource.LICHEN to 0,
                Resource.IRON_ORE to 0,
                Resource.CRYSTAL to 0
            )
        }
    
    /**
     * Victory points required to win
     */
    val victoryPointsToWin: Int
        get() = when (this) {
            EASY -> 12
            NORMAL -> 14
            HARD -> 18
            NIGHTMARE -> 20
        }
    
    /**
     * Maximum actions allowed per turn
     */
    val maxActionsPerTurn: Int
        get() = when (this) {
            EASY -> 2
            NORMAL -> 2
            HARD -> 2
            NIGHTMARE -> 1
        }
    
    /**
     * How many tiles a Lantern illuminates (radius)
     */
    val lanternRange: Int
        get() = when (this) {
            EASY -> 2
            NORMAL -> 1
            HARD -> 1
            NIGHTMARE -> 1
        }
    
    /**
     * Bonus production on resource tiles (can be negative)
     */
    val productionBonus: Int
        get() = when (this) {
            EASY -> 0
            NORMAL -> 0
            HARD -> 0
            NIGHTMARE -> 0
        }
    
    /**
     * Whether rare resources (Iron, Crystal) get reduced production
     */
    val rareResourcePenalty: Boolean
        get() = when (this) {
            EASY -> false
            NORMAL -> false
            HARD -> false
            NIGHTMARE -> true  // Rare resources produce less
        }
    
    /**
     * Chance of negative exploration events (0.0 - 1.0)
     */
    val hazardChance: Float
        get() = when (this) {
            EASY -> 0.1f     // 10% chance of hazards
            NORMAL -> 0.3f   // 30% chance
            HARD -> 0.5f     // 50% chance
            NIGHTMARE -> 0.7f // 70% chance
        }
    
    /**
     * Building cost multiplier (1.0 = normal)
     */
    val buildCostMultiplier: Float
        get() = when (this) {
            EASY -> 0.75f    // 25% cheaper
            NORMAL -> 1.0f
            HARD -> 1.25f    // 25% more expensive
            NIGHTMARE -> 1.5f // 50% more expensive
        }
    
    /**
     * Whether to show tutorial hints
     */
    val showTutorial: Boolean
        get() = when (this) {
            EASY -> true
            NORMAL -> true
            HARD -> false
            NIGHTMARE -> false
        }
    
    /**
     * Can explore multiple times per turn?
     */
    val multipleExploresPerTurn: Boolean
        get() = when (this) {
            EASY -> false
            NORMAL -> false
            HARD -> false
            NIGHTMARE -> false
        }
    
    /**
     * Trade ratio (resources given for 1 received)
     */
    val tradeRatio: Int
        get() = when (this) {
            EASY -> 2      // 2:1 trades (encourages trading)
            NORMAL -> 3    // 3:1 trades
            HARD -> 4      // 4:1 trades
            NIGHTMARE -> 5 // 5:1 trades
        }
    
    /**
     * Maximum turns before the game is lost
     */
    val maxTurns: Int
        get() = when (this) {
            EASY -> 30
            NORMAL -> 25
            HARD -> 20
            NIGHTMARE -> 18
        }
}
