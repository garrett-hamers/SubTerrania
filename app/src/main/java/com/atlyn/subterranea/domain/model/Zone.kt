package com.atlyn.subterranea.domain.model

enum class Zone(val index: Int) {
    SURFACE(0), // The safe center
    CRUST(1),   // The shallow dig
    MANTLE(2),  // The danger zone
    CORE(3)     // The objective
}
