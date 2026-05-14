package com.atlyn.subterranea.domain.model

/**
 * Meta-progression system - tracks lifetime achievements and unlocks
 */
data class MetaProgression(
    val lifetimeAchievements: Set<Achievement> = emptySet(),
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val totalVPEarned: Int = 0,
    val selectedCharacter: GameCharacter = GameCharacter.EXPLORER,
    val unlockedCharacters: Set<GameCharacter> = setOf(GameCharacter.EXPLORER),
    val fastModeEnabled: Boolean = false,
    val selectedMapPreset: MapPreset = MapPreset.STANDARD,
    // Phase O-1: tracks whether the new-player coachmark tutorial has been
    // shown. Persisted via the same SharedPreferences as the rest of
    // meta-progression so it survives reinstalls (when Android backup is on).
    val tutorialSeen: Boolean = false
) {
    /**
     * Get starting bonuses based on lifetime achievements
     */
    fun getStartingBonuses(): StartingBonuses {
        var bonusCrystal = 0
        var bonusIron = 0
        var bonusExploreRange = 0
        var structureDiscount = 0
        var bonusActions = 0
        
        if (Achievement.CRYSTAL_BARON in lifetimeAchievements) {
            bonusCrystal += 1
        }
        if (Achievement.DEEP_DELVER in lifetimeAchievements) {
            bonusExploreRange += 1 // Can see further with first lantern
        }
        if (Achievement.MASTER_BUILDER in lifetimeAchievements) {
            structureDiscount += 1 // First structure costs 1 less resource
        }
        if (Achievement.FIRST_EXPLORER in lifetimeAchievements) {
            bonusIron += 1
        }
        if (Achievement.CORE_SEEKER in lifetimeAchievements) {
            bonusActions += 1 // Extra action on turn 1 only
        }
        
        return StartingBonuses(
            bonusCrystal = bonusCrystal,
            bonusIron = bonusIron,
            bonusExploreRange = bonusExploreRange,
            structureDiscount = structureDiscount,
            bonusActionsFirstTurn = bonusActions
        )
    }
    
    fun unlockCharacter(character: GameCharacter): MetaProgression {
        return copy(unlockedCharacters = unlockedCharacters + character)
    }
    
    fun addAchievement(achievement: Achievement): MetaProgression {
        return copy(lifetimeAchievements = lifetimeAchievements + achievement)
    }
    
    fun recordGameEnd(won: Boolean, vpEarned: Int, achievements: Set<Achievement>): MetaProgression {
        return copy(
            gamesPlayed = gamesPlayed + 1,
            gamesWon = if (won) gamesWon + 1 else gamesWon,
            totalVPEarned = totalVPEarned + vpEarned,
            lifetimeAchievements = lifetimeAchievements + achievements
        )
    }
}

/**
 * Starting bonuses from meta-progression
 */
data class StartingBonuses(
    val bonusCrystal: Int = 0,
    val bonusIron: Int = 0,
    val bonusExploreRange: Int = 0,
    val structureDiscount: Int = 0,
    val bonusActionsFirstTurn: Int = 0
)

/**
 * Playable characters with unique abilities
 */
enum class GameCharacter(
    val displayName: String,
    val emoji: String,
    val description: String,
    val unlockCondition: String
) {
    EXPLORER(
        displayName = "The Explorer",
        emoji = "🧭",
        description = "Balanced stats, no special abilities",
        unlockCondition = "Default character"
    ),
    PROSPECTOR(
        displayName = "The Prospector",
        emoji = "⛏️",
        description = "Starts with +1 Iron and +1 Crystal",
        unlockCondition = "Win a game on Normal difficulty"
    ),
    SCOUT(
        displayName = "The Scout", 
        emoji = "🔦",
        description = "Can explore twice per turn, but 1 fewer action",
        unlockCondition = "Explore 50 tiles total"
    ),
    ENGINEER(
        displayName = "The Engineer",
        emoji = "🔧",
        description = "Structures cost 1 less rare resource",
        unlockCondition = "Build 20 structures total"
    ),
    SURVIVOR(
        displayName = "The Survivor",
        emoji = "🛡️",
        description = "50% chance to ignore hazard events",
        unlockCondition = "Win a game on Hard difficulty"
    );
    
    /**
     * Apply character bonuses to starting resources
     */
    fun applyToResources(resources: Map<Resource, Int>): Map<Resource, Int> {
        val mutable = resources.toMutableMap()
        when (this) {
            PROSPECTOR -> {
                mutable[Resource.IRON_ORE] = (mutable[Resource.IRON_ORE] ?: 0) + 1
                mutable[Resource.CRYSTAL] = (mutable[Resource.CRYSTAL] ?: 0) + 1
            }
            else -> { /* No resource modifications */ }
        }
        return mutable
    }
    
    /**
     * Modify max actions per turn
     */
    fun modifyMaxActions(baseActions: Int): Int {
        return when (this) {
            SCOUT -> maxOf(1, baseActions - 1)
            else -> baseActions
        }
    }
    
    /**
     * Can explore multiple times per turn?
     */
    fun canExploreMultiple(): Boolean {
        return this == SCOUT
    }
    
    /**
     * Chance to ignore hazards (0.0 - 1.0)
     */
    fun hazardResistance(): Float {
        return when (this) {
            SURVIVOR -> 0.5f
            else -> 0f
        }
    }
    
    /**
     * Rare resource discount for structures
     */
    fun structureRareDiscount(): Int {
        return when (this) {
            ENGINEER -> 1
            else -> 0
        }
    }
}

/**
 * Map presets for variety
 */
enum class MapPreset(
    val displayName: String,
    val emoji: String,
    val description: String
) {
    STANDARD(
        displayName = "Standard",
        emoji = "🗺️",
        description = "Balanced map with all resource types"
    ),
    CRYSTAL_CAVES(
        displayName = "Crystal Caves",
        emoji = "💎",
        description = "More Crystal Grottos, fewer Iron Veins"
    ),
    IRON_DEPTHS(
        displayName = "Iron Depths",
        emoji = "⚙️",
        description = "More Iron Veins, fewer Crystals"
    ),
    FUNGAL_JUNGLE(
        displayName = "Fungal Jungle",
        emoji = "🍄",
        description = "Abundant Mycelium and Lichen, rare metals"
    ),
    VOLCANIC_CORE(
        displayName = "Volcanic Core",
        emoji = "🌋",
        description = "More hazards, but richer rewards in deep zones"
    ),
    DAILY_CHALLENGE(
        displayName = "Daily Challenge",
        emoji = "📅",
        description = "Fixed seed based on today's date"
    )
}

/**
 * Exploration event choices for interactive events
 */
enum class InteractiveChoiceId(val wireId: String) {
    FIGHT("fight"),
    SNEAK("sneak"),
    RETREAT("retreat"),
    CAREFUL("careful"),
    RUSH("rush"),
    REINFORCE("reinforce"),
    OPEN("open"),
    STUDY("study"),
    LEAVE("leave"),
    RESCUE("rescue"),
    TRADE("trade"),
    DIRECTIONS("directions");

    companion object {
        fun fromWireId(wireId: String): InteractiveChoiceId? {
            return entries.find { it.wireId == wireId }
        }
    }
}

data class EventChoice(
    val id: InteractiveChoiceId,
    val displayText: String,
    val emoji: String,
    val description: String
)

/**
 * Events that offer player choices
 */
sealed class InteractiveEvent {
    abstract val title: String
    abstract val description: String
    abstract val choices: List<EventChoice>
    
    data class BeetleSwarm(
        override val title: String = "Beetle Swarm Detected!",
        override val description: String = "A swarm of cave beetles blocks your path.",
        override val choices: List<EventChoice> = listOf(
            EventChoice(InteractiveChoiceId.FIGHT, "Fight", "⚔️", "50% chance: gain 3 Chitin, or lose 1 action"),
            EventChoice(InteractiveChoiceId.SNEAK, "Sneak Past", "🤫", "Safe, but tile marked as Infested"),
            EventChoice(InteractiveChoiceId.RETREAT, "Retreat", "🏃", "Keep your action, don't explore")
        )
    ) : InteractiveEvent()
    
    data class UnstableGround(
        override val title: String = "Unstable Ground!",
        override val description: String = "The floor here looks dangerous.",
        override val choices: List<EventChoice> = listOf(
            EventChoice(InteractiveChoiceId.CAREFUL, "Proceed Carefully", "🚶", "Explore safely but costs 2 actions"),
            EventChoice(InteractiveChoiceId.RUSH, "Rush Through", "🏃", "Normal explore, 30% chance of cave-in"),
            EventChoice(InteractiveChoiceId.REINFORCE, "Reinforce", "🏗️", "Spend 2 Basalt to make tile safe")
        )
    ) : InteractiveEvent()
    
    data class AncientCache(
        override val title: String = "Ancient Cache Found!",
        override val description: String = "You discover a sealed container.",
        override val choices: List<EventChoice> = listOf(
            EventChoice(InteractiveChoiceId.OPEN, "Open It", "📦", "Random reward: resources, artifact, or trap"),
            EventChoice(InteractiveChoiceId.STUDY, "Study First", "🔍", "Learn contents, can still open next turn"),
            EventChoice(InteractiveChoiceId.LEAVE, "Leave It", "🚫", "Play it safe, no reward")
        )
    ) : InteractiveEvent()
    
    data class LostMinerEncounter(
        override val title: String = "Lost Miner Found!",
        override val description: String = "A stranded miner needs help.",
        override val choices: List<EventChoice> = listOf(
            EventChoice(InteractiveChoiceId.RESCUE, "Rescue", "🤝", "Gain extra exploration this turn"),
            EventChoice(InteractiveChoiceId.TRADE, "Trade Supplies", "🔄", "Give 2 Lichen, gain 1 Iron"),
            EventChoice(InteractiveChoiceId.DIRECTIONS, "Give Directions", "👆", "They leave, 25% chance they return with gift")
        )
    ) : InteractiveEvent()
}
