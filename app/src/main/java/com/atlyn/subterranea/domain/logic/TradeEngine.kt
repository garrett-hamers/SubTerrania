package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.GameState
import com.atlyn.subterranea.domain.model.Resource

object TradeEngine {

    fun tradeResources(state: GameState, give: Resource, receive: Resource): GameState {
        if (give == receive) {
            return state.addEvent("❌ Cannot trade same resource!")
        }

        val player = state.currentPlayer
        val useDiscount = state.discountTradeAvailable
        val tradeRatio = if (useDiscount) 2 else state.difficulty.tradeRatio

        if (player.getResourceCount(give) < tradeRatio) {
            return state.addEvent("❌ Need $tradeRatio ${give.displayName()} to trade!")
        }

        val newPlayer = player
            .addResource(give, -tradeRatio)
            .addResource(receive, 1)

        var newState = state.updatePlayer(newPlayer)
            .addEvent("🔄 Traded $tradeRatio ${give.displayName()} for 1 ${receive.displayName()}")

        if (useDiscount) {
            newState = newState.copy(discountTradeAvailable = false)
                .addEvent("🤝 Discount trade used!")
        }

        return newState
    }

    fun canTrade(state: GameState): Boolean {
        val tradeRatio = if (state.discountTradeAvailable) 2 else state.difficulty.tradeRatio
        return state.currentPlayer.resources.any { (_, count) -> count >= tradeRatio }
    }

    fun getTradableResources(state: GameState): List<Resource> {
        val tradeRatio = if (state.discountTradeAvailable) 2 else state.difficulty.tradeRatio
        return state.currentPlayer.resources
            .filter { (_, count) -> count >= tradeRatio }
            .keys.toList()
    }
}
