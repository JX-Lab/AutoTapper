package com.example.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.random.Random

class AutoClickService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var isClicking = false
    private var completedClicks = 0

    private var tapX = -1
    private var tapY = -1
    private var intervalMs = 400L
    private var randomExtraMs = 80L
    private var repeatCount = 0

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoTapperConfig.ACTION_START_CLICKING -> startClicking()
                AutoTapperConfig.ACTION_STOP_CLICKING -> {
                    if (isClicking) {
                        stopClicking(showToast = true, message = getString(R.string.click_stopped))
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerControlReceiver()
        broadcastClickState(false)
        Toast.makeText(this, R.string.accessibility_ready, Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopClicking(showToast = false, message = null)
    }

    override fun onDestroy() {
        stopClicking(showToast = false, message = null)
        unregisterControlReceiver()
        super.onDestroy()
    }

    private fun performClick() {
        if (!isClicking) {
            return
        }

        if (repeatCount > 0 && completedClicks >= repeatCount) {
            stopClicking(showToast = true, message = getString(R.string.click_completed, completedClicks))
            return
        }

        val gesture: GestureDescription = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply { moveTo(tapX.toFloat(), tapY.toFloat()) },
                    0,
                    40
                )
            )
            .build()

        val dispatched: Boolean = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    completedClicks++
                    if (!isClicking) {
                        return
                    }

                    val randomDelay: Long = if (randomExtraMs > 0) {
                        Random.nextLong(randomExtraMs + 1)
                    } else {
                        0L
                    }
                    scheduleNextClick(intervalMs + randomDelay)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    stopClicking(showToast = true, message = getString(R.string.click_cancelled))
                }
            },
            null
        )

        if (!dispatched) {
            stopClicking(showToast = true, message = getString(R.string.click_dispatch_failed))
        }
    }

    private fun startClicking() {
        val prefs = AutoTapperConfig.prefs(this)
        tapX = prefs.getInt(AutoTapperConfig.KEY_TAP_X, -1)
        tapY = prefs.getInt(AutoTapperConfig.KEY_TAP_Y, -1)
        intervalMs = prefs.getLong(AutoTapperConfig.KEY_INTERVAL_MS, 400L).coerceAtLeast(100L)
        randomExtraMs = prefs.getLong(AutoTapperConfig.KEY_RANDOM_EXTRA_MS, 80L).coerceAtLeast(0L)
        repeatCount = prefs.getInt(AutoTapperConfig.KEY_REPEAT_COUNT, 0).coerceAtLeast(0)

        if (tapX < 0 || tapY < 0) {
            Toast.makeText(this, R.string.point_required, Toast.LENGTH_SHORT).show()
            return
        }

        completedClicks = 0
        isClicking = true
        broadcastClickState(true)
        clearPendingClicks()
        performClick()
        Toast.makeText(this, R.string.click_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopClicking(showToast: Boolean, message: String?) {
        val wasClicking = isClicking
        isClicking = false
        clearPendingClicks()
        broadcastClickState(false)

        if (showToast && (wasClicking || !message.isNullOrBlank())) {
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

    private fun broadcastClickState(isRunning: Boolean) {
        sendBroadcast(
            Intent(AutoTapperConfig.ACTION_CLICK_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(AutoTapperConfig.EXTRA_IS_CLICKING, isRunning)
        )
    }

    private fun registerControlReceiver() {
        if (receiverRegistered) {
            return
        }

        val filter = IntentFilter().apply {
            addAction(AutoTapperConfig.ACTION_START_CLICKING)
            addAction(AutoTapperConfig.ACTION_STOP_CLICKING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(controlReceiver, filter)
        }
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
