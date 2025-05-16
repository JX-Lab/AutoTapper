/*
*AutoClickService.kt
*使用无障碍服务模拟点击，支持循环点击与随机延迟。 */


package com.example.autotapper.service

import android.accessibilityservice.AccessibilityService 
import android.accessibilityservice.GestureDescription 
import android.graphics.Path 
import android.os.Handler 
import android.os.Looper 
import android.view.accessibility.AccessibilityEvent 
import android.widget.Toast 
import kotlin.random.Random

class AutoClickService : AccessibilityService() {
    companion object {
        var clickX: Int = 500 
        var clickY: Int = 500 
        var clickCount: Int = 10 
        var delayMin: Long = 300L 
        var delayMax: Long = 700L 
    }

    private var currentClick = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentClick = 0
        performClickLoop()
        Toast.makeText(this, "开始自动点击...", Toast.LENGTH_SHORT).show()
    }

    private fun performClickLoop() {
        if (currentClick >= clickCount) {
            Toast.makeText(this, "点击完成，共点击 $clickCount 次", Toast.LENGTH_SHORT).show()
            return
        }

        val path = Path().apply {
            moveTo(clickX.toFloat(), clickY.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                currentClick++
                val delay = Random.nextLong(delayMin, delayMax)
                handler.postDelayed({ performClickLoop() }, delay)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Toast.makeText(applicationContext, "点击被取消", Toast.LENGTH_SHORT).show()
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

}