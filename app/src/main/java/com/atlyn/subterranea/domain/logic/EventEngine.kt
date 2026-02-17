package com.atlyn.subterranea.domain.logic

import com.atlyn.subterranea.domain.model.*

object EventEngine {

    fun maybeGenerateInteractiveEvent(state: GameState, coord: HexCoordinate): InteractiveEvent? {
        val tile = state.board[coord] ?: return null

        val eventChance = tile.zone.interactiveEventChance
        if (eventChance <= 0) return null

        if ((1..GameConstants.RANDOM_ROLL_MAX).random() > eventChance) return null

        val events = when (tile.presetHint) {
            MapPresetHint.CRYSTAL_RICH -> listOf(
                InteractiveEvent.AncientCache(),
                InteractiveEvent.AncientCache(),
                InteractiveEvent.UnstableGround()
            )
            MapPresetHint.HAZARDOUS -> listOf(
                InteractiveEvent.UnstableGround(),
                InteractiveEvent.BeetleSwarm(),
                InteractiveEvent.UnstableGround()
            )
            MapPresetHint.ORGANIC_RICH -> listOf(
                InteractiveEvent.BeetleSwarm(),
                InteractiveEvent.LostMinerEncounter(),
                InteractiveEvent.BeetleSwarm()
            )
            else -> listOf(
                InteractiveEvent.BeetleSwarm(),
                InteractiveEvent.UnstableGround(),
                InteractiveEvent.AncientCache(),
                InteractiveEvent.LostMinerEncounter()
            )
        }

        return events.random()
    }

    fun resolveInteractiveEvent(
        state: GameState,
        event: InteractiveEvent,
        choiceId: InteractiveChoiceId,
        coord: HexCoordinate
    ): GameState {
        var newState = state.copy(pendingInteractiveEvent = null, pendingEventCoord = null)
        var player = newState.currentPlayer
        val character = newState.selectedCharacter

        when (event) {
            is InteractiveEvent.BeetleSwarm -> {
                when (choiceId) {
                    InteractiveChoiceId.FIGHT -> {
                        val win = (1..GameConstants.RANDOM_ROLL_MAX).random() <= GameConstants.Probabilities.BEETLE_SWARM_WIN_CHANCE
                        if (win) {
                            player = player.addResource(Resource.CHITIN, 3)
                            newState = newState.addEvent("⚔️ Fought off beetles! +3 Chitin")
                        } else {
                            newState = newState.copy(actionsThisTurn = newState.actionsThisTurn + 1)
                                .addEvent("💢 Beetles overwhelmed you! Lost 1 action")
                        }
                    }
                    InteractiveChoiceId.SNEAK -> {
                        newState = newState.addEvent("🤫 Snuck past. Tile marked infested")
                    }
                    InteractiveChoiceId.RETREAT -> {
                        newState = newState.addEvent("🏃 Retreated safely")
                    }
                    else -> Unit
                }
            }

            is InteractiveEvent.UnstableGround -> {
                when (choiceId) {
                    InteractiveChoiceId.CAREFUL -> {
                        newState = newState.copy(actionsThisTurn = newState.actionsThisTurn + 1)
                            .addEvent("🚶 Proceeded carefully. Extra action used")
                    }
                    InteractiveChoiceId.RUSH -> {
                        val caveIn = (1..GameConstants.RANDOM_ROLL_MAX).random() <= GameConstants.Probabilities.UNSTABLE_GROUND_CAVE_IN_CHANCE
                        val resistChance = (character.hazardResistance() * 100).toInt()
                        val resisted = caveIn && (1..GameConstants.RANDOM_ROLL_MAX).random() <= resistChance

                        if (caveIn && !resisted) {
                            val board = newState.board.toMutableMap()
                            board[coord] = board[coord]!!.copy(hasRubble = true)
                            newState = newState.copy(board = board).addEvent("💥 Cave-in! Tile has rubble")
                        } else if (resisted) {
                            newState = newState.addEvent("🛡️ Cave-in blocked by ${character.displayName}'s resistance!")
                        } else {
                            newState = newState.addEvent("🏃 Rushed through safely!")
                        }
                    }
                    InteractiveChoiceId.REINFORCE -> {
                        val cost = mapOf(Resource.BASALT to 2)
                        if (player.canAfford(cost)) {
                            player = player.removeResources(cost)
                            newState = newState.addEvent("🏗️ Reinforced tunnel. Tile is safe")
                        } else {
                            newState = newState.addEvent("❌ Not enough Basalt!")
                        }
                    }
                    else -> Unit
                }
            }

            is InteractiveEvent.AncientCache -> {
                when (choiceId) {
                    InteractiveChoiceId.OPEN -> {
                        val roll = (1..GameConstants.RANDOM_ROLL_MAX).random()
                        when {
                            roll <= 40 -> {
                                val resource = listOf(Resource.CRYSTAL, Resource.IRON_ORE, Resource.CHITIN).random()
                                val amount = (2..4).random()
                                player = player.addResource(resource, amount)
                                newState = newState.addEvent("📦 Cache contained $amount ${resource.displayName()}!")
                            }
                            roll <= 70 -> {
                                player = player.copy(victoryPoints = player.victoryPoints + 1)
                                newState = newState.addEvent("🏆 Ancient artifact found! +1 VP")
                            }
                            else -> {
                                val chitinLoss = minOf(2, player.resources[Resource.CHITIN] ?: 0)
                                if (chitinLoss > 0) {
                                    player = player.removeResources(mapOf(Resource.CHITIN to chitinLoss))
                                }
                                newState = newState.addEvent("⚠️ Trap! Lost $chitinLoss Chitin")
                            }
                        }
                    }
                    InteractiveChoiceId.STUDY -> {
                        newState = newState.addEvent("🔍 Contents look valuable... (can open next turn)")
                    }
                    InteractiveChoiceId.LEAVE -> {
                        newState = newState.addEvent("🚫 Left cache alone")
                    }
                    else -> Unit
                }
            }

            is InteractiveEvent.LostMinerEncounter -> {
                when (choiceId) {
                    InteractiveChoiceId.RESCUE -> {
                        newState = newState.copy(canExploreThisTurn = true, exploresThisTurn = 0)
                            .addEvent("🤝 Rescued miner! They show you a shortcut (extra explore)")
                    }
                    InteractiveChoiceId.TRADE -> {
                        val cost = mapOf(Resource.LICHEN to 2)
                        if (player.canAfford(cost)) {
                            player = player.removeResources(cost)
                            player = player.addResource(Resource.IRON_ORE, 1)
                            newState = newState.addEvent("🔄 Traded 2 Lichen for 1 Iron")
                        } else {
                            newState = newState.addEvent("❌ Not enough Lichen!")
                        }
                    }
                    InteractiveChoiceId.DIRECTIONS -> {
                        val returnsWithGift = (1..GameConstants.RANDOM_ROLL_MAX).random() <= GameConstants.Probabilities.LOST_MINER_GIFT_CHANCE
                        if (returnsWithGift) {
                            player = player.addResource(Resource.CRYSTAL, 1)
                            newState = newState.addEvent("👆 Miner returns with a gift! +1 Crystal")
                        } else {
                            newState = newState.addEvent("👆 Gave directions. Miner thanks you")
                        }
                    }
                    else -> Unit
                }
            }
        }

        return newState.updatePlayer(player)
    }
}
