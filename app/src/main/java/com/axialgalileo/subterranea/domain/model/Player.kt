package com.axialgalileo.subterranea.domain.model

data class Player(
    val id: Int,
    val name: String,
    val resources: Map<Resource, Int> = Resource.entries.associateWith { 0 },
    val victoryPoints: Int = 0,
    val structuresBuilt: List<Structure> = emptyList(),
    val explorationCount: Int = 0,
    val achievements: Set<Achievement> = emptySet()
) {
    fun getResourceCount(resource: Resource): Int = resources[resource] ?: 0
    
    fun canAfford(cost: Map<Resource, Int>): Boolean {
        return cost.all { (resource, amount) -> getResourceCount(resource) >= amount }
    }
    
    fun withResources(newResources: Map<Resource, Int>): Player {
        return copy(resources = newResources)
    }
    
    fun addResource(resource: Resource, amount: Int): Player {
        val current = resources.toMutableMap()
        current[resource] = (current[resource] ?: 0) + amount
        return copy(resources = current)
    }
    
    fun removeResources(cost: Map<Resource, Int>): Player {
        val current = resources.toMutableMap()
        cost.forEach { (resource, amount) ->
            current[resource] = (current[resource] ?: 0) - amount
        }
        return copy(resources = current)
    }
    
    fun calculateVictoryPoints(): Int {
        var vp = 0
        
        // Points from structures
        structuresBuilt.forEach { structure ->
            vp += structure.type.victoryPoints
        }
        
        // Points from achievements
        achievements.forEach { achievement ->
            vp += achievement.victoryPoints
        }
        
        return vp
    }
}

/**
 * Structures that can be built on tiles
 */
data class Structure(
    val type: StructureType,
    val location: HexCoordinate,
    val ownerId: Int
)

enum class StructureType(
    val displayName: String,
    val cost: Map<Resource, Int>,
    val victoryPoints: Int,
    val description: String
) {
    LANTERN(
        displayName = "Lantern Post",
        cost = mapOf(Resource.CRYSTAL to 1, Resource.IRON_ORE to 1),
        victoryPoints = 0,
        description = "Illuminates adjacent tiles, required for production"
    ),
    OUTPOST(
        displayName = "Mining Outpost",
        cost = mapOf(Resource.BASALT to 2, Resource.MYCELIUM to 1, Resource.CHITIN to 1),
        victoryPoints = 1,
        description = "Claim a tile for resource production"
    ),
    EXCAVATOR(
        displayName = "Deep Excavator",
        cost = mapOf(Resource.IRON_ORE to 3, Resource.BASALT to 2),
        victoryPoints = 2,
        description = "Upgraded outpost with double production"
    ),
    FUNGAL_FARM(
        displayName = "Fungal Farm",
        cost = mapOf(Resource.MYCELIUM to 2, Resource.LICHEN to 2),
        victoryPoints = 1,
        description = "Produces extra Mycelium each turn"
    ),
    BEETLE_STABLE(
        displayName = "Beetle Stable",
        cost = mapOf(Resource.CHITIN to 2, Resource.LICHEN to 3),
        victoryPoints = 1,
        description = "Beetles help transport resources"
    ),
    CRYSTAL_REFINERY(
        displayName = "Crystal Refinery",
        cost = mapOf(Resource.CRYSTAL to 2, Resource.IRON_ORE to 2, Resource.BASALT to 1),
        victoryPoints = 2,
        description = "Doubles Crystal production"
    ),
    CORE_ANCHOR(
        displayName = "Core Anchor",
        cost = mapOf(
            Resource.CRYSTAL to 3, 
            Resource.IRON_ORE to 3, 
            Resource.BASALT to 2,
            Resource.MYCELIUM to 2
        ),
        victoryPoints = 4,
        description = "Ultimate structure - anchor to the planet's core"
    )
}

/**
 * Achievements that award bonus victory points
 */
enum class Achievement(
    val displayName: String,
    val description: String,
    val victoryPoints: Int
) {
    FIRST_EXPLORER(
        displayName = "First Explorer",
        description = "First to reveal a tile in the Mantle zone",
        victoryPoints = 2
    ),
    CORE_SEEKER(
        displayName = "Core Seeker", 
        description = "First to reveal a tile in the Core zone",
        victoryPoints = 3
    ),
    MASTER_BUILDER(
        displayName = "Master Builder",
        description = "Build 5 structures",
        victoryPoints = 2
    ),
    CRYSTAL_BARON(
        displayName = "Crystal Baron",
        description = "Collect 10 crystals total",
        victoryPoints = 2
    ),
    DEEP_DELVER(
        displayName = "Deep Delver",
        description = "Reveal 10 tiles",
        victoryPoints = 2
    ),
    ILLUMINATOR(
        displayName = "The Illuminator",
        description = "Build 3 Lantern Posts",
        victoryPoints = 1
    )
}
