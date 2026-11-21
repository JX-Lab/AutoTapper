package com.example.autotapper

data class TapPoint(
    val x: Int,
    val y: Int,
    /** 0 means use the global interval. */
    override val delayMs: Long = 0L,
    override val repeatCount: Int = 1,
    val holdDurationMs: Long = DEFAULT_HOLD_DURATION_MS
) : AutomationAction {
    companion object {
        const val DEFAULT_HOLD_DURATION_MS = 40L
    }
}
