/*
*OverlayService.kt
*显示悬浮窗用于记录用户点击坐标。 */


package com.example.autotapper.service

import android.app.Service 
import android.content.Intent 
import android.graphics.PixelFormat
import android.os.IBinder 
import android.view.Gravity 
import android.view.LayoutInflater 
import android.view.MotionEvent 
import android.view.View 
import android.view.WindowManager 
import android.widget.Toast 
import com.example.autotapper.MainActivity 
import com.example.autotapper.R

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager 
    private lateinit var overlayView: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 300
        layoutParams.y = 300

        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                val intent = Intent("com.example.autotapper.UPDATE_POSITION")
                intent.putExtra("x", x)
                intent.putExtra("y", y)
                sendBroadcast(intent)

                Toast.makeText(this, "位置记录：($x, $y)", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
            true
        }

        windowManager.addView(overlayView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }

}

