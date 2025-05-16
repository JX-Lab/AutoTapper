/*
*PermissionUtils.kt
*权限检查与请求工具类（悬浮窗权限与无障碍权限） */


package com.example.autotapper.util

import android.content.ComponentName 
import android.content.Context 
import android.content.Intent 
import android.provider.Settings 
import android.text.TextUtils 
import android.view.accessibility.AccessibilityManager

object PermissionUtils { 
    fun isOverlayPermissionGranted(context: Context): Boolean { 
        return Settings.canDrawOverlays(context) 
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponent = ComponentName(context, serviceClass)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        for (item in colonSplitter) {
            val enabledComponent = ComponentName.unflattenFromString(item)
            if (enabledComponent == expectedComponent) {
                return true
            }
        }
        return false
    }

    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

}