package com.example.autotapper

import android.content.Context

object AutoTapperConfig {
    const val PREFS_NAME = "autotapper_prefs"

    const val KEY_TAP_X = "tap_x"
    const val KEY_TAP_Y = "tap_y"
    const val KEY_INTERVAL_MS = "interval_ms"
    const val KEY_RANDOM_EXTRA_MS = "random_extra_ms"
    const val KEY_REPEAT_COUNT = "repeat_count"

    const val ACTION_POINT_SELECTED = "com.example.autotapper.action.POINT_SELECTED"
    const val ACTION_START_CLICKING = "com.example.autotapper.action.START_CLICKING"
    const val ACTION_STOP_CLICKING = "com.example.autotapper.action.STOP_CLICKING"
    const val ACTION_SHOW_CONTROLLER = "com.example.autotapper.action.SHOW_CONTROLLER"
    const val ACTION_START_PICKING = "com.example.autotapper.action.START_PICKING"
    const val ACTION_STOP_CONTROLLER = "com.example.autotapper.action.STOP_CONTROLLER"
    const val ACTION_CLICK_STATE_CHANGED = "com.example.autotapper.action.CLICK_STATE_CHANGED"

    const val EXTRA_TAP_X = "extra_tap_x"
    const val EXTRA_TAP_Y = "extra_tap_y"
    const val EXTRA_IS_CLICKING = "extra_is_clicking"
    const val EXTRA_START_PICKING = "extra_start_picking"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
