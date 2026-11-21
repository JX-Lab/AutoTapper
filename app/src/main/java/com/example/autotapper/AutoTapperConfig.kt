package com.example.autotapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AutoTapperConfig {
    const val PREFS_NAME = "autotapper_prefs"

    const val KEY_ACTIONS = "automation_actions_v2"
    const val KEY_TAP_POINTS = "tap_points"
    const val KEY_INTERVAL_MS = "interval_ms"
    const val KEY_RANDOM_EXTRA_MS = "random_extra_ms"
    const val KEY_REPEAT_COUNT = "repeat_count"
    const val KEY_CONTROLLER_ALPHA = "controller_alpha"
    const val KEY_PROFILES = "automation_profiles"
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_LANGUAGE_MODE = "language_mode"
    const val KEY_DEFAULT_HOLD_DURATION_MS = "default_hold_duration_ms"
    const val KEY_DEFAULT_POINT_REPEAT = "default_point_repeat"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_CHINESE = "zh-CN"
    const val LANGUAGE_ENGLISH = "en"

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
    const val ACTION_PREVIEW_GESTURE = "com.example.autotapper.action.PREVIEW_GESTURE"
    const val ACTION_PREVIEW_COMPLETED = "com.example.autotapper.action.PREVIEW_COMPLETED"

    const val EXTRA_TAP_X = "extra_tap_x"
    const val EXTRA_TAP_Y = "extra_tap_y"
    const val EXTRA_IS_CLICKING = "extra_is_clicking"
    const val EXTRA_IS_PAUSED = "extra_is_paused"
    const val EXTRA_START_PICKING = "extra_start_picking"
    const val EXTRA_CURRENT_POINT_INDEX = "extra_current_point_index"
    const val EXTRA_ROUNDS_COMPLETED = "extra_rounds_completed"
    const val EXTRA_POINT_REPEAT_PROGRESS = "extra_point_repeat_progress"
    const val EXTRA_PENDING_ACTION = "extra_pending_action"
    const val EXTRA_GESTURE_JSON = "extra_gesture_json"
    const val EXTRA_PREVIEW_SUCCESS = "extra_preview_success"

    const val PENDING_NONE = 0
    const val PENDING_PAUSE = 1
    const val PENDING_STOP = 2

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadActions(context: Context): List<AutomationAction> {
        val prefs = prefs(context)
        prefs.getString(KEY_ACTIONS, null)?.let { raw ->
            return parseActions(raw)
        }

        val legacyPoints = loadLegacyPoints(context)
        if (legacyPoints.isNotEmpty()) saveActions(context, legacyPoints)
        return legacyPoints
    }

    private fun loadLegacyPoints(context: Context): List<TapPoint> {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_TAP_POINTS, null)
        if (raw != null) {
            return parsePoints(raw)
        }

        val legacyX = prefs.getInt(KEY_TAP_X, -1)
        val legacyY = prefs.getInt(KEY_TAP_Y, -1)
        if (legacyX >= 0 && legacyY >= 0) {
            val points = listOf(TapPoint(legacyX, legacyY))
            return points
        }
        return emptyList()
    }

    fun loadPoints(context: Context): List<TapPoint> = loadActions(context).filterIsInstance<TapPoint>()

    fun saveActions(context: Context, actions: List<AutomationAction>) {
        prefs(context).edit().putString(KEY_ACTIONS, actionsToJson(actions).toString()).apply()
    }

    fun savePoints(context: Context, points: List<TapPoint>) {
        saveActions(context, points)
    }

    fun addPoint(context: Context, x: Int, y: Int): List<AutomationAction> {
        val actions = loadActions(context) + TapPoint(
            x = x,
            y = y,
            repeatCount = getDefaultPointRepeat(context),
            holdDurationMs = getDefaultHoldDuration(context)
        )
        saveActions(context, actions)
        return actions
    }

    fun addGesture(context: Context, gesture: GestureAction): List<AutomationAction> {
        val actions = loadActions(context) + gesture
        saveActions(context, actions)
        return actions
    }

    fun updateActionAt(
        context: Context,
        index: Int,
        action: AutomationAction
    ): List<AutomationAction> {
        val actions = loadActions(context)
        if (index !in actions.indices) return actions
        val updated = actions.toMutableList().apply { this[index] = action }
        saveActions(context, updated)
        return updated
    }

    fun updatePointAt(context: Context, index: Int, point: TapPoint): List<AutomationAction> =
        updateActionAt(context, index, point)

    fun removeLastPoint(context: Context): List<AutomationAction> {
        val actions = loadActions(context)
        val updated = if (actions.isEmpty()) actions else actions.dropLast(1)
        saveActions(context, updated)
        return updated
    }

    fun clearPoints(context: Context) {
        saveActions(context, emptyList())
    }

    fun removePointAt(context: Context, index: Int): List<AutomationAction> {
        val actions = loadActions(context)
        val updated = if (index in actions.indices) {
            actions.toMutableList().apply { removeAt(index) }
        } else {
            actions
        }
        saveActions(context, updated)
        return updated
    }

    fun hasPoints(context: Context): Boolean = loadActions(context).isNotEmpty()

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

    fun getThemeMode(context: Context): String =
        prefs(context).getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

    fun saveThemeMode(context: Context, mode: String) {
        val safeMode = mode.takeIf {
            it == THEME_SYSTEM || it == THEME_LIGHT || it == THEME_DARK
        } ?: THEME_SYSTEM
        prefs(context).edit().putString(KEY_THEME_MODE, safeMode).apply()
    }

    fun getLanguageMode(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE_MODE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM

    fun saveLanguageMode(context: Context, mode: String) {
        val safeMode = mode.takeIf {
            it == LANGUAGE_SYSTEM || it == LANGUAGE_CHINESE || it == LANGUAGE_ENGLISH
        } ?: LANGUAGE_SYSTEM
        prefs(context).edit().putString(KEY_LANGUAGE_MODE, safeMode).apply()
    }

    fun getDefaultHoldDuration(context: Context): Long =
        prefs(context).getLong(
            KEY_DEFAULT_HOLD_DURATION_MS,
            TapPoint.DEFAULT_HOLD_DURATION_MS
        ).coerceIn(MIN_HOLD_DURATION_MS, MAX_HOLD_DURATION_MS)

    fun getDefaultPointRepeat(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT_POINT_REPEAT, 1).coerceIn(1, MAX_POINT_REPEAT)

    fun saveDefaultPointSettings(context: Context, holdDurationMs: Long, repeatCount: Int) {
        prefs(context).edit()
            .putLong(
                KEY_DEFAULT_HOLD_DURATION_MS,
                holdDurationMs.coerceIn(MIN_HOLD_DURATION_MS, MAX_HOLD_DURATION_MS)
            )
            .putInt(KEY_DEFAULT_POINT_REPEAT, repeatCount.coerceIn(1, MAX_POINT_REPEAT))
            .apply()
    }

    fun applyDefaultPointSettingsToAll(context: Context) {
        val hold = getDefaultHoldDuration(context)
        val repeats = getDefaultPointRepeat(context)
        saveActions(
            context,
            loadActions(context).map { action ->
                if (action is TapPoint) {
                    action.copy(holdDurationMs = hold, repeatCount = repeats)
                } else {
                    action
                }
            }
        )
    }

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
        return active.actions != loadActions(context) ||
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
            actions = loadActions(context),
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
        saveActions(context, profile.actions)
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
        .put("actions", actionsToJson(profile.actions))
        .put("intervalMs", profile.intervalMs)
        .put("randomExtraMs", profile.randomExtraMs)
        .put("repeatCount", profile.repeatCount)

    private fun profileFromJson(obj: JSONObject): AutomationProfile? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        return AutomationProfile(
            id = id,
            name = name,
            actions = if (obj.has("actions")) {
                parseActions(obj.optJSONArray("actions") ?: JSONArray())
            } else {
                parsePoints(obj.optJSONArray("points") ?: JSONArray())
            },
            intervalMs = obj.optLong("intervalMs", 400L).coerceAtLeast(100L),
            randomExtraMs = obj.optLong("randomExtraMs", 80L).coerceAtLeast(0L),
            repeatCount = obj.optInt("repeatCount", 0).coerceAtLeast(0)
        )
    }

    fun gestureToJson(gesture: GestureAction): String = actionToJson(gesture).toString()

    fun gestureFromJson(raw: String): GestureAction? = runCatching {
        parseAction(JSONObject(raw)) as? GestureAction
    }.getOrNull()

    private fun actionsToJson(actions: List<AutomationAction>) = JSONArray().apply {
        actions.forEach { put(actionToJson(it)) }
    }

    private fun actionToJson(action: AutomationAction): JSONObject = when (action) {
        is TapPoint -> JSONObject()
            .put("type", "tap")
            .put("x", action.x)
            .put("y", action.y)
            .put("delayMs", action.delayMs)
            .put("repeatCount", action.repeatCount)
            .put("holdDurationMs", action.holdDurationMs)

        is GestureAction -> JSONObject()
            .put("type", "gesture")
            .put("delayMs", action.delayMs)
            .put("repeatCount", action.repeatCount)
            .put(
                "strokes",
                JSONArray().apply {
                    action.strokes.forEach { stroke ->
                        put(
                            JSONObject()
                                .put("startOffsetMs", stroke.startOffsetMs)
                                .put("durationMs", stroke.durationMs)
                                .put(
                                    "points",
                                    JSONArray().apply {
                                        stroke.points.forEach { point ->
                                            put(
                                                JSONObject()
                                                    .put("x", point.x)
                                                    .put("y", point.y)
                                                    .put("timeOffsetMs", point.timeOffsetMs)
                                            )
                                        }
                                    }
                                )
                        )
                    }
                }
            )
    }

    private fun parseActions(raw: String): List<AutomationAction> =
        runCatching { parseActions(JSONArray(raw)) }.getOrDefault(emptyList())

    private fun parseActions(array: JSONArray): List<AutomationAction> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseAction(obj)?.let(::add)
        }
    }

    private fun parseAction(obj: JSONObject): AutomationAction? {
        return when (obj.optString("type", "tap")) {
            "gesture" -> {
                val strokesJson = obj.optJSONArray("strokes") ?: return null
                val strokes = buildList {
                    for (index in 0 until strokesJson.length()) {
                        val strokeJson = strokesJson.optJSONObject(index) ?: continue
                        val pointsJson = strokeJson.optJSONArray("points") ?: continue
                        val points = buildList {
                            for (pointIndex in 0 until pointsJson.length()) {
                                val pointJson = pointsJson.optJSONObject(pointIndex) ?: continue
                                val x = pointJson.optInt("x", -1)
                                val y = pointJson.optInt("y", -1)
                                if (x >= 0 && y >= 0) {
                                    add(
                                        GesturePoint(
                                            x = x,
                                            y = y,
                                            timeOffsetMs = pointJson.optLong(
                                                "timeOffsetMs",
                                                0L
                                            ).coerceAtLeast(0L)
                                        )
                                    )
                                }
                            }
                        }
                        if (points.size >= 2) {
                            add(
                                GestureStroke(
                                    points = points,
                                    startOffsetMs = strokeJson.optLong(
                                        "startOffsetMs",
                                        0L
                                    ).coerceAtLeast(0L),
                                    durationMs = strokeJson.optLong(
                                        "durationMs",
                                        GestureStroke.DEFAULT_DURATION_MS
                                    ).coerceIn(MIN_GESTURE_DURATION_MS, MAX_GESTURE_DURATION_MS)
                                )
                            )
                        }
                    }
                }
                strokes.takeIf { it.isNotEmpty() }?.let {
                    GestureAction(
                        strokes = it,
                        delayMs = obj.optLong("delayMs", 0L).coerceAtLeast(0L),
                        repeatCount = obj.optInt("repeatCount", 1)
                            .coerceIn(1, MAX_POINT_REPEAT)
                    )
                }
            }

            else -> parseTapPoint(obj)
        }
    }

    private fun parseTapPoint(obj: JSONObject): TapPoint? {
        val x = obj.optInt("x", -1)
        val y = obj.optInt("y", -1)
        if (x < 0 || y < 0) return null
        return TapPoint(
            x = x,
            y = y,
            delayMs = obj.optLong("delayMs", 0L).coerceAtLeast(0L),
            repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, MAX_POINT_REPEAT),
            holdDurationMs = obj.optLong(
                "holdDurationMs",
                TapPoint.DEFAULT_HOLD_DURATION_MS
            ).coerceIn(MIN_HOLD_DURATION_MS, MAX_HOLD_DURATION_MS)
        )
    }

    private fun parsePoints(raw: String): List<TapPoint> =
        runCatching { parsePoints(JSONArray(raw)) }.getOrDefault(emptyList())

    private fun parsePoints(array: JSONArray): List<TapPoint> = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseTapPoint(obj)?.let(::add)
        }
    }

    const val MIN_HOLD_DURATION_MS = 20L
    const val MAX_HOLD_DURATION_MS = 10_000L
    const val MIN_GESTURE_DURATION_MS = 100L
    const val MAX_GESTURE_DURATION_MS = 5_000L
    const val MAX_POINT_REPEAT = 9_999
    const val MAX_PROFILE_NAME_LENGTH = 32
}
