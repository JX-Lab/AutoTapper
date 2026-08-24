package com.example.autotapper

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
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
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private var collapsedHandleView: ImageView? = null
    private var controllerView: LinearLayout? = null
    private var controllerStatusView: TextView? = null
    private var pointsTextView: TextView? = null
    private var playButtonView: ImageView? = null
    private var clearButtonView: ImageView? = null
    private var pickerView: View? = null
    private var pickerCounterView: TextView? = null
    private val pointMarkers: MutableList<View> = mutableListOf()
    private val markerAnimators: MutableMap<View, ObjectAnimator> = mutableMapOf()

    private var isControllerExpanded = false
    private var isClicking = false
    private var currentPointIndex = -1
    private var sessionStartCount = 0

    // 悬浮组件当前锚点位置（屏幕坐标，重力 TOP|START），拖动后随之更新
    private var floatX = -1
    private var floatY = -1

    // 收起状态下悬浮球应处的位置。展开后如未拖动控制器，收起时回到此位置。
    private var collapsedAnchorX = -1
    private var collapsedAnchorY = -1

    // 控制器透明度（0.3 ~ 1.0）
    private var controllerAlpha = 0.9f

    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragActive = false

    private val touchSlop: Int
        get() = ViewConfiguration.get(this).scaledTouchSlop

    private val dragTouchListener = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDownRawX = event.rawX
                dragDownRawY = event.rawY
                dragStartX = floatX.coerceAtLeast(0)
                dragStartY = floatY.coerceAtLeast(0)
                dragActive = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragDownRawX).toInt()
                val dy = (event.rawY - dragDownRawY).toInt()
                if (!dragActive && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragActive = true
                }
                if (dragActive) {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = (dragStartX + dx).coerceIn(0, maxOf(0, screenWidth - view.width))
                    params.y = (dragStartY + dy).coerceIn(0, maxOf(0, screenHeight - view.height))
                    windowManager.updateViewLayout(view, params)
                    floatX = params.x
                    floatY = params.y
                    collapsedAnchorX = params.x
                    collapsedAnchorY = params.y
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragActive) {
                    view.performClick()
                }
                true
            }

            else -> true
        }
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoTapperConfig.ACTION_CLICK_STATE_CHANGED -> {
                    isClicking = intent.getBooleanExtra(AutoTapperConfig.EXTRA_IS_CLICKING, false)
                    currentPointIndex =
                        intent.getIntExtra(AutoTapperConfig.EXTRA_CURRENT_POINT_INDEX, -1)
                    refreshControllerState()
                    refreshMarkers()
                    refreshNotification()
                }

                AutoTapperConfig.ACTION_POINT_SELECTED -> {
                    refreshControllerState()
                    refreshMarkers()
                }

                AutoTapperConfig.ACTION_CONTROLLER_ALPHA_CHANGED -> {
                    refreshControllerAlpha()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        controllerAlpha = AutoTapperConfig.getControllerAlpha(this)
        registerServiceReceiver()
        startAsForegroundService()
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
                if (intent.getBooleanExtra(AutoTapperConfig.EXTRA_START_PICKING, false)) {
                    showExpandedController()
                    showPickerOverlay()
                } else {
                    showExpandedController()
                }
            }

            null -> showCollapsedHandle(animate = false)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopClickingIfNeeded()
        dismissPickerOverlay()
        hideMarkers()
        removeViewSafely(controllerView)
        removeViewSafely(collapsedHandleView)
        pointMarkers.clear()
        markerAnimators.clear()
        controllerView = null
        collapsedHandleView = null
        controllerStatusView = null
        pointsTextView = null
        playButtonView = null
        clearButtonView = null
        runCatching { unregisterReceiver(serviceReceiver) }
        super.onDestroy()
    }

    private fun registerServiceReceiver() {
        val filter = IntentFilter().apply {
            addAction(AutoTapperConfig.ACTION_CLICK_STATE_CHANGED)
            addAction(AutoTapperConfig.ACTION_POINT_SELECTED)
            addAction(AutoTapperConfig.ACTION_CONTROLLER_ALPHA_CHANGED)
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
            .addAction(
                0,
                getString(R.string.overlay_notification_stop),
                buildStopControllerPendingIntent()
            )
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
        val intent = Intent(this, OverlayService::class.java)
            .setAction(AutoTapperConfig.ACTION_STOP_CONTROLLER)
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

    private fun showCollapsedHandle(animate: Boolean = true) {
        isControllerExpanded = false
        dismissPickerOverlay()
        val previousController = controllerView
        controllerView = null
        controllerStatusView = null
        pointsTextView = null
        playButtonView = null
        clearButtonView = null
        hideMarkers()

        val x = if (collapsedAnchorX >= 0) collapsedAnchorX else floatX.coerceAtLeast(0)
        val y = if (collapsedAnchorY >= 0) collapsedAnchorY else floatY.coerceAtLeast(0)
        floatX = x
        floatY = y

        val handle = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setImageResource(R.drawable.ic_floating_handle)
            setColorFilter(color(R.color.on_primary))
            background = ContextCompat.getDrawable(this@OverlayService, R.drawable.bg_floating_handle)
            setOnClickListener { showExpandedController() }
            setOnTouchListener(dragTouchListener)
            elevation = 8f
            alpha = if (animate) 0f else controllerAlpha
            translationX = if (animate) dp(48).toFloat() else 0f
        }
        windowManager.addView(handle, floatLayoutParams(x, y))
        collapsedHandleView = handle

        handle.post {
            if (collapsedHandleView !== handle) {
                return@post
            }
            if (floatX < 0 || floatY < 0) {
                floatX = screenWidth - handle.width - dp(8)
                floatY = (screenHeight - handle.height) / 2
                collapsedAnchorX = floatX
                collapsedAnchorY = floatY
            }
            updateFloatPosition(handle)
        }

        if (animate) {
            previousController?.animate()
                ?.alpha(0f)
                ?.translationX((-dp(48)).toFloat())
                ?.setDuration(ANIMATION_DURATION)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.withEndAction { removeViewSafely(previousController) }
                ?.start()

            handle.animate()
                .alpha(controllerAlpha)
                .translationX(0f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            removeViewSafely(previousController)
        }

        refreshControllerState()
    }

    private fun showExpandedController(animate: Boolean = true) {
        isControllerExpanded = true
        val previousHandle = collapsedHandleView
        collapsedHandleView = null

        if (floatX < 0 || floatY < 0) {
            floatX = (screenWidth - dp(170)).coerceAtLeast(0)
            floatY = (screenHeight - dp(180)) / 2
            collapsedAnchorX = floatX
            collapsedAnchorY = floatY
        }

        val view = buildControllerView()
        view.setOnTouchListener(dragTouchListener)
        view.alpha = if (animate) 0f else controllerAlpha
        view.translationX = if (animate) dp(48).toFloat() else 0f
        windowManager.addView(
            view,
            floatLayoutParams(floatX.coerceAtLeast(0), floatY.coerceAtLeast(0))
        )
        controllerView = view

        view.post {
            if (controllerView !== view) {
                return@post
            }
            floatX = floatX.coerceIn(0, maxOf(0, screenWidth - view.width - dp(8)))
            floatY = floatY.coerceIn(0, maxOf(0, screenHeight - view.height - dp(8)))
            updateFloatPosition(view)
        }

        if (animate) {
            previousHandle?.animate()
                ?.alpha(0f)
                ?.translationX((-dp(48)).toFloat())
                ?.setDuration(ANIMATION_DURATION)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.withEndAction { removeViewSafely(previousHandle) }
                ?.start()

            view.animate()
                .alpha(controllerAlpha)
                .translationX(0f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            removeViewSafely(previousHandle)
        }

        refreshControllerState()
        refreshMarkers()
    }

    private fun updateFloatPosition(view: View) {
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = floatX
        params.y = floatY
        windowManager.updateViewLayout(view, params)
    }

    private fun buildControllerView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                fillColor = color(R.color.surface_container),
                strokeColor = color(R.color.outline),
                radiusDp = 16f
            )
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = resources.getDimension(R.dimen.overlay_elevation)
            alpha = controllerAlpha
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val collapseChip = TextView(this).apply {
            text = getString(R.string.overlay_collapse_label)
            setTextColor(color(R.color.text_primary))
            textSize = 13f
            background = roundedDrawable(
                fillColor = color(R.color.card_bg),
                strokeColor = color(R.color.outline),
                radiusDp = 12f
            )
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { showCollapsedHandle() }
        }

        val title = TextView(this).apply {
            text = getString(R.string.overlay_controller_title)
            setTextColor(color(R.color.text_primary))
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
            setOnClickListener { removeLastPoint() }
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
            setTextColor(color(R.color.text_secondary))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        controllerStatusView = statusView

        val pointsView = TextView(this).apply {
            setTextColor(color(R.color.text_secondary))
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        pointsTextView = pointsView

        root.addView(header)
        root.addView(buttonRow)
        root.addView(statusView)
        root.addView(pointsView)
        return root
    }

    private fun buildActionButton(iconRes: Int, contentDescriptionRes: Int) =
        ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setImageResource(iconRes)
            setColorFilter(color(R.color.text_primary))
            this.contentDescription = getString(contentDescriptionRes)
            background = ContextCompat.getDrawable(this@OverlayService, R.drawable.bg_circular_button)
        }

    private fun spacerView() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
    }

    private fun showPickerOverlay() {
        if (pickerView != null) {
            return
        }

        sessionStartCount = AutoTapperConfig.loadPoints(this).size

        // Service 上下文没有 MaterialComponents 主题，MaterialButton 膨胀会崩溃，需包一层主题
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_AutoTapper)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_layout, null)
        pickerCounterView = view.findViewById(R.id.tv_picker_counter)
        updatePickerCounter()

        view.findViewById<View>(R.id.capture_root).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                addPickedPoint(event.rawX.toInt(), event.rawY.toInt())
                true
            } else {
                true
            }
        }
        view.findViewById<View>(R.id.btn_finish_capture).setOnClickListener {
            finishPicking()
        }
        view.findViewById<View>(R.id.btn_cancel_capture).setOnClickListener {
            cancelPicking()
        }

        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_picker).alpha = controllerAlpha

        windowManager.addView(view, fullscreenLayoutParams())
        pickerView = view
        refreshMarkers()
    }

    private fun dismissPickerOverlay() {
        removeViewSafely(pickerView)
        pickerView = null
        pickerCounterView = null
    }

    private fun addPickedPoint(x: Int, y: Int) {
        AutoTapperConfig.addPoint(this, x, y)
        broadcastPointsChanged(x, y)
        updatePickerCounter()
        refreshMarkers()
        Toast.makeText(this, getString(R.string.point_added_toast, x, y), Toast.LENGTH_SHORT)
            .show()
    }

    private fun finishPicking() {
        dismissPickerOverlay()
        refreshControllerState()
        refreshMarkers()
        Toast.makeText(
            this,
            getString(R.string.picking_finished_toast, AutoTapperConfig.loadPoints(this).size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cancelPicking() {
        val all = AutoTapperConfig.loadPoints(this)
        if (all.size > sessionStartCount) {
            AutoTapperConfig.savePoints(this, all.take(sessionStartCount))
        }
        broadcastPointsChanged()
        dismissPickerOverlay()
        refreshControllerState()
        refreshMarkers()
        Toast.makeText(this, R.string.picking_cancelled_toast, Toast.LENGTH_SHORT).show()
    }

    private fun updatePickerCounter() {
        pickerCounterView?.text = getString(
            R.string.picker_counter_template,
            AutoTapperConfig.loadPoints(this).size
        )
    }

    private fun broadcastPointsChanged(x: Int = -1, y: Int = -1) {
        sendBroadcast(
            Intent(AutoTapperConfig.ACTION_POINT_SELECTED)
                .setPackage(packageName)
                .putExtra(AutoTapperConfig.EXTRA_TAP_X, x)
                .putExtra(AutoTapperConfig.EXTRA_TAP_Y, y)
        )
    }

    private fun removeLastPoint() {
        if (isClicking) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        }

        if (AutoTapperConfig.loadPoints(this).isEmpty()) {
            Toast.makeText(this, R.string.no_point_to_delete_toast, Toast.LENGTH_SHORT).show()
            return
        }

        AutoTapperConfig.removeLastPoint(this)
        broadcastPointsChanged()
        refreshControllerState()
        refreshMarkers()
        Toast.makeText(this, R.string.point_removed_toast, Toast.LENGTH_SHORT).show()
    }

    private fun toggleClicking() {
        if (isClicking) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
            return
        }

        if (!PermissionUtils.isAccessibilityServiceEnabled(this, AutoClickService::class.java)) {
            Toast.makeText(this, R.string.accessibility_permission_required, Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (AutoTapperConfig.loadPoints(this).isEmpty()) {
            Toast.makeText(this, R.string.point_required, Toast.LENGTH_SHORT).show()
            return
        }

        sendBroadcast(Intent(AutoTapperConfig.ACTION_START_CLICKING).setPackage(packageName))
    }

    private fun refreshControllerAlpha() {
        controllerAlpha = AutoTapperConfig.getControllerAlpha(this)
        controllerView?.alpha = controllerAlpha
        collapsedHandleView?.alpha = controllerAlpha
        pickerView?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_picker)?.alpha = controllerAlpha
    }

    private fun refreshControllerState() {
        val hasPoint = AutoTapperConfig.hasPoints(this)

        collapsedHandleView?.apply {
            background = roundedDrawable(
                fillColor = if (isClicking) {
                    color(R.color.accent_container)
                } else {
                    color(R.color.primary)
                },
                strokeColor = if (isClicking) {
                    color(R.color.accent)
                } else {
                    color(R.color.primary_container)
                },
                radiusDp = 24f,
                shape = GradientDrawable.OVAL
            )
            setColorFilter(
                if (isClicking) color(R.color.on_accent_container)
                else color(R.color.on_primary)
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
        pointsTextView?.text = buildControllerPointsText()
    }

    private fun buildControllerStatusText(): String {
        val prefs = AutoTapperConfig.prefs(this)
        val intervalMs = prefs.getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).coerceAtLeast(100L)
        val randomExtraMs = prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L)
        val repeatCount = prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).coerceAtLeast(0)

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
            intervalMs,
            intervalMs + randomExtraMs,
            repeatText
        )
    }

    private fun buildControllerPointsText(): String {
        val points = AutoTapperConfig.loadPoints(this)
        if (points.isEmpty()) {
            return getString(R.string.overlay_point_missing)
        }
        val maxShown = 2
        val head = points.take(maxShown).joinToString("  ") { "(${it.x},${it.y})" }
        return if (points.size > maxShown) {
            "$head  ${getString(R.string.overlay_points_more, points.size - maxShown)}"
        } else {
            head
        }
    }

    private fun refreshMarkers() {
        val points = AutoTapperConfig.loadPoints(this)
        if (!isControllerExpanded || points.isEmpty()) {
            hideMarkers()
            return
        }

        while (pointMarkers.size < points.size) {
            val marker = View(this).apply {
                background = roundedDrawable(
                    fillColor = applyAlpha(color(R.color.accent), 0x66),
                    strokeColor = applyAlpha(color(R.color.accent), 0xCC),
                    radiusDp = 20f,
                    shape = GradientDrawable.OVAL
                )
            }
            windowManager.addView(marker, markerLayoutParams(0, 0))
            pointMarkers.add(marker)
        }
        while (pointMarkers.size > points.size) {
            val removed = pointMarkers.removeAt(pointMarkers.size - 1)
            stopMarkerPulse(removed)
            removeViewSafely(removed)
        }

        points.forEachIndexed { index, point ->
            val marker = pointMarkers[index]
            windowManager.updateViewLayout(marker, markerLayoutParams(point.x, point.y))
            if (isClicking && index == currentPointIndex) {
                marker.alpha = 1f
                startMarkerPulse(marker)
            } else {
                stopMarkerPulse(marker)
                marker.alpha = 0.75f
                marker.scaleX = 1f
                marker.scaleY = 1f
            }
        }
    }

    private fun hideMarkers() {
        pointMarkers.forEach { marker ->
            stopMarkerPulse(marker)
            removeViewSafely(marker)
        }
        pointMarkers.clear()
    }

    private fun startMarkerPulse(view: View) {
        if (markerAnimators[view] != null) {
            return
        }
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.25f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.25f),
            PropertyValuesHolder.ofFloat("alpha", 0.5f, 1f)
        ).apply {
            duration = 600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        markerAnimators[view] = animator
    }

    private fun stopMarkerPulse(view: View) {
        markerAnimators.remove(view)?.cancel()
    }

    private fun floatLayoutParams(x: Int, y: Int) =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
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

    private fun roundedDrawable(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Float,
        shape: Int = GradientDrawable.RECTANGLE
    ) = GradientDrawable().apply {
        this.shape = shape
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setColor(fillColor)
        setStroke(dp(1), strokeColor)
    }

    private fun applyAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

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

    private val screenWidth: Int
        get() = resources.displayMetrics.widthPixels

    private val screenHeight: Int
        get() = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "autotapper_overlay_controller"
        private const val NOTIFICATION_ID = 1001
        private const val ANIMATION_DURATION = 250L
    }
}
