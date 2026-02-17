package com.atlyn.subterranea.domain.model

object GameConstants {
    const val LUCKY_ROLL = 7
    const val LANTERN_BONUS_THRESHOLD = 4
    const val EVENT_LOG_MAX = 20
    const val RANDOM_ROLL_MAX = 100

    val RUBBLE_CLEAR_COST = mapOf(
        Resource.IRON_ORE to 1,
        Resource.BASALT to 1
    )

    object Probabilities {
        const val MANTLE_INTERACTIVE_EVENT_CHANCE = 25
        const val CORE_INTERACTIVE_EVENT_CHANCE = 40
        const val BEETLE_SWARM_WIN_CHANCE = 50
        const val UNSTABLE_GROUND_CAVE_IN_CHANCE = 30
        const val LOST_MINER_GIFT_CHANCE = 25
    }
}
