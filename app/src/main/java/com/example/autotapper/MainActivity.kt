package com.example.autotapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvPermissionState: TextView
    private lateinit var tvSelectedPoint: TextView
    private lateinit var tvConfigSummary: TextView
    private lateinit var etIntervalMs: EditText
    private lateinit var etRandomExtraMs: EditText
    private lateinit var etRepeatCount: EditText

    private var pointReceiverRegistered = false
    private var selectedX = -1
    private var selectedY = -1

    private val pointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AutoTapperConfig.ACTION_POINT_SELECTED) {
                return
            }

            val x = intent.getIntExtra(AutoTapperConfig.EXTRA_TAP_X, -1)
            val y = intent.getIntExtra(AutoTapperConfig.EXTRA_TAP_Y, -1)
            updateSelectedPoint(x, y)
            if (x >= 0 && y >= 0) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.point_selected_toast, x, y),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.point_cleared_toast, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPermissionState = findViewById(R.id.tv_permission_state)
        tvSelectedPoint = findViewById(R.id.tv_selected_point)
        tvConfigSummary = findViewById(R.id.tv_config_summary)
        etIntervalMs = findViewById(R.id.et_interval_ms)
        etRandomExtraMs = findViewById(R.id.et_random_extra_ms)
        etRepeatCount = findViewById(R.id.et_repeat_count)

        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            PermissionUtils.requestOverlayPermission(this)
        }
        findViewById<Button>(R.id.btn_grant_accessibility).setOnClickListener {
            PermissionUtils.requestAccessibilityPermission(this)
        }
        findViewById<Button>(R.id.btn_pick_point).setOnClickListener {
            openFloatingController()
        }
        findViewById<Button>(R.id.btn_close_controller).setOnClickListener {
            closeFloatingController()
        }
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startAutoTap()
        }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopAutoTap()
        }
        findViewById<Button>(R.id.btn_refresh_status).setOnClickListener {
            updateStatus()
        }

        etIntervalMs.doAfterTextChanged { updateConfigSummary() }
        etRandomExtraMs.doAfterTextChanged { updateConfigSummary() }
        etRepeatCount.doAfterTextChanged { updateConfigSummary() }

        loadSavedConfig()
        updateStatus()
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
        val prefs = AutoTapperConfig.prefs(this)
        etIntervalMs.setText(prefs.getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).toString())
        etRandomExtraMs.setText(
            prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).toString()
        )
        etRepeatCount.setText(prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).toString())

        val savedX = prefs.getInt(AutoTapperConfig.KEY_TAP_X, -1)
        val savedY = prefs.getInt(AutoTapperConfig.KEY_TAP_Y, -1)
        updateSelectedPoint(savedX, savedY)
    }

    private fun updateStatus() {
        val overlayGranted = PermissionUtils.isOverlayPermissionGranted(this)
        val accessibilityEnabled =
            PermissionUtils.isAccessibilityServiceEnabled(this, AutoClickService::class.java)

        tvPermissionState.text = getString(
            R.string.permission_state_template,
            if (overlayGranted) getString(R.string.permission_granted)
            else getString(R.string.permission_missing),
            if (accessibilityEnabled) getString(R.string.permission_granted)
            else getString(R.string.permission_missing)
        )

        val prefs = AutoTapperConfig.prefs(this)
        updateSelectedPoint(
            prefs.getInt(AutoTapperConfig.KEY_TAP_X, -1),
            prefs.getInt(AutoTapperConfig.KEY_TAP_Y, -1)
        )
    }

    private fun updateSelectedPoint(x: Int, y: Int) {
        selectedX = x
        selectedY = y
        tvSelectedPoint.text = if (x >= 0 && y >= 0) {
            getString(R.string.selected_point_template, x, y)
        } else {
            getString(R.string.point_not_selected)
        }
        updateConfigSummary()
    }

    private fun openFloatingController() {
        if (!PermissionUtils.isOverlayPermissionGranted(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
            PermissionUtils.requestOverlayPermission(this)
            return
        }

        val prefs = AutoTapperConfig.prefs(this)
        val hasSavedPoint =
            prefs.getInt(AutoTapperConfig.KEY_TAP_X, -1) >= 0 &&
                prefs.getInt(AutoTapperConfig.KEY_TAP_Y, -1) >= 0

        val intent = Intent(this, OverlayService::class.java).apply {
            action = AutoTapperConfig.ACTION_SHOW_CONTROLLER
            putExtra(AutoTapperConfig.EXTRA_START_PICKING, !hasSavedPoint)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(
            this,
            if (hasSavedPoint) R.string.overlay_started else R.string.overlay_started_pick,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun closeFloatingController() {
        stopService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, R.string.overlay_closed, Toast.LENGTH_SHORT).show()
    }

    private fun startAutoTap() {
        if (!PermissionUtils.isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
            Toast.makeText(this, R.string.accessibility_permission_required, Toast.LENGTH_SHORT)
                .show()
            PermissionUtils.requestAccessibilityPermission(this)
            return
        }

        val prefs = AutoTapperConfig.prefs(this)
        val x = prefs.getInt(AutoTapperConfig.KEY_TAP_X, -1)
        val y = prefs.getInt(AutoTapperConfig.KEY_TAP_Y, -1)
        if (x < 0 || y < 0) {
            Toast.makeText(this, R.string.point_required, Toast.LENGTH_SHORT).show()
            return
        }

        val intervalMs = etIntervalMs.text.toString().toLongOrNull()?.coerceAtLeast(100L)
        val randomExtraMs = etRandomExtraMs.text.toString().toLongOrNull()?.coerceAtLeast(0L)
        val repeatCount = etRepeatCount.text.toString().toIntOrNull()?.coerceAtLeast(0)

        if (intervalMs == null || randomExtraMs == null || repeatCount == null) {
            Toast.makeText(this, R.string.invalid_number_input, Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putLong(AutoTapperConfig.KEY_INTERVAL_MS, intervalMs)
            .putLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, randomExtraMs)
            .putInt(AutoTapperConfig.KEY_REPEAT_COUNT, repeatCount)
            .apply()

        sendBroadcast(Intent(AutoTapperConfig.ACTION_START_CLICKING).setPackage(packageName))
        Toast.makeText(this, R.string.start_command_sent, Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoTap() {
        sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        Toast.makeText(this, R.string.stop_command_sent, Toast.LENGTH_SHORT).show()
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

        val pointSummary = if (selectedX >= 0 && selectedY >= 0) {
            getString(R.string.config_summary_point_selected, selectedX, selectedY)
        } else {
            getString(R.string.config_summary_point_missing)
        }

        val repeatSummary = if (repeatCount == 0) {
            getString(R.string.config_summary_repeat_infinite)
        } else {
            getString(R.string.config_summary_repeat_finite, repeatCount)
        }

        val summaryText = getString(
            R.string.config_summary_template,
            pointSummary,
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

        val filter = IntentFilter(AutoTapperConfig.ACTION_POINT_SELECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pointReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(pointReceiver, filter)
        }
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
