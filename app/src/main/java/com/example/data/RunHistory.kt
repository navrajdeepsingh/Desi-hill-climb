package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_history")
data class RunHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val distance: Float,
    val coinsCount: Int,
    val vehicleType: String,
    val score: Int,
    val timestamp: Long = System.currentTimeMillis()
)
