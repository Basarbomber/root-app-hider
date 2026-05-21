package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_apps")
data class HiddenAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val hiddenAt: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false
)
