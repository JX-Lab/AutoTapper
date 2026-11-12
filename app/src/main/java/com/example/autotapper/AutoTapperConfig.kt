package com.example.autotapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AutoTapperConfig {
    const val PREFS_NAME = "autotapper_prefs"

    const val KEY_TAP_POINTS = "tap_points"
    const val KEY_INTERVAL_MS = "interval_ms"
    const val KEY_RANDOM_EXTRA_MS = "random_extra_ms"
    const val KEY_REPEAT_COUNT = "repeat_count"
    const val KEY_CONTROLLER_ALPHA = "controller_alpha"
    const val KEY_PROFILES = "automation_profiles"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

    const val KEY_UPDATE_IGNORED_VERSION = "update_ignored_version"
    const val KEY_UPDATE_NOTIFIED_VERSION = "update_notified_version"

    // Legacy single-point fields, retained only for one-time migration.
    const val KEY_TAP_X = "tap_x"
    const val KEY_TAP_Y = "tap_y"

    const val ACTION_POINT_SELECTED = "com.example.autotapper.action.POINT_SELECTED"
    const val ACTION_START_CLICKING = "com.example.autotapper.action.START_CLICKING"
    const val ACTION_PAUSE_CLICKING = "com.example.autotapper.action.PAUSE_CLICKING"
    const val ACTION_RESUME_CLICKING = "com.example.autotapper.action.RESUME_CLICKING"
    const val ACTION_STOP_CLICKING = "com.example.autotapper.action.STOP_CLICKING"
    const val ACTION_SHOW_CONTROLLER = "com.example.autotapper.action.SHOW_CONTROLLER"
    const val ACTION_START_PICKING = "com.example.autotapper.action.START_PICKING"
    const val ACTION_STOP_CONTROLLER = "com.example.autotapper.action.STOP_CONTROLLER"
    const val ACTION_CLICK_STATE_CHANGED = "com.example.autotapper.action.CLICK_STATE_CHANGED"
    const val ACTION_CONTROLLER_ALPHA_CHANGED = "com.example.autotapper.action.CONTROLLER_ALPHA_CHANGED"

    const val EXTRA_TAP_X = "extra_tap_x"
    const val EXTRA_TAP_Y = "extra_tap_y"
    const val EXTRA_IS_CLICKING = "extra_is_clicking"
    const val EXTRA_IS_PAUSED = "extra_is_paused"
    const val EXTRA_START_PICKING = "extra_start_picking"
    const val EXTRA_CURRENT_POINT_INDEX = "extra_current_point_index"
    const val EXTRA_ROUNDS_COMPLETED = "extra_rounds_completed"
    const val EXTRA_POINT_REPEAT_PROGRESS = "extra_point_repeat_progress"
    const val EXTRA_PENDING_ACTION = "extra_pending_action"

    const val PENDING_NONE = 0
    const val PENDING_PAUSE = 1
    const val PENDING_STOP = 2

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPoints(context: Context): List<TapPoint> {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_TAP_POINTS, null)
        if (raw != null) {
            return parsePoints(raw)
        }

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
        prefs(context).edit().putString(KEY_TAP_POINTS, pointsToJson(points).toString()).apply()
    }

    fun addPoint(context: Context, x: Int, y: Int): List<TapPoint> {
        val points = loadPoints(context) + TapPoint(x, y)
        savePoints(context, points)
        return points
    }

    fun updatePointAt(context: Context, index: Int, point: TapPoint): List<TapPoint> {
        val points = loadPoints(context)
        if (index !in points.indices) return points
        val updated = points.toMutableList().apply { this[index] = point }
        savePoints(context, updated)
        return updated
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

    fun saveBaseConfig(
        context: Context,
        intervalMs: Long,
        randomExtraMs: Long,
        repeatCount: Int
    ) {
        prefs(context).edit()
            .putLong(KEY_INTERVAL_MS, intervalMs.coerceAtLeast(100L))
            .putLong(KEY_RANDOM_EXTRA_MS, randomExtraMs.coerceAtLeast(0L))
            .putInt(KEY_REPEAT_COUNT, repeatCount.coerceAtLeast(0))
            .apply()
    }

    fun saveControllerAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_CONTROLLER_ALPHA, alpha.coerceIn(0.3f, 1f)).apply()
    }

    fun getControllerAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_CONTROLLER_ALPHA, 0.9f).coerceIn(0.3f, 1f)

    fun loadProfiles(context: Context): List<AutomationProfile> {
        val raw = prefs(context).getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    profileFromJson(obj)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getActiveProfile(context: Context): AutomationProfile? {
        val id = prefs(context).getString(KEY_ACTIVE_PROFILE_ID, null) ?: return null
        return loadProfiles(context).firstOrNull { it.id == id }
    }

    fun isActiveProfileModified(context: Context): Boolean {
        val active = getActiveProfile(context) ?: return false
        val currentPrefs = prefs(context)
        return active.points != loadPoints(context) ||
            active.intervalMs != currentPrefs.getLong(KEY_INTERVAL_MS, 400L).coerceAtLeast(100L) ||
            active.randomExtraMs != currentPrefs.getLong(
                KEY_RANDOM_EXTRA_MS,
                80L
            ).coerceAtLeast(0L) ||
            active.repeatCount != currentPrefs.getInt(KEY_REPEAT_COUNT, 0).coerceAtLeast(0)
    }

    fun saveCurrentProfile(context: Context, requestedName: String): AutomationProfile {
        val name = requestedName.trim().take(MAX_PROFILE_NAME_LENGTH).ifBlank { "Profile" }
        val profiles = loadProfiles(context).toMutableList()
        val existingIndex = profiles.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val existingId = profiles.getOrNull(existingIndex)?.id
        val currentPrefs = prefs(context)
        val profile = AutomationProfile(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name,
            points = loadPoints(context),
            intervalMs = currentPrefs.getLong(KEY_INTERVAL_MS, 400L).coerceAtLeast(100L),
            randomExtraMs = currentPrefs.getLong(KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L),
            repeatCount = currentPrefs.getInt(KEY_REPEAT_COUNT, 0).coerceAtLeast(0)
        )
        if (existingIndex >= 0) profiles[existingIndex] = profile else profiles.add(profile)
        saveProfiles(context, profiles)
        currentPrefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profile.id).apply()
        return profile
    }

    fun activateProfile(context: Context, profileId: String): AutomationProfile? {
        val profile = loadProfiles(context).firstOrNull { it.id == profileId } ?: return null
        savePoints(context, profile.points)
        prefs(context).edit()
            .putLong(KEY_INTERVAL_MS, profile.intervalMs)
            .putLong(KEY_RANDOM_EXTRA_MS, profile.randomExtraMs)
            .putInt(KEY_REPEAT_COUNT, profile.repeatCount)
            .putString(KEY_ACTIVE_PROFILE_ID, profile.id)
            .apply()
        return profile
    }

    fun deleteProfile(context: Context, profileId: String): Boolean {
        val profiles = loadProfiles(context)
        val updated = profiles.filterNot { it.id == profileId }
        if (updated.size == profiles.size) return false
        saveProfiles(context, updated)
        val prefs = prefs(context)
        if (prefs.getString(KEY_ACTIVE_PROFILE_ID, null) == profileId) {
            prefs.edit().remove(KEY_ACTIVE_PROFILE_ID).apply()
        }
        return true
    }

    private fun saveProfiles(context: Context, profiles: List<AutomationProfile>) {
        val array = JSONArray().apply { profiles.forEach { put(profileToJson(it)) } }
        prefs(context).edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun profileToJson(profile: AutomationProfile) = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("points", pointsToJson(profile.points))
        .put("intervalMs", profile.intervalMs)
        .put("randomExtraMs", profile.randomExtraMs)
        .put("repeatCount", profile.repeatCount)

    private fun profileFromJson(obj: JSONObject): AutomationProfile? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        return AutomationProfile(
            id = id,
            name = name,
            points = parsePoints(obj.optJSONArray("points") ?: JSONArray()),
            intervalMs = obj.optLong("intervalMs", 400L).coerceAtLeast(100L),
            randomExtraMs = obj.optLong("randomExtraMs", 80L).coerceAtLeast(0L),
            repeatCount = obj.optInt("repeatCount", 0).coerceAtLeast(0)
        )
    }

    private fun pointsToJson(points: List<TapPoint>) = JSONArray().apply {
        points.forEach { point ->
            put(
                JSONObject()
                    .put("x", point.x)
                    .put("y", point.y)
                    .put("delayMs", point.delayMs)
                    .put("repeatCount", point.repeatCount)
                    .put("holdDurationMs", point.holdDurationMs)
            )
        }
    }

    private fun parsePoints(raw: String): List<TapPoint> =
        runCatching { parsePoints(JSONArray(raw)) }.getOrDefault(emptyList())

    private fun parsePoints(array: JSONArray): List<TapPoint> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val x = obj.optInt("x", -1)
            val y = obj.optInt("y", -1)
            if (x >= 0 && y >= 0) {
                add(
                    TapPoint(
                        x = x,
                        y = y,
                        delayMs = obj.optLong("delayMs", 0L).coerceAtLeast(0L),
                        repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, MAX_POINT_REPEAT),
                        holdDurationMs = obj.optLong(
                            "holdDurationMs",
                            TapPoint.DEFAULT_HOLD_DURATION_MS
                        ).coerceIn(MIN_HOLD_DURATION_MS, MAX_HOLD_DURATION_MS)
                    )
                )
            }
        }
    }

    const val MIN_HOLD_DURATION_MS = 20L
    const val MAX_HOLD_DURATION_MS = 10_000L
    const val MAX_POINT_REPEAT = 9_999
    const val MAX_PROFILE_NAME_LENGTH = 32
}
