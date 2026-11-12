package com.example.autotapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var dotOverlay: View
    private lateinit var dotAccessibility: View
    private lateinit var tvOverlayState: TextView
    private lateinit var tvAccessibilityState: TextView
    private lateinit var rvPoints: RecyclerView
    private lateinit var tvPointsEmpty: TextView
    private lateinit var tvPointsCount: TextView
    private lateinit var tvConfigSummary: TextView
    private lateinit var tvVersion: TextView
    private lateinit var etIntervalMs: EditText
    private lateinit var etRandomExtraMs: EditText
    private lateinit var etRepeatCount: EditText
    private lateinit var sliderControllerAlpha: Slider
    private lateinit var tvControllerAlphaValue: TextView
    private lateinit var tvProfileName: TextView
    private lateinit var btnProfileSave: MaterialButton
    private lateinit var btnProfileDelete: View

    private var pointReceiverRegistered = false
    private var isCheckingUpdate = false
    private var isLoadingConfig = false
    private var points: List<TapPoint> = emptyList()
    private lateinit var pointsAdapter: PointListAdapter

    private val pointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoTapperConfig.ACTION_POINT_SELECTED -> loadAndShowPoints()
                AutoTapperConfig.ACTION_CONTROLLER_ALPHA_CHANGED -> refreshControllerAlpha()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dotOverlay = findViewById(R.id.dot_overlay)
        dotAccessibility = findViewById(R.id.dot_accessibility)
        tvOverlayState = findViewById(R.id.tv_overlay_state)
        tvAccessibilityState = findViewById(R.id.tv_accessibility_state)
        rvPoints = findViewById(R.id.rv_points)
        tvPointsEmpty = findViewById(R.id.tv_points_empty)
        tvPointsCount = findViewById(R.id.tv_points_count)
        tvConfigSummary = findViewById(R.id.tv_config_summary)
        tvVersion = findViewById(R.id.tv_version)
        etIntervalMs = findViewById(R.id.et_interval_ms)
        etRandomExtraMs = findViewById(R.id.et_random_extra_ms)
        etRepeatCount = findViewById(R.id.et_repeat_count)
        sliderControllerAlpha = findViewById(R.id.slider_controller_alpha)
        tvControllerAlphaValue = findViewById(R.id.tv_controller_alpha_value)
        tvProfileName = findViewById(R.id.tv_profile_name)
        btnProfileSave = findViewById(R.id.btn_profile_save)
        btnProfileDelete = findViewById(R.id.btn_profile_delete)

        pointsAdapter = PointListAdapter(
            onEdit = { index -> showPointEditor(index) },
            onDelete = { index -> deletePoint(index) }
        )
        rvPoints.layoutManager = LinearLayoutManager(this)
        rvPoints.adapter = pointsAdapter
        rvPoints.itemAnimator = DefaultItemAnimator()

        findViewById<View>(R.id.btn_grant_overlay).setOnClickListener {
            PermissionUtils.requestOverlayPermission(this)
        }
        findViewById<View>(R.id.btn_grant_accessibility).setOnClickListener {
            PermissionUtils.requestAccessibilityPermission(this)
        }
        findViewById<View>(R.id.btn_open_controller).setOnClickListener {
            openFloatingController()
        }
        findViewById<View>(R.id.btn_check_update).setOnClickListener {
            startUpdateCheck(silent = false)
        }
        findViewById<View>(R.id.btn_profile_select).setOnClickListener {
            showProfileSelector()
        }
        btnProfileSave.setOnClickListener {
            showSaveProfileDialog()
        }
        btnProfileDelete.setOnClickListener {
            confirmDeleteActiveProfile()
        }

        sliderControllerAlpha.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                AutoTapperConfig.saveControllerAlpha(this, value)
                tvControllerAlphaValue.text = getString(R.string.controller_alpha_value, value * 100)
                sendBroadcast(
                    Intent(AutoTapperConfig.ACTION_CONTROLLER_ALPHA_CHANGED).setPackage(packageName)
                )
            }
        }

        tvVersion.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)

        etIntervalMs.doAfterTextChanged { onBaseConfigChanged() }
        etRandomExtraMs.doAfterTextChanged { onBaseConfigChanged() }
        etRepeatCount.doAfterTextChanged { onBaseConfigChanged() }

        loadSavedConfig()
        updateStatus()
        startUpdateCheck(silent = true)
    }

    override fun onStart() {
        super.onStart()
        registerPointReceiver()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onStop() {
        super.onStop()
        unregisterPointReceiver()
    }

    private fun loadSavedConfig() {
        isLoadingConfig = true
        val prefs = AutoTapperConfig.prefs(this)
        etIntervalMs.setText(prefs.getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).toString())
        etRandomExtraMs.setText(
            prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).toString()
        )
        etRepeatCount.setText(prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).toString())

        val alpha = AutoTapperConfig.getControllerAlpha(this)
        sliderControllerAlpha.value = alpha
        tvControllerAlphaValue.text = getString(R.string.controller_alpha_value, alpha * 100)
        isLoadingConfig = false
        updateConfigSummary()
        refreshActiveProfile()
    }

    private fun refreshControllerAlpha() {
        val alpha = AutoTapperConfig.getControllerAlpha(this)
        sliderControllerAlpha.value = alpha
        tvControllerAlphaValue.text = getString(R.string.controller_alpha_value, alpha * 100)
    }

    private fun updateStatus() {
        updatePermissionRow(
            dotOverlay,
            tvOverlayState,
            R.string.overlay_permission_state,
            PermissionUtils.isOverlayPermissionGranted(this)
        )
        updatePermissionRow(
            dotAccessibility,
            tvAccessibilityState,
            R.string.accessibility_permission_state,
            PermissionUtils.isAccessibilityServiceEnabled(this, AutoClickService::class.java)
        )
        loadAndShowPoints()
        refreshActiveProfile()
    }

    private fun onBaseConfigChanged() {
        updateConfigSummary()
        if (isLoadingConfig) return
        val interval = etIntervalMs.text.toString().toLongOrNull() ?: return
        val randomExtra = etRandomExtraMs.text.toString().toLongOrNull() ?: return
        val rounds = etRepeatCount.text.toString().toIntOrNull() ?: return
        AutoTapperConfig.saveBaseConfig(this, interval, randomExtra, rounds)
        refreshActiveProfile()
    }

    private fun refreshActiveProfile() {
        val active = AutoTapperConfig.getActiveProfile(this)
        tvProfileName.text = when {
            active == null -> getString(R.string.profile_none)
            AutoTapperConfig.isActiveProfileModified(this) ->
                getString(R.string.profile_modified, active.name)
            else -> active.name
        }
        btnProfileSave.setText(
            if (active != null && AutoTapperConfig.isActiveProfileModified(this)) {
                R.string.profile_update
            } else {
                R.string.profile_save
            }
        )
        btnProfileDelete.isEnabled = active != null
        btnProfileDelete.alpha = if (active != null) 1f else 0.45f
    }

    private fun updatePermissionRow(dot: View, textView: TextView, templateRes: Int, granted: Boolean) {
        textView.text = getString(
            templateRes,
            getString(if (granted) R.string.permission_granted else R.string.permission_missing)
        )
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (granted) R.color.success else R.color.danger
                )
            )
        }
    }

    private fun loadAndShowPoints() {
        points = AutoTapperConfig.loadPoints(this)
        pointsAdapter.submitPoints(points)
        val empty = points.isEmpty()
        tvPointsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvPoints.visibility = if (empty) View.GONE else View.VISIBLE
        tvPointsCount.text = getString(R.string.points_count_badge, points.size)
        updateConfigSummary()
        refreshActiveProfile()
    }

    private fun deletePoint(index: Int) {
        val current = AutoTapperConfig.loadPoints(this)
        if (index !in current.indices) {
            return
        }
        sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        AutoTapperConfig.removePointAt(this, index)
        points = AutoTapperConfig.loadPoints(this)
        pointsAdapter.removeItem(index)
        tvPointsEmpty.visibility = if (points.isEmpty()) View.VISIBLE else View.GONE
        rvPoints.visibility = if (points.isEmpty()) View.GONE else View.VISIBLE
        tvPointsCount.text = getString(R.string.points_count_badge, points.size)
        updateConfigSummary()
        Toast.makeText(this, getString(R.string.point_deleted_toast, index + 1), Toast.LENGTH_SHORT)
            .show()
    }

    private fun showPointEditor(index: Int) {
        val point = AutoTapperConfig.loadPoints(this).getOrNull(index) ?: return
        sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        val view = layoutInflater.inflate(R.layout.dialog_point_settings, null)
        val delayInput = view.findViewById<EditText>(R.id.et_point_delay)
        val repeatInput = view.findViewById<EditText>(R.id.et_point_repeat)
        val holdInput = view.findViewById<EditText>(R.id.et_point_hold)
        delayInput.setText(point.delayMs.toString())
        repeatInput.setText(point.repeatCount.toString())
        holdInput.setText(point.holdDurationMs.toString())

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_point_title, index + 1, point.x, point.y))
            .setView(view)
            .setPositiveButton(R.string.save_changes, null)
            .setNegativeButton(R.string.cancel_capture, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val delay = delayInput.text.toString().toLongOrNull()
                val repeat = repeatInput.text.toString().toIntOrNull()
                val hold = holdInput.text.toString().toLongOrNull()
                val valid = delay != null && delay >= 0L &&
                    repeat != null && repeat in 1..AutoTapperConfig.MAX_POINT_REPEAT &&
                    hold != null && hold in (
                        AutoTapperConfig.MIN_HOLD_DURATION_MS..
                            AutoTapperConfig.MAX_HOLD_DURATION_MS
                        )
                if (!valid) {
                    Toast.makeText(this, R.string.point_settings_invalid, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                AutoTapperConfig.updatePointAt(
                    this,
                    index,
                    point.copy(
                        delayMs = delay!!,
                        repeatCount = repeat!!,
                        holdDurationMs = hold!!
                    )
                )
                loadAndShowPoints()
                sendBroadcast(
                    Intent(AutoTapperConfig.ACTION_POINT_SELECTED).setPackage(packageName)
                )
                Toast.makeText(
                    this,
                    getString(R.string.point_updated_toast, index + 1),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showSaveProfileDialog(onSaved: (() -> Unit)? = null) {
        onBaseConfigChanged()
        val view = layoutInflater.inflate(R.layout.dialog_profile_name, null)
        val nameInput = view.findViewById<EditText>(R.id.et_profile_name)
        nameInput.setText(AutoTapperConfig.getActiveProfile(this)?.name.orEmpty())
        nameInput.setSelection(nameInput.text.length)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.profile_save_title)
            .setView(view)
            .setPositiveButton(R.string.profile_save, null)
            .setNegativeButton(R.string.cancel_capture, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    nameInput.error = getString(R.string.profile_name_hint)
                    return@setOnClickListener
                }
                val existing = AutoTapperConfig.loadProfiles(this)
                    .firstOrNull { it.name.equals(name, ignoreCase = true) }
                val activeId = AutoTapperConfig.getActiveProfile(this)?.id
                if (existing != null && existing.id != activeId) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.profile_overwrite_title)
                        .setMessage(getString(R.string.profile_overwrite_message, existing.name))
                        .setPositiveButton(R.string.profile_update) { _, _ ->
                            saveProfileNow(name)
                            onSaved?.invoke()
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel_capture, null)
                        .show()
                } else {
                    saveProfileNow(name)
                    onSaved?.invoke()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showProfileSelector() {
        val profiles = AutoTapperConfig.loadProfiles(this)
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.profile_list_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val names = profiles.map { it.name }.toTypedArray()
        val activeId = AutoTapperConfig.getActiveProfile(this)?.id
        val checked = profiles.indexOfFirst { it.id == activeId }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_select_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val target = profiles[which]
                val active = AutoTapperConfig.getActiveProfile(this)
                dialog.dismiss()
                if (active?.id == target.id) return@setSingleChoiceItems
                if (active == null && AutoTapperConfig.loadPoints(this).isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.profile_unsaved_title)
                        .setMessage(R.string.profile_unsaved_current_message)
                        .setPositiveButton(R.string.profile_save_and_switch) { _, _ ->
                            showSaveProfileDialog { activateProfile(target.id) }
                        }
                        .setNeutralButton(R.string.profile_discard_and_switch) { _, _ ->
                            activateProfile(target.id)
                        }
                        .setNegativeButton(R.string.cancel_capture, null)
                        .show()
                } else if (active != null && AutoTapperConfig.isActiveProfileModified(this)) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.profile_unsaved_title)
                        .setMessage(getString(R.string.profile_unsaved_message, active.name))
                        .setPositiveButton(R.string.profile_save_and_switch) { _, _ ->
                            saveProfileNow(active.name)
                            activateProfile(target.id)
                        }
                        .setNeutralButton(R.string.profile_discard_and_switch) { _, _ ->
                            activateProfile(target.id)
                        }
                        .setNegativeButton(R.string.cancel_capture, null)
                        .show()
                } else {
                    activateProfile(target.id)
                }
            }
            .setNegativeButton(R.string.cancel_capture, null)
            .show()
    }

    private fun saveProfileNow(name: String) {
        val profile = AutoTapperConfig.saveCurrentProfile(this, name)
        refreshActiveProfile()
        Toast.makeText(
            this,
            getString(R.string.profile_saved, profile.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun activateProfile(profileId: String) {
        sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        val loaded = AutoTapperConfig.activateProfile(this, profileId) ?: return
        loadSavedConfig()
        loadAndShowPoints()
        sendBroadcast(Intent(AutoTapperConfig.ACTION_POINT_SELECTED).setPackage(packageName))
        Toast.makeText(
            this,
            getString(R.string.profile_loaded, loaded.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmDeleteActiveProfile() {
        val profile = AutoTapperConfig.getActiveProfile(this) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_delete_title)
            .setMessage(getString(R.string.profile_delete_message, profile.name))
            .setPositiveButton(R.string.profile_delete) { _, _ ->
                AutoTapperConfig.deleteProfile(this, profile.id)
                refreshActiveProfile()
                Toast.makeText(this, R.string.profile_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel_capture, null)
            .show()
    }

    private fun openFloatingController() {
        onBaseConfigChanged()
        if (!PermissionUtils.isOverlayPermissionGranted(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
            PermissionUtils.requestOverlayPermission(this)
            return
        }

        val hasPoints = AutoTapperConfig.hasPoints(this)
        val intent = Intent(this, OverlayService::class.java).apply {
            action = AutoTapperConfig.ACTION_SHOW_CONTROLLER
            putExtra(AutoTapperConfig.EXTRA_START_PICKING, !hasPoints)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(
            this,
            if (hasPoints) R.string.overlay_started else R.string.overlay_started_pick,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startUpdateCheck(silent: Boolean) {
        if (isCheckingUpdate) {
            return
        }
        isCheckingUpdate = true
        if (!silent) {
            Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        }
        UpdateChecker.checkForUpdate { info ->
            runOnUiThread {
                isCheckingUpdate = false
                handleUpdateResult(info, silent)
            }
        }
    }

    private fun handleUpdateResult(info: UpdateChecker.UpdateInfo?, silent: Boolean) {
        if (info == null) {
            if (!silent) {
                Toast.makeText(this, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!UpdateChecker.isNewer(info.latestVersion, BuildConfig.VERSION_NAME)) {
            if (!silent) {
                Toast.makeText(this, R.string.update_latest, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val prefs = AutoTapperConfig.prefs(this)
        if (prefs.getString(AutoTapperConfig.KEY_UPDATE_IGNORED_VERSION, null) ==
            info.latestVersion
        ) {
            return
        }

        val alreadyNotified = prefs.getString(AutoTapperConfig.KEY_UPDATE_NOTIFIED_VERSION, null) ==
            info.latestVersion
        if (silent && alreadyNotified) {
            return
        }

        prefs.edit().putString(AutoTapperConfig.KEY_UPDATE_NOTIFIED_VERSION, info.latestVersion)
            .apply()
        UpdateNotifier.notify(this, info)
        if (!isFinishing && !isDestroyed) {
            showUpdateDialog(info)
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        val message = buildString {
            append(
                getString(
                    R.string.update_dialog_message,
                    BuildConfig.VERSION_NAME,
                    info.latestVersion
                )
            )
            val notes = info.notes?.lineSequence()
                ?.filter { it.isNotBlank() }
                ?.joinToString("\n")
                ?.take(400)
            if (!notes.isNullOrBlank()) {
                append("\n\n").append(getString(R.string.update_dialog_notes_title))
                    .append("\n").append(notes)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.update_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ ->
                openUrl(info.downloadUrl)
            }
            .setNeutralButton(R.string.update_ignore) { _, _ ->
                AutoTapperConfig.prefs(this).edit()
                    .putString(AutoTapperConfig.KEY_UPDATE_IGNORED_VERSION, info.latestVersion)
                    .apply()
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun openUrl(url: String) {
        if (!UpdateChecker.isSafeServerUrl(url)) {
            Toast.makeText(this, R.string.update_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.update_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateConfigSummary() {
        val intervalInput = etIntervalMs.text.toString().toLongOrNull()
        val randomExtraInput = etRandomExtraMs.text.toString().toLongOrNull()
        val repeatCountInput = etRepeatCount.text.toString().toIntOrNull()

        if (intervalInput == null || randomExtraInput == null || repeatCountInput == null) {
            tvConfigSummary.text = getString(R.string.config_summary_invalid)
            return
        }

        val intervalMs = intervalInput.coerceAtLeast(100L)
        val randomExtraMs = randomExtraInput.coerceAtLeast(0L)
        val repeatCount = repeatCountInput.coerceAtLeast(0)

        val repeatSummary = if (repeatCount == 0) {
            getString(R.string.config_summary_repeat_infinite)
        } else {
            getString(R.string.config_summary_repeat_finite, repeatCount)
        }

        val summaryText = getString(
            R.string.config_summary_template,
            points.size,
            intervalMs,
            intervalMs + randomExtraMs,
            repeatSummary
        )

        tvConfigSummary.text = if (intervalInput < 100L) {
            "$summaryText\n${getString(R.string.interval_adjusted_note)}"
        } else {
            summaryText
        }
    }

    private fun registerPointReceiver() {
        if (pointReceiverRegistered) {
            return
        }

        val filter = IntentFilter().apply {
            addAction(AutoTapperConfig.ACTION_POINT_SELECTED)
            addAction(AutoTapperConfig.ACTION_CONTROLLER_ALPHA_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            pointReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        pointReceiverRegistered = true
    }

    private fun unregisterPointReceiver() {
        if (!pointReceiverRegistered) {
            return
        }

        unregisterReceiver(pointReceiver)
        pointReceiverRegistered = false
    }
}
