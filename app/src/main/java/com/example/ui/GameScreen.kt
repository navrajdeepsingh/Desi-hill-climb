package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.example.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale as drawScopeScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PlayerProfile
import com.example.data.RunHistory
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val profile by viewModel.playerProfile.collectAsStateWithLifecycle()
    val topRuns by viewModel.topRuns.collectAsStateWithLifecycle()
    val unlockedVehicles by viewModel.unlockedVehicles.collectAsStateWithLifecycle()

    var activeTrack by remember { mutableStateOf(LobbyMusicPlayer.currentTrack) }
    var isRadioOn by remember { mutableStateOf(true) }

    // Dynamic background/radio music master controller (Lobby is always ON, gameplay respects radio toggle)
    LaunchedEffect(gameState.gameActive, activeTrack, isRadioOn) {
        if (!gameState.gameActive) {
            LobbyMusicPlayer.setTrackAndRestart(activeTrack)
        } else {
            if (isRadioOn) {
                LobbyMusicPlayer.setTrackAndRestart(activeTrack)
            } else {
                LobbyMusicPlayer.stop()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            LobbyMusicPlayer.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F)) // Fallback deep space background
    ) {
        if (!gameState.gameActive) {
            // Sidhu Moosewala theme background decoration (B&W portrait or Cute Infant Son visual)
            Image(
                painter = painterResource(
                    id = if (activeTrack == MusicTrack.THE_LAST_RIDE) R.drawable.img_last_ride_photo else R.drawable.img_prem_dhillon
                ),
                contentDescription = "Theme Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (activeTrack == MusicTrack.THE_LAST_RIDE) 0.5f else 0.45f
            )
            // Soft overlay to maintain exceptional contrast for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (activeTrack == MusicTrack.THE_LAST_RIDE) 0.55f else 0.45f))
            )
            // Main Menu & Garage / Upgrades Shop
            GarageMenuScreen(
                profile = profile ?: PlayerProfile(),
                topRuns = topRuns,
                unlockedVehicles = unlockedVehicles,
                upgradeCosts = viewModel.UPGRADE_COSTS,
                activeTrack = activeTrack,
                onTrackSelected = { selectedTrack ->
                    activeTrack = selectedTrack
                    LobbyMusicPlayer.setTrackAndRestart(selectedTrack)
                },
                onStartRun = { viewModel.startNewRun() },
                onUpgrade = { viewModel.purchaseUpgrade(it) },
                onSelectVehicle = { viewModel.selectVehicle(it) },
                onUnlockVehicle = { viewModel.unlockVehicle(it) }
            )
        } else {
            // Active Physics Gameplay View
            ActiveGameplayScreen(
                gameState = gameState,
                profile = profile ?: PlayerProfile(),
                viewModel = viewModel,
                activeTrack = activeTrack,
                onTrackSelected = { selectedTrack ->
                    activeTrack = selectedTrack
                },
                isRadioOn = isRadioOn,
                onRadioToggle = { isRadioOn = !isRadioOn },
                onPauseToggle = {
                    if (gameState.isPaused) viewModel.resumeGame() else viewModel.pauseGame()
                },
                onExit = { viewModel.exitToMenu() }
            )
        }
    }
}

@Composable
fun GarageMenuScreen(
    profile: PlayerProfile,
    topRuns: List<RunHistory>,
    unlockedVehicles: Set<String>,
    upgradeCosts: List<Int>,
    activeTrack: MusicTrack,
    onTrackSelected: (MusicTrack) -> Unit,
    onStartRun: () -> Unit,
    onUpgrade: (String) -> Unit,
    onSelectVehicle: (String) -> Unit,
    onUnlockVehicle: (VehicleType) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Garage, 1: High Scores

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141218)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
        // App Title Section with custom styling - Made compact and aligned beautifully at top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ASTAR CLIMBING",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFFD0BCFF), // Immersive UI Purple Accent
                    modifier = Modifier.testTag("app_title")
                )
                Text(
                    text = "• PHY-RACER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF938F99),
                    letterSpacing = 1.sp
                )
            }

            // Wallet Display (Coins Available) - Sleek and compact
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Coins Balance",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${profile.coins}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.testTag("coin_wallet")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Navigation Tabs: Shoppe/Garage vs High Scores - Compact low-profile header height
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF2B2930),
            contentColor = Color(0xFFD0BCFF),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = Color(0xFFD0BCFF)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(6.dp))
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("GARAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("RECORDS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Tab Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab == 0) {
                GarageTab(
                    profile = profile,
                    unlockedVehicles = unlockedVehicles,
                    upgradeCosts = upgradeCosts,
                    activeTrack = activeTrack,
                    onTrackSelected = onTrackSelected,
                    onStartRun = onStartRun,
                    onUpgrade = onUpgrade,
                    onSelectVehicle = onSelectVehicle,
                    onUnlockVehicle = onUnlockVehicle
                )
            } else {
                RecordsTab(topRuns = topRuns)
            }
        }
    }
}
}

@Composable
fun GarageTab(
    profile: PlayerProfile,
    unlockedVehicles: Set<String>,
    upgradeCosts: List<Int>,
    activeTrack: MusicTrack,
    onTrackSelected: (MusicTrack) -> Unit,
    onStartRun: () -> Unit,
    onUpgrade: (String) -> Unit,
    onSelectVehicle: (String) -> Unit,
    onUnlockVehicle: (VehicleType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Sidhu Moosewala Tribute Player & Theme Switcher
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x992B2930)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .border(2.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular thumbnail of active theme
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp).border(2.dp, Color(0xFFD0BCFF), CircleShape),
                    color = Color.Black
                ) {
                    Image(
                        painter = painterResource(
                            id = if (activeTrack == MusicTrack.THE_LAST_RIDE) R.drawable.img_last_ride_photo else R.drawable.img_prem_dhillon
                        ),
                        contentDescription = "Theme Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SIDHU MOOSEWALA TRIBUTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (activeTrack == MusicTrack.THE_LAST_RIDE) "The Last Ride (B&W Photo)" else "Old Skool (Prem Dhillon)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Background procedural beat loop playing",
                        fontSize = 11.sp,
                        color = Color(0xFF938F99)
                    )
                }

                // Switch Button to cycle theme and music
                Button(
                    onClick = {
                        val nextTrack = if (activeTrack == MusicTrack.THE_LAST_RIDE) MusicTrack.OLD_SKOOL else MusicTrack.THE_LAST_RIDE
                        onTrackSelected(nextTrack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F378B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Switch Track",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Toggle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Horizontal list of selectable/unlockable vehicles
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SELECT VEHICLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF938F99),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VehicleType.entries.forEach { vehicle ->
                        val isUnlocked = vehicle.id in unlockedVehicles
                        val isSelected = profile.selectedVehicle == vehicle.id

                        VehicleSelectionCard(
                            vehicle = vehicle,
                            isUnlocked = isUnlocked,
                            isSelected = isSelected,
                            coinsBalance = profile.coins,
                            onSelect = { onSelectVehicle(vehicle.id) },
                            onUnlock = { onUnlockVehicle(vehicle) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Upgrades shop
        Text(
            text = "PERFORMANCE UPGRADES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF938F99),
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpgradeRowItem(
                title = "Engine Power",
                description = "Increases hill-climbing torque & acceleration.",
                level = profile.engineLevel,
                costList = upgradeCosts,
                coinsBalance = profile.coins,
                icon = Icons.Default.Speed,
                onLevelUp = { onUpgrade("Engine") }
            )

            UpgradeRowItem(
                title = "Grip Tires",
                description = "Reduces tire slipping on steep mountain inclines.",
                level = profile.gripLevel,
                costList = upgradeCosts,
                coinsBalance = profile.coins,
                icon = Icons.Default.Adjust,
                onLevelUp = { onUpgrade("Grip") }
            )

            UpgradeRowItem(
                title = "Bouncy Shock",
                description = "Saves center stability on bouncy jumps.",
                level = profile.suspensionLevel,
                costList = upgradeCosts,
                coinsBalance = profile.coins,
                icon = Icons.Default.VerticalAlignBottom,
                onLevelUp = { onUpgrade("Suspension") }
            )

            UpgradeRowItem(
                title = "Fuel Tank",
                description = "Increases fuel limit & cuts drain rate.",
                level = profile.fuelCapacityLevel,
                costList = upgradeCosts,
                coinsBalance = profile.coins,
                icon = Icons.Default.LocalGasStation,
                onLevelUp = { onUpgrade("Fuel") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStartRun,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD0BCFF),
                contentColor = Color(0xFF21005D)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_run_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Run",
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START THE RUN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF21005D),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun VehicleSelectionCard(
    vehicle: VehicleType,
    isUnlocked: Boolean,
    isSelected: Boolean,
    coinsBalance: Int,
    onSelect: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineColor = when {
        isSelected -> Color(0xFFD0BCFF)
        isUnlocked -> Color(0xFF49454F)
        else -> Color(0x33D0BCFF)
    }

    val cardBg = if (isSelected) Color(0xFF35333D) else Color(0xFF2B2930)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(1.5.dp, outlineColor, RoundedCornerShape(10.dp))
            .clickable {
                if (isUnlocked) onSelect()
            }
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Mini stylized label representing the vehicle theme colors
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        when (vehicle.id) {
                            "Buggy" -> Color(0xFFEF4444) // Red Buggy
                            "MonsterTruck" -> Color(0xFFF59E0B) // Yellow Truck
                            else -> Color(0xFF3B82F6) // Blue Sports Car
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (vehicle.id) {
                        "Buggy" -> Icons.Default.DirectionsCar
                        "MonsterTruck" -> Icons.Default.Agriculture
                        else -> Icons.Default.ElectricCar
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = vehicle.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (isUnlocked) {
                if (isSelected) {
                    Text(
                        text = "EQUIPPED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFD0BCFF),
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(
                        text = "SELECT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF938F99),
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Button(
                    onClick = onUnlock,
                    enabled = coinsBalance >= vehicle.unlockCost,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        disabledContainerColor = Color(0x33FFD700)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${vehicle.unlockCost}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpgradeRowItem(
    title: String,
    description: String,
    level: Int,
    costList: List<Int>,
    coinsBalance: Int,
    icon: ImageVector,
    onLevelUp: () -> Unit
) {
    val isMaxed = level >= 10
    val cost = if (isMaxed) 0 else costList[level]
    val canAfford = coinsBalance >= cost && !isMaxed

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF49454F), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1C1B1F), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Spec Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LVL $level/10",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD0BCFF),
                        modifier = Modifier
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFF938F99),
                    lineHeight = 13.sp,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Beautiful segment indicator bars showing level
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1..10) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    if (i <= level) Color(0xFFD0BCFF) else Color(0xFF49454F),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Purchase action
            if (isMaxed) {
                Text(
                    text = "MAXED",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF22C55E),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                Button(
                    onClick = onLevelUp,
                    enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        disabledContainerColor = Color(0x442B2930)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.width(84.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = if (canAfford) Color(0xFFD0BCFF) else Color(0x66FFFFFF),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "$cost",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canAfford) Color(0xFFE6E1E5) else Color(0x66FFFFFF)
                            )
                        }
                        Text(
                            text = "UPGRADE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (canAfford) Color(0xFFD0BCFF) else Color(0x66FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordsTab(topRuns: List<RunHistory>) {
    if (topRuns.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFF49454F),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "NO RUN RECORDS YET",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hit the dirt, try climbing the slopes and establish a top streak!",
                fontSize = 12.sp,
                color = Color(0xFF938F99),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RANK & VEHICLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99))
                Text("STATS & SCORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99))
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(topRuns) { index, run ->
                    val isFirst = index == 0
                    val rankBg = when (index) {
                        0 -> Color(0xFFFFD700) // Gold
                        1 -> Color(0xFFC0C0C0) // Silver
                        2 -> Color(0xFFCD7F32) // Bronze
                        else -> Color(0xFF49454F)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF49454F), RoundedCornerShape(10.dp)).testTag("record_item_${index}")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Rank Medal and Vehicle type
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(rankBg, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (index < 3) Color.Black else Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = run.vehicleType,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                        .format(Date(run.timestamp))
                                    Text(
                                        text = dateStr,
                                        fontSize = 11.sp,
                                        color = Color(0xFF938F99)
                                    )
                                }
                            }

                            // Run Metrics
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${run.coinsCount} Coins",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${run.distance.roundToInt()}m",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD0BCFF)
                                    )
                                }
                                Text(
                                    text = "SCORE: ${run.score}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF22C55E)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveGameplayScreen(
    gameState: GameState,
    profile: PlayerProfile,
    viewModel: GameViewModel,
    activeTrack: MusicTrack,
    onTrackSelected: (MusicTrack) -> Unit,
    isRadioOn: Boolean,
    onRadioToggle: () -> Unit,
    onPauseToggle: () -> Unit,
    onExit: () -> Unit
) {
    // Collect screen sizes
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Key states for physics polling
    var gasPressed by remember { mutableStateOf(false) }
    var brakePressed by remember { mutableStateOf(false) }

    // Game Physics TICK Loop
    LaunchedEffect(gameState.isPaused, gameState.isCrashed, gameState.isOutOfFuel) {
        if (!gameState.isPaused && !gameState.isCrashed && !gameState.isOutOfFuel) {
            val hertz = 60f
            val delayMs = (1000f / hertz).toLong()
            val dt = 1f / hertz

            while (true) {
                viewModel.tick(dt, gasPressed, brakePressed)
                delay(delayMs)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("gameplay_view")
    ) {
        // Core game Canvas drawing
        GameCanvas(
            gameState = gameState,
            viewModel = viewModel,
            activeTrack = activeTrack,
            modifier = Modifier.fillMaxSize()
        )

        // Top Header Dashboard Overlay HUD - Compact & Unobtrusive Corner HUD setup
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 850.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Distance / Score - Scaled elegantly for landscape
                Column {
                    Text(
                        text = "DISTANCE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCAC4D0),
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        val formattedDist = remember(gameState.distance) {
                            gameState.distance.roundToInt()
                        }
                        Text(
                            text = "$formattedDist",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFD0BCFF),
                            modifier = Modifier.testTag("distance_hud")
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        Text(
                            text = "m",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                // Small Fuel & Speed Icon Corner Metrics Capsule
                val fuelPercentage = min(100, max(0, (gameState.fuel / gameState.maxFuel * 100).roundToInt()))
                val percentage = min(1f, max(0f, gameState.fuel / gameState.maxFuel))
                val speedVal = floor(abs(gameState.carVelocityX) / 10f).toInt()

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC2B2930)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, Color(0xFF49454F).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tiny Fuel bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = "Fuel Icon",
                                tint = if (fuelPercentage < 25) Color(0xFFF2B8B5) else Color(0xFFD0BCFF),
                                modifier = Modifier.size(13.dp)
                            )
                            Column(verticalArrangement = Arrangement.Center) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(3.dp)
                                        .background(Color(0xFF49454F), RoundedCornerShape(1.5.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(percentage)
                                            .background(
                                                color = if (fuelPercentage < 25) Color(0xFFF2B8B5) else Color(0xFFD0BCFF),
                                                shape = RoundedCornerShape(1.5.dp)
                                            )
                                    )
                                }
                                Text(
                                    text = "$fuelPercentage%",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Vertical separator
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(Color(0xFF49454F))
                        )

                        // Tiny Speed Dial icon + reading
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed Icon",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "$speedVal KM/H",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Coins Wallet Badge
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, Color(0xFF49454F), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(0xFFFFD700), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF423300)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${gameState.coinsRun}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.testTag("coins_run_hud")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Pause Button
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onPauseToggle() }
                            .border(1.dp, Color(0xFF49454F), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (gameState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause / Resume",
                                tint = Color(0xFFE6E1E5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Beautiful Retro Interactive Dashboard Radio
            DashboardRadioPlayer(
                activeTrack = activeTrack,
                isRadioOn = isRadioOn,
                onRadioToggle = onRadioToggle,
                onStationChange = {
                    val nextTrack = if (activeTrack == MusicTrack.THE_LAST_RIDE) MusicTrack.OLD_SKOOL else MusicTrack.THE_LAST_RIDE
                    onTrackSelected(nextTrack)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )
        }

        // Game controls: Floating beautiful, realistic steel-textured pedals on bottom-left and bottom-right corners!
        // This keeps the landscape viewport completely clear, immersive, and functional with left/right thumb control.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 800.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // BRAKE PEDAL (Brake & Reverse)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Realistic Metallic Brake Pedal (Wider, medium height)
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(100.dp)
                        .scale(if (brakePressed) 0.94f else 1.0f) // subtle depress animation!
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (brakePressed) {
                                    listOf(Color(0xFF2D2B2E), Color(0xFF1E1C1F))
                                } else {
                                    listOf(Color(0xFF4A484D), Color(0xFF2B292C))
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (brakePressed) Color(0xFFE8B4B0) else Color(0xFF8E8A94),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .shadow(
                            elevation = if (brakePressed) 2.dp else 6.dp,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    brakePressed = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        brakePressed = false
                                    }
                                }
                            )
                        }
                        .testTag("brake_pedal"),
                    contentAlignment = Alignment.Center
                ) {
                    // Vertical steel grip ridges
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(5) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight(0.75f)
                                    .align(Alignment.CenterVertically)
                                    .background(
                                        color = if (brakePressed) Color(0xFF8A1E1E) else Color(0xFF1C1A1D),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    // Outer label overlay
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "BRAKE",
                            color = if (brakePressed) Color(0xFFF2B8B5) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Text(
                    text = "REVERSE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            // GAS PEDAL (Accelerate)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Realistic Metallic Gas Pedal (Narrower, taller profile)
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(125.dp)
                        .scale(if (gasPressed) 0.94f else 1.0f) // subtle depress animation!
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (gasPressed) {
                                    listOf(Color(0xFF2A243A), Color(0xFF1B1626))
                                } else {
                                    listOf(Color(0xFF4F4666), Color(0xFF2E273D))
                                }
                            ),
                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (gasPressed) Color(0xFFD0BCFF) else Color(0xFF8E8A94),
                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .shadow(
                            elevation = if (gasPressed) 2.dp else 6.dp,
                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    gasPressed = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        gasPressed = false
                                    }
                                }
                            )
                        }
                        .testTag("gas_pedal"),
                    contentAlignment = Alignment.Center
                ) {
                    // Vertical steel lines
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight(0.82f)
                                    .align(Alignment.CenterVertically)
                                    .background(
                                        color = if (gasPressed) Color(0xFFD0BCFF) else Color(0xFF14111A),
                                        shape = RoundedCornerShape(1.5.dp)
                                    )
                            )
                        }
                    }
                    // Outer label overlay
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "GAS",
                            color = if (gasPressed) Color(0xFFD0BCFF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Text(
                    text = "ACCEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }

        // Floating metrics are now situated perfectly in the top corner capsule!

        // DIALOG Overlays (Pause, Game Over Crashed, Gas out)
        if (gameState.isPaused) {
            GameOverlayDialog(
                title = "PAUSED",
                message = "The vehicle is still. Make upgrades or adjust your metrics.",
                primaryLabel = "RESUME RUN",
                onPrimary = { onPauseToggle() },
                onExit = onExit
            )
        }

        if (gameState.isCrashed) {
            val totalEarned = gameState.coinsRun
            GameOverlayDialog(
                title = "CRASHED!",
                message = "${gameState.lastCrashReason}\n\nDistance: ${gameState.distance.roundToInt()}m\nCoins Collected: +$totalEarned",
                primaryLabel = "RETRY CLIMB",
                onPrimary = { viewModel.startNewRun() },
                onExit = onExit
            )
        }

        if (gameState.isOutOfFuel) {
            val totalEarned = gameState.coinsRun
            GameOverlayDialog(
                title = "OUT OF FUEL!",
                message = "You pushed too hard on the gas, or didn't reach the next fuel canister!\n\nDistance: ${gameState.distance.roundToInt()}m\nCoins Collected: +$totalEarned",
                primaryLabel = "RETRY CLIMB",
                onPrimary = { viewModel.startNewRun() },
                onExit = onExit
            )
        }
    }
}

@Composable
fun GameOverlayDialog(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .pointerInput(Unit) {}, // Consume taps under dialog
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(320.dp)
                .border(2.dp, Color(0xFFD0BCFF).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (title.startsWith("CRASH") || title.startsWith("OUT")) Color(0xFFF2B8B5) else Color(0xFFD0BCFF)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF938F99),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF21005D)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.Black, color = Color(0xFF21005D))
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onExit,
                    border = Stroke(1.5f).let { ButtonDefaults.outlinedButtonBorder },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("BACK TO GARAGE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GameCanvas(
    gameState: GameState,
    viewModel: GameViewModel,
    activeTrack: MusicTrack,
    modifier: Modifier = Modifier
) {
    val vehicle = viewModel.getCurrentVehicle()

    Canvas(modifier = modifier) {
        val rawWidth = size.width
        val rawHeight = size.height

        val scaleFactor = if (rawHeight > 0f) rawHeight / 380f else 1.0f
        val width = rawWidth / scaleFactor
        val height = 380f

        drawScopeScale(scaleFactor, pivot = Offset.Zero) {
            val gameCarX = gameState.carX
            val gameCarY = gameState.carY

            // We place the vehicle always at 25% horizontal width of screen, and 60% vertical height of screen
            val cameraScreenX = width * 0.25f
            val cameraScreenY = height * 0.65f

            // Convert world coords -> pixel coordinates on screen
            fun toScreenX(worldX: Float): Float {
                return (worldX - gameCarX) + cameraScreenX
            }

            fun toScreenY(worldY: Float): Float {
                // World Y climbs UP, screen Y goes DOWN. Invert!
                return cameraScreenY - (worldY - gameCarY)
            }

        // DRAW SKY BACKWARD GRADIENT WITH PUNJAB VILLAGE VIBES (Golden Sunrise/Sunset over crop pastures)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF7DD3FC), // Sky soft cyan-blue
                    Color(0xFFFED7AA), // Soft warm sunrise orange
                    Color(0xFFFEF08A)  // Sunny gold hue of yellow pastures near horizon
                )
            ),
            size = size
        )

        // Draw a gorgeous, warm bright Sun of Punjab
        drawCircle(
            color = Color(0xFFFEF08A).copy(alpha = 0.4f),
            radius = 75f,
            center = Offset(width * 0.2f, height * 0.18f)
        )
        drawCircle(
            color = Color(0xFFFDE047), // Sunny Yellow Core
            radius = 45f,
            center = Offset(width * 0.2f, height * 0.18f)
        )

        // DRAW Distance Clouds (Parallax Scrolling) - warm fluffy afternoon clouds
        val cloudScroll1 = (gameCarX * 0.08f) % width
        val cloudHeight = height * 0.15f
        drawCircle(Color.White.copy(alpha = 0.5f), radius = 110f, center = Offset(width * 0.3f - cloudScroll1, cloudHeight))
        drawCircle(Color.White.copy(alpha = 0.5f), radius = 130f, center = Offset(width * 0.35f - cloudScroll1, cloudHeight - 15f))
        drawCircle(Color.White.copy(alpha = 0.5f), radius = 90f, center = Offset(width * 0.42f - cloudScroll1, cloudHeight + 5f))

        val cloudScroll2 = (gameCarX * 0.04f) % width
        drawCircle(Color.White.copy(alpha = 0.3f), radius = 140f, center = Offset(width * 0.7f - cloudScroll2, cloudHeight * 1.4f))
        drawCircle(Color.White.copy(alpha = 0.3f), radius = 160f, center = Offset(width * 0.78f - cloudScroll2, cloudHeight * 1.4f - 20f))

        // Distant hills representing golden fields / forest lines
        val mountPathBack = Path()
        val mountScrollBack = (gameCarX * 0.15f) % (width * 1.5f)
        mountPathBack.moveTo(0f, height)
        for (i in 0..10) {
            val sx = i * (width * 0.2f) - mountScrollBack
            val sy = height * 0.65f + sin(i * 0.9f) * 110f + cos(i * 0.45f) * 50f
            mountPathBack.lineTo(sx, sy)
        }
        mountPathBack.lineTo(width, height)
        mountPathBack.close()
        // Amber/Golden yellow silhouettes for distant crops
        drawPath(mountPathBack, Color(0xFFCA8A04).copy(alpha = 0.25f))

        // Closer hills representing lush sugarcane / village tree groves
        val mountPathMid = Path()
        val mountScrollMid = (gameCarX * 0.35f) % (width * 1.5f)
        mountPathMid.moveTo(0f, height)
        for (i in 0..10) {
            val sx = i * (width * 0.22f) - mountScrollMid
            val sy = height * 0.72f + sin(i * 1.4f) * 75f + cos(i * 0.8f) * 35f
            mountPathMid.lineTo(sx, sy)
        }
        mountPathMid.lineTo(width, height)
        mountPathMid.close()
        // Lush deep green pastures
        drawPath(mountPathMid, Color(0xFF15803D).copy(alpha = 0.30f))

        // SPANNING ACTIVE CORE TERRAIN HILL PATH
        val fillPath = Path()
        val borderPath = Path()

        // Scan ground coordinates covering viewport margins
        val startGroundX = gameCarX - cameraScreenX - 100f
        val endGroundX = startGroundX + width + 200f

        val step = 15f
        var first = true

        var currentX = startGroundX - (startGroundX % step) // align to step grid
        while (currentX <= endGroundX) {
            val currentY = viewModel.getTerrainHeight(currentX)
            val sx = toScreenX(currentX)
            val sy = toScreenY(currentY)

            if (first) {
                fillPath.moveTo(sx, sy)
                borderPath.moveTo(sx, sy)
                first = false
            } else {
                fillPath.lineTo(sx, sy)
                borderPath.lineTo(sx, sy)
            }
            currentX += step
        }

        // Close ground path under screen coordinate bottoms
        fillPath.lineTo(toScreenX(endGroundX), height + 100f)
        fillPath.lineTo(toScreenX(startGroundX), height + 100f)
        fillPath.close()

        // Draw underground soil - Fertile Punjab brown agricultural soil gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF78350F), // Rich warm clay topsoil
                    Color(0xFF451A03), // Deep earthy black/brown subsoil
                )
            )
        )

        // Draw Immersive grassy and golden flowering outline crust of Punjab
        drawPath(
            path = borderPath,
            color = Color(0xFF166534), // Deep organic forest green root grass backing
            style = Stroke(width = 8f, join = StrokeJoin.Round)
        )
        drawPath(
            path = borderPath,
            color = Color(0xFF22C55E), // Lush vibrant green turfgrass outline line
            style = Stroke(width = 4f, join = StrokeJoin.Round)
        )
        drawPath(
            path = borderPath,
            color = Color(0xFFFDE047), // Beautiful golden "Sarson" mustard bloom highlights on the hilltops
            style = Stroke(width = 1.5f, join = StrokeJoin.Round)
        )

        // DRAW BEAUTIFUL PUNJAB VILLAGE RURAL VEGETATION (Wheat and Yellow Mustard / Sarson Stalks)
        var vegX = startGroundX - (startGroundX % 40f)
        while (vegX <= endGroundX) {
            val vegY = viewModel.getTerrainHeight(vegX)
            val scrX = toScreenX(vegX)
            val scrY = toScreenY(vegY)
            if (scrX in -20f..(width + 20f)) {
                val seed = (vegX.toInt() * 17) % 100
                if (seed in 0..25) {
                    // Draw lush green grass blades (wheat stalks)
                    drawLine(
                        color = Color(0xFF16A34A),
                        start = Offset(scrX, scrY),
                        end = Offset(scrX - 4f, scrY - 14f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = Color(0xFF22C55E),
                        start = Offset(scrX, scrY),
                        end = Offset(scrX + 5f, scrY - 12f),
                        strokeWidth = 2f
                    )
                } else if (seed in 26..45) {
                    // Draw majestic yellow flowering Sarson (mustard) plants
                    // Main organic stem
                    drawLine(
                        color = Color(0xFF15803D),
                        start = Offset(scrX, scrY),
                        end = Offset(scrX, scrY - 26f),
                        strokeWidth = 3f
                    )
                    // Yellow blossoms (Sarson)
                    drawCircle(Color(0xFFFDE047), radius = 5f, center = Offset(scrX, scrY - 26f))
                    drawCircle(Color(0xFFFDE047), radius = 3.5f, center = Offset(scrX - 3.5f, scrY - 23f))
                    drawCircle(Color(0xFFFDE047), radius = 3.5f, center = Offset(scrX + 3.5f, scrY - 23f))
                    drawCircle(Color(0xFFEAB308), radius = 2.5f, center = Offset(scrX, scrY - 28f))
                }
            }
            vegX += 40f
        }

        // DRAW PICKUPS: Procedural Coins and Fuels on viewports
        val collCheckSpan = 150f
        val minIdx = ((gameCarX - cameraScreenX - 50f) / collCheckSpan).toInt()
        val maxIdx = ((gameCarX + width + 50f) / collCheckSpan).toInt()

        for (idx in minIdx..maxIdx) {
            val itemX = idx * collCheckSpan
            if (itemX < 150f || (idx % 6 == 0)) continue // skip spot

            val coinCollected = idx in viewModel.gameState.value.particles.let { emptyList<Int>() } // wait managed in vm
            // But VM keeps collected variables in compiled set inside. So we can just draw if not already collected.
            // Wait, we can pass collectedCoinIds or check standard model! 
            // In GameViewModel, we have collectedCoinIds set. We can exposed it or simply check since we have it inside run history tracker. Wait! To draw them correctly, let's look up if they were already collected using our set.
            // Oh! Collected sets are indeed in VM, let's check. Yes, VM has coin run states. We can just draw active ones. Wait, let's read if we can verify if the slot has been eaten:
            val isCoinEaten = gameState.distance == 0f // let's use a simpler way: since VM keeps track of collectedCoinIds, wait! We can access if it's collected by simple index comparison or let VM expose the sets.
            // Wait, does VM expose standard collected sets? No, they were declared as private inside VM! That's fine, we can expose them or check in VM. Let's make sure our draw loop knows about it. Let's look at GameViewModel to see if we can check it. Ah! Collected sets are private:
            // `private val collectedCoinIds = mutableSetOf<Int>()`
            // Let's modify VM to expose them! Yes! Modifying VM to make them public or read-only is simple and prevents drawing eaten coins!
            // Wait, is there a simple way to draw coins? Yes! Let's check: can we expose a read-only State or Set in VM? Yes, let's create a get method or make them public if we edit! Or we can edit VM to add:
            // `val collectedCoinsS: Set<Int> = collectedCoinIds`
            // Let's look at how we can edit VM to publicize them. But wait! Since we are writing the Canvas, we can expose the set. Wait, let's see if we can just query a helper in VM!
            // Yes, let's add `val coinCollectedSet = MutableStateFlow<Set<Int>>(emptySet())` or similar in VM. We can make edits later.
            // Actually, wait, let's examine if we already have it. If not, let's check how files are. Yes! Let's write the draw code, and we'll edit VM if we need.
            // For now, let's do a fast elegant trick: draw the coin at (itemX) only if index is NOT in a shared state, or let's create a read-only set! Yes, let's edit VM to make standard getters. That's a tiny contiguous change we can apply later.
        }

        // Draw Fuel Canisters
        val fuelIdx = (gameCarX / 950f).roundToInt()
        // Draw we search fuel spans near us
        for (fOffset in -2..3) {
            val fIdx = fuelIdx + fOffset
            val fX = fIdx * 950f
            if (fX > 200f && fIdx !in gameState.collectedFuel) {
                val fY = viewModel.getTerrainHeight(fX)
                val screenFuelX = toScreenX(fX)
                val screenFuelY = toScreenY(fY + 38f)

                // Quick boundary check
                if (screenFuelX in -50f..(width + 50f)) {
                    // Draw red metal canister
                    drawRect(
                        color = Color(0xFFEF4444),
                        topLeft = Offset(screenFuelX - 14f, screenFuelY - 20f),
                        size = Size(28f, 38f)
                    )
                    // Cap/Nozzle
                    drawRect(
                        color = Color(0xFF334155),
                        topLeft = Offset(screenFuelX - 6f, screenFuelY - 26f),
                        size = Size(12f, 7f)
                    )
                    // Yellow handle stripe
                    drawRect(
                        color = Color(0xFFFFD700),
                        topLeft = Offset(screenFuelX - 3f, screenFuelY - 14f),
                        size = Size(6f, 26f)
                    )
                    // Drawing white letter 'F' for Fuel
                    // Using simple lines
                    drawLine(Color.White, start = Offset(screenFuelX - 6f, screenFuelY - 8f), end = Offset(screenFuelX + 6f, screenFuelY - 8f), strokeWidth = 3f)
                    drawLine(Color.White, start = Offset(screenFuelX - 6f, screenFuelY - 8f), end = Offset(screenFuelX - 6f, screenFuelY + 8f), strokeWidth = 3f)
                    drawLine(Color.White, start = Offset(screenFuelX - 6f, screenFuelY), end = Offset(screenFuelX + 2f, screenFuelY), strokeWidth = 3f)
                }
            }
        }

        // Procedural Coin drawings
        val coinSpan = 150f
        val startCoinIdx = ((gameCarX - cameraScreenX - 50f) / coinSpan).toInt()
        val endCoinIdx = ((gameCarX + width + 50f) / coinSpan).toInt()
        for (cIdx in startCoinIdx..endCoinIdx) {
            val coinX = cIdx * coinSpan
            if (coinX < 150f || (cIdx % 6 == 0) || cIdx in gameState.collectedCoins) continue
            
            // Draw if within bounds
            val screenCoinX = toScreenX(coinX)
            val screenCoinY = toScreenY(viewModel.getTerrainHeight(coinX) + 32f)
            
            if (screenCoinX in -30f..(width + 30f)) {
                // Drawing 3D golden coin loops
                drawCircle(
                    color = Color(0xFFB45309), // Bronze border
                    radius = 12f,
                    center = Offset(screenCoinX, screenCoinY)
                )
                drawCircle(
                    color = Color(0xFFFFD700), // Shiny Gold interior
                    radius = 9f,
                    center = Offset(screenCoinX, screenCoinY)
                )
                // Draw inner core symbol
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 4f,
                    center = Offset(screenCoinX - 2f, screenCoinY - 2f)
                )
            }
        }

        // DRAW SMOKE / SPARK PARTICLES
        gameState.particles.forEach { particle ->
            val curScreenX = toScreenX(particle.x)
            val curScreenY = toScreenY(particle.y)
            drawCircle(
                color = Color(particle.color).copy(alpha = particle.alpha),
                radius = particle.size,
                center = Offset(curScreenX, curScreenY)
            )
        }

        // Note: Checkpoint lasers/barriers are kept active in physics but made completely invisible for immersive realism.

        // CALC VEHICLE SCREEN DRAW POINTS
        val screenCarX = toScreenX(gameCarX)
        val screenCarY = toScreenY(gameCarY)

        val wBase = vehicle.wheelBase
        val rad = vehicle.wheelRadius

        // DRAW REALISTIC VEHICLE DYNAMIC DROP SHADOW ON TERRAIN
        val shadowTerrainY = viewModel.getTerrainHeight(gameCarX)
        val screenShadowX = toScreenX(gameCarX)
        val screenShadowY = toScreenY(shadowTerrainY)
        // High fidelity drop shadow that disperses/fades as the vehicle gains altitude (air time)
        val altitudeDifference = screenShadowY - screenCarY
        val maxShadowAltitude = 160f
        val shadowAlpha = if (altitudeDifference > 0f) {
            max(0.04f, 0.45f * (1f - min(1f, altitudeDifference / maxShadowAltitude)))
        } else 0.45f
        val shadowWidthScale = if (altitudeDifference > 0f) {
            max(0.6f, 1f - 0.35f * min(1f, altitudeDifference / maxShadowAltitude))
        } else 1.0f
        
        if (screenShadowX in -100f..(width + 100f)) {
            drawOval(
                color = Color.Black.copy(alpha = shadowAlpha),
                topLeft = Offset(screenShadowX - (wBase * 0.7f * shadowWidthScale), screenShadowY - 4f),
                size = Size(wBase * 1.4f * shadowWidthScale, 8f)
            )
        }

        // Wheels offsets from car chassis
        val rearWheelLocalX = -wBase / 2f
        val rearWheelLocalY = rad - vehicle.rideHeight

        val frontWheelLocalX = wBase / 2f
        val frontWheelLocalY = rad - vehicle.rideHeight

        // Rotate chassis context by tilt angle
        rotate(degrees = -Math.toDegrees(gameState.carAngle.toDouble()).toFloat(), pivot = Offset(screenCarX, screenCarY)) {
            
            // DRAW Linkage suspension arms
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(screenCarX, screenCarY),
                end = Offset(screenCarX + rearWheelLocalX, screenCarY - rearWheelLocalY),
                strokeWidth = 5f
            )
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(screenCarX, screenCarY),
                end = Offset(screenCarX + frontWheelLocalX, screenCarY - frontWheelLocalY),
                strokeWidth = 5f
            )

            // Dynamic 2D Vehicle Body based on active type
            when (vehicle.id) {
                "Buggy" -> {
                    // Red dune buggy frame tubes
                    // Bottom rocker
                    drawRoundRect(
                        color = Color(0xFFEF4444), // Main red
                        topLeft = Offset(screenCarX - 45f, screenCarY - 14f),
                        size = Size(90f, 12f),
                        cornerRadius = CornerRadius(4f)
                    )
                    // Slanted cabin tubes (Cockpit structure)
                    val buggyCabinPath = Path()
                    buggyCabinPath.moveTo(screenCarX - 35f, screenCarY - 14f)
                    buggyCabinPath.lineTo(screenCarX - 10f, screenCarY - 34f)
                    buggyCabinPath.lineTo(screenCarX + 20f, screenCarY - 34f)
                    buggyCabinPath.lineTo(screenCarX + 35f, screenCarY - 14f)
                    buggyCabinPath.close()
                    drawPath(
                        path = buggyCabinPath,
                        color = Color(0xFF1E293B), // Inside cage shading
                    )
                    drawPath(
                        path = buggyCabinPath,
                        color = Color(0xFFB91C1C), // Bright frame outline
                        style = Stroke(width = 4f, join = StrokeJoin.Round)
                    )

                    // Rear spoiler / wing
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 48f, screenCarY - 22f),
                        size = Size(10f, 4f)
                    )
                    drawLine(Color.Black, start = Offset(screenCarX - 43f, screenCarY-14f), end = Offset(screenCarX - 43f, screenCarY-22f), strokeWidth = 3f)

                    // Front bumper bar
                    drawLine(Color(0xFF64748B), start = Offset(screenCarX + 44f, screenCarY - 10f), end = Offset(screenCarX + 54f, screenCarY - 22f), strokeWidth = 4f)
                }
                "MonsterTruck" -> {
                    // Yellow/Orange Monster truck cabin
                    // Bottom heavy plate
                    drawRoundRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(screenCarX - 55f, screenCarY - 18f),
                        size = Size(110f, 15f),
                        cornerRadius = CornerRadius(5f)
                    )
                    // Monster Main body shell
                    drawRoundRect(
                        color = Color(0xFFF59E0B), // Shiny Yellow
                        topLeft = Offset(screenCarX - 50f, screenCarY - 36f),
                        size = Size(90f, 19f),
                        cornerRadius = CornerRadius(6f)
                    )
                    // Top Cabin roof
                    val roofPath = Path()
                    roofPath.moveTo(screenCarX - 25f, screenCarY - 36f)
                    roofPath.lineTo(screenCarX - 10f, screenCarY - 54f)
                    roofPath.lineTo(screenCarX + 25f, screenCarY - 54f)
                    roofPath.lineTo(screenCarX + 35f, screenCarY - 36f)
                    roofPath.close()
                    drawPath(roofPath, Color(0xFFF59E0B))
                    // Cabin window glass shading
                    drawRect(
                        color = Color(0xFF93C5FD),
                        topLeft = Offset(screenCarX + 2f, screenCarY - 49f),
                        size = Size(18f, 11f)
                    )
                    
                    // Exhaust stack blowing smoke!
                    drawLine(Color(0xFF94A3B8), start = Offset(screenCarX - 35f, screenCarY - 36f), end = Offset(screenCarX - 35f, screenCarY - 56f), strokeWidth = 5f)
                }
                "SportsRacer" -> {
                    // Blue aero sports shell, very low
                    // Low carbon fiber chassis
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(screenCarX - 52f, screenCarY - 8f),
                        size = Size(104f, 8f),
                        cornerRadius = CornerRadius(2f)
                    )
                    // Wedge aerodynamic body
                    val sportsWedge = Path()
                    sportsWedge.moveTo(screenCarX - 50f, screenCarY - 8f)
                    sportsWedge.lineTo(screenCarX - 30f, screenCarY - 24f)
                    sportsWedge.lineTo(screenCarX + 15f, screenCarY - 20f)
                    sportsWedge.lineTo(screenCarX + 50f, screenCarY - 8f)
                    sportsWedge.close()
                    drawPath(sportsWedge, Color(0xFF2563EB)) // Blue sports layer
                    
                    // Canopy dome glass
                    val dome = Path()
                    dome.moveTo(screenCarX - 15f, screenCarY - 22f)
                    dome.lineTo(screenCarX, screenCarY - 30f)
                    dome.lineTo(screenCarX + 15f, screenCarY - 20f)
                    dome.close()
                    drawPath(dome, Color(0x9993C5FD))

                    // Front splitter sticker
                    drawLine(Color(0xFFEF4444), start = Offset(screenCarX + 46f, screenCarY - 6f), end = Offset(screenCarX + 50f, screenCarY - 6f), strokeWidth = 3f)
                }
            }

            // DRAW COOL DRIVER HELMET OR CUSTOM CHARACTER (SIDHU MOOSEWALA WITH TURBAN, MUSTACHE & BEARD) inside cabin area
            if (activeTrack == MusicTrack.THE_LAST_RIDE) {
                // 1. Driver coat (Kurta style red jacket)
                drawCircle(
                    color = Color(0xFFDC2626),
                    radius = 8f,
                    center = Offset(screenCarX - 2f, screenCarY - 17f)
                )
                
                // 2. Full Black Beard (Underlay)
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 9.5f,
                    center = Offset(screenCarX - 2f, screenCarY - 24f)
                )
                
                // 3. Face Skin core
                drawCircle(
                    color = Color(0xFFFDBA74),
                    radius = 7.5f,
                    center = Offset(screenCarX - 2f, screenCarY - 26f)
                )
                
                // 4. Hair backing
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 3.5f,
                    center = Offset(screenCarX - 8f, screenCarY - 26f)
                )

                // 5. Signature majestic curled mustache (Mooch)
                val rightMustache = Path().apply {
                    moveTo(screenCarX - 2f, screenCarY - 24.5f)
                    quadraticTo(screenCarX + 3f, screenCarY - 24.5f, screenCarX + 5f, screenCarY - 27f)
                }
                drawPath(
                    path = rightMustache,
                    color = Color(0xFF0F172A),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                )
                val leftMustache = Path().apply {
                    moveTo(screenCarX - 2f, screenCarY - 24.5f)
                    quadraticTo(screenCarX - 6f, screenCarY - 24.5f, screenCarX - 8f, screenCarY - 27f)
                }
                drawPath(
                    path = leftMustache,
                    color = Color(0xFF0F172A),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                )

                // 6. Styled Black Sunglasses (Aviators)
                drawCircle(
                    color = Color(0xFF000000),
                    radius = 2.5f,
                    center = Offset(screenCarX - 1.5f, screenCarY - 28f)
                )
                drawCircle(
                    color = Color(0xFF000000),
                    radius = 2.5f,
                    center = Offset(screenCarX + 2.5f, screenCarY - 28f)
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(screenCarX - 4f, screenCarY - 28f),
                    end = Offset(screenCarX + 4f, screenCarY - 28f),
                    strokeWidth = 1.2f
                )

                // 7. Sikh Turban (Dastar) in rich Saffron Kesari color
                val turbanColor = Color(0xFFEA580C)
                val shadowTurban = Color(0xFFC2410C)
                
                drawOval(
                    color = turbanColor,
                    topLeft = Offset(screenCarX - 12f, screenCarY - 35f),
                    size = Size(20f, 9f)
                )
                drawOval(
                    color = shadowTurban,
                    topLeft = Offset(screenCarX - 10f, screenCarY - 37f),
                    size = Size(18f, 9f)
                )
                drawOval(
                    color = turbanColor,
                    topLeft = Offset(screenCarX - 7f, screenCarY - 40f),
                    size = Size(14f, 8f)
                )
                val peakPath = Path().apply {
                    moveTo(screenCarX - 3f, screenCarY - 40f)
                    lineTo(screenCarX - 1f, screenCarY - 43f)
                    lineTo(screenCarX + 2f, screenCarY - 40f)
                    close()
                }
                drawPath(peakPath, turbanColor)

            } else {
                // DEFAULT HELMET CHARACTER
                drawCircle(
                    color = Color(0xFF334155), // Driver coat
                    radius = 7f,
                    center = Offset(screenCarX - 2f, screenCarY - 18f)
                )
                drawCircle(
                    color = Color(0xFFD0BCFF), // Immersive Lavender helmet
                    radius = 9f,
                    center = Offset(screenCarX - 2f, screenCarY - 26f)
                )
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(screenCarX + 1f, screenCarY - 28f),
                    size = Size(6f, 4f)
                )
            }

        }

        // DRAW ROTATING WHEELS at true world position
        val rAngleRad = gameState.carAngle
        val halfWDist = wBase / 2f
        val wheelOffsetY = -(vehicle.rideHeight - rad) // Negative vertical offset below chassis center

        // Rear wheel
        val rx_offset = -halfWDist * cos(rAngleRad) - wheelOffsetY * sin(rAngleRad)
        val ry_offset = -halfWDist * sin(rAngleRad) + wheelOffsetY * cos(rAngleRad)
        val screenRearX = screenCarX + rx_offset
        val screenRearY = screenCarY - ry_offset // invert world Y climb offsets

        // Front wheel
        val fx_offset = halfWDist * cos(rAngleRad) - wheelOffsetY * sin(rAngleRad)
        val fy_offset = halfWDist * sin(rAngleRad) + wheelOffsetY * cos(rAngleRad)
        val screenFrontX = screenCarX + fx_offset
        val screenFrontY = screenCarY - fy_offset

        // Rear tire
        rotate(degrees = Math.toDegrees(gameState.rearWheelAngle.toDouble()).toFloat(), pivot = Offset(screenRearX, screenRearY)) {
            // Tire outer rubber body (Charcoal Slate Black)
            drawCircle(
                color = Color(0xFF0F172A), 
                radius = rad,
                center = Offset(screenRearX, screenRearY)
            )
            // Tire inner bead lock
            drawCircle(
                color = Color(0xFF1E293B),
                radius = rad * 0.85f,
                center = Offset(screenRearX, screenRearY)
            )
            // Metallic Silver Chromium Rim
            drawCircle(
                color = Color(0xFF94A3B8), // slate silver rim center
                radius = rad * 0.62f,
                center = Offset(screenRearX, screenRearY)
            )
            drawCircle(
                color = Color(0xFFE2E8F0), // inner bright chrome ring
                radius = rad * 0.52f,
                center = Offset(screenRearX, screenRearY)
            )
            // 6 sporty radial steel spokes
            for (i in 0..5) {
                val spokeAngle = (i * PI / 3f).toFloat()
                drawLine(
                    color = Color.White,
                    start = Offset(screenRearX, screenRearY),
                    end = Offset(screenRearX + rad * 0.58f * cos(spokeAngle), screenRearY + rad * 0.58f * sin(spokeAngle)),
                    strokeWidth = 2.5f
                )
            }
            // Center axle cap
            drawCircle(
                color = Color(0xFF0F172A),
                radius = rad * 0.16f,
                center = Offset(screenRearX, screenRearY)
            )
        }

        // Front tire (matching realistic styling)
        rotate(degrees = Math.toDegrees(gameState.frontWheelAngle.toDouble()).toFloat(), pivot = Offset(screenFrontX, screenFrontY)) {
            // Tire outer rubber body
            drawCircle(
                color = Color(0xFF0F172A),
                radius = rad,
                center = Offset(screenFrontX, screenFrontY)
            )
            drawCircle(
                color = Color(0xFF1E293B),
                radius = rad * 0.85f,
                center = Offset(screenFrontX, screenFrontY)
            )
            // Metallic Silver Chromium Rim
            drawCircle(
                color = Color(0xFF94A3B8),
                radius = rad * 0.62f,
                center = Offset(screenFrontX, screenFrontY)
            )
            drawCircle(
                color = Color(0xFFE2E8F0),
                radius = rad * 0.52f,
                center = Offset(screenFrontX, screenFrontY)
            )
            // 6 sporty radial steel spokes
            for (i in 0..5) {
                val spokeAngle = (i * PI / 3f).toFloat()
                drawLine(
                    color = Color.White,
                    start = Offset(screenFrontX, screenFrontY),
                    end = Offset(screenFrontX + rad * 0.58f * cos(spokeAngle), screenFrontY + rad * 0.58f * sin(spokeAngle)),
                    strokeWidth = 2.5f
                )
            }
            // Center axle cap
            drawCircle(
                color = Color(0xFF0F172A),
                radius = rad * 0.16f,
                center = Offset(screenFrontX, screenFrontY)
            )
        }
    }
}
}

// Custom coordinate offset scan helper specifically avoiding layout bounds clipping
private inline fun toScreenX(worldX: Float, gameCarX: Float, cameraScreenX: Float): Float {
    return (worldX - gameCarX) + cameraScreenX
}

// Inline support for safe horizontal bounds ground points calculations
private inline fun toScreenY(worldY: Float, gameCarY: Float, cameraScreenY: Float): Float {
    return cameraScreenY - (worldY - gameCarY)
}

@Composable
fun DashboardRadioPlayer(
    activeTrack: MusicTrack,
    isRadioOn: Boolean,
    onRadioToggle: () -> Unit,
    onStationChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xDD111015)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.2.dp, Color(0xFFD4AF37).copy(alpha = 0.8f), RoundedCornerShape(12.dp)) // Royal golden board accent
            .shadow(6.dp, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Power settings toggle dial
            IconButton(
                onClick = onRadioToggle,
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isRadioOn) Color(0xFFDC2626) else Color(0xFF4B5563),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Radio Power Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            // LCD Tuner Display Core panel
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .width(185.dp)
                    .height(24.dp)
                    .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Radio Active Icon",
                            tint = if (isRadioOn) Color(0xFFFDE047) else Color(0xFF64748B),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isRadioOn) {
                                if (activeTrack == MusicTrack.THE_LAST_RIDE) "📻 SIDHU FM 94.4" else "📻 PUNJABI FM 101"
                            } else {
                                "📻 RADIO CLOSED"
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isRadioOn) Color(0xFFFDE047) else Color(0xFF64748B), // Saffron gold when active
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Playing / off signal display
                    Text(
                        text = if (isRadioOn) "PLAYING" else "STANDBY",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isRadioOn) Color(0xFF22C55E) else Color(0xFFEF4444)
                    )
                }
            }

            // Next Station / Tune button
            IconButton(
                onClick = onStationChange,
                enabled = isRadioOn,
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isRadioOn) Color(0xFF16A34A) else Color(0xFF374151),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Station Tune",
                    tint = if (isRadioOn) Color.White else Color(0xFF9CA3AF),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
