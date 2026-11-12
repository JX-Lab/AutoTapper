package com.example.autotapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AutoTapperConfig {
    const val PREFS_NAME = "autotapper_prefs"

    const val KEY_TAP_POINTS = "tap_points"
    const val KEY_INTERVAL_MS = "interval_ms"
    const val KEY_RANDOM_EXTRA_MS = "random_extra_ms"
    const val KEY_REPEAT_COUNT = "repeat_count"
    const val KEY_CONTROLLER_ALPHA = "controller_alpha"

    // 更新检查：用户主动忽略的版本、最近一次已提醒过的版本
    const val KEY_UPDATE_IGNORED_VERSION = "update_ignored_version"
    const val KEY_UPDATE_NOTIFIED_VERSION = "update_notified_version"

    // 旧版单点字段，仅用于一次性迁移
    const val KEY_TAP_X = "tap_x"
    const val KEY_TAP_Y = "tap_y"

    const val ACTION_POINT_SELECTED = "com.example.autotapper.action.POINT_SELECTED"
    const val ACTION_START_CLICKING = "com.example.autotapper.action.START_CLICKING"
    const val ACTION_STOP_CLICKING = "com.example.autotapper.action.STOP_CLICKING"
    const val ACTION_SHOW_CONTROLLER = "com.example.autotapper.action.SHOW_CONTROLLER"
    const val ACTION_START_PICKING = "com.example.autotapper.action.START_PICKING"
    const val ACTION_STOP_CONTROLLER = "com.example.autotapper.action.STOP_CONTROLLER"
    const val ACTION_CLICK_STATE_CHANGED = "com.example.autotapper.action.CLICK_STATE_CHANGED"
    const val ACTION_CONTROLLER_ALPHA_CHANGED = "com.example.autotapper.action.CONTROLLER_ALPHA_CHANGED"

    const val EXTRA_TAP_X = "extra_tap_x"
    const val EXTRA_TAP_Y = "extra_tap_y"
    const val EXTRA_IS_CLICKING = "extra_is_clicking"
    const val EXTRA_START_PICKING = "extra_start_picking"
    const val EXTRA_CURRENT_POINT_INDEX = "extra_current_point_index"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPoints(context: Context): List<TapPoint> {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_TAP_POINTS, null)
        if (raw != null) {
            return parsePoints(raw)
        }

        // 一次性迁移：把旧版单点坐标转成点位列表
        val legacyX = prefs.getInt(KEY_TAP_X, -1)
        val legacyY = prefs.getInt(KEY_TAP_Y, -1)
        if (legacyX >= 0 && legacyY >= 0) {
            val points = listOf(TapPoint(legacyX, legacyY))
            savePoints(context, points)
            return points
        }
        return emptyList()
    }

    fun savePoints(context: Context, points: List<TapPoint>) {
        prefs(context).edit().putString(KEY_TAP_POINTS, toJson(points)).apply()
    }

    fun addPoint(context: Context, x: Int, y: Int): List<TapPoint> {
        val points = loadPoints(context) + TapPoint(x, y)
        savePoints(context, points)
        return points
    }

    fun removeLastPoint(context: Context): List<TapPoint> {
        val points = loadPoints(context)
        val updated = if (points.isEmpty()) points else points.dropLast(1)
        savePoints(context, updated)
        return updated
    }

    fun removePointAt(context: Context, index: Int): List<TapPoint> {
        val points = loadPoints(context)
        val updated = if (index in points.indices) {
            points.toMutableList().apply { removeAt(index) }
        } else {
            points
        }
        savePoints(context, updated)
        return updated
    }

    fun hasPoints(context: Context): Boolean = loadPoints(context).isNotEmpty()

    fun saveControllerAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_CONTROLLER_ALPHA, alpha.coerceIn(0.3f, 1f)).apply()
    }

    fun getControllerAlpha(context: Context): Float {
        return prefs(context).getFloat(KEY_CONTROLLER_ALPHA, 0.9f).coerceIn(0.3f, 1f)
    }

    private fun toJson(points: List<TapPoint>): String =
        JSONArray().apply {
            points.forEach { point ->
                put(JSONObject().put("x", point.x).put("y", point.y))
            }
        }.toString()

    private fun parsePoints(raw: String): List<TapPoint> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val x = obj.optInt("x", -1)
                val y = obj.optInt("y", -1)
                if (x >= 0 && y >= 0) {
                    add(TapPoint(x, y))
                }
            }
        }
    }.getOrDefault(emptyList())
}
