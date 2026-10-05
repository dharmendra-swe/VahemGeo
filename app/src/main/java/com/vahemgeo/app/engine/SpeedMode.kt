package com.vahemgeo.app.engine

enum class SpeedMode(val label: String, val speedKmH: Float, val icon: String) {
    WALK("Walk", 5.0f, "🚶"),
    CYCLE("Cycle", 15.0f, "🚲"),
    DRIVE("Drive", 60.0f, "🚗")
}
