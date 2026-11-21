package com.example.autotapper

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.appcompat.widget.TooltipCompat
import androidx.appcompat.app.AppCompatDelegate
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private var collapsedHandleView: ImageView? = null
    private var controllerView: LinearLayout? = null
    private var controllerStatusView: TextView? = null
    private var pointsTextView: TextView? = null
    private var playButtonView: ImageView? = null
    private var addButtonView: ImageView? = null
    private var clearButtonView: ImageView? = null
    private var stopButtonView: ImageView? = null
    private var settingsButtonView: ImageView? = null
    private var settingsPanelView: View? = null
    private var pickerView: View? = null
    private var pickerCounterView: TextView? = null
    private val pointMarkers: MutableList<View> = mutableListOf()
    private val markerAnimators: MutableMap<View, ObjectAnimator> = mutableMapOf()

    private var isControllerExpanded = false
    private var isClicking = false
    private var isPaused = false
    private var currentPointIndex = -1
    private var roundsCompleted = 0
    private var pointRepeatProgress = 0
    private var pendingAction = AutoTapperConfig.PENDING_NONE
    private var sessionStartCount = 0

    // 悬浮组件当前锚点位置（屏幕坐标，重力 TOP|START），拖动后随之更新
    private var floatX = -1
    private var floatY = -1

    // 收起状态下悬浮球应处的位置。展开后如未拖动控制器，收起时回到此位置。
    private var collapsedAnchorY = -1
    private var dockOnRight = true
    private var snapAnimator: ValueAnimator? = null
    private var settingsExpanded = false

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
                snapAnimator?.cancel()
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
                    collapsedAnchorY = params.y
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragActive) {
                    view.performClick()
                } else {
                    snapToNearestEdge(view, animate = true)
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
                    val wasClicking = isClicking
                    isClicking = intent.getBooleanExtra(AutoTapperConfig.EXTRA_IS_CLICKING, false)
                    isPaused = intent.getBooleanExtra(AutoTapperConfig.EXTRA_IS_PAUSED, false)
                    currentPointIndex =
                        intent.getIntExtra(AutoTapperConfig.EXTRA_CURRENT_POINT_INDEX, -1)
                    roundsCompleted = intent.getIntExtra(
                        AutoTapperConfig.EXTRA_ROUNDS_COMPLETED,
                        0
                    )
                    pointRepeatProgress = intent.getIntExtra(
                        AutoTapperConfig.EXTRA_POINT_REPEAT_PROGRESS,
                        0
                    )
                    pendingAction = intent.getIntExtra(
                        AutoTapperConfig.EXTRA_PENDING_ACTION,
                        AutoTapperConfig.PENDING_NONE
                    )
                    if (!wasClicking && isClicking && isControllerExpanded) {
                        showCollapsedHandle()
                    }
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
        applyLanguagePreference()
        applyThemePreference()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        controllerAlpha = AutoTapperConfig.getControllerAlpha(this)
        registerServiceReceiver()
        startAsForegroundService()
    }

    private fun applyThemePreference() {
        val mode = when (AutoTapperConfig.getThemeMode(this)) {
            AutoTapperConfig.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AutoTapperConfig.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyLanguagePreference() {
        val tags = when (AutoTapperConfig.getLanguageMode(this)) {
            AutoTapperConfig.LANGUAGE_CHINESE -> AutoTapperConfig.LANGUAGE_CHINESE
            AutoTapperConfig.LANGUAGE_ENGLISH -> AutoTapperConfig.LANGUAGE_ENGLISH
            else -> ""
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
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
        snapAnimator?.cancel()
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
        addButtonView = null
        clearButtonView = null
        stopButtonView = null
        settingsButtonView = null
        settingsPanelView = null
        runCatching { unregisterReceiver(serviceReceiver) }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildOverlayForTheme()
    }

    private fun rebuildOverlayForTheme() {
        val wasExpanded = isControllerExpanded
        val wasPicking = pickerView != null
        dismissPickerOverlay()
        hideMarkers()
        removeViewSafely(controllerView)
        removeViewSafely(collapsedHandleView)
        controllerView = null
        collapsedHandleView = null
        if (wasExpanded) showExpandedController(animate = false)
        else showCollapsedHandle(animate = false)
        if (wasPicking) showPickerOverlay()
    }

    private fun registerServiceReceiver() {
        val filter = IntentFilter().apply {
            addAction(AutoTapperConfig.ACTION_CLICK_STATE_CHANGED)
            addAction(AutoTapperConfig.ACTION_POINT_SELECTED)
            addAction(AutoTapperConfig.ACTION_CONTROLLER_ALPHA_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            serviceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
                when {
                    pendingAction == AutoTapperConfig.PENDING_STOP ->
                        getString(R.string.overlay_notification_stopping)
                    pendingAction == AutoTapperConfig.PENDING_PAUSE ->
                        getString(R.string.overlay_notification_pause_pending)
                    isClicking -> getString(R.string.overlay_notification_running)
                    isPaused -> getString(R.string.overlay_notification_paused)
                    else -> getString(R.string.overlay_notification_idle)
                }
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
        if (settingsExpanded) setControllerFocusable(false)
        dismissPickerOverlay()
        val previousController = controllerView
        controllerView = null
        controllerStatusView = null
        pointsTextView = null
        playButtonView = null
        addButtonView = null
        clearButtonView = null
        stopButtonView = null
        settingsButtonView = null
        settingsPanelView = null
        settingsExpanded = false
        hideMarkers()

        val x = if (dockOnRight) screenWidth - dp(48) - dp(8) else dp(8)
        val y = when {
            collapsedAnchorY >= 0 -> collapsedAnchorY
            floatY >= 0 -> floatY
            else -> (screenHeight - dp(48)) / 2
        }
        floatX = x
        floatY = y

        val handle = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setImageResource(R.drawable.ic_floating_handle)
            setColorFilter(color(R.color.primary))
            background = ContextCompat.getDrawable(this@OverlayService, R.drawable.bg_floating_handle)
            setOnClickListener { showExpandedController() }
            setOnTouchListener(dragTouchListener)
            elevation = 8f
            alpha = if (animate && previousController != null) 0f else controllerAlpha
        }
        collapsedHandleView = handle

        val addHandle = {
            if (collapsedHandleView === handle && !handle.isAttachedToWindow) {
                windowManager.addView(handle, floatLayoutParams(x, y))
                handle.post {
                    if (collapsedHandleView !== handle) {
                        return@post
                    }
                    snapToNearestEdge(handle, animate = false)
                }
                if (animate && previousController != null) {
                    handle.animate()
                        .alpha(controllerAlpha)
                        .setDuration(ANIMATION_DURATION)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
            }
        }

        if (animate && previousController != null) {
            previousController.isEnabled = false
            previousController.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(ANIMATION_DURATION / 2)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    removeViewSafely(previousController)
                    addHandle()
                }
                .start()
        } else {
            removeViewSafely(previousController)
            addHandle()
        }

        refreshControllerState()
    }

    private fun showExpandedController(animate: Boolean = true) {
        isControllerExpanded = true
        val previousHandle = collapsedHandleView
        collapsedHandleView = null

        if (floatX < 0 || floatY < 0) {
            floatX = if (dockOnRight) (screenWidth - dp(288)).coerceAtLeast(0) else dp(8)
            floatY = (screenHeight - dp(180)) / 2
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
            snapToNearestEdge(view, animate = false)
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

    private fun snapToNearestEdge(view: View, animate: Boolean) {
        if (view.width <= 0 || view.height <= 0) return
        val params = view.layoutParams as WindowManager.LayoutParams
        dockOnRight = params.x + view.width / 2 >= screenWidth / 2
        val targetX = if (dockOnRight) {
            (screenWidth - view.width - dp(8)).coerceAtLeast(0)
        } else {
            dp(8)
        }
        val targetY = params.y.coerceIn(
            dp(8),
            maxOf(dp(8), screenHeight - view.height - dp(8))
        )
        collapsedAnchorY = targetY
        snapAnimator?.cancel()
        if (!animate) {
            floatX = targetX
            floatY = targetY
            updateFloatPosition(view)
            return
        }

        val startX = params.x
        val startY = params.y
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val currentParams = view.layoutParams as? WindowManager.LayoutParams
                    ?: return@addUpdateListener
                currentParams.x = (startX + (targetX - startX) * progress).toInt()
                currentParams.y = (startY + (targetY - startY) * progress).toInt()
                runCatching { windowManager.updateViewLayout(view, currentParams) }
                floatX = currentParams.x
                floatY = currentParams.y
            }
            start()
        }
    }

    private fun buildControllerView(): LinearLayout {
        val isPicking = pickerView != null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@OverlayService, R.drawable.bg_overlay_panel)
            minimumWidth = dp(292)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = resources.getDimension(R.dimen.overlay_elevation)
            alpha = controllerAlpha
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val collapseButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_collapse,
            contentDescriptionRes = R.string.overlay_collapse_label
        ).apply {
            scaleX = if (dockOnRight) -1f else 1f
            isEnabled = !isPicking
            alpha = if (isPicking) 0.4f else 1f
            setOnClickListener { showCollapsedHandle() }
        }

        val closeButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_close,
            contentDescriptionRes = R.string.overlay_close_button_desc
        ).apply {
            setOnClickListener { closeController() }
        }

        val settingsButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_settings,
            contentDescriptionRes = R.string.overlay_settings_button_desc
        ).apply {
            setColorFilter(
                color(if (settingsExpanded) R.color.primary else R.color.text_primary)
            )
            setOnClickListener { toggleSettingsPanel() }
        }
        settingsButtonView = settingsButton

        header.addView(collapseButton)
        header.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        if (!isPicking) header.addView(settingsButton)
        header.addView(closeButton)

        root.addView(header)
        if (isPicking) {
            root.addView(buildPickerControls())
            return root
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }

        val addButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_add,
            contentDescriptionRes = R.string.overlay_add_button_desc
        ).apply {
            setOnClickListener {
                if (!isClicking && !isPaused &&
                    pendingAction == AutoTapperConfig.PENDING_NONE
                ) {
                    if (settingsExpanded) toggleSettingsPanel()
                    showPickerOverlay()
                }
            }
        }
        addButtonView = addButton

        val clearButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_delete,
            contentDescriptionRes = R.string.overlay_clear_button_desc
        ).apply {
            setOnClickListener { removeLastPoint() }
        }
        clearButtonView = clearButton

        val playButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_play,
            contentDescriptionRes = R.string.overlay_play_button_desc,
            primary = true
        ).apply {
            setOnClickListener { toggleClicking() }
        }
        playButtonView = playButton

        val stopButton = buildActionButton(
            iconRes = R.drawable.ic_overlay_stop,
            contentDescriptionRes = R.string.overlay_stop_button_desc
        ).apply {
            setColorFilter(color(R.color.danger))
            setOnClickListener { handleStopOrClearAll() }
        }
        stopButtonView = stopButton

        buttonRow.addView(addButton)
        buttonRow.addView(spacerView())
        buttonRow.addView(clearButton)
        buttonRow.addView(spacerView())
        buttonRow.addView(playButton)
        buttonRow.addView(spacerView())
        buttonRow.addView(stopButton)

        val statusView = TextView(this).apply {
            setTextColor(color(R.color.text_secondary))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            background = roundedDrawable(
                fillColor = color(R.color.surface_variant),
                strokeColor = color(R.color.outline_variant),
                radiusDp = 14f
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        controllerStatusView = statusView

        val pointsView = TextView(this).apply {
            setTextColor(color(R.color.on_primary_container))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(8), dp(2), 0)
        }
        pointsTextView = pointsView

        root.addView(buttonRow)
        root.addView(statusView)
        root.addView(pointsView)
        val settingsPanel = buildSettingsPanel()
        settingsPanel.visibility = if (settingsExpanded) View.VISIBLE else View.GONE
        settingsPanelView = settingsPanel
        root.addView(settingsPanel)
        return root
    }

    private fun buildActionButton(
        iconRes: Int,
        contentDescriptionRes: Int,
        primary: Boolean = false
    ) =
        ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setImageResource(iconRes)
            setColorFilter(color(if (primary) R.color.on_primary else R.color.text_primary))
            this.contentDescription = getString(contentDescriptionRes)
            background = ContextCompat.getDrawable(
                this@OverlayService,
                if (primary) R.drawable.bg_overlay_primary_button
                else R.drawable.bg_circular_button
            )
            TooltipCompat.setTooltipText(this, getString(contentDescriptionRes))
        }

    private fun spacerView() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(6), dp(1))
    }

    private fun buildPickerControls(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val counter = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setTextColor(color(R.color.on_primary_container))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = ContextCompat.getDrawable(this@OverlayService, R.drawable.bg_badge)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        pickerCounterView = counter
        updatePickerCounter()
        val cancel = buildActionButton(
            R.drawable.ic_overlay_close,
            R.string.cancel_capture
        ).apply { setOnClickListener { cancelPicking() } }
        val done = buildActionButton(
            R.drawable.ic_overlay_done,
            R.string.finish_capture,
            primary = true
        ).apply { setOnClickListener { finishPicking() } }
        row.addView(counter)
        row.addView(spacerView())
        row.addView(cancel)
        row.addView(spacerView())
        row.addView(done)
        return row
    }

    private fun buildSettingsPanel(): LinearLayout {
        val prefs = AutoTapperConfig.prefs(this)
        val interval = buildSettingField(
            R.string.overlay_setting_interval,
            prefs.getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).toString()
        )
        val random = buildSettingField(
            R.string.overlay_setting_random,
            prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).toString()
        )
        val rounds = buildSettingField(
            R.string.overlay_setting_rounds,
            prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).toString()
        )
        val hold = buildSettingField(
            R.string.overlay_setting_hold,
            AutoTapperConfig.getDefaultHoldDuration(this).toString()
        )
        val pointRepeat = buildSettingField(
            R.string.overlay_setting_repeat,
            AutoTapperConfig.getDefaultPointRepeat(this).toString()
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                fillColor = color(R.color.surface_variant),
                strokeColor = color(R.color.outline_variant),
                radiusDp = 14f
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        panel.addView(TextView(this).apply {
            text = getString(R.string.overlay_settings_title)
            setTextColor(color(R.color.text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(buildSettingRow(interval.first, random.first))
        panel.addView(buildSettingRow(hold.first, pointRepeat.first))
        panel.addView(buildSettingRow(rounds.first, null))
        panel.addView(TextView(this).apply {
            text = getString(R.string.overlay_settings_apply_all)
            setTextColor(color(R.color.text_secondary))
            textSize = 11f
            setPadding(0, dp(8), 0, 0)
        })
        panel.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
            addView(
                buildActionButton(
                    R.drawable.ic_overlay_done,
                    R.string.overlay_settings_save_desc,
                    primary = true
                ).apply {
                    setOnClickListener {
                        saveOverlaySettings(
                            interval.second,
                            random.second,
                            rounds.second,
                            hold.second,
                            pointRepeat.second
                        )
                    }
                }
            )
        })
        return panel
    }

    private fun buildSettingField(labelRes: Int, value: String): Pair<LinearLayout, EditText> {
        val input = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value)
            setSelectAllOnFocus(true)
            setTextColor(color(R.color.text_primary))
            setHintTextColor(color(R.color.text_secondary))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(
                fillColor = color(R.color.surface_container),
                strokeColor = color(R.color.outline),
                radiusDp = 10f
            )
            setPadding(dp(10), 0, dp(10), 0)
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.post {
                        val inputMethodManager =
                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
        val field = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(TextView(this@OverlayService).apply {
                text = getString(labelRes)
                setTextColor(color(R.color.text_secondary))
                textSize = 11f
                setPadding(0, 0, 0, dp(4))
            })
            addView(input)
        }
        return field to input
    }

    private fun buildSettingRow(first: View, second: View?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
            addView(first)
            if (second != null) {
                addView(View(this@OverlayService).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), 1)
                })
                addView(second)
            } else {
                addView(View(this@OverlayService).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                    )
                })
            }
        }

    private fun saveOverlaySettings(
        intervalInput: EditText,
        randomInput: EditText,
        roundsInput: EditText,
        holdInput: EditText,
        pointRepeatInput: EditText
    ) {
        val interval = intervalInput.text.toString().toLongOrNull()
        val random = randomInput.text.toString().toLongOrNull()
        val rounds = roundsInput.text.toString().toIntOrNull()
        val hold = holdInput.text.toString().toLongOrNull()
        val repeats = pointRepeatInput.text.toString().toIntOrNull()
        val valid = interval != null && interval >= 100L &&
            random != null && random >= 0L && rounds != null && rounds >= 0 &&
            hold != null && hold in AutoTapperConfig.MIN_HOLD_DURATION_MS..
            AutoTapperConfig.MAX_HOLD_DURATION_MS && repeats != null &&
            repeats in 1..AutoTapperConfig.MAX_POINT_REPEAT
        if (!valid) {
            Toast.makeText(this, R.string.overlay_settings_invalid, Toast.LENGTH_LONG).show()
            return
        }
        AutoTapperConfig.saveBaseConfig(this, interval!!, random!!, rounds!!)
        AutoTapperConfig.saveDefaultPointSettings(this, hold!!, repeats!!)
        AutoTapperConfig.applyDefaultPointSettingsToAll(this)
        broadcastPointsChanged()
        refreshControllerState()
        Toast.makeText(this, R.string.overlay_settings_saved, Toast.LENGTH_SHORT).show()
        toggleSettingsPanel()
    }

    private fun toggleSettingsPanel() {
        if (isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) return
        val panel = settingsPanelView ?: return
        settingsExpanded = !settingsExpanded
        settingsButtonView?.setColorFilter(
            color(if (settingsExpanded) R.color.primary else R.color.text_primary)
        )
        if (settingsExpanded) {
            setControllerFocusable(true)
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.translationY = -dp(8).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(200L).start()
            panel.post {
                controllerView?.let { snapToNearestEdge(it, animate = true) }
            }
        } else {
            setControllerFocusable(false)
            panel.animate()
                .alpha(0f)
                .translationY((-dp(8)).toFloat())
                .setDuration(160L)
                .withEndAction {
                    panel.visibility = View.GONE
                    controllerView?.post {
                        controllerView?.let { snapToNearestEdge(it, animate = true) }
                    }
                }
                .start()
        }
    }

    private fun setControllerFocusable(focusable: Boolean) {
        val view = controllerView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = if (focusable) {
            (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            (params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        }
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        runCatching { windowManager.updateViewLayout(view, params) }
        if (!focusable) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    private fun showPickerOverlay() {
        if (pickerView != null) return

        sessionStartCount = AutoTapperConfig.loadPoints(this).size
        settingsExpanded = false
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        view.findViewById<View>(R.id.capture_root).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                addPickedPoint(event.rawX.toInt(), event.rawY.toInt())
                true
            } else {
                true
            }
        }
        windowManager.addView(view, fullscreenLayoutParams())
        pickerView = view
        rebuildExpandedControllerOnTop()
        updatePickerCounter()
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
        rebuildExpandedControllerOnTop()
        refreshMarkers()
        Toast.makeText(
            this,
            getString(R.string.picking_finished_toast, AutoTapperConfig.loadPoints(this).size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cancelPicking() {
        rollbackPickedPoints()
        dismissPickerOverlay()
        rebuildExpandedControllerOnTop()
        refreshMarkers()
        Toast.makeText(this, R.string.picking_cancelled_toast, Toast.LENGTH_SHORT).show()
    }

    private fun rollbackPickedPoints() {
        val all = AutoTapperConfig.loadPoints(this)
        if (all.size > sessionStartCount) {
            AutoTapperConfig.savePoints(this, all.take(sessionStartCount))
        }
        broadcastPointsChanged()
    }

    private fun updatePickerCounter() {
        pickerCounterView?.text = getString(
            R.string.picker_counter_template,
            AutoTapperConfig.loadPoints(this).size
        )
    }

    private fun rebuildExpandedControllerOnTop() {
        removeViewSafely(controllerView)
        controllerView = null
        controllerStatusView = null
        pointsTextView = null
        playButtonView = null
        addButtonView = null
        clearButtonView = null
        stopButtonView = null
        settingsButtonView = null
        settingsPanelView = null
        showExpandedController(animate = false)
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
        if (isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) return

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

    private fun clearAllPoints() {
        if (isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) return
        if (AutoTapperConfig.loadPoints(this).isEmpty()) {
            Toast.makeText(this, R.string.no_point_to_delete_toast, Toast.LENGTH_SHORT).show()
            return
        }
        AutoTapperConfig.clearPoints(this)
        broadcastPointsChanged()
        refreshControllerState()
        refreshMarkers()
        Toast.makeText(this, R.string.clear_all_done, Toast.LENGTH_SHORT).show()
    }

    private fun handleStopOrClearAll() {
        if (isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_STOP_CLICKING).setPackage(packageName))
        } else {
            clearAllPoints()
        }
    }

    private fun toggleClicking() {
        if (pendingAction == AutoTapperConfig.PENDING_STOP) return
        if (pendingAction == AutoTapperConfig.PENDING_PAUSE) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_RESUME_CLICKING).setPackage(packageName))
            return
        }
        if (isClicking) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_PAUSE_CLICKING).setPackage(packageName))
            return
        }

        if (isPaused) {
            sendBroadcast(Intent(AutoTapperConfig.ACTION_RESUME_CLICKING).setPackage(packageName))
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

        if (settingsExpanded) toggleSettingsPanel()
        sendBroadcast(Intent(AutoTapperConfig.ACTION_START_CLICKING).setPackage(packageName))
    }

    private fun refreshControllerAlpha() {
        controllerAlpha = AutoTapperConfig.getControllerAlpha(this)
        controllerView?.alpha = controllerAlpha
        collapsedHandleView?.alpha = controllerAlpha
    }

    private fun refreshControllerState() {
        val hasPoint = AutoTapperConfig.hasPoints(this)
        val sessionActive = isClicking || isPaused ||
            pendingAction != AutoTapperConfig.PENDING_NONE

        collapsedHandleView?.apply {
            background = ContextCompat.getDrawable(this@OverlayService, R.drawable.bg_floating_handle)
            setColorFilter(color(R.color.primary))
            scaleX = if (isClicking) 1.08f else 1f
            scaleY = if (isClicking) 1.08f else 1f
        }

        playButtonView?.apply {
            setImageResource(if (isClicking) R.drawable.ic_overlay_pause else R.drawable.ic_overlay_play)
            isEnabled = pendingAction != AutoTapperConfig.PENDING_STOP
            alpha = if (isEnabled) 1f else 0.45f
            contentDescription = getString(
                when {
                    pendingAction == AutoTapperConfig.PENDING_PAUSE ->
                        R.string.overlay_resume_button_desc
                    isClicking -> R.string.overlay_pause_button_desc
                    isPaused -> R.string.overlay_resume_button_desc
                    else -> R.string.overlay_play_button_desc
                }
            )
            TooltipCompat.setTooltipText(this, contentDescription)
        }

        addButtonView?.apply {
            isEnabled = !sessionActive
            alpha = if (isEnabled) 1f else 0.45f
        }
        settingsButtonView?.apply {
            isEnabled = !sessionActive
            alpha = if (isEnabled) 1f else 0.45f
        }
        clearButtonView?.apply {
            isEnabled = hasPoint && !sessionActive
            alpha = if (isEnabled) 1f else 0.45f
        }
        stopButtonView?.apply {
            isEnabled = (sessionActive && pendingAction != AutoTapperConfig.PENDING_STOP) ||
                (!sessionActive && hasPoint)
            alpha = if (isEnabled) 1f else 0.45f
            val clearing = !sessionActive
            setImageResource(
                if (clearing) R.drawable.ic_overlay_clear_all else R.drawable.ic_overlay_stop
            )
            contentDescription = getString(
                if (clearing) R.string.overlay_clear_all_button_desc
                else R.string.overlay_stop_button_desc
            )
            TooltipCompat.setTooltipText(this, contentDescription)
        }
        controllerStatusView?.text = buildControllerStatusText()
        pointsTextView?.text = buildControllerPointsText()
    }

    private fun buildControllerStatusText(): String {
        val prefs = AutoTapperConfig.prefs(this)
        val globalIntervalMs = prefs.getLong(
            AutoTapperConfig.KEY_INTERVAL_MS,
            400L
        ).coerceAtLeast(100L)
        val randomExtraMs = prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L)
        val repeatCount = prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).coerceAtLeast(0)
        val currentPoint = AutoTapperConfig.loadPoints(this).getOrNull(currentPointIndex)
        val intervalMs = currentPoint?.delayMs?.takeIf { it > 0L } ?: globalIntervalMs
        val holdDurationMs = currentPoint?.holdDurationMs ?: TapPoint.DEFAULT_HOLD_DURATION_MS

        val repeatText = if (repeatCount == 0) {
            getString(R.string.overlay_repeat_infinite)
        } else {
            getString(R.string.overlay_repeat_finite, repeatCount)
        }
        val stateText = when {
            pendingAction == AutoTapperConfig.PENDING_STOP ->
                getString(R.string.overlay_state_stopping)
            pendingAction == AutoTapperConfig.PENDING_PAUSE ->
                getString(R.string.overlay_state_pause_pending)
            isClicking -> getString(R.string.overlay_state_running)
            isPaused -> getString(R.string.overlay_state_paused)
            else -> getString(R.string.overlay_state_idle)
        }

        return getString(
            R.string.overlay_status_template,
            stateText,
            intervalMs,
            intervalMs + randomExtraMs,
            repeatText,
            roundsCompleted,
            holdDurationMs
        )
    }

    private fun buildControllerPointsText(): String {
        val points = AutoTapperConfig.loadPoints(this)
        if (points.isEmpty()) {
            return getString(R.string.overlay_point_missing)
        }
        val maxShown = 2
        val head = points.take(maxShown).joinToString("  ") { "(${it.x},${it.y})" }
        val coordinates = if (points.size > maxShown) {
            "$head  ${getString(R.string.overlay_points_more, points.size - maxShown)}"
        } else {
            head
        }
        val current = points.getOrNull(currentPointIndex)
        if ((isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) &&
            current != null
        ) {
            val progress = getString(
                R.string.overlay_current_point_progress,
                currentPointIndex + 1,
                (pointRepeatProgress + 1).coerceAtMost(current.repeatCount),
                current.repeatCount
            )
            return "$progress\n$coordinates"
        }
        return coordinates
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
            if ((isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) &&
                index == currentPointIndex
            ) {
                marker.alpha = 1f
                if (isClicking) startMarkerPulse(marker) else stopMarkerPulse(marker)
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
        if (settingsExpanded) setControllerFocusable(false)
        if (pickerView != null) rollbackPickedPoints()
        stopClickingIfNeeded()
        stopSelf()
    }

    private fun stopClickingIfNeeded() {
        if (isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) {
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
        private const val SNAP_DURATION = 220L
    }
}
