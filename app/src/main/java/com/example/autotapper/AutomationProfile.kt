package com.example.autotapper

data class AutomationProfile(
    val id: String,
    val name: String,
    val actions: List<AutomationAction>,
    val intervalMs: Long,
    val randomExtraMs: Long,
    val repeatCount: Int
)
