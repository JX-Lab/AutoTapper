package com.example.autotapper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private var collapsedHandleView: TextView? = null
    private var controllerView: LinearLayout? = null
    private var controllerStatusView: TextView? = null
    private var playButtonView: ImageView? = null
    private var clearButtonView: ImageView? = null
    private var pointMarkerView: View? = null
    private var pickerView: View? = null

    private var isControllerExpanded = false
    private var isClicking = false

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoTapperConfig.ACTION_CLICK_STATE_CHANGED -> {
                    isClicking = intent.getBooleanExtra(AutoTapperConfig.EXTRA_IS_CLICKING, false)
                    refreshControllerState()
                    refreshMarker()
                    refreshNotification()
                }

                AutoTapperConfig.ACTION_POINT_SELECTED -> {
                    refreshControllerState()
                    refreshMarker()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerServiceReceiver()
        startAsForegroundService()
        showCollapsedHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PermissionUtils.isOverlayPermissionGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            AutoTapperConfig.ACTION_STOP_CONTROLLER -> {
                closeController()
                return START_NOT_STICKY
            }

            AutoTapperConfig.ACTION_START_PICKING -> {
                showExpandedController()
                showPickerOverlay()
            }

            AutoTapperConfig.ACTION_SHOW_CONTROLLER -> {
                if (intent?.getBooleanExtra(AutoTapperConfig.EXTRA_START_PICKING, false) == true) {
                    showExpandedController()
                    showPickerOverlay()
                } else {
                    showExpandedController()
                }
            }

            null -> showCollapsedHandle()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopClickingIfNeeded()
        dismissPickerOverlay()
        removeViewSafely(pointMarkerView)
        removeViewSafely(controllerView)
        removeViewSafely(collapsedHandleView)
        pointMarkerView = null
        controllerView = null
        collapsedHandleView = null
        controllerStatusView = null
        playButtonView = null
        clearButtonView = null
        runCatching { unregisterReceiver(serviceReceiver) }
        super.onDestroy()
    }

    private fun registerServiceReceiver() {
        val filter = IntentFilter().apply {
            addAction(AutoTapperConfig.ACTION_CLICK_STATE_CHANGED)
            addAction(AutoTapperConfig.ACTION_POINT_SELECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(serviceReceiver, filter)
        }
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_autotapper)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(
                if (isClicking) getString(R.string.overlay_notification_running)
                else getString(R.string.overlay_notification_idle)
            )
            .setOngoing(true)
            .setContentIntent(buildOpenAppPendingIntent())
            .addAction(0, getString(R.string.overlay_notification_stop), buildStopControllerPendingIntent())
            .build()

    private fun refreshNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildStopControllerPendingIntent(): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(AutoTapperConfig.ACTION_STOP_CONTROLLER)
        return PendingIntent.getService(
            this,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun showCollapsedHandle() {
        isControllerExpanded = false
        dismissPickerOverlay()
        removeViewSafely(controllerView)
        controllerView = null
        controllerStatusView = null
        playButtonView = null
        clearButtonView = null
        hideMarker()

        if (collapsedHandleView == null) {
            val handle = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#111111"))
                textSize = 13f
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { showExpandedController() }
            }
            windowManager.addView(handle, collapsedLayoutParams())
            collapsedHandleView = handle
        }

        refreshControllerState()
    }

    private fun showExpandedController() {
        isControllerExpanded = true
        removeViewSafely(collapsedHandleView)
        collapsedHandleView = null

        if (controllerView == null) {
            val view = buildControllerView()
            windowManager.addView(view, controllerLayoutParams())
            controllerView = view
        }

        refreshControllerState()
        refreshMarker()
    }

    private fun buildControllerView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable("#F7FAFC", "#CBD2D9", 18f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = 12f
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val collapseChip = TextView(this).apply {
            text = getString(R.string.overlay_collapse_label)
            setTextColor(Color.parseColor("#102A43"))
            textSize = 13f
            background = roundedDrawable("#FFFFFF", "#D0D7DE", 12f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { showCollapsedHandle() }
        }

        val title = TextView(this).apply {
            text = getString(R.string.overlay_controller_title)
            setTextColor(Color.parseColor("#102A43"))
            textSize = 15f
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_close,
            contentDescriptionRes = R.string.overlay_close_button_desc
        ).apply {
            setOnClickListener { closeController() }
        }

        header.addView(collapseChip)
        header.addView(title)
        header.addView(closeButton)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        val addButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_add,
            contentDescriptionRes = R.string.overlay_add_button_desc
        ).apply {
            setOnClickListener { showPickerOverlay() }
        }

        val clearButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_delete,
            contentDescriptionRes = R.string.overlay_clear_button_desc
        ).apply {
            setOnClickListener { clearSelectedPoint() }
        }
        clearButtonView = clearButton

        val playButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_play,
            contentDescriptionRes = R.string.overlay_play_button_desc
        ).apply {
            setOnClickListener { toggleClicking() }
        }
        playButtonView = playButton

        buttonRow.addView(addButton)
        buttonRow.addView(spacerView())
        buttonRow.addView(clearButton)
        buttonRow.addView(spacerView())
        buttonRow.addView(playButton)

        val statusView = TextView(this).apply {
            setTextColor(Color.parseColor("#486581"))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        controllerStatusView = statusView

        root.addView(header)
        root.addView(buttonRow)
        root.addView(statusView)
        return root
    }

    private fun buildActionButton(iconRes: Int, contentDescriptionRes: Int) =
        ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#111111"))
            contentDescription = getString(contentDescriptionRes)
            background = roundedDrawable("#FFFFFF", "#D0D7DE", 14f)
        }

    private fun spacerView() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
    }

    private fun showPickerOverlay() {
        if (pickerView != null) {
            return
        }

        hideMarker()
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        view.findViewById<View>(R.id.capture_root).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                saveSelectedPoint(event.rawX.toInt(), event.rawY.toInt())
                true
            } else {
                true
            }
        }
        view.findViewById<Button>(R.id.btn_cancel_capture).setOnClickListener {
            dismissPickerOverlay()
            refreshMarker()
        }

        windowManager.addView(view, fullscreenLayoutParams())
        pickerView = view
    }

    private fun dismissPickerOverlay() {
        removeViewSafely(pickerView)
        pickerView = null
    }

    private fun saveSelectedPoint(x: Int, y: Int) {
        AutoTapperConfig.prefs(this)
            .edit()
            .putInt(AutoTapperConfig.KEY_TAP_X, x)
            .putInt(AutoTapperConfig.KEY_TAP_Y, y)
            .apply()

        sendPointSelectedBroadcast(x, y)
        dismissPickerOverlay()
        refreshControllerState()
        refreshMarker()
        Toast.makeText(this, getString(R.string.point_selected_toast, x, y), Toast.LENGTH_SHORT).show()
    }

    private fun clearSelectedPoint() {
        if (isClicking) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        }

        AutoTapperConfig.prefs(this)
            .edit()
            .putInt(AutoTapperConfig.KEY_TAP_X, -1)
            .putInt(AutoTapperConfig.KEY_TAP_Y, -1)
            .apply()

        sendPointSelectedBroadcast(-1, -1)
        refreshControllerState()
        refreshMarker()
        Toast.makeText(this, R.string.point_cleared_toast, Toast.LENGTH_SHORT).show()
    }

    private fun sendPointSelectedBroadcast(x: Int, y: Int) {
        sendBroadcast(
            Intent(AutoTapperConfig.ACTION_POINT_SELECTED)
                .setPackage(packageName)
                .putExtra(AutoTapperConfig.EXTRA_TAP_X, x)
                .putExtra(AutoTapperConfig.EXTRA_TAP_Y, y)
        )
    }

    private fun toggleClicking() {
        if (isClicking) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
            return
        }

        if (!PermissionUtils.isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
            Toast.makeText(this, R.string.accessibility_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        val (x, y) = currentPoint()
        if (x < 0 || y < 0) {
            Toast.makeText(this, R.string.point_required, Toast.LENGTH_SHORT).show()
            return
        }

        sendBroadcast(Intent(AutoTapperConfig.ACTION_START_CLICKING).setPackage(packageName))
    }

    private fun refreshControllerState() {
        val hasPoint = hasSelectedPoint()
        collapsedHandleView?.apply {
            text = if (isClicking) getString(R.string.overlay_handle_running)
            else getString(R.string.overlay_handle_idle)
            background = roundedDrawable(
                fillColor = if (isClicking) "#E6FFFFFF" else "#CCFFFFFF",
                strokeColor = if (isClicking) "#4D111111" else "#33111111",
                radiusDp = 18f
            )
        }

        playButtonView?.apply {
            setImageResource(if (isClicking) R.drawable.ic_overlay_pause else R.drawable.ic_overlay_play)
            contentDescription = getString(
                if (isClicking) R.string.overlay_stop_button_desc
                else R.string.overlay_play_button_desc
            )
        }

        clearButtonView?.alpha = if (hasPoint) 1f else 0.55f
        controllerStatusView?.text = buildControllerStatusText()
    }

    private fun buildControllerStatusText(): String {
        val prefs = AutoTapperConfig.prefs(this)
        val intervalMs = prefs.getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).coerceAtLeast(100L)
        val randomExtraMs = prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L)
        val repeatCount = prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).coerceAtLeast(0)
        val (x, y) = currentPoint()

        val pointText = if (x >= 0 && y >= 0) {
            getString(R.string.overlay_point_template, x, y)
        } else {
            getString(R.string.overlay_point_missing)
        }
        val repeatText = if (repeatCount == 0) {
            getString(R.string.overlay_repeat_infinite)
        } else {
            getString(R.string.overlay_repeat_finite, repeatCount)
        }
        val stateText = if (isClicking) getString(R.string.overlay_state_running)
        else getString(R.string.overlay_state_idle)

        return getString(
            R.string.overlay_status_template,
            stateText,
            pointText,
            intervalMs,
            intervalMs + randomExtraMs,
            repeatText
        )
    }

    private fun refreshMarker() {
        val (x, y) = currentPoint()
        val shouldShowMarker = isControllerExpanded && pickerView == null && x >= 0 && y >= 0
        if (!shouldShowMarker) {
            hideMarker()
            return
        }

        val marker = pointMarkerView ?: View(this).also { view ->
            view.background = roundedDrawable("#66F97316", "#CCEA580C", 20f)
            windowManager.addView(view, markerLayoutParams(x, y))
            pointMarkerView = view
        }

        windowManager.updateViewLayout(marker, markerLayoutParams(x, y))
        if (isClicking) {
            startMarkerPulse(marker)
        } else {
            marker.clearAnimation()
            marker.alpha = 0.75f
        }
    }

    private fun hideMarker() {
        pointMarkerView?.clearAnimation()
        removeViewSafely(pointMarkerView)
        pointMarkerView = null
    }

    private fun startMarkerPulse(view: View) {
        if (view.animation != null) {
            return
        }

        view.startAnimation(
            AlphaAnimation(0.35f, 1f).apply {
                duration = 280L
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
        )
    }

    private fun currentPoint(): Pair<Int, Int> {
        val prefs = AutoTapperConfig.prefs(this)
        return prefs.getInt(AutoTapperConfig.KEY_TAP_X, -1) to
            prefs.getInt(AutoTapperConfig.KEY_TAP_Y, -1)
    }

    private fun hasSelectedPoint(): Boolean {
        val (x, y) = currentPoint()
        return x >= 0 && y >= 0
    }

    private fun collapsedLayoutParams() =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(8)
        }

    private fun controllerLayoutParams() =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12)
        }

    private fun markerLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val size = dp(40)
        return WindowManager.LayoutParams(
            size,
            size,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x - size / 2
            this.y = y - size / 2
        }
    }

    private fun fullscreenLayoutParams() =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun roundedDrawable(fillColor: String, strokeColor: String, radiusDp: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }

    private fun removeViewSafely(view: View?) {
        view ?: return
        runCatching { windowManager.removeView(view) }
    }

    private fun closeController() {
        stopClickingIfNeeded()
        stopSelf()
    }

    private fun stopClickingIfNeeded() {
        if (isClicking) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "autotapper_overlay_controller"
        private const val NOTIFICATION_ID = 1001
    }
}
