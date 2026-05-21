package com.example.model

import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isHiddenInDb: Boolean,
    val isSystemDisabled: Boolean,
    val icon: Drawable,
    val lastUpdateTime: Long = 0L,
    val versionName: String = ""
)
