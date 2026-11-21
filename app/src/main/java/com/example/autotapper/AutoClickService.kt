package com.example.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.random.Random

class AutoClickService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var isClicking = false
    private var isPaused = false
    private var gestureInFlight = false
    private var pendingAction = AutoTapperConfig.PENDING_NONE
    private var sessionGeneration = 0
    private var roundsCompleted = 0
    private var actionIndex = 0
    private var actionRepeatProgress = 0

    private var actions: List<AutomationAction> = emptyList()
    private var intervalMs = 400L
    private var randomExtraMs = 80L
    private var repeatCount = 0

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoTapperConfig.ACTION_START_CLICKING -> startClicking()
                AutoTapperConfig.ACTION_PAUSE_CLICKING -> pauseClicking()
                AutoTapperConfig.ACTION_RESUME_CLICKING -> resumeClicking()
                AutoTapperConfig.ACTION_STOP_CLICKING -> {
                    if (isClicking || isPaused || pendingAction != AutoTapperConfig.PENDING_NONE) {
                        requestStop()
                    }
                }
                AutoTapperConfig.ACTION_PREVIEW_GESTURE -> {
                    val raw = intent.getStringExtra(AutoTapperConfig.EXTRA_GESTURE_JSON)
                    val gesture = raw?.let(AutoTapperConfig::gestureFromJson)
                    if (gesture == null || isClicking || isPaused || gestureInFlight) {
                        broadcastPreviewCompleted(success = false)
                    } else {
                        previewGesture(gesture)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerControlReceiver()
        broadcastClickState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        finishStop(showToast = false, message = null)
    }

    override fun onDestroy() {
        finishStop(showToast = false, message = null)
        unregisterControlReceiver()
        super.onDestroy()
    }

    private fun performNextAction() {
        if (!isClicking) {
            return
        }

        if (actions.isEmpty()) {
            finishStop(showToast = true, message = getString(R.string.point_required))
            return
        }

        if (repeatCount > 0 && roundsCompleted >= repeatCount) {
            finishStop(showToast = false, message = null)
            return
        }

        val action = actions[actionIndex]
        broadcastClickState()
        val generation = sessionGeneration
        val gesture = buildGesture(action) ?: run {
            finishStop(showToast = true, message = getString(R.string.click_dispatch_failed))
            return
        }

        gestureInFlight = true
        val dispatched: Boolean = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    gestureInFlight = false
                    if (generation != sessionGeneration) {
                        if (isClicking) performNextAction()
                        return
                    }
                    if (!isClicking && !isPaused &&
                        pendingAction == AutoTapperConfig.PENDING_NONE
                    ) {
                        return
                    }

                    if (pendingAction == AutoTapperConfig.PENDING_STOP) {
                        finishStop(showToast = false, message = null)
                        return
                    }

                    actionRepeatProgress++
                    if (actionRepeatProgress >= action.repeatCount) {
                        actionRepeatProgress = 0
                        actionIndex++
                        if (actionIndex >= actions.size) {
                            actionIndex = 0
                            roundsCompleted++
                        }
                    }
                    broadcastClickState()

                    if (repeatCount > 0 && roundsCompleted >= repeatCount) {
                        finishStop(showToast = false, message = null)
                        return
                    }

                    if (pendingAction == AutoTapperConfig.PENDING_PAUSE) {
                        pendingAction = AutoTapperConfig.PENDING_NONE
                        isPaused = true
                        broadcastClickState()
                        return
                    }

                    if (isPaused) return

                    val randomDelay: Long = if (randomExtraMs > 0) {
                        Random.nextLong(randomExtraMs + 1)
                    } else {
                        0L
                    }
                    val actionDelay = action.delayMs.takeIf { it > 0L } ?: intervalMs
                    scheduleNextAction(actionDelay + randomDelay)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    gestureInFlight = false
                    if (generation != sessionGeneration) return
                    if (pendingAction == AutoTapperConfig.PENDING_STOP) {
                        finishStop(showToast = false, message = null)
                    } else if (pendingAction == AutoTapperConfig.PENDING_PAUSE) {
                        pendingAction = AutoTapperConfig.PENDING_NONE
                        isPaused = true
                        broadcastClickState()
                    } else {
                        finishStop(
                            showToast = true,
                            message = getString(R.string.click_cancelled)
                        )
                    }
                }
            },
            null
        )

        if (!dispatched) {
            gestureInFlight = false
            finishStop(showToast = true, message = getString(R.string.click_dispatch_failed))
        }
    }

    private fun buildGesture(action: AutomationAction): GestureDescription? {
        val builder = GestureDescription.Builder()
        when (action) {
            is TapPoint -> {
                builder.addStroke(
                    GestureDescription.StrokeDescription(
                        Path().apply { moveTo(action.x.toFloat(), action.y.toFloat()) },
                        0L,
                        action.holdDurationMs
                    )
                )
            }

            is GestureAction -> {
                if (action.strokes.isEmpty()) return null
                action.strokes.take(MAX_GESTURE_STROKES).forEach { stroke ->
                    if (stroke.points.size < 2) return@forEach
                    val path = Path().apply {
                        val first = stroke.points.first()
                        moveTo(first.x.toFloat(), first.y.toFloat())
                        stroke.points.drop(1).forEach { point ->
                            lineTo(point.x.toFloat(), point.y.toFloat())
                        }
                    }
                    builder.addStroke(
                        GestureDescription.StrokeDescription(
                            path,
                            stroke.startOffsetMs,
                            stroke.durationMs.coerceIn(
                                AutoTapperConfig.MIN_GESTURE_DURATION_MS,
                                AutoTapperConfig.MAX_GESTURE_DURATION_MS
                            )
                        )
                    )
                }
            }
        }
        return runCatching { builder.build() }.getOrNull()
    }

    private fun previewGesture(action: GestureAction) {
        val gesture = buildGesture(action)
        if (gesture == null) {
            broadcastPreviewCompleted(success = false)
            return
        }
        gestureInFlight = true
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                    broadcastPreviewCompleted(success = true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                    broadcastPreviewCompleted(success = false)
                }
            },
            null
        )
        if (!dispatched) {
            gestureInFlight = false
            broadcastPreviewCompleted(success = false)
        }
    }

    private fun broadcastPreviewCompleted(success: Boolean) {
        sendBroadcast(
            Intent(AutoTapperConfig.ACTION_PREVIEW_COMPLETED)
                .setPackage(packageName)
                .putExtra(AutoTapperConfig.EXTRA_PREVIEW_SUCCESS, success)
        )
    }

    private fun startClicking() {
        if (pendingAction == AutoTapperConfig.PENDING_STOP) return
        if (pendingAction == AutoTapperConfig.PENDING_PAUSE) {
            resumeClicking()
            return
        }
        if (isPaused) {
            resumeClicking()
            return
        }
        actions = AutoTapperConfig.loadActions(this)
        intervalMs = AutoTapperConfig.prefs(this)
            .getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).coerceAtLeast(100L)
        randomExtraMs = AutoTapperConfig.prefs(this)
            .getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L)
        repeatCount = AutoTapperConfig.prefs(this)
            .getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).coerceAtLeast(0)

        if (actions.isEmpty()) {
            Toast.makeText(this, R.string.point_required, Toast.LENGTH_SHORT).show()
            return
        }

        roundsCompleted = 0
        actionIndex = 0
        actionRepeatProgress = 0
        sessionGeneration++
        pendingAction = AutoTapperConfig.PENDING_NONE
        isClicking = true
        isPaused = false
        broadcastClickState()
        clearPendingClicks()
        if (!gestureInFlight) performNextAction()
    }

    private fun pauseClicking() {
        if (!isClicking) return
        isClicking = false
        clearPendingClicks()
        if (gestureInFlight) {
            pendingAction = AutoTapperConfig.PENDING_PAUSE
            isPaused = false
            broadcastClickState()
        } else {
            pendingAction = AutoTapperConfig.PENDING_NONE
            isPaused = true
            broadcastClickState()
        }
    }

    private fun resumeClicking() {
        if (pendingAction == AutoTapperConfig.PENDING_STOP) return
        if (pendingAction == AutoTapperConfig.PENDING_PAUSE) {
            pendingAction = AutoTapperConfig.PENDING_NONE
            isPaused = false
            isClicking = true
            broadcastClickState()
            return
        }
        if (!isPaused) return
        isPaused = false
        isClicking = true
        broadcastClickState()
        if (!gestureInFlight) performNextAction()
    }

    private fun requestStop() {
        clearPendingClicks()
        if (gestureInFlight) {
            isClicking = false
            isPaused = false
            pendingAction = AutoTapperConfig.PENDING_STOP
            broadcastClickState()
        } else {
            finishStop(showToast = false, message = null)
        }
    }

    private fun finishStop(showToast: Boolean, message: String?) {
        val wasActive = isClicking || isPaused ||
            pendingAction != AutoTapperConfig.PENDING_NONE
        isClicking = false
        isPaused = false
        pendingAction = AutoTapperConfig.PENDING_NONE
        gestureInFlight = false
        sessionGeneration++
        clearPendingClicks()
        actionRepeatProgress = 0
        broadcastClickState()

        if (showToast && (wasActive || !message.isNullOrBlank())) {
            Toast.makeText(this, message ?: getString(R.string.click_stopped), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun scheduleNextAction(delayMs: Long) {
        handler.postDelayed({ performNextAction() }, delayMs)
    }

    private fun clearPendingClicks() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun broadcastClickState() {
        val isActive = isClicking || isPaused ||
            pendingAction != AutoTapperConfig.PENDING_NONE
        sendBroadcast(
            Intent(AutoTapperConfig.ACTION_CLICK_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(AutoTapperConfig.EXTRA_IS_CLICKING, isClicking)
                .putExtra(AutoTapperConfig.EXTRA_IS_PAUSED, isPaused)
                .putExtra(
                    AutoTapperConfig.EXTRA_CURRENT_POINT_INDEX,
                    if (isActive) actionIndex else -1
                )
                .putExtra(AutoTapperConfig.EXTRA_ROUNDS_COMPLETED, roundsCompleted)
                .putExtra(AutoTapperConfig.EXTRA_POINT_REPEAT_PROGRESS, actionRepeatProgress)
                .putExtra(AutoTapperConfig.EXTRA_PENDING_ACTION, pendingAction)
        )
    }

    private fun registerControlReceiver() {
        if (receiverRegistered) {
            return
        }

        val filter = IntentFilter().apply {
            addAction(AutoTapperConfig.ACTION_START_CLICKING)
            addAction(AutoTapperConfig.ACTION_PAUSE_CLICKING)
            addAction(AutoTapperConfig.ACTION_RESUME_CLICKING)
            addAction(AutoTapperConfig.ACTION_STOP_CLICKING)
            addAction(AutoTapperConfig.ACTION_PREVIEW_GESTURE)
        }
        ContextCompat.registerReceiver(
            this,
            controlReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterControlReceiver() {
        if (!receiverRegistered) {
            return
        }

        unregisterReceiver(controlReceiver)
        receiverRegistered = false
    }

    companion object {
        private const val MAX_GESTURE_STROKES = 10
    }
}
