package com.axialgalileo.subterranea.domain.model

import kotlin.math.abs

/**
 * Represents a Hexagon in an Axial Coordinate system (q, r).
 * s is derived as -q - r.
 * This is effectively a standard Cubic coordinate system where x + y + z = 0
 * q = x, r = z, s = y (convention may vary but this is consistent)
 */
data class HexCoordinate(
    val q: Int,
    val r: Int
) {
    val s: Int get() = -q - r

    operator fun plus(other: HexCoordinate): HexCoordinate {
        return HexCoordinate(q + other.q, r + other.r)
    }

    operator fun minus(other: HexCoordinate): HexCoordinate {
        return HexCoordinate(q - other.q, r - other.r)
    }

    /**
     * Returns the 6 neighbors of this hex.
     */
    fun neighbors(): List<HexCoordinate> {
        return directions.map { this + it }
    }

    /**
     * Distance to another hex (Manhattan distance in cube form)
     */
    fun distanceTo(other: HexCoordinate): Int {
        return (abs(q - other.q) + abs(q + r - other.q - other.r) + abs(r - other.r)) / 2
    }
    
    companion object {
        val directions = listOf(
            HexCoordinate(1, 0), HexCoordinate(1, -1), HexCoordinate(0, -1),
            HexCoordinate(-1, 0), HexCoordinate(-1, 1), HexCoordinate(0, 1)
        )
    }
}
