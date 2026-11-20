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
    private var pointIndex = 0
    private var pointRepeatProgress = 0

    private var points: List<TapPoint> = emptyList()
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
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerControlReceiver()
        broadcastClickState()
        Toast.makeText(this, R.string.accessibility_ready, Toast.LENGTH_SHORT).show()
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

    private fun performClick() {
        if (!isClicking) {
            return
        }

        if (points.isEmpty()) {
            finishStop(showToast = true, message = getString(R.string.point_required))
            return
        }

        if (repeatCount > 0 && roundsCompleted >= repeatCount) {
            finishStop(showToast = true, message = getString(R.string.click_completed, roundsCompleted))
            return
        }

        val point = points[pointIndex]
        broadcastClickState()
        val generation = sessionGeneration

        val gesture: GestureDescription = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) },
                    0,
                    point.holdDurationMs
                )
            )
            .build()

        gestureInFlight = true
        val dispatched: Boolean = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    gestureInFlight = false
                    if (generation != sessionGeneration) {
                        if (isClicking) performClick()
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

                    pointRepeatProgress++
                    if (pointRepeatProgress >= point.repeatCount) {
                        pointRepeatProgress = 0
                        pointIndex++
                        if (pointIndex >= points.size) {
                            pointIndex = 0
                            roundsCompleted++
                        }
                    }
                    broadcastClickState()

                    if (repeatCount > 0 && roundsCompleted >= repeatCount) {
                        finishStop(
                            showToast = true,
                            message = getString(R.string.click_completed, roundsCompleted)
                        )
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
                    val pointDelay = point.delayMs.takeIf { it > 0L } ?: intervalMs
                    scheduleNextClick(pointDelay + randomDelay)
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
        points = AutoTapperConfig.loadPoints(this)
        intervalMs = AutoTapperConfig.prefs(this)
            .getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).coerceAtLeast(100L)
        randomExtraMs = AutoTapperConfig.prefs(this)
            .getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L)
        repeatCount = AutoTapperConfig.prefs(this)
            .getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).coerceAtLeast(0)

        if (points.isEmpty()) {
            Toast.makeText(this, R.string.point_required, Toast.LENGTH_SHORT).show()
            return
        }

        roundsCompleted = 0
        pointIndex = 0
        pointRepeatProgress = 0
        sessionGeneration++
        pendingAction = AutoTapperConfig.PENDING_NONE
        isClicking = true
        isPaused = false
        broadcastClickState()
        clearPendingClicks()
        if (!gestureInFlight) performClick()
        Toast.makeText(this, R.string.click_started, Toast.LENGTH_SHORT).show()
    }

    private fun pauseClicking() {
        if (!isClicking) return
        isClicking = false
        clearPendingClicks()
        if (gestureInFlight) {
            pendingAction = AutoTapperConfig.PENDING_PAUSE
            isPaused = false
            broadcastClickState()
            Toast.makeText(this, R.string.click_pause_pending, Toast.LENGTH_SHORT).show()
        } else {
            pendingAction = AutoTapperConfig.PENDING_NONE
            isPaused = true
            broadcastClickState()
            Toast.makeText(this, R.string.click_paused, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeClicking() {
        if (pendingAction == AutoTapperConfig.PENDING_STOP) return
        if (pendingAction == AutoTapperConfig.PENDING_PAUSE) {
            pendingAction = AutoTapperConfig.PENDING_NONE
            isPaused = false
            isClicking = true
            broadcastClickState()
            Toast.makeText(this, R.string.click_resumed, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isPaused) return
        isPaused = false
        isClicking = true
        broadcastClickState()
        if (!gestureInFlight) performClick()
        Toast.makeText(this, R.string.click_resumed, Toast.LENGTH_SHORT).show()
    }

    private fun requestStop() {
        clearPendingClicks()
        if (gestureInFlight) {
            isClicking = false
            isPaused = false
            pendingAction = AutoTapperConfig.PENDING_STOP
            broadcastClickState()
            Toast.makeText(this, R.string.click_stop_pending, Toast.LENGTH_SHORT).show()
        } else {
            finishStop(showToast = true, message = getString(R.string.click_stopped))
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
        pointRepeatProgress = 0
        broadcastClickState()

        if (showToast && (wasActive || !message.isNullOrBlank())) {
            Toast.makeText(this, message ?: getString(R.string.click_stopped), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun scheduleNextClick(delayMs: Long) {
        handler.postDelayed({ performClick() }, delayMs)
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
                    if (isActive) pointIndex else -1
                )
                .putExtra(AutoTapperConfig.EXTRA_ROUNDS_COMPLETED, roundsCompleted)
                .putExtra(AutoTapperConfig.EXTRA_POINT_REPEAT_PROGRESS, pointRepeatProgress)
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
}
