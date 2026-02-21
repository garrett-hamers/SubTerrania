package com.atlyn.subterranea.domain.telemetry

import com.atlyn.subterranea.domain.model.GameState
import com.atlyn.subterranea.domain.model.HexCoordinate
import com.atlyn.subterranea.domain.model.Resource
import java.util.Locale

object GameTelemetry {
    private const val TAG = "GameTelemetry"
    @Volatile
    private var androidLoggingEnabled = true
    private val androidDebugLogMethod by lazy {
        try {
            Class.forName("android.util.Log").getMethod(
                "d",
                String::class.java,
                String::class.java
            )
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    enum class Outcome {
        ATTEMPT,
        SUCCESS,
        REJECTED
    }

    fun logState(
        event: String,
        state: GameState,
        outcome: Outcome? = null,
        reasonCode: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        val payload = linkedMapOf<String, Any?>(
            "event" to event,
            "ts" to System.currentTimeMillis(),
            "turn" to state.turnNumber,
            "phase" to state.turnPhase.name,
            "playerId" to state.currentPlayer.id,
            "actionIndex" to state.actionsThisTurn,
            "maxActions" to state.maxActionsPerTurn,
            "resources" to resourceSnapshot(state),
            "vp" to state.totalVPFor(state.currentPlayer),
            "flags" to stateFlags(state)
        )
        if (outcome != null) payload["outcome"] = outcome.name.lowercase(Locale.US)
        if (reasonCode != null) payload["reasonCode"] = reasonCode
        if (details.isNotEmpty()) payload["details"] = details

        writeLog(encodeJson(payload))
    }

    fun logTransition(
        event: String,
        before: GameState,
        after: GameState,
        outcome: Outcome,
        reasonCode: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        val beforeResources = resourceSnapshot(before)
        val afterResources = resourceSnapshot(after)
        val resourceDelta = afterResources
            .mapValues { (name, amount) -> amount - (beforeResources[name] ?: 0) }
            .filterValues { it != 0 }

        val vpBefore = before.totalVPFor(before.currentPlayer)
        val vpAfter = after.totalVPFor(after.currentPlayer)

        val payload = linkedMapOf<String, Any?>(
            "event" to event,
            "ts" to System.currentTimeMillis(),
            "turn" to after.turnNumber,
            "phase" to after.turnPhase.name,
            "playerId" to after.currentPlayer.id,
            "actionIndex" to after.actionsThisTurn,
            "maxActions" to after.maxActionsPerTurn,
            "outcome" to outcome.name.lowercase(Locale.US),
            "reasonCode" to reasonCode,
            "resourcesBefore" to beforeResources,
            "resourcesAfter" to afterResources,
            "resourcesDelta" to resourceDelta,
            "vpBefore" to vpBefore,
            "vpAfter" to vpAfter,
            "vpDelta" to (vpAfter - vpBefore),
            "flags" to stateFlags(after)
        )
        if (details.isNotEmpty()) payload["details"] = details

        writeLog(encodeJson(payload))
    }

    // --- Fun-factor telemetry events ---

    fun logFunDeadRoll(state: GameState, rollTotal: Int) {
        logState("fun_dead_roll", state, details = mapOf("roll" to rollTotal))
    }

    fun logFunBuildFrustration(state: GameState, structureType: String, deficit: Map<String, Int>) {
        logState("fun_build_frustration", state, details = mapOf(
            "structureType" to structureType,
            "deficit" to deficit
        ))
    }

    fun logFunStuckTurn(state: GameState, consecutiveStuckTurns: Int) {
        logState("fun_stuck_turn", state, details = mapOf(
            "consecutiveStuckTurns" to consecutiveStuckTurns
        ))
    }

    fun logFunAhaMoment(state: GameState, vpBefore: Int, vpAfter: Int) {
        logState("fun_aha_moment", state, details = mapOf(
            "vpBefore" to vpBefore,
            "vpAfter" to vpAfter,
            "vpJump" to (vpAfter - vpBefore)
        ))
    }

    fun logFunExplorationReward(state: GameState, rewardType: String, value: Int) {
        logState("fun_exploration_reward", state, details = mapOf(
            "rewardType" to rewardType,
            "value" to value
        ))
    }

    fun logFunAgencySnapshot(state: GameState, availableCategories: List<String>) {
        logState("fun_agency_snapshot", state, details = mapOf(
            "availableCategories" to availableCategories,
            "categoryCount" to availableCategories.size
        ))
    }

    fun coordinatePayload(coord: HexCoordinate?): Map<String, Int>? {
        return coord?.let { mapOf("q" to it.q, "r" to it.r) }
    }

    fun eventMessage(state: GameState): String? = state.eventLog.firstOrNull()

    fun isRejectedMessage(message: String?): Boolean = message?.startsWith("❌") == true

    fun reasonCodeFromEventMessage(message: String?): String? {
        if (message.isNullOrBlank()) return null
        if (!message.startsWith("❌")) return null
        val normalized = message.lowercase(Locale.US)
        return when {
            "cannot trade same resource" in normalized -> "same_resource_trade"
            "to trade" in normalized -> "insufficient_trade_resources"
            "invalid location" in normalized -> "invalid_location"
            "must reveal tile first" in normalized -> "tile_unrevealed"
            "cannot build on" in normalized -> "invalid_terrain"
            "already has a structure" in normalized -> "occupied_tile"
            "requires an outpost" in normalized -> "missing_outpost_prerequisite"
            "cannot afford" in normalized -> "cannot_afford"
            "no rubble to clear" in normalized -> "no_rubble"
            "clear rubble" in normalized && "need" in normalized -> "cannot_afford"
            "no structure at this location" in normalized -> "no_structure"
            "no active ability" in normalized -> "no_active_ability"
            "cooldown" in normalized -> "cooldown_active"
            "tile already revealed" in normalized -> "tile_already_revealed"
            "already explored" in normalized -> "explore_cap_reached"
            "must explore adjacent" in normalized -> "not_adjacent_to_revealed"
            "not enough" in normalized -> "cannot_afford"
            else -> "rules_rejected"
        }
    }

    private fun resourceSnapshot(state: GameState): Map<String, Int> {
        val player = state.currentPlayer
        return Resource.entries.associate { resource ->
            resource.name.lowercase(Locale.US) to player.getResourceCount(resource)
        }
    }

    private fun stateFlags(state: GameState): Map<String, Any> {
        return linkedMapOf(
            "canExploreThisTurn" to state.canExploreThisTurn,
            "pendingConsolation" to state.pendingConsolation,
            "discountTradeAvailable" to state.discountTradeAvailable,
            "gameOver" to state.gameOver
        )
    }

    private fun encodeJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number, is Boolean -> value.toString()
            is Enum<*> -> "\"${escape(value.name)}\""
            is Map<*, *> -> value.entries.joinToString(
                prefix = "{",
                postfix = "}"
            ) { (key, entryValue) ->
                "\"${escape(key.toString())}\":${encodeJson(entryValue)}"
            }
            is Iterable<*> -> value.joinToString(
                prefix = "[",
                postfix = "]"
            ) { item ->
                encodeJson(item)
            }
            else -> "\"${escape(value.toString())}\""
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun writeLog(message: String) {
        val debugMethod = androidDebugLogMethod
        if (debugMethod != null && androidLoggingEnabled) {
            try {
                debugMethod.invoke(null, TAG, message)
                return
            } catch (_: ReflectiveOperationException) {
                androidLoggingEnabled = false
                // Fall through to stdout in test/runtime environments without Android logging.
            } catch (_: IllegalArgumentException) {
                androidLoggingEnabled = false
                // Fall through to stdout in test/runtime environments without Android logging.
            }
        }
        println("$TAG: $message")
    }
}
