package com.vahemgeo.app.engine

enum class SpeedMode(val label: String, val speedKmH: Float) {
    WALK("Walk", 5.0f),
    CYCLE("Cycle", 15.0f),
    DRIVE("Drive", 60.0f)
}
