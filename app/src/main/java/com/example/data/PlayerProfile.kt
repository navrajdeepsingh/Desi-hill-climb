package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_profile")
data class PlayerProfile(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 0,
    val engineLevel: Int = 1,
    val suspensionLevel: Int = 1,
    val gripLevel: Int = 1,
    val fuelCapacityLevel: Int = 1,
    val selectedVehicle: String = "Buggy"
)
