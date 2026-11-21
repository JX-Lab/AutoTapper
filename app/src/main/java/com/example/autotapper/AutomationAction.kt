package com.example.autotapper

sealed interface AutomationAction {
    val delayMs: Long
    val repeatCount: Int
}

data class GesturePoint(
    val x: Int,
    val y: Int,
    val timeOffsetMs: Long
)

data class GestureStroke(
    val points: List<GesturePoint>,
    val startOffsetMs: Long = 0L,
    val durationMs: Long = DEFAULT_DURATION_MS
) {
    companion object {
        const val DEFAULT_DURATION_MS = 300L
    }
}

data class GestureAction(
    val strokes: List<GestureStroke>,
    override val delayMs: Long = 0L,
    override val repeatCount: Int = 1
) : AutomationAction {
    val primaryStroke: GestureStroke?
        get() = strokes.firstOrNull()
}
