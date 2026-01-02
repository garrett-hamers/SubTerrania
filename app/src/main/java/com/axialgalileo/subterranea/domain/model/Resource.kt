package com.axialgalileo.subterranea.domain.model

enum class Resource {
    MYCELIUM, // Wood
    BASALT,   // Brick
    CHITIN,   // Wool
    LICHEN,   // Grain/Wheat
    IRON_ORE, // Ore
    CRYSTAL;  // Gold/Wildcard

    fun displayName(): String {
        return when(this) {
            MYCELIUM -> "Mycelium Stalks"
            BASALT -> "Basalt Blocks"
            CHITIN -> "Beetle Chitin/Tallow"
            LICHEN -> "Cave Lichen"
            IRON_ORE -> "Iron Ore"
            CRYSTAL -> "Luminary Crystal"
        }
    }
}
