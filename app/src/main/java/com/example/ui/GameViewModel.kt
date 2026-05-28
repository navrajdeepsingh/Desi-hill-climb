package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.data.PlayerProfile
import com.example.data.RunHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.*

enum class VehicleType(
    val id: String,
    val displayName: String,
    val description: String,
    val baseMass: Float,
    val baseEnginePower: Float,
    val baseGrip: Float,
    val baseSuspension: Float,
    val wheelBase: Float,
    val rideHeight: Float,
    val wheelRadius: Float,
    val unlockCost: Int
) {
    BUGGY("Buggy", "Desi Jeep", "Classic 4x4 rugged open-top vintage utility Jeep in military olive green.", 1.05f, 335f, 1.1f, 1.05f, 85f, 32f, 18f, 0),
    SPLENDOR("Splendor", "Desi Splendor", "Highly fuel efficient, the ultimate daily commuter bike.", 0.85f, 345f, 1.05f, 0.95f, 65f, 26f, 14f, 1200),
    THAR("Thar", "Mahindra Thar 4x4", "Heavy-duty offroader with massive climbing torque, unstoppable 4x4 power.", 1.55f, 460f, 1.4f, 1.35f, 96f, 42f, 23f, 2000),
    BULLET("Bullet", "Royal Bullet", "Heavy legendary cruiser, iconic retro thump sound & high mass.", 1.25f, 385f, 1.25f, 1.1f, 75f, 28f, 17f, 3000),
    MONSTER_TRUCK("MonsterTruck", "Monster Truck", "Heavy, giant tires, and deep bouncy suspension.", 1.5f, 440f, 1.3f, 1.4f, 100f, 45f, 26f, 4500),
    SPORTS_RACER("SportsRacer", "Sports Racer", "Low center of gravity, ultra-fast racing engine.", 0.9f, 520f, 1.5f, 0.7f, 90f, 22f, 16f, 6000)
}

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Long,
    val initialSize: Float,
    val size: Float,
    val alpha: Float,
    val maxLife: Float,
    val life: Float
)

data class GameState(
    val gameActive: Boolean = false,
    val isPaused: Boolean = false,
    val isCrashed: Boolean = false,
    val isOutOfFuel: Boolean = false,
    
    // Physical state
    val carX: Float = 0f,
    val carY: Float = 50f,
    val carVelocityX: Float = 0f,
    val carVelocityY: Float = 0f,
    val carAngle: Float = 0f,
    val carAngularVelocity: Float = 0f,
    val rearWheelAngle: Float = 0f,
    val frontWheelAngle: Float = 0f,
    
    // Game stats
    val fuel: Float = 100f,
    val maxFuel: Float = 100f,
    val coinsRun: Int = 0,
    val distance: Float = 0f,
    
    // Audio / Visual feedback offsets
    val lastCrashReason: String = "",
    val particles: List<Particle> = emptyList(),
    val collectedCoins: Set<Int> = emptySet(),
    val collectedFuel: Set<Int> = emptySet(),
    val barrierX: Float = 20f,
    val lastMilestonePlayed: Int = 0,
    val nitroCharges: Int = 0,
    val isNitroActive: Boolean = false,
    val nitroActiveTimeRemaining: Float = 0f
)

class GameViewModel(
    application: Application,
    private val repository: GameRepository
) : AndroidViewModel(application) {

    // Upgrades Pricing config
    val UPGRADE_COSTS = listOf(0, 150, 300, 600, 1200, 2000, 3500, 5000, 7500, 10000)
    val MAX_UPGRADE_LEVEL = 10

    // Database Flows
    val topRuns: StateFlow<List<RunHistory>> = repository.topRuns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playerProfile: StateFlow<PlayerProfile?> = repository.playerProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerProfile()
    )

    // Unlocked vehicle sets - stored inside high score database or simulated with a simple local bitmap
    // But we can store unlocked states as a string list in SharedPreferences or inside the DB!
    // Since we want standard offline persistence, let's keep unlocked vehicles stored in memory
    // and easily serialized or saved in SharedPreferences.
    private val _unlockedVehicles = MutableStateFlow<Set<String>>(setOf("Buggy"))
    val unlockedVehicles: StateFlow<Set<String>> = _unlockedVehicles.asStateFlow()

    private val _nitroCharges = MutableStateFlow(2)
    val nitroCharges: StateFlow<Int> = _nitroCharges.asStateFlow()

    // Active Game State
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    // Coin tracking list within active run
    private val collectedCoinIds = mutableSetOf<Int>()
    private val collectedFuelIds = mutableSetOf<Int>()
    private var maxCarXReached = 50f

    init {
        // Load unlocked vehicles and nitro charges from shared preferences OR save initial state
        val sp = application.getSharedPreferences("hill_climb_prefs", android.content.Context.MODE_PRIVATE)
        val saved = sp.getStringSet("unlocked_vehicles", setOf("Buggy")) ?: setOf("Buggy")
        _unlockedVehicles.value = saved
        
        val nitroCount = sp.getInt("nitro_charges", 2)
        _nitroCharges.value = nitroCount
    }

    private fun persistUnlockedVehicles(vehicles: Set<String>) {
        _unlockedVehicles.value = vehicles
        val sp = getApplication<Application>().getSharedPreferences("hill_climb_prefs", android.content.Context.MODE_PRIVATE)
        sp.edit().putStringSet("unlocked_vehicles", vehicles).apply()
    }

    // Select different vehicle
    fun selectVehicle(vehicleName: String) {
        val currentProfile = playerProfile.value ?: PlayerProfile()
        if (vehicleName in unlockedVehicles.value) {
            viewModelScope.launch {
                repository.saveProfile(currentProfile.copy(selectedVehicle = vehicleName))
            }
        }
    }

    // Purchase / Unlock vehicles
    fun unlockVehicle(vehicleType: VehicleType) {
        val currentProfile = playerProfile.value ?: PlayerProfile()
        if (currentProfile.coins >= vehicleType.unlockCost) {
            val updatedVehicles = unlockedVehicles.value + vehicleType.id
            persistUnlockedVehicles(updatedVehicles)
            
            viewModelScope.launch {
                repository.saveProfile(currentProfile.copy(
                    coins = currentProfile.coins - vehicleType.unlockCost,
                    selectedVehicle = vehicleType.id
                ))
            }
        }
    }

    // Purchase Upgrades (Engine, Suspension, Tires, Fuel)
    fun purchaseUpgrade(upgradeName: String) {
        val profile = playerProfile.value ?: PlayerProfile()
        val currentLevel = when (upgradeName) {
            "Engine" -> profile.engineLevel
            "Suspension" -> profile.suspensionLevel
            "Grip" -> profile.gripLevel
            "Fuel" -> profile.fuelCapacityLevel
            else -> 1
        }

        if (currentLevel >= MAX_UPGRADE_LEVEL) return
        val cost = UPGRADE_COSTS[currentLevel] // e.g. level 1 to level 2 costs cost[1] = 150

        if (profile.coins >= cost) {
            val updatedProfile = when (upgradeName) {
                "Engine" -> profile.copy(engineLevel = currentLevel + 1, coins = profile.coins - cost)
                "Suspension" -> profile.copy(suspensionLevel = currentLevel + 1, coins = profile.coins - cost)
                "Grip" -> profile.copy(gripLevel = currentLevel + 1, coins = profile.coins - cost)
                "Fuel" -> profile.copy(fuelCapacityLevel = currentLevel + 1, coins = profile.coins - cost)
                else -> profile
            }
            viewModelScope.launch {
                repository.saveProfile(updatedProfile)
            }
        }
    }

    // Purchase Nitro for exactly 100 coins
    fun purchaseNitro() {
        val profile = playerProfile.value ?: PlayerProfile()
        if (profile.coins >= 100) {
            viewModelScope.launch {
                repository.saveProfile(profile.copy(coins = profile.coins - 100))
            }
            val sp = getApplication<Application>().getSharedPreferences("hill_climb_prefs", android.content.Context.MODE_PRIVATE)
            val current = sp.getInt("nitro_charges", 2)
            val updated = current + 1
            sp.edit().putInt("nitro_charges", updated).apply()
            _nitroCharges.value = updated
            // Also update live running state if any active race
            if (_gameState.value.gameActive) {
                _gameState.update { it.copy(nitroCharges = updated) }
            }
        }
    }

    // Activate/use Nitro Boost during gameplay drive
    fun triggerNitro() {
        val state = _gameState.value
        if (state.nitroCharges > 0 && !state.isNitroActive && !state.isPaused && !state.isCrashed) {
            val updatedCharges = state.nitroCharges - 1
            _gameState.update {
                it.copy(
                    nitroCharges = updatedCharges,
                    isNitroActive = true,
                    nitroActiveTimeRemaining = 1.0f
                )
            }
            _nitroCharges.value = updatedCharges
            val sp = getApplication<Application>().getSharedPreferences("hill_climb_prefs", android.content.Context.MODE_PRIVATE)
            sp.edit().putInt("nitro_charges", updatedCharges).apply()
        }
    }

    // Direct buy and trigger under-the-fly when charges are 0 but user has 100 coins
    fun buyAndTriggerNitro() {
        val profile = playerProfile.value ?: PlayerProfile()
        val state = _gameState.value
        if (profile.coins >= 100 && !state.isNitroActive && !state.isPaused && !state.isCrashed) {
            viewModelScope.launch {
                repository.saveProfile(profile.copy(coins = profile.coins - 100))
            }
            _gameState.update {
                it.copy(
                    isNitroActive = true,
                    nitroActiveTimeRemaining = 1.0f
                )
            }
        }
    }

    // Game Core Operations
    fun startNewRun() {
        collectedCoinIds.clear()
        collectedFuelIds.clear()
        maxCarXReached = 50f
        
        // Pre-buffer the online stream for milestone audio so it plays instantly
        MilestoneSoundPlayer.preBuffer()
        
        // Start playing the custom driving engine audio streams
        DrivingSoundPlayer.start()
        
        val activeVehicle = getCurrentVehicle()
        val profile = playerProfile.value ?: PlayerProfile()
        
        // Multipliers from upgrade values
        val initialMaxFuel = 100f + (profile.fuelCapacityLevel - 1) * 12f
        val startY = getTerrainHeight(50f) + activeVehicle.rideHeight + 35f

        _gameState.value = GameState(
            gameActive = true,
            isPaused = false,
            isCrashed = false,
            isOutOfFuel = false,
            carX = 50f,
            carY = startY,
            carVelocityX = 0f,
            carVelocityY = 0f,
            carAngle = 0f,
            carAngularVelocity = 0f,
            fuel = initialMaxFuel,
            maxFuel = initialMaxFuel,
            coinsRun = 0,
            distance = 0f,
            particles = emptyList(),
            nitroCharges = _nitroCharges.value,
            isNitroActive = false,
            nitroActiveTimeRemaining = 0f
        )
    }

    fun pauseGame() {
        _gameState.update { it.copy(isPaused = true) }
        DrivingSoundPlayer.pause()
    }

    fun resumeGame() {
        _gameState.update { it.copy(isPaused = false) }
        DrivingSoundPlayer.resume()
    }

    fun exitToMenu() {
        // End active state
        _gameState.update { it.copy(gameActive = false) }
        DrivingSoundPlayer.stop()
    }

    // Physics constants
    private val gravity = -420f // pixels per sec^2
    private val airResistanceX = 0.05f
    private val airResistanceAngular = 1.5f

    // Get active vehicle physics properties
    fun getCurrentVehicle(): VehicleType {
        val selected = playerProfile.value?.selectedVehicle ?: "Buggy"
        return VehicleType.entries.firstOrNull { it.id == selected } ?: VehicleType.BUGGY
    }

    // Procedural terrain heights
    fun getTerrainHeight(x: Float): Float {
        if (x < 20f) {
            // Remove flat starting plane: turn into a steep drop-off cliff
            return -450f + (x - 20f) * 12f
        }
        
        // Base sine frequencies
        val baseHills = sin(x * 0.003f) * 120f
        val details = sin(x * 0.015f) * 35f
        val mountains = cos(x * 0.0006f) * 220f
        
        // Progression increments: steeper segments
        var slant = 0f
        if (x > 600f) {
            val progress = min(1f, (x - 600f) / 1000f)
            slant += progress * (x - 600f) * 0.06f // steep incline
        }
        if (x > 2000f) {
            val progress = min(1f, (x - 2000f) / 1500f)
            slant -= progress * (x - 2000f) * 0.12f // downhill slide
        }
        if (x > 4000f) {
            // Extreme waves
            slant += sin(x * 0.008f) * 180f
        }
        
        // Tiny noisy bumps for physical feedback
        val bumps = sin(x * 0.09f) * 4f * (1f + sin(x * 0.005f) * 0.5f)
        
        return baseHills + details + mountains + slant + bumps
    }

    // Terrain slope
    fun getTerrainSlope(x: Float): Float {
        val delta = 2f
        val h1 = getTerrainHeight(x - delta)
        val h2 = getTerrainHeight(x + delta)
        return (h2 - h1) / (2f * delta)
    }

    // Physics Engine Update (dt = elapsed delta-time in seconds)
    fun tick(dt: Float, gasPressed: Boolean, brakePressed: Boolean) {
        val state = _gameState.value
        if (!state.gameActive || state.isPaused || state.isCrashed || state.isOutOfFuel) {
            DrivingSoundPlayer.stop()
            return
        }

        // Dynamically adjust audio pitch and volume based on throttle / gas input
        DrivingSoundPlayer.setGasState(gasPressed)

        val vehicle = getCurrentVehicle()
        val profile = playerProfile.value ?: PlayerProfile()

        // Upgrades calculations
        val engineMultiplier = 1f + (profile.engineLevel - 1) * 0.15f
        val gripMultiplier = 1f + (profile.gripLevel - 1) * 0.12f
        val suspMultiplier = 1f + (profile.suspensionLevel - 1) * 0.15f

        // Fuel consumption rate
        val fuelDrainBase = 3.5f
        val currentFuelDrain = fuelDrainBase * (if (gasPressed) 2.2f else 1.0f) * (1f / (1f + (profile.fuelCapacityLevel - 1) * 0.05f))

        var newFuel = state.fuel - currentFuelDrain * dt
        if (newFuel <= 0f) {
            newFuel = 0f
            _gameState.update { it.copy(isOutOfFuel = true, fuel = 0f) }
            endRaceRun()
            return
        }

        // Calculate wheel dimensions and position
        val wBase = vehicle.wheelBase
        val wRadius = vehicle.wheelRadius
        val rideHeight = vehicle.rideHeight
        val mAngle = state.carAngle
        val halfW = wBase / 2f
        val wheelOffsetY = -(rideHeight - wRadius) // Negative vertical offset representing distance below chassis center

        // Rear wheel offset (rotated by mAngle)
        val rx_offset = -halfW * cos(mAngle) - wheelOffsetY * sin(mAngle)
        val ry_offset = -halfW * sin(mAngle) + wheelOffsetY * cos(mAngle)
        val rearX = state.carX + rx_offset
        val rearY = state.carY + ry_offset

        // Front wheel offset (rotated by mAngle)
        val fx_offset = halfW * cos(mAngle) - wheelOffsetY * sin(mAngle)
        val fy_offset = halfW * sin(mAngle) + wheelOffsetY * cos(mAngle)
        val frontX = state.carX + fx_offset
        val frontY = state.carY + fy_offset

        // Ground heights at wheel positions
        val rearGroundY = getTerrainHeight(rearX)
        val frontGroundY = getTerrainHeight(frontX)

        // Contact overlap detection
        val rearOverlap = (rearGroundY + wRadius) - rearY
        val frontOverlap = (frontGroundY + wRadius) - frontY

        val rearContact = rearOverlap > 0f
        val frontContact = frontOverlap > 0f
        val anyGroundContact = rearContact || frontContact

        var newIsNitroActive = state.isNitroActive
        var newNitroActiveTimeRemaining = state.nitroActiveTimeRemaining
        if (state.isNitroActive) {
            newNitroActiveTimeRemaining -= dt
            if (newNitroActiveTimeRemaining <= 0f) {
                newIsNitroActive = false
                newNitroActiveTimeRemaining = 0f
            }
        }

        var vx = state.carVelocityX
        var vy = state.carVelocityY
        var av = state.carAngularVelocity

        // Apply Nitro Boost thrust force along car's orientation
        if (newIsNitroActive) {
            val nitroForce = 900f // capped power boost (balanced and smooth)
            vx += nitroForce * cos(mAngle) * dt / vehicle.baseMass
            vy += (nitroForce * sin(mAngle) + 40f) * dt / vehicle.baseMass // gentle lift
        }

        // Shocks parameters
        val stiffness = 160f * suspMultiplier
        val dampening = 10f * suspMultiplier

        var appliedTorque = 0f
        var totalForceY = 0f

        // Calculate rear suspension force
        if (rearContact) {
            val rearVerticalSpeed = vy - av * halfW * cos(mAngle)
            val shockForceY = rearOverlap * stiffness - rearVerticalSpeed * dampening
            val finalShockForceY = max(0f, shockForceY)
            
            totalForceY += finalShockForceY
            // Clockwise torque when rear shock compresses
            appliedTorque -= finalShockForceY * (halfW / 240f)
        }

        // Calculate front suspension force
        if (frontContact) {
            val frontVerticalSpeed = vy + av * halfW * cos(mAngle)
            val shockForceY = frontOverlap * stiffness - frontVerticalSpeed * dampening
            val finalShockForceY = max(0f, shockForceY)
            
            totalForceY += finalShockForceY
            // Counter-clockwise torque when front shock compresses
            appliedTorque += finalShockForceY * (halfW / 240f)
        }

        // Apply external forces (Gravity & Springs)
        vy += (gravity + (totalForceY / vehicle.baseMass)) * dt
        av += (appliedTorque / vehicle.baseMass) * dt

        // Driver Controls & Motor Forces on Ground
        var newRearWheelAngle = state.rearWheelAngle
        var newFrontWheelAngle = state.frontWheelAngle

        // Setup particlesList (decay and map particles at start of tick)
        val particlesList = state.particles.map { p ->
            p.copy(
                x = p.x + p.vx * dt,
                y = p.y + p.vy * dt,
                life = p.life - dt,
                alpha = max(0f, p.life / p.maxLife),
                size = p.initialSize * (p.life / p.maxLife)
            )
        }.filter { it.life > 0 }.toMutableList()

        // Spawn gorgeous thrust fire particles from exhaust pipe if Nitro is active
        if (newIsNitroActive) {
            if (Math.random() < 0.72) {
                val exhaustOffsetX = -halfW * cos(mAngle) - wheelOffsetY * sin(mAngle) - 16f * cos(mAngle)
                val exhaustOffsetY = -halfW * sin(mAngle) + wheelOffsetY * cos(mAngle) - 16f * sin(mAngle)
                spawnFireParticle(state.carX + exhaustOffsetX, state.carY + exhaustOffsetY, particlesList)
            }
        }

        // Base roll rotation delta based strictly on current vehicle speed
        val baseRollDelta = (vx * dt) / wRadius

        if (anyGroundContact) {
            val hillAngle = atan2(frontGroundY - rearGroundY, frontX - rearX)
            
            // Dynamic slope gravity physics (uphill deceleration and downhill force)
            val slopeGravityStrength = gravity * sin(hillAngle)
            vx += slopeGravityStrength * cos(hillAngle) * dt
            vy += slopeGravityStrength * sin(hillAngle) * dt
            
            // Smoothed stabilization: align car representation with hill slope gently
            val angleError = hillAngle - mAngle
            val normalizedError = atan2(sin(angleError), cos(angleError))
            val stabilizationStrength = if (rearContact && frontContact) 8f else 1.5f
            av += normalizedError * stabilizationStrength * dt

            // Strong angular damping on ground to prevent weird vibration or infinite spinning
            val groundAngularDamping = 8f
            av -= av * groundAngularDamping * dt

            // Motor acceleration along hill slope
            val powerBase = vehicle.baseEnginePower * engineMultiplier * gripMultiplier
            val brakeBase = 220f * gripMultiplier

            if (gasPressed) {
                // RWD 2WD: Propulsion force is ONLY applied when the rear (back) tire has contact with the ground!
                if (rearContact) {
                    val forceX = powerBase * cos(hillAngle)
                    val forceY = powerBase * sin(hillAngle)
                    vx += forceX * dt / vehicle.baseMass
                    vy += forceY * dt / vehicle.baseMass
                    
                    // Active acceleration pitch torque (lively wheelie feel)
                    av += 4.5f * dt / vehicle.baseMass
                    
                    // Spin back wheel forward with some active power slip
                    newRearWheelAngle += baseRollDelta + 12f * dt
                    
                    // Smoke dust under active tire accumulated locally
                    if (Math.random() < 0.25) {
                        spawnSmokeParticle(rearX, rearY, particlesList)
                    }
                } else {
                    // Back wheel is in the air: no forward propulsion force!
                    // But back wheel spins super fast in mid-air because it is under power
                    newRearWheelAngle += baseRollDelta + 32f * dt
                }
                // Front wheel is passive (non-driven): only rolls matching background speed
                newFrontWheelAngle += baseRollDelta
            } else if (brakePressed) {
                // Brake or Reverse
                if (vx > 10f) {
                    // Brake force works on both/any wheels that have contact
                    vx -= brakeBase * sign(vx) * dt / vehicle.baseMass
                    vy -= brakeBase * sign(vy) * dt / vehicle.baseMass
                    av -= 2.0f * dt / vehicle.baseMass
                    // Sliding/lock-up rotation
                    newRearWheelAngle += baseRollDelta * 0.15f
                    newFrontWheelAngle += baseRollDelta * 0.15f
                } else {
                    // Reverse propulsion (Back-wheel drive reverse is also RWD)
                    if (rearContact) {
                        val revForceX = -120f * cos(hillAngle)
                        val revForceY = -120f * sin(hillAngle)
                        vx += revForceX * dt
                        vy += revForceY * dt
                        newRearWheelAngle += baseRollDelta - 8f * dt
                    } else {
                        // Backend is in the air: spins backwards uselessly
                        newRearWheelAngle += baseRollDelta - 24f * dt
                    }
                    newFrontWheelAngle += baseRollDelta
                }
            } else {
                // Slow down nicely due to rolling mechanical resistance/friction (solving "no friction")
                vx -= vx * 1.1f * dt
                // Pure rolling/coasting!
                newRearWheelAngle += baseRollDelta
                newFrontWheelAngle += baseRollDelta
            }
        } else {
            // Airborne controls (Nose tilt rotation)
            val airTorque = 4.2f
            if (gasPressed) {
                av += airTorque * dt
                // RWD: Rear wheel spins forward under power in mid-air, front wheel is passive
                newRearWheelAngle += 24f * dt
                newFrontWheelAngle += baseRollDelta
            } else if (brakePressed) {
                av -= airTorque * dt
                // Lock / stop wheels in mid-air
                newRearWheelAngle -= 12f * dt
                newFrontWheelAngle -= 12f * dt
            } else {
                // Pure rolling/coasting in air
                newRearWheelAngle += baseRollDelta
                newFrontWheelAngle += baseRollDelta
            }
        }

        // High air damping for super clean controls
        av -= av * airResistanceAngular * dt
        vx -= vx * airResistanceX * dt

        // Integrate positions
        var newCarX = state.carX + vx * dt
        var newCarY = state.carY + vy * dt
        var newCarAngle = mAngle + av * dt

        // Track the maximum X coordinate reached during the run
        if (newCarX > maxCarXReached) {
            maxCarXReached = newCarX
        }
        // Smooth dynamic barrier always 100 meters (1000f world units) behind the driver's maximum progress
        val barrierWorldX = max(20f, maxCarXReached - 1000f)

        // Robust rearmost tire-limit collision enforcement: do not let ANY part of the car cross the invisible barrier
        val halfWDist = vehicle.wheelBase / 2f
        val carRearLimitX = newCarX - halfWDist - vehicle.wheelRadius
        if (carRearLimitX < barrierWorldX) {
            newCarX = barrierWorldX + halfWDist + vehicle.wheelRadius
            vx = max(0f, vx)
        }

        // Normalize angle to its principle value [-PI, PI] to make comparison completely bug-free
        var normAngle = atan2(sin(newCarAngle), cos(newCarAngle))

        // Robust wheel-and-center multi-point terrain collision clamp to prevent any clipping under slopes
        val centerGroundY = getTerrainHeight(newCarX)
        val minHeight = centerGroundY + rideHeight - 4f
        var finalCarY = newCarY
        if (finalCarY < minHeight) {
            finalCarY = minHeight
        }

        val rearGroundYNew = getTerrainHeight(newCarX + rx_offset)
        val frontGroundYNew = getTerrainHeight(newCarX + fx_offset)
        
        val minRearY = rearGroundYNew + wRadius - 2f
        val currentRearY = finalCarY + ry_offset
        if (currentRearY < minRearY) {
            finalCarY += (minRearY - currentRearY)
        }

        val minFrontY = frontGroundYNew + wRadius - 2f
        val currentFrontY = finalCarY + fy_offset
        if (currentFrontY < minFrontY) {
            finalCarY += (minFrontY - currentFrontY)
        }

        if (finalCarY > newCarY) {
            newCarY = finalCarY
            vy = max(0f, vy)
        }

        // Bound extreme heights or falls
        if (newCarY < centerGroundY - 100f) {
            newCarY = centerGroundY + rideHeight
            vy = 0f
        }

        val displayDistance = max(0f, (newCarX - 50f) / 10f)

        // Crash mechanics: head touch ground, or car is extremely upside down on contact (relaxed for stability & spawn protection)
        val headLocalX = -2f
        val headLocalY = 26f
        val headWorldX = newCarX + headLocalX * cos(newCarAngle) - headLocalY * sin(newCarAngle)
        val headWorldY = newCarY + headLocalX * sin(newCarAngle) + headLocalY * cos(newCarAngle)
        val groundHeightAtHead = getTerrainHeight(headWorldX)

        val isHeadHittingGround = displayDistance > 0.8f && (headWorldY <= groundHeightAtHead + 2.0f)

        val isHeadCrashed = isHeadHittingGround || (displayDistance > 0.8f && (
            (newCarY <= centerGroundY + 12f && abs(normAngle) > PI / 1.9f) ||
            (abs(normAngle) > PI / 1.3f && anyGroundContact)
        ))

        if (isHeadCrashed) {
            _gameState.update {
                it.copy(
                    isCrashed = true,
                    carVelocityX = 0f,
                    carVelocityY = 0f,
                    carAngularVelocity = 0f,
                    lastCrashReason = "Ouch! Driver head collision!"
                )
            }
            endRaceRun()
            return
        }

        // Proc items collection and accumulate gold/green sparks in particlesList
        val (runCoins, fuelPickedUp) = checkPickups(newCarX, newCarY, particlesList)
        if (fuelPickedUp) {
            val activeMaxFuel = state.maxFuel
            newFuel = min(activeMaxFuel, newFuel + activeMaxFuel * 0.45f)
        }

        // Check 1000m milestones
        val currentMilestone = (displayDistance / 1000f).toInt()
        var updatedMilestone = state.lastMilestonePlayed
        if (currentMilestone > state.lastMilestonePlayed) {
            updatedMilestone = currentMilestone
            if (currentMilestone > 0) {
                // Play sound every 1000m (streams GDrive sound file, falls back to sweet synthetic triplet)
                MilestoneSoundPlayer.play()
                // Spectacular visual reward particle cascade centered at driver height
                spawnCelebrationSplash(newCarX, newCarY - 20f, particlesList)
            }
        }

        // SINGLE and atomic state update Flow emission
        _gameState.update {
            it.copy(
                carX = newCarX,
                carY = newCarY,
                carVelocityX = vx,
                carVelocityY = vy,
                carAngle = normAngle,
                carAngularVelocity = av,
                rearWheelAngle = newRearWheelAngle,
                frontWheelAngle = newFrontWheelAngle,
                fuel = newFuel,
                coinsRun = it.coinsRun + runCoins,
                distance = displayDistance,
                particles = particlesList,
                collectedCoins = collectedCoinIds.toSet(),
                collectedFuel = collectedFuelIds.toSet(),
                barrierX = barrierWorldX,
                lastMilestonePlayed = updatedMilestone,
                isNitroActive = newIsNitroActive,
                nitroActiveTimeRemaining = newNitroActiveTimeRemaining
            )
        }
    }

    private fun spawnCelebrationSplash(x: Float, y: Float, particlesList: MutableList<Particle>) {
        for (i in 0..45) {
            val angle = Math.random() * 2 * Math.PI
            val speed = 140f + (Math.random() * 220f).toFloat()
            particlesList.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (speed * cos(angle)).toFloat(),
                    vy = (speed * sin(angle)).toFloat(),
                    // Vibrant colors (Golden, Purple, Magenta, Cyan)
                    color = when (i % 4) {
                        0 -> 0xDDFFD700 // Gold star shimmer
                        1 -> 0xDDD0BCFF // Glowing violet
                        2 -> 0xDDF43F5E // Radiant rose pink
                        else -> 0xDD06B6D4 // Neon teal
                    },
                    initialSize = 7f + (Math.random() * 12f).toFloat(),
                    size = 1f,
                    alpha = 1f,
                    maxLife = 1.4f,
                    life = 1.4f
                )
            )
        }
    }

    private fun spawnSmokeParticle(wx: Float, wy: Float, particlesList: MutableList<Particle>) {
        val vx = -50f - (Math.random() * 80f).toFloat()
        val vy = 30f + (Math.random() * 60f).toFloat()
        val p = Particle(
            x = wx,
            y = wy,
            vx = vx,
            vy = vy,
            color = 0xAA8D6E5D, // dirty brownish-gray smoke
            initialSize = 3f + (Math.random() * 8f).toFloat(),
            size = 1f,
            alpha = 1f,
            maxLife = 0.6f,
            life = 0.6f
        )
        particlesList.add(p)
    }

    private fun spawnFireParticle(wx: Float, wy: Float, particlesList: MutableList<Particle>) {
        val vx = -190f - (Math.random() * 140f).toFloat() // blast fire backward strongly
        val vy = -25f + (Math.random() * 50f).toFloat()
        val randomColor = when ((Math.random() * 3).toInt()) {
            0 -> 0xFFFF2200L // glowing red fire core
            1 -> 0xFFFF8F00L // orange exhaust
            else -> 0xFFFFEE00L // golden yellow tips
        }
        val p = Particle(
            x = wx,
            y = wy,
            vx = vx,
            vy = vy,
            color = randomColor,
            initialSize = 5f + (Math.random() * 9f).toFloat(),
            size = 1f,
            alpha = 1.0f,
            maxLife = 0.42f,
            life = 0.42f
        )
        particlesList.add(p)
    }

    private fun checkPickups(cx: Float, cy: Float, particlesList: MutableList<Particle>): Pair<Int, Boolean> {
        var coinsAwarded = 0
        var fuelPickedUp = false
        val collRadius = 45f // collision distance

        // Spawn pickups procedurally at intervals
        val startXCheck = cx - 150f
        val endXCheck = cx + 150f

        // Fuel Canister check
        val fuelIdx = (cx / 950f).roundToInt()
        val fuelX = fuelIdx * 950f
        if (fuelX > 200f && fuelIdx !in collectedFuelIds) {
            val fuelY = getTerrainHeight(fuelX) + 38f
            val dx = cx - fuelX
            val dy = cy - fuelY
            val dist = sqrt(dx*dx + dy*dy)
            if (dist <= collRadius) {
                // Picked up fuel!
                collectedFuelIds.add(fuelIdx)
                fuelPickedUp = true
                spawnPickupSplash(fuelX, fuelY, 0xFF4CAF50, particlesList) // Green flash
            }
        }

        // Coin list checks - coins spawn every 150 pixels
        val coinSpan = 150f
        val minIdx = (startXCheck / coinSpan).toInt()
        val maxIdx = (endXCheck / coinSpan).toInt()

        for (idx in minIdx..maxIdx) {
            val coinX = idx * coinSpan
            if (coinX < 150f || (idx % 6 == 0)) continue // skip spawn if it coincides with a fuel canisters position
            
            if (idx !in collectedCoinIds) {
                val coinY = getTerrainHeight(coinX) + 32f
                val dx = cx - coinX
                val dy = cy - coinY
                val dist = sqrt(dx*dx + dy*dy)
                if (dist <= collRadius) {
                    collectedCoinIds.add(idx)
                    coinsAwarded += 5
                    spawnPickupSplash(coinX, coinY, 0xFFFFD700, particlesList) // Golden flash
                }
            }
        }

        return Pair(coinsAwarded, fuelPickedUp)
    }

    private fun spawnPickupSplash(x: Float, y: Float, color: Long, particlesList: MutableList<Particle>) {
        for (i in 0..12) {
            val angle = Math.random() * 2 * PI
            val speed = 80f + (Math.random() * 100f).toFloat()
            particlesList.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (speed * cos(angle)).toFloat(),
                    vy = (speed * sin(angle)).toFloat(),
                    color = color,
                    initialSize = 5f + (Math.random() * 6).toFloat(),
                    size = 1f,
                    alpha = 1f,
                    maxLife = 0.4f,
                    life = 0.4f
                )
            )
        }
    }

    // End run and persists metrics
    private fun endRaceRun() {
        // Stop playing the driving sounds immediately
        DrivingSoundPlayer.stop()

        val state = _gameState.value
        val profile = playerProfile.value ?: PlayerProfile()

        val finalDistance = state.distance
        val finalCoins = state.coinsRun
        val scoreFormula = (finalDistance * 10f + finalCoins * 40f).roundToInt()

        viewModelScope.launch {
            // Save run to DB
            repository.saveRun(
                RunHistory(
                    distance = finalDistance,
                    coinsCount = finalCoins,
                    vehicleType = profile.selectedVehicle,
                    score = scoreFormula
                )
            )

            // Add coins to user profile balance
            repository.saveProfile(
                profile.copy(
                    coins = profile.coins + finalCoins
                )
            )
        }
    }
}

class GameViewModelFactory(
    private val application: Application,
    private val repository: GameRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
