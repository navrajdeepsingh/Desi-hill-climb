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

@Composable
fun VideoBackground(videoFile: java.io.File, modifier: Modifier = Modifier) {
    var viewLocal by remember { mutableStateOf<android.widget.VideoView?>(null) }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.widget.VideoView(context).apply {
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    try {
                        mp.setVolume(0f, 0f) // Mute audio since background only provides visuals
                    } catch (e: Exception) {
                        android.util.Log.e("VideoBackground", "Error muting video volume: ${e.message}")
                    }
                }
                setOnErrorListener { mp, what, extra ->
                    android.util.Log.e("VideoBackground", "Error playing video: what=$what, extra=$extra")
                    true // handled! prevents dialog popup or crash on video codec/file errors
                }
                try {
                    setVideoPath(videoFile.absolutePath)
                    start()
                } catch (e: Exception) {
                    android.util.Log.e("VideoBackground", "Error setting path: ${e.message}")
                }
                viewLocal = this
            }
        },
        update = { view ->
            try {
                if (!view.isPlaying) {
                    view.start()
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoBackground", "Error in AndroidView update: ${e.message}")
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                viewLocal?.stopPlayback()
            } catch (e: Exception) {
                android.util.Log.e("VideoBackground", "Error stopping VideoView: ${e.message}")
            }
        }
    }
}

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
    val unlockedMaps by viewModel.unlockedMaps.collectAsStateWithLifecycle()
    val selectedMapId by viewModel.selectedMap.collectAsStateWithLifecycle()
    val nitroCount by viewModel.nitroCharges.collectAsStateWithLifecycle()

    var activeTrack by remember { mutableStateOf(LobbyMusicPlayer.currentTrack) }
    var isRadioOn by remember { mutableStateOf(true) }
    val isOfflinePlayback by LobbyMusicPlayer.isOfflinePlaybackFlow.collectAsStateWithLifecycle()
    val downloadStates by LobbyMusicPlayer.downloadStates.collectAsStateWithLifecycle()
    val videoDownloadState by LobbyMusicPlayer.videoDownloadState.collectAsStateWithLifecycle()

    // Keep lobby music playing / stopped based on state changes smoothly
    LaunchedEffect(gameState.gameActive, activeTrack, isRadioOn) {
        if (!gameState.gameActive) {
            LobbyMusicPlayer.setTrackAndRestart(activeTrack)
        } else if (isRadioOn) {
            LobbyMusicPlayer.setTrackAndRestart(activeTrack)
        } else {
            LobbyMusicPlayer.stop()
        }
    }

    // Lifecycle-aware background/radio music master controller (Stops/pauses active audio when app is closed / backgrounded)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (!gameState.gameActive) {
                        LobbyMusicPlayer.setTrackAndRestart(activeTrack)
                    } else {
                        if (isRadioOn) {
                            LobbyMusicPlayer.setTrackAndRestart(activeTrack)
                        }
                        DrivingSoundPlayer.resume()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    LobbyMusicPlayer.stop()
                    MilestoneSoundPlayer.stop()
                    DrivingSoundPlayer.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            LobbyMusicPlayer.stop()
            MilestoneSoundPlayer.stop()
            DrivingSoundPlayer.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F)) // Fallback deep space background
    ) {
        if (!gameState.gameActive) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val cachedVideoFile = remember(videoDownloadState) {
                java.io.File(context.filesDir, "video_OLD_SKOOL.mp4")
            }
            val hasVideo = remember(videoDownloadState, activeTrack) {
                activeTrack == MusicTrack.OLD_SKOOL && 
                cachedVideoFile.exists() && 
                cachedVideoFile.length() > 2 * 1024 * 1024
            }

            if (hasVideo) {
                VideoBackground(
                    videoFile = cachedVideoFile,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Sidhu Moosewala theme background decoration (B&W portrait or Cute Infant Son visual)
                Image(
                    painter = painterResource(
                         id = when (activeTrack) {
                            MusicTrack.THE_LAST_RIDE -> R.drawable.img_last_ride_photo
                            MusicTrack.OLD_SKOOL -> R.drawable.img_prem_dhillon
                            MusicTrack.SIDHU_MOOSEWALA -> R.drawable.img_sidhu_son_bg
                            MusicTrack.LEGEND -> R.drawable.img_sidhu_son_bg
                            MusicTrack.MUSTANG -> R.drawable.img_last_ride_photo
                        }
                    ),
                    contentDescription = "Theme Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = when (activeTrack) {
                        MusicTrack.THE_LAST_RIDE -> 0.5f
                        MusicTrack.OLD_SKOOL -> 0.45f
                        MusicTrack.SIDHU_MOOSEWALA -> 0.55f
                        MusicTrack.LEGEND -> 0.50f
                        MusicTrack.MUSTANG -> 0.45f
                    }
                )
            }
            // Soft overlay to maintain exceptional contrast for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = when (activeTrack) {
                                MusicTrack.THE_LAST_RIDE -> 0.55f
                                MusicTrack.OLD_SKOOL -> if (hasVideo) 0.55f else 0.45f
                                MusicTrack.SIDHU_MOOSEWALA -> 0.50f
                                MusicTrack.LEGEND -> 0.50f
                                MusicTrack.MUSTANG -> 0.45f
                            }
                        )
                    )
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
                },
                onStartRun = { viewModel.startNewRun() },
                onUpgrade = { viewModel.purchaseUpgrade(it) },
                onSelectVehicle = { viewModel.selectVehicle(it) },
                onUnlockVehicle = { viewModel.unlockVehicle(it) },
                unlockedMaps = unlockedMaps,
                selectedMap = selectedMapId,
                onSelectMap = { viewModel.selectMap(it) },
                onUnlockMap = { viewModel.unlockMap(it) },
                isRadioOn = isRadioOn,
                onRadioToggle = { isRadioOn = !isRadioOn },
                nitroCharges = nitroCount,
                onBuyNitro = { viewModel.purchaseNitro() }
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
    onUnlockVehicle: (VehicleType) -> Unit,
    unlockedMaps: Set<String>,
    selectedMap: String,
    onSelectMap: (String) -> Unit,
    onUnlockMap: (MapType) -> Unit,
    isRadioOn: Boolean,
    onRadioToggle: () -> Unit,
    nitroCharges: Int,
    onBuyNitro: () -> Unit
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
                    onUnlockVehicle = onUnlockVehicle,
                    unlockedMaps = unlockedMaps,
                    selectedMap = selectedMap,
                    onSelectMap = onSelectMap,
                    onUnlockMap = onUnlockMap,
                    isRadioOn = isRadioOn,
                    onRadioToggle = onRadioToggle,
                    nitroCharges = nitroCharges,
                    onBuyNitro = onBuyNitro
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
    onUnlockVehicle: (VehicleType) -> Unit,
    unlockedMaps: Set<String>,
    selectedMap: String,
    onSelectMap: (String) -> Unit,
    onUnlockMap: (MapType) -> Unit,
    isRadioOn: Boolean,
    onRadioToggle: () -> Unit,
    nitroCharges: Int,
    onBuyNitro: () -> Unit
) {
    val isOfflinePlayback by LobbyMusicPlayer.isOfflinePlaybackFlow.collectAsStateWithLifecycle()
    val downloadStates by LobbyMusicPlayer.downloadStates.collectAsStateWithLifecycle()
    val videoDownloadState by LobbyMusicPlayer.videoDownloadState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Sidhu Moosewala Retro Radio Dashboard Receiver
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .border(2.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Radio",
                            tint = if (isRadioOn) Color(0xFFFDE047) else Color(0xFF64748B),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "SIDHU MOOSEWALA RAD-STREAM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD4AF37),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Lobby Dashboard Receiver",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    // Power Toggle switch
                    Button(
                        onClick = onRadioToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRadioOn) Color(0xFFEF4444) else Color(0xFF475569),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(
                            text = if (isRadioOn) "POWER ON" else "STANDBY",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // LCD Receiver Tuner Screen
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isRadioOn) "TUNED STATION" else "RADIO OFF",
                                fontSize = 8.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isRadioOn) {
                                    when (activeTrack) {
                                        MusicTrack.THE_LAST_RIDE -> "SIDHU FM — The Last Ride"
                                        MusicTrack.OLD_SKOOL -> "PUNJABI 101 — Old Skool"
                                        MusicTrack.SIDHU_MOOSEWALA -> "LEGEND FM — Sidhu Moose Wala"
                                        MusicTrack.LEGEND -> "LEGEND FM — Legend"
                                        MusicTrack.MUSTANG -> "MUSTANG FM — Mustang"
                                    }
                                } else {
                                    "PRESS POWER TO TUNER"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isRadioOn) Color(0xFFFDE047) else Color(0xFF475569),
                                letterSpacing = 0.5.sp
                            )
                            if (isRadioOn) {
                                Text(
                                    text = if (isOfflinePlayback) "✓ Playing Offline (High Quality Cached MP3)" else "📶 Streaming original studio audio",
                                    fontSize = 9.sp,
                                    color = if (isOfflinePlayback) Color(0xFF10B981) else Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Cycle station button
                        Button(
                            onClick = {
                                val nextTrack = when (activeTrack) {
                                    MusicTrack.OLD_SKOOL -> MusicTrack.THE_LAST_RIDE
                                    MusicTrack.THE_LAST_RIDE -> MusicTrack.SIDHU_MOOSEWALA
                                    MusicTrack.SIDHU_MOOSEWALA -> MusicTrack.LEGEND
                                    MusicTrack.LEGEND -> MusicTrack.MUSTANG
                                    MusicTrack.MUSTANG -> MusicTrack.OLD_SKOOL
                                }
                                onTrackSelected(nextTrack)
                            },
                            enabled = isRadioOn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Station",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TUNE", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // DIRECT SELECT STATION (Radio buttons)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "SELECT STATION DIRECTLY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MusicTrack.entries.forEach { track ->
                        val trackLabel = when (track) {
                            MusicTrack.OLD_SKOOL -> "Old Skool (Sidhu Moose Wala ft. Prem Dhillon)"
                            MusicTrack.THE_LAST_RIDE -> "The Last Ride (Sidhu Moose Wala)"
                            MusicTrack.SIDHU_MOOSEWALA -> "295 - Moosetape (Sidhu Moose Wala)"
                            MusicTrack.LEGEND -> "LEGEND (Sidhu Moose Wala)"
                            MusicTrack.MUSTANG -> "MUSTANG (Sidhu Moose Wala)"
                        }
                        val isSelected = activeTrack == track
                        val downloadState = downloadStates[track] ?: DownloadStatus.NotDownloaded

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isRadioOn) { onTrackSelected(track) }
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFD4AF37) else Color(0xFF334155),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { if (isRadioOn) onTrackSelected(track) },
                                enabled = isRadioOn,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFDE047),
                                    unselectedColor = Color(0xFF64748B),
                                    disabledSelectedColor = Color(0xFF475569)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.weight(1.0f)
                            ) {
                                Text(
                                    text = trackLabel,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                when (downloadState) {
                                    is DownloadStatus.Downloaded -> {
                                        Text(
                                            text = "✓ OFFLINE READY (HIGH QUALITY CACHED)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                    is DownloadStatus.Downloading -> {
                                        Text(
                                            text = "⚡ DOWNLOADING OFFLINE DATA: ${downloadState.progress}%",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                    is DownloadStatus.Failed -> {
                                        Text(
                                            text = "☁ ONLINE ONLY (TAP CLOUD TO RETRY)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "☁ ONLINE STREAM (TAP CLOUD TO DOWNLOAD)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                if (track == MusicTrack.OLD_SKOOL) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.clickable(enabled = isRadioOn && videoDownloadState !is DownloadStatus.Downloading) {
                                            if (videoDownloadState !is DownloadStatus.Downloaded) {
                                                LobbyMusicPlayer.downloadVideo()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Video Status",
                                            tint = when (videoDownloadState) {
                                                is DownloadStatus.Downloaded -> Color(0xFF38BDF8)
                                                is DownloadStatus.Downloading -> Color(0xFFFBBF24)
                                                is DownloadStatus.Failed -> Color(0xFFEF4444)
                                                else -> Color(0xFF64748B)
                                            },
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = when (videoDownloadState) {
                                                is DownloadStatus.Downloaded -> "✓ OFFLINE VIDEO ENABLED (TAP TO PREVIEW VIDEO)"
                                                is DownloadStatus.Downloading -> "⚡ DOWNLOADING HD VIDEO... ${(videoDownloadState as DownloadStatus.Downloading).progress}%"
                                                is DownloadStatus.Failed -> "🎥 OFFLINE VIDEO (TAP TO RETRY DOWNLOAD)"
                                                else -> "🎥 OFFLINE VIDEO AVAILABLE (TAP TO DOWNLOAD)"
                                            },
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (videoDownloadState) {
                                                is DownloadStatus.Downloaded -> Color(0xFF38BDF8)
                                                is DownloadStatus.Downloading -> Color(0xFFFDE047)
                                                is DownloadStatus.Failed -> Color(0xFFEF4444)
                                                else -> Color(0xFF94A3B8)
                                            }
                                        )
                                    }
                                }
                            }
                            
                            if (downloadState !is DownloadStatus.Downloaded && downloadState !is DownloadStatus.Downloading) {
                                IconButton(
                                    onClick = { LobbyMusicPlayer.downloadTrack(track) },
                                    enabled = isRadioOn,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = "Cache track offline",
                                        tint = if (downloadState is DownloadStatus.Failed) Color(0xFFEF4444) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else if (downloadState is DownloadStatus.Downloaded) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Cached",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (downloadState is DownloadStatus.Downloading) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFDE047),
                                    strokeWidth = 1.5.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
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

            // Nitro Propulsion Engine booster (Price: 100)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF49454F), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF1C1B1F), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFDE047),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nitro Rocket Booster",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CHARGES: $nitroCharges",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFDE047),
                                modifier = Modifier
                                    .background(Color(0xFFFDE047).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Unleash extreme acceleration during gameplay! Standard cost: 100 coins.",
                            fontSize = 11.sp,
                            color = Color(0xFF938F99),
                            lineHeight = 13.sp,
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val canAffordNitro = profile.coins >= 100
                    Button(
                        onClick = onBuyNitro,
                        enabled = canAffordNitro,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFDE047),
                            contentColor = Color(0xFF020617),
                            disabledContainerColor = Color(0x3349454F),
                            disabledContentColor = Color(0x66938F99)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BUY", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text("100 🪙", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Map selection section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                .testTag("select_environment_panel")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SELECT ENVIRONMENT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MapType.entries.forEach { map ->
                        val isUnlocked = map.id in unlockedMaps
                        val isSelected = selectedMap == map.id

                        MapSelectionCard(
                            map = map,
                            isUnlocked = isUnlocked,
                            isSelected = isSelected,
                            coinsBalance = profile.coins,
                            onSelect = { onSelectMap(map.id) },
                            onUnlock = { onUnlockMap(map) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
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
                            "Buggy" -> Color(0xFF4C583E) // Olive Green Desi Jeep
                            "Splendor" -> Color(0xFF06B6D4) // Cyan Splendor
                            "Thar" -> Color(0xFF991B1B) // Bold Crimson Red Thar
                            "Bullet" -> Color(0xFF1E293B) // Dark Black Bullet
                            "MonsterTruck" -> Color(0xFFF59E0B) // Yellow Truck
                            else -> Color(0xFF2563EB) // Blue Sports Car
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (vehicle.id) {
                        "Buggy" -> Icons.Default.DirectionsCar
                        "Splendor" -> Icons.Default.DirectionsBike
                        "Thar" -> Icons.Default.TimeToLeave
                        "Bullet" -> Icons.Default.DirectionsBike
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
fun MapSelectionCard(
    map: MapType,
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

    val mapIcon = when (map.id) {
        "HimalayanPass" -> Icons.Default.AcUnit
        "GTRoadNight" -> Icons.Default.NightlightRound
        else -> Icons.Default.Terrain
    }

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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = Color(map.bgSkyMidColor),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mapIcon,
                    contentDescription = null,
                    tint = Color(map.flowerColor),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = map.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = map.description,
                fontSize = 8.sp,
                color = Color(0xFF938F99),
                textAlign = TextAlign.Center,
                lineHeight = 10.sp,
                minLines = 2,
                maxLines = 2
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
                    enabled = coinsBalance >= map.unlockCost,
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
                            text = "${map.unlockCost}",
                            fontSize = 8.sp,
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
                    // Beautiful Retro Interactive Dashboard Radio
                    DashboardRadioPlayer(
                        activeTrack = activeTrack,
                        isRadioOn = isRadioOn,
                        onRadioToggle = onRadioToggle,
                        onStationChange = {
                            val nextTrack = when (activeTrack) {
                                MusicTrack.OLD_SKOOL -> MusicTrack.THE_LAST_RIDE
                                MusicTrack.THE_LAST_RIDE -> MusicTrack.SIDHU_MOOSEWALA
                                MusicTrack.SIDHU_MOOSEWALA -> MusicTrack.LEGEND
                                MusicTrack.LEGEND -> MusicTrack.MUSTANG
                                MusicTrack.MUSTANG -> MusicTrack.OLD_SKOOL
                            }
                            onTrackSelected(nextTrack)
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

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
        }

        // Game controls: Floating beautiful, realistic steel-textured pedals on bottom-left and bottom-right corners!
        // This keeps the landscape viewport completely clear, immersive, and functional with left/right thumb control.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 64.dp, end = 16.dp, top = 0.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // BRAKE PEDAL (Brake & Reverse)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.Bottom)
            ) {
                Text(
                    text = "REVERSE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                // Realistic Red Brake Pedal (Wider, medium height)
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(100.dp)
                        .scale(if (brakePressed) 0.94f else 1.0f) // subtle depress animation!
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (brakePressed) {
                                    listOf(Color(0xFF9F1239), Color(0xFF4C0519))
                                } else {
                                    listOf(Color(0xFFBE123C), Color(0xFF881337))
                                }
                             ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (brakePressed) Color(0xFFFDA4AF) else Color(0xFFE11D48),
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
                                        color = if (brakePressed) Color(0xFFFDA4AF) else Color(0xFF9F1239),
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
                            color = if (brakePressed) Color(0xFFFDA4AF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // MIDDLE NITRO ROCKET THRUSTER CONTROL
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.Bottom)
            ) {
                val hasCharges = gameState.nitroCharges > 0
                val isActive = gameState.isNitroActive
                val canInstantRefill = profile.coins >= 100

                Text(
                    text = "ROCKET BOOST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(height = 84.dp, width = 84.dp)
                        .scale(if (isActive) 0.94f else 1.0f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = when {
                                    isActive -> listOf(Color(0xFFEA580C), Color(0xFF991B1B))
                                    hasCharges -> listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                                    canInstantRefill -> listOf(Color(0xFFD4AF37), Color(0xFF78350F))
                                    else -> listOf(Color(0xFF334155), Color(0xFF1E293B))
                                }
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = when {
                                isActive -> Color(0xFFF97316)
                                hasCharges -> Color(0xFF3B82F6)
                                canInstantRefill -> Color(0xFFFDE047)
                                else -> Color(0xFF475569)
                            },
                            shape = CircleShape
                        )
                        .shadow(
                            elevation = if (isActive) 3.dp else 7.dp,
                            shape = CircleShape
                        )
                        .clickable {
                            if (hasCharges) {
                                viewModel.triggerNitro()
                            } else if (canInstantRefill) {
                                viewModel.buyAndTriggerNitro()
                            }
                        }
                        .testTag("nitro_pedal"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Nitro",
                            tint = when {
                                isActive -> Color(0xFFFDE047)
                                hasCharges -> Color(0xFF60A5FA)
                                canInstantRefill -> Color(0xFFFBCFE8)
                                else -> Color(0xFF94A3B8)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = when {
                                isActive -> "BOOSTING"
                                hasCharges -> "NITRO"
                                canInstantRefill -> "REFILL"
                                else -> "EMPTY"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (isActive) {
                             Text(
                                text = "%.1fs".format(gameState.nitroActiveTimeRemaining),
                                color = Color(0xFFFDE047),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = when {
                                    hasCharges -> "(${gameState.nitroCharges} left)"
                                    canInstantRefill -> "100 🪙"
                                    else -> "No Coins"
                                },
                                color = if (canInstantRefill && !hasCharges) Color(0xFFFDE047) else Color.White.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // GAS PEDAL (Accelerate)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.Bottom)
            ) {
                Text(
                    text = "ACCEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                // Realistic Green Gas Pedal (Narrower, taller profile)
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(125.dp)
                        .scale(if (gasPressed) 0.94f else 1.0f) // subtle depress animation!
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (gasPressed) {
                                    listOf(Color(0xFF166534), Color(0xFF14532D))
                                } else {
                                    listOf(Color(0xFF22C55E), Color(0xFF15803D))
                                }
                            ),
                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (gasPressed) Color(0xFF86EFAC) else Color(0xFF22C55E),
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
                                        color = if (gasPressed) Color(0xFF86EFAC) else Color(0xFF166534),
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
                            color = if (gasPressed) Color(0xFF86EFAC) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
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

        val currentMapId = viewModel.selectedMap.value
        val currentMap = MapType.entries.firstOrNull { it.id == currentMapId } ?: MapType.PUNJAB_FIELDS

        // DRAW SKY BACKWARD GRADIENT
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(currentMap.bgSkyTopColor),
                    Color(currentMap.bgSkyMidColor),
                    Color(currentMap.bgSkyHorizonColor)
                )
            ),
            size = size
        )

        // Draw Twinkling Stars in GT Road Night
        if (currentMap.id == "GTRoadNight") {
            val starCount = 35
            for (starIdx in 0 until starCount) {
                val stX = (starIdx * 83f) % width
                val stY = (starIdx * 41f) % (height * 0.45f)
                val twinkle = 0.3f + 0.7f * sin(starIdx * 1.5f + gameCarX * 0.01f)
                drawCircle(
                    color = Color.White.copy(alpha = twinkle),
                    radius = 1.5f + (starIdx % 2),
                    center = Offset(stX, stY)
                )
            }
        }

        // Animated gently falling snowflakes in Himalayan Pass
        if (currentMap.id == "HimalayanPass") {
            val snowflakeCount = 28
            for (snowflakeIdx in 0 until snowflakeCount) {
                val xSeed = (snowflakeIdx * 137f + (gameCarX * 0.15f)) % width
                val ySeed = (snowflakeIdx * 243f + (gameCarX * 0.08f) + (snowflakeIdx * 5f)) % height
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 2.2f + (snowflakeIdx % 3),
                    center = Offset(xSeed, ySeed)
                )
            }
        }

        // Draw Sun or Moon
        if (currentMap.id == "GTRoadNight") {
            // Crescent Moon
            drawCircle(
                color = Color(0xFFF1F5F9).copy(alpha = 0.25f),
                radius = 45f,
                center = Offset(width * 0.2f, height * 0.18f)
            )
            drawCircle(
                color = Color(0xFFF8FAFC),
                radius = 25f,
                center = Offset(width * 0.2f, height * 0.18f)
            )
            drawCircle(
                color = Color(currentMap.bgSkyMidColor),
                radius = 25f,
                center = Offset(width * 0.22f, height * 0.16f)
            )
        } else if (currentMap.id == "HimalayanPass") {
            // Soft pale glowing winter sun
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = 0.15f),
                radius = 90f,
                center = Offset(width * 0.2f, height * 0.18f)
            )
            drawCircle(
                color = Color(0xFFE2E8F0),
                radius = 40f,
                center = Offset(width * 0.2f, height * 0.18f)
            )
        } else {
            // Bright Sun of Punjab
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = 0.4f),
                radius = 75f,
                center = Offset(width * 0.2f, height * 0.18f)
            )
            drawCircle(
                color = Color(0xFFFDE047),
                radius = 45f,
                center = Offset(width * 0.2f, height * 0.18f)
            )
        }

        // DRAW Distance Clouds (Parallax Scrolling) - warm fluffy afternoon clouds
        val cloudScroll1 = (gameCarX * 0.08f) % width
        val cloudHeight = height * 0.15f
        val cloudAlpha = if (currentMap.id == "GTRoadNight") 0.15f else 0.5f
        drawCircle(Color.White.copy(alpha = cloudAlpha), radius = 110f, center = Offset(width * 0.3f - cloudScroll1, cloudHeight))
        drawCircle(Color.White.copy(alpha = cloudAlpha), radius = 130f, center = Offset(width * 0.35f - cloudScroll1, cloudHeight - 15f))
        drawCircle(Color.White.copy(alpha = cloudAlpha), radius = 90f, center = Offset(width * 0.42f - cloudScroll1, cloudHeight + 5f))

        val cloudScroll2 = (gameCarX * 0.04f) % width
        drawCircle(Color.White.copy(alpha = cloudAlpha * 0.6f), radius = 140f, center = Offset(width * 0.7f - cloudScroll2, cloudHeight * 1.4f))
        drawCircle(Color.White.copy(alpha = cloudAlpha * 0.6f), radius = 160f, center = Offset(width * 0.78f - cloudScroll2, cloudHeight * 1.4f - 20f))

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
        
        val backHillColor = when (currentMap.id) {
            "HimalayanPass" -> Color(0xFF64748B).copy(alpha = 0.3f)
            "GTRoadNight" -> Color(0xFF1E293B).copy(alpha = 0.5f)
            else -> Color(0xFFCA8A04).copy(alpha = 0.25f)
        }
        drawPath(mountPathBack, backHillColor)

        // Closer hills
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
        
        val midHillColor = when (currentMap.id) {
            "HimalayanPass" -> Color(0xFF334155).copy(alpha = 0.4f)
            "GTRoadNight" -> Color(0xFF0F172A).copy(alpha = 0.7f)
            else -> Color(0xFF15803D).copy(alpha = 0.30f)
        }
        drawPath(mountPathMid, midHillColor)

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
                    Color(currentMap.soilTopColor),
                    Color(currentMap.soilBottomColor),
                )
            )
        )

        // Draw Immersive grassy and golden flowering outline crust of Punjab
        drawPath(
            path = borderPath,
            color = Color(currentMap.grassBackColor), // Deep organic forest green root grass backing
            style = Stroke(width = 8f, join = StrokeJoin.Round)
        )
        drawPath(
            path = borderPath,
            color = Color(currentMap.grassFrontColor), // Lush vibrant green turfgrass outline line
            style = Stroke(width = 4f, join = StrokeJoin.Round)
        )
        drawPath(
            path = borderPath,
            color = Color(currentMap.flowerColor), // Beautiful golden "Sarson" mustard bloom highlights on the hilltops
            style = Stroke(width = 1.5f, join = StrokeJoin.Round)
        )

        // DRAW MAP-SPECIFIC ENVIRONMENT SCENERY DECORATIONS
        var vegX = startGroundX - (startGroundX % 40f)
        while (vegX <= endGroundX) {
            val vegY = viewModel.getTerrainHeight(vegX)
            val scrX = toScreenX(vegX)
            val scrY = toScreenY(vegY)
            if (scrX in -20f..(width + 20f)) {
                val seed = (vegX.toInt() * 17) % 100
                when (currentMap.id) {
                    "HimalayanPass" -> {
                        if (seed in 0..30) {
                            // Draw scenic snowy pine saplings
                            // Wooden base trunk
                            drawLine(
                                color = Color(0xFF451A03),
                                start = Offset(scrX, scrY),
                                end = Offset(scrX, scrY - 14f),
                                strokeWidth = 2.5f
                            )
                            // Pine needle bundle lines
                            val sPath = Path().apply {
                                moveTo(scrX, scrY - 28f)
                                lineTo(scrX - 9f, scrY - 12f)
                                lineTo(scrX + 9f, scrY - 12f)
                                close()
                            }
                            drawPath(sPath, Color(0xFF1E293B))
                            // Snow cover peak cap overlay
                            val snPath = Path().apply {
                                moveTo(scrX, scrY - 28f)
                                lineTo(scrX - 5f, scrY - 20f)
                                lineTo(scrX + 5f, scrY - 20f)
                                close()
                            }
                            drawPath(snPath, Color.White)
                        }
                    }
                    "GTRoadNight" -> {
                        if (seed in 0..15) {
                            // Draw highway guardrail barricade posts!
                            drawLine(
                                color = Color(0xFF94A3B8), // Metal slate grey post
                                start = Offset(scrX, scrY),
                                end = Offset(scrX, scrY - 15f),
                                strokeWidth = 4f
                            )
                            drawCircle(
                                color = Color(0xFFEF4444), // Crimson danger reflector
                                radius = 2f,
                                center = Offset(scrX, scrY - 12f)
                            )
                            drawLine(
                                color = Color(0xFF64748B),
                                start = Offset(scrX - 20f, scrY - 10f),
                                end = Offset(scrX + 20f, scrY - 10f),
                                strokeWidth = 2f
                            )
                        } else if (seed in 16..24) {
                            // Draw shining flight particles (bioluminescence fireflies)
                            val hoverY = scrY - 25f - (seed % 15f)
                            drawCircle(
                                color = Color(0xFF10B981).copy(alpha = 0.5f), // Glowing neon green aura
                                radius = 4f,
                                center = Offset(scrX, hoverY)
                            )
                            drawCircle(
                                color = Color.White, // Glowing white center core
                                radius = 1.5f,
                                center = Offset(scrX, hoverY)
                            )
                        }
                    }
                    else -> {
                        // Punjab Fields standard lush crop vegetation
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
                    // 1. Olive/Military Green Rugged Body Tub
                    val armyGreenMajor = Color(0xFF5E6B4E) // Exact olive green from picture
                    val armyGreenShadow = Color(0xFF3D4533) // Darker shadow green for contours
                    val armyGreenLight = Color(0xFF788764) // Highlight green
                    val ironColor = Color(0xFF64748B)       // Metal gray for bumper/shocks
                    val triSaffron = Color(0xFFEA580C)      // flag saffron
                    val triGreen = Color(0xFF16A34A)        // flag green

                    // A. Seats (Driver Seat Cushion and Headrest)
                    // Driver side back headrest outline and fill
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 16f, screenCarY - 33f),
                        size = Size(8f, 11f),
                        cornerRadius = CornerRadius(3f)
                    )
                    drawRoundRect(
                        color = Color(0xFF3E352F), // Dark brown/charcoal seat
                        topLeft = Offset(screenCarX - 15f, screenCarY - 32f),
                        size = Size(6f, 9f),
                        cornerRadius = CornerRadius(2f)
                    )
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenCarX - 12f, screenCarY - 22f),
                        end = Offset(screenCarX - 12f, screenCarY - 10f),
                        strokeWidth = 2.5f
                    )
                    // Cushion seat back
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 24f, screenCarY - 22f),
                        size = Size(9f, 21f),
                        cornerRadius = CornerRadius(4f)
                    )
                    drawRoundRect(
                        color = Color(0xFF3E352F),
                        topLeft = Offset(screenCarX - 23f, screenCarY - 21f),
                        size = Size(7f, 19f),
                        cornerRadius = CornerRadius(3f)
                    )

                    // B. Drivers Body & Sidhu Style 2D Profile (Side-view)
                    // Legs (beige/grey pants)
                    val legPath = Path().apply {
                        moveTo(screenCarX - 10f, screenCarY - 10f) // Hip
                        lineTo(screenCarX - 3f, screenCarY - 3f)   // Knee
                        lineTo(screenCarX + 7f, screenCarY - 3f)   // Foot
                        lineTo(screenCarX + 7f, screenCarY - 7f)   // Sole
                        lineTo(screenCarX + 1f, screenCarY - 7f)   // Top calf
                        lineTo(screenCarX - 6f, screenCarY - 14f)  // Thigh
                        close()
                    }
                    drawPath(legPath, Color(0xFFCBBCA4))
                    drawPath(legPath, Color.Black, style = Stroke(width = 1.5f))

                    // Torso (Olive matching Kurta / Shirt)
                    val torsoPath = Path().apply {
                        moveTo(screenCarX - 15f, screenCarY - 10f) // lower tail
                        lineTo(screenCarX - 4f, screenCarY - 10f)  // front bottom
                        lineTo(screenCarX - 3f, screenCarY - 24f)  // front chest
                        lineTo(screenCarX - 11f, screenCarY - 25f) // shoulder
                        lineTo(screenCarX - 16f, screenCarY - 15f) // back
                        close()
                    }
                    drawPath(torsoPath, Color(0xFF7E8D62))
                    drawPath(torsoPath, Color.Black, style = Stroke(width = 1.5f))

                    // Steering Wheel & Steering Column
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenCarX + 4f, screenCarY - 10f),
                        end = Offset(screenCarX + 1f, screenCarY - 22f),
                        strokeWidth = 2.5f
                    )
                    rotate(degrees = -25f, pivot = Offset(screenCarX + 1f, screenCarY - 22f)) {
                        drawOval(
                            color = Color.Black,
                            topLeft = Offset(screenCarX - 3f, screenCarY - 24f),
                            size = Size(8f, 4f)
                        )
                    }

                    // Sleeve arm extending forward to steering wheel
                    val armPath = Path().apply {
                        moveTo(screenCarX - 10f, screenCarY - 24f) // shoulder
                        lineTo(screenCarX + 2f, screenCarY - 18f)  // hand/wrist
                        lineTo(screenCarX + 1f, screenCarY - 14f)  // lower wrist
                        lineTo(screenCarX - 9f, screenCarY - 20f)  // elbow
                        close()
                    }
                    drawPath(armPath, Color(0xFF7E8D62))
                    drawPath(armPath, Color.Black, style = Stroke(width = 1.5f))

                    // Hand
                    drawCircle(color = Color(0xFFFDBA74), radius = 2.2f, center = Offset(screenCarX + 2f, screenCarY - 16f))
                    drawCircle(color = Color.Black, radius = 2.2f, center = Offset(screenCarX + 2f, screenCarY - 16f), style = Stroke(width = 1.2f))

                    // Neck & Head with side profile face and black beard
                    drawRect(color = Color(0xFFFDBA74), topLeft = Offset(screenCarX - 9f, screenCarY - 28f), size = Size(3.5f, 3.5f))
                    drawRect(color = Color.Black, topLeft = Offset(screenCarX - 9f, screenCarY - 28f), size = Size(3.5f, 3.5f), style = Stroke(width = 1.2f))

                    // Face shape facing right
                    val facePath = Path().apply {
                        moveTo(screenCarX - 8f, screenCarY - 33f)
                        lineTo(screenCarX - 4f, screenCarY - 33f) // forehead
                        lineTo(screenCarX - 1.5f, screenCarY - 30.5f) // nose
                        lineTo(screenCarX - 4f, screenCarY - 29.5f) // chin/mouth
                        lineTo(screenCarX - 8f, screenCarY - 28f) // jaw
                        close()
                    }
                    drawPath(facePath, Color(0xFFFDBA74))
                    drawPath(facePath, Color.Black, style = Stroke(width = 1.2f))

                    // Full classic Black Beard (beautiful overlay matching the image)
                    val beardPath = Path().apply {
                        moveTo(screenCarX - 8f, screenCarY - 28f)
                        lineTo(screenCarX - 3.5f, screenCarY - 28f) // chin edge
                        lineTo(screenCarX - 2.5f, screenCarY - 31f) // mustache start
                        lineTo(screenCarX - 6f, screenCarY - 32f) // cheek
                        close()
                    }
                    drawPath(beardPath, Color(0xFF0F172A))
                    drawPath(beardPath, Color.Black, style = Stroke(width = 1.2f))

                    // Turban - Elegant 2D Side Profile folded turban in army green/olive matching the picture
                    drawOval(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 13f, screenCarY - 40f),
                        size = Size(10f, 9f)
                    )
                    drawOval(
                        color = Color(0xFF516240),
                        topLeft = Offset(screenCarX - 12.5f, screenCarY - 39.5f),
                        size = Size(9f, 8f)
                    )
                    val turbanFrontPath = Path().apply {
                        moveTo(screenCarX - 11f, screenCarY - 40f)
                        quadraticTo(screenCarX - 5f, screenCarY - 43f, screenCarX - 2f, screenCarY - 37f)
                        quadraticTo(screenCarX - 5f, screenCarY - 33f, screenCarX - 9f, screenCarY - 34f)
                        close()
                    }
                    drawPath(turbanFrontPath, Color.Black)
                    drawPath(turbanFrontPath, Color(0xFF435134))

                    val turbanPeakPath = Path().apply {
                        moveTo(screenCarX - 9f, screenCarY - 41f)
                        lineTo(screenCarX - 2.5f, screenCarY - 41f)
                        lineTo(screenCarX - 3.5f, screenCarY - 35f)
                        close()
                    }
                    drawPath(turbanPeakPath, Color.Black)
                    drawPath(turbanPeakPath, Color(0xFF516240))


                    // C. Main Body Tub / Chassis Shell
                    val bodyPath = Path().apply {
                        moveTo(screenCarX - 52f, screenCarY - 20f) // Rear corner top
                        lineTo(screenCarX - 52f, screenCarY - 2f)  // Rear corner bottom
                        lineTo(screenCarX + 46f, screenCarY - 2f)  // Front bottom corner
                        lineTo(screenCarX + 46f, screenCarY - 16f) // Front grill top corner
                        lineTo(screenCarX + 12f, screenCarY - 16f) // Hood top start
                        lineTo(screenCarX + 10f, screenCarY - 11f) // Cowl top
                        // Scoop cutout for door
                        quadraticTo(screenCarX + 8f, screenCarY - 4f, screenCarX + 4f, screenCarY - 4f)
                        lineTo(screenCarX - 18f, screenCarY - 4f) // Bottom of door scooped line
                        quadraticTo(screenCarX - 22f, screenCarY - 4f, screenCarX - 24f, screenCarY - 20f) // Rise to rear quarter panels
                        close()
                    }
                    drawPath(bodyPath, armyGreenMajor)
                    drawPath(bodyPath, Color.Black, style = Stroke(width = 2.5f))

                    // Rear lower quarter shadow board
                    val shadowBoardPath = Path().apply {
                        moveTo(screenCarX - 52f, screenCarY - 20f)
                        lineTo(screenCarX - 52f, screenCarY - 12f)
                        lineTo(screenCarX - 24f, screenCarY - 12f)
                        lineTo(screenCarX - 24f, screenCarY - 20f)
                        close()
                    }
                    drawPath(shadowBoardPath, armyGreenShadow)
                    drawPath(shadowBoardPath, Color.Black, style = Stroke(width = 1.5f))

                    // Saffron/White/Green Tricolor sticker stripe on side body
                    drawRect(triSaffron, Offset(screenCarX + 14f, screenCarY - 13f), Size(6f, 1.8f))
                    drawRect(Color.White, Offset(screenCarX + 14f, screenCarY - 11.2f), Size(6f, 1.8f))
                    drawRect(triGreen, Offset(screenCarX + 14f, screenCarY - 9.4f), Size(6f, 1.8f))
                    // Draw black borders for sticker stripe
                    drawRect(Color.Black, Offset(screenCarX + 14f, screenCarY - 13f), Size(6f, 5.4f), style = Stroke(width = 1f))


                    // D. Cargo / Jerry cans / Roll cages & Back wall assets (Classic Willie's Profile)
                    // Rolled Canvas tent
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 31f, screenCarY - 23f),
                        size = Size(13f, 9f),
                        cornerRadius = CornerRadius(2.5f)
                    )
                    drawRoundRect(
                        color = Color(0xFF8B5A2B), // Brown cargo sheet
                        topLeft = Offset(screenCarX - 30f, screenCarY - 22f),
                        size = Size(11f, 7f),
                        cornerRadius = CornerRadius(2f)
                    )
                    // Tie ropes
                    drawLine(Color.Black, Offset(screenCarX - 27f, screenCarY - 23f), Offset(screenCarX - 27f, screenCarY - 14f), strokeWidth = 1.2f)
                    drawLine(Color.Black, Offset(screenCarX - 22f, screenCarY - 23f), Offset(screenCarX - 22f, screenCarY - 14f), strokeWidth = 1.2f)

                    // 2 Overlapping Army Jerry Cans
                    // Can 1 (Rear, shadow green)
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 45f, screenCarY - 30f),
                        size = Size(11f, 17f),
                        cornerRadius = CornerRadius(2f)
                    )
                    drawRoundRect(
                        color = Color(0xFF323B2A),
                        topLeft = Offset(screenCarX - 44f, screenCarY - 29f),
                        size = Size(9f, 15f),
                        cornerRadius = CornerRadius(1.5f)
                    )
                    // Can 2 (Foreground, primary green)
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX - 39f, screenCarY - 26f),
                        size = Size(11f, 17f),
                        cornerRadius = CornerRadius(2f)
                    )
                    drawRoundRect(
                        color = armyGreenMajor,
                        topLeft = Offset(screenCarX - 38f, screenCarY - 25f),
                        size = Size(9f, 15f),
                        cornerRadius = CornerRadius(1.5f)
                    )
                    // Cross X embossing on Jerry can 2
                    drawLine(Color(0xFF323B2A), Offset(screenCarX - 36f, screenCarY - 23f), Offset(screenCarX - 32f, screenCarY - 12f), strokeWidth = 1.5f)
                    drawLine(Color(0xFF323B2A), Offset(screenCarX - 32f, screenCarY - 23f), Offset(screenCarX - 36f, screenCarY - 12f), strokeWidth = 1.5f)

                    // Black Cage Roll-bar curve behind driver's head
                    val barPath = Path().apply {
                        moveTo(screenCarX - 32f, screenCarY - 10f)
                        lineTo(screenCarX - 32f, screenCarY - 40f)
                        quadraticTo(screenCarX - 32f, screenCarY - 43f, screenCarX - 27f, screenCarY - 43f)
                        lineTo(screenCarX - 4f, screenCarY - 36f)
                    }
                    drawPath(barPath, Color.Black, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                    drawPath(barPath, Color(0xFF1E293B), style = Stroke(width = 2.5f, cap = StrokeCap.Round))


                    // E. Spare Tyre (Mounted vertically on the back tailgate)
                    val spareY = screenCarY - 12f
                    val spareX = screenCarX - 58f
                    drawRoundRect(Color.Black, Offset(spareX - 11f, spareY - 20f), Size(11f, 32f), CornerRadius(4f))
                    drawRoundRect(Color(0xFF1E293B), Offset(spareX - 9.5f, spareY - 18.5f), Size(8f, 29f), CornerRadius(2.5f))
                    for (step in 0..6) {
                        val treadY = spareY - 16f + (step * 5f)
                        drawLine(Color.Black, Offset(spareX - 11f, treadY), Offset(spareX - 6f, treadY), strokeWidth = 1.8f)
                    }


                    // F. Lettering / Decals on Side Hood "DESI JEEP"
                    drawContext.canvas.nativeCanvas.apply {
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 8.0f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        val textOutlinePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 8.0f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            textAlign = android.graphics.Paint.Align.CENTER
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2.0f
                            isAntiAlias = true
                        }
                        drawText("DESI JEEP", screenCarX + 27f, screenCarY - 7f, textOutlinePaint)
                        drawText("DESI JEEP", screenCarX + 27f, screenCarY - 7f, textPaint)
                    }


                    // G. Windshield Frame (Tilted forward windshield)
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenCarX + 10f, screenCarY - 13f),
                        end = Offset(screenCarX + 16f, screenCarY - 38f),
                        strokeWidth = 4.0f
                    )
                    drawLine(
                        color = armyGreenMajor,
                        start = Offset(screenCarX + 10f, screenCarY - 13f),
                        end = Offset(screenCarX + 16f, screenCarY - 37f),
                        strokeWidth = 2.0f
                    )
                    val windshieldPath = Path().apply {
                        moveTo(screenCarX + 11f, screenCarY - 13f)
                        lineTo(screenCarX + 16f, screenCarY - 36f)
                        lineTo(screenCarX + 18f, screenCarY - 36f)
                        lineTo(screenCarX + 13f, screenCarY - 13f)
                        close()
                    }
                    drawPath(windshieldPath, Color(0x3D93C5FD))


                    // H. Flags (Indian Tricolor Flag on mast & triangular Saffron flag on hood)
                    // 1. Rear Tricolor
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenCarX - 32f, screenCarY - 14f),
                        end = Offset(screenCarX - 32f, screenCarY - 58f),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(screenCarX - 32f, screenCarY - 14f),
                        end = Offset(screenCarX - 32f, screenCarY - 57f),
                        strokeWidth = 1.5f
                    )
                    val flagWidth = 18f
                    val stripeHeight = 4.2f
                    val flagTopY = screenCarY - 57f

                    // Saffron
                    drawRect(Color(0xFFEA580C), Offset(screenCarX - 50f, flagTopY), Size(flagWidth, stripeHeight))
                    drawRect(Color.Black, Offset(screenCarX - 50f, flagTopY), Size(flagWidth, stripeHeight), style = Stroke(width = 1f))
                    // White
                    drawRect(Color.White, Offset(screenCarX - 50f, flagTopY + stripeHeight), Size(flagWidth, stripeHeight))
                    drawRect(Color.Black, Offset(screenCarX - 50f, flagTopY + stripeHeight), Size(flagWidth, stripeHeight), style = Stroke(width = 1f))
                    // Green
                    drawRect(Color(0xFF16A34A), Offset(screenCarX - 50f, flagTopY + 2 * stripeHeight), Size(flagWidth, stripeHeight))
                    drawRect(Color.Black, Offset(screenCarX - 50f, flagTopY + 2 * stripeHeight), Size(flagWidth, stripeHeight), style = Stroke(width = 1f))
                    // Ashok Chakra navy blue dot
                    drawCircle(
                        color = Color(0xFF1E3A8A),
                        radius = 1.3f,
                        center = Offset(screenCarX - 41f, flagTopY + 1.5f * stripeHeight)
                    )

                    // 2. Front triangular saffron flag
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenCarX + 44f, screenCarY - 16f),
                        end = Offset(screenCarX + 44f, screenCarY - 33f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(screenCarX + 44f, screenCarY - 16f),
                        end = Offset(screenCarX + 44f, screenCarY - 32f),
                        strokeWidth = 1.2f
                    )
                    val saffronFlagPath = Path().apply {
                        moveTo(screenCarX + 44f, screenCarY - 32f)
                        lineTo(screenCarX + 32f, screenCarY - 28.5f)
                        lineTo(screenCarX + 44f, screenCarY - 25f)
                        close()
                    }
                    drawPath(saffronFlagPath, Color.Black)
                    drawPath(saffronFlagPath, Color(0xFFEA580C))


                    // I. Front details: Headlight, Grill, Mud splatters, Winch & Fender arches
                    // Front halogen headlight
                    drawCircle(Color.Black, radius = 4f, center = Offset(screenCarX + 43f, screenCarY - 11f))
                    drawCircle(Color.White, radius = 3.2f, center = Offset(screenCarX + 43f, screenCarY - 11f))
                    drawCircle(Color(0xFFFEF08A), radius = 2.0f, center = Offset(screenCarX + 43f, screenCarY - 11f))

                    // Front heavy guard winch bumper
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX + 45f, screenCarY - 8f),
                        size = Size(10f, 6f),
                        cornerRadius = CornerRadius(1.5f)
                    )
                    drawRoundRect(
                        color = ironColor,
                        topLeft = Offset(screenCarX + 46f, screenCarY - 7f),
                        size = Size(8f, 4f),
                        cornerRadius = CornerRadius(1f)
                    )

                    // Mud splatters decoration (exact 2D paint splats look)
                    val mudColorVal = Color(0xFF654321)
                    drawCircle(mudColorVal, radius = 2f, center = Offset(screenCarX - 44f, screenCarY - 4f))
                    drawCircle(mudColorVal, radius = 3f, center = Offset(screenCarX - 24f, screenCarY - 5f))
                    drawCircle(mudColorVal, radius = 1.5f, center = Offset(screenCarX - 10f, screenCarY - 4f))
                    drawCircle(mudColorVal, radius = 4f, center = Offset(screenCarX + 16f, screenCarY - 5f))
                    drawCircle(mudColorVal, radius = 2.5f, center = Offset(screenCarX + 32f, screenCarY - 6f))

                    // Outlined wheel arch mudguard shields
                    val rearArch = Path().apply {
                        moveTo(screenCarX - 44f, screenCarY - 2f)
                        quadraticTo(screenCarX - 28f, screenCarY - 14f, screenCarX - 12f, screenCarY - 2f)
                    }
                    drawPath(rearArch, Color.Black, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    drawPath(rearArch, armyGreenMajor, style = Stroke(width = 3f, cap = StrokeCap.Round))

                    val frontArch = Path().apply {
                        moveTo(screenCarX + 12f, screenCarY - 2f)
                        quadraticTo(screenCarX + 28f, screenCarY - 14f, screenCarX + 44f, screenCarY - 2f)
                    }
                    drawPath(frontArch, Color.Black, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    drawPath(frontArch, armyGreenMajor, style = Stroke(width = 3f, cap = StrokeCap.Round))
                }
                "Thar" -> {
                    // Thar - Rugged Indian 4x4 Offroader SUV
                    // 1. Lower chassis frame / robust black rocker panel and bumpers
                    drawRoundRect(
                        color = Color(0xFF1E293B), // Charcoal/Black heavy bumpers
                        topLeft = Offset(screenCarX - 58f, screenCarY - 14f),
                        size = Size(116f, 12f),
                        cornerRadius = CornerRadius(4f)
                    )
                    
                    // 2. High ground clearance under-guard / transmission block (metallic dark grey)
                    drawRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(screenCarX - 32f, screenCarY - 6f),
                        size = Size(64f, 6f)
                    )

                    // 3. Main Muscular Body shell - Deep Crimson Red
                    drawRoundRect(
                        color = Color(0xFFB91C1C), // Deep Indian Red
                        topLeft = Offset(screenCarX - 52f, screenCarY - 32f),
                        size = Size(100f, 20f),
                        cornerRadius = CornerRadius(3f)
                    )
                    
                    // 4. Front grille bumper slant
                    val frontHood = Path().apply {
                        moveTo(screenCarX + 30f, screenCarY - 32f)
                        quadraticTo(screenCarX + 42f, screenCarY - 32f, screenCarX + 48f, screenCarY - 30f)
                        lineTo(screenCarX + 48f, screenCarY - 14f)
                        lineTo(screenCarX + 30f, screenCarY - 14f)
                        close()
                    }
                    drawPath(frontHood, Color(0xFF991B1B)) // Darker red hood front

                    // Front circular classic halogen headlight (glowing warm-yellow)
                    drawCircle(
                        color = Color(0xFFFEF08A), // Bright warm-yellow glow
                        radius = 4.5f,
                        center = Offset(screenCarX + 44f, screenCarY - 24f)
                    )
                    drawCircle(
                        color = Color(0xFFFFFFFF).copy(alpha = 0.5f), // Highlight lens
                        radius = 2.5f,
                        center = Offset(screenCarX + 45f, screenCarY - 25f)
                    )

                    // 5. Hardtop cabin - Tough matte black canopy/D-pillar structure
                    val hardTopPath = Path().apply {
                        moveTo(screenCarX - 52f, screenCarY - 32f)
                        lineTo(screenCarX - 52f, screenCarY - 48f)
                        lineTo(screenCarX + 12f, screenCarY - 48f)
                        lineTo(screenCarX + 24f, screenCarY - 32f)
                        close()
                    }
                    drawPath(hardTopPath, Color(0xFF0F172A)) // Pure off-road matte black hardtop cabin

                    // 6. Detailed Side Windows (Blue/Sky tinted glass)
                    // Rear sliding windows
                    drawRoundRect(
                        color = Color(0xAA93C5FD), // Sky blue transparent glass
                        topLeft = Offset(screenCarX - 44f, screenCarY - 44f),
                        size = Size(23f, 10f),
                        cornerRadius = CornerRadius(1.5f)
                    )
                    // Front passenger window
                    val frontWindow = Path().apply {
                        moveTo(screenCarX - 16f, screenCarY - 44f)
                        lineTo(screenCarX + 9f, screenCarY - 44f)
                        lineTo(screenCarX + 18f, screenCarY - 34f)
                        lineTo(screenCarX - 16f, screenCarY - 34f)
                        close()
                    }
                    drawPath(frontWindow, Color(0xBB93C5FD))

                    // 7. Muscular Heavy Black Fender flares (arch moldings above wheels for rough conditions)
                    // Rear fender flare wheelarch
                    drawArc(
                        color = Color(0xFF0F172A),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(screenCarX - 45f, screenCarY - 18f),
                        size = Size(26f, 12f)
                    )
                    // Front fender flare wheelarch
                    drawArc(
                        color = Color(0xFF0F172A),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(screenCarX + 14f, screenCarY - 18f),
                        size = Size(26f, 12f)
                    )

                    // 8. Spare Wheel mounted on tailgate (Classic Thar 4x4 trademark asset)
                    drawRoundRect(
                        color = Color(0xFF334155), // Rugged rubber tyre color
                        topLeft = Offset(screenCarX - 60f, screenCarY - 38f),
                        size = Size(10f, 18f),
                        cornerRadius = CornerRadius(2.5f)
                    )
                    drawRect(
                        color = Color(0xFF94A3B8), // Metallic wheel spacer/plate
                        topLeft = Offset(screenCarX - 52f, screenCarY - 31f),
                        size = Size(4f, 4f)
                    )

                    // 9. Side Steps footboard (essential SUV detail)
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(screenCarX - 18f, screenCarY - 2f),
                        end = Offset(screenCarX + 18f, screenCarY - 2f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )

                    // 10. Distinctive "4x4" badge moniker and door details
                    drawRect(
                        color = Color(0xFFDFB600), // Golden chrome logo label
                        topLeft = Offset(screenCarX - 18f, screenCarY - 22f),
                        size = Size(6f, 3f)
                    )
                    // Door handle
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(screenCarX + 4f, screenCarY - 24f),
                        size = Size(6f, 2.2f),
                        cornerRadius = CornerRadius(0.5f)
                    )
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
                "Bullet" -> {
                    // Bullet - Classic solid glossy cruiser body
                    // Massive blocky engine/crankcase in the middle (silver metallic)
                    drawRoundRect(
                        color = Color(0xFF64748B),
                        topLeft = Offset(screenCarX - 15f, screenCarY - 10f),
                        size = Size(32f, 15f),
                        cornerRadius = CornerRadius(2f)
                    )
                    drawCircle(
                        color = Color(0xFF94A3B8),
                        radius = 8f,
                        center = Offset(screenCarX + 2f, screenCarY - 2f)
                    )
                    // Signature black teardrop Royal Fuel Tank with a gold badge
                    val fuelTank = Path().apply {
                        moveTo(screenCarX - 18f, screenCarY - 14f)
                        quadraticTo(screenCarX - 10f, screenCarY - 26f, screenCarX + 12f, screenCarY - 22f)
                        lineTo(screenCarX + 12f, screenCarY - 13f)
                        quadraticTo(screenCarX - 4f, screenCarY - 10f, screenCarX - 18f, screenCarY - 14f)
                        close()
                    }
                    drawPath(fuelTank, Color(0xFF0F172A)) // Shiny pitch black
                    drawPath(fuelTank, Color(0xFFDFB600), style = Stroke(width = 1.2f)) // Royal gold coachline stripes

                    // Metallic golden side-panel monogram
                    drawCircle(Color(0xFFDFB600), radius = 3.5f, center = Offset(screenCarX - 4f, screenCarY - 16f))

                    // Front fork support + High chrome retro handlebars
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(screenCarX + 12f, screenCarY - 16f),
                        end = Offset(screenCarX + 26f, screenCarY - 36f),
                        strokeWidth = 3.5f
                    )
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(screenCarX + 23f, screenCarY - 33f),
                        end = Offset(screenCarX + 16f, screenCarY - 35f), // Swept back cruiser handlebar grip
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    
                    // Heavy rear chrome exhaust pipe with classic straight outline
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(screenCarX - 22f, screenCarY - 2f),
                        end = Offset(screenCarX + 8f, screenCarY - 2f),
                        strokeWidth = 3.8f,
                        cap = StrokeCap.Round
                    )
                    // Cozy brown stitched single saddle leather seat
                    drawRoundRect(
                        color = Color(0xFF78350F), // Dark leather brown
                        topLeft = Offset(screenCarX - 25f, screenCarY - 18f),
                        size = Size(18f, 6f),
                        cornerRadius = CornerRadius(2f)
                    )
                }
                "Splendor" -> {
                    // Splendor - Commuter cruiser bike
                    // Thin simple commuter engine block
                    drawRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(screenCarX - 10f, screenCarY - 8f),
                        size = Size(20f, 13f)
                    )
                    // Sleek rectangular black commuter fuel tank with cyan decals
                    val splendorTank = Path().apply {
                        moveTo(screenCarX - 20f, screenCarY - 11f)
                        lineTo(screenCarX - 12f, screenCarY - 20f)
                        lineTo(screenCarX + 10f, screenCarY - 18f)
                        lineTo(screenCarX + 10f, screenCarY - 10f)
                        lineTo(screenCarX - 20f, screenCarY - 10f)
                        close()
                    }
                    drawPath(splendorTank, Color(0xFF1E293B))
                    drawPath(splendorTank, Color(0xFF06B6D4), style = Stroke(width = 1.5f)) // Iconic cyan/blue stripes

                    // Comfortable long flat black seat (commuter double seat)
                    drawRoundRect(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(screenCarX - 26f, screenCarY - 15f),
                        size = Size(20f, 5f),
                        cornerRadius = CornerRadius(1.5f)
                    )
                    
                    // Rear black metal carrier rack (Splendor's most iconic utility)
                    drawLine(
                        color = Color(0xFF000000),
                        start = Offset(screenCarX - 26f, screenCarY - 13f),
                        end = Offset(screenCarX - 34f, screenCarY - 13f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = Color(0xFF000000),
                        start = Offset(screenCarX - 32f, screenCarY - 13f),
                        end = Offset(screenCarX - 28f, screenCarY - 6f),
                        strokeWidth = 2f
                    )

                    // Front sporty commuter headlight cowl
                    val headlightCowl = Path().apply {
                        moveTo(screenCarX + 10f, screenCarY - 14f)
                        lineTo(screenCarX + 22f, screenCarY - 22f)
                        lineTo(screenCarX + 20f, screenCarY - 10f)
                        close()
                    }
                    drawPath(headlightCowl, Color(0xFF1E293B))
                    // Bright halogen headlight beam
                    drawRect(Color(0xFFFEF08A), topLeft = Offset(screenCarX + 20f, screenCarY - 19f), size = Size(3f, 4f))

                    // Handlebars and fork links
                    drawLine(
                        color = Color(0xFF475569),
                        start = Offset(screenCarX + 12f, screenCarY - 14f),
                        end = Offset(screenCarX + 18f, screenCarY - 28f),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(screenCarX + 16f, screenCarY - 26f),
                        end = Offset(screenCarX + 10f, screenCarY - 26f),
                        strokeWidth = 2.2f,
                        cap = StrokeCap.Round
                    )
                    
                    // Slanted chrome exhaust silencer
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(screenCarX - 24f, screenCarY),
                        end = Offset(screenCarX + 4f, screenCarY - 4f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
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
            // DRAW COOL DRIVER HELMET OR CUSTOM CHARACTER (SIDHU MOOSEWALA WITH TURBAN, MUSTACHE & BEARD) inside cabin area
            if (true) {
                val coatColor = if (vehicle.id == "Buggy") Color(0xFF4B5320) else Color(0xFFDC2626)
                val turbanColor = if (vehicle.id == "Buggy") Color(0xFF2E6F40) else Color(0xFFEA580C)
                val shadowTurban = if (vehicle.id == "Buggy") Color(0xFF1B4324) else Color(0xFFC2410C)

                // 1. Driver coat (Kurta style matching shirt/jacket) - outlined
                drawCircle(
                    color = Color.Black,
                    radius = 10f,
                    center = Offset(screenCarX - 2f, screenCarY - 17f)
                )
                drawCircle(
                    color = coatColor,
                    radius = 7.5f,
                    center = Offset(screenCarX - 2f, screenCarY - 17f)
                )
                
                // 2. Full Black Beard (Underlay with outline)
                drawCircle(
                    color = Color.Black,
                    radius = 12f,
                    center = Offset(screenCarX - 2f, screenCarY - 24f)
                )
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 9.5f,
                    center = Offset(screenCarX - 2f, screenCarY - 24f)
                )
                
                // 3. Face Skin core - outlined
                drawCircle(
                    color = Color.Black,
                    radius = 10f,
                    center = Offset(screenCarX - 2f, screenCarY - 26f)
                )
                drawCircle(
                    color = Color(0xFFFDBA74),
                    radius = 7.5f,
                    center = Offset(screenCarX - 2f, screenCarY - 26f)
                )
                
                // 4. Hair backing
                drawCircle(
                    color = Color.Black,
                    radius = 5.5f,
                    center = Offset(screenCarX - 8f, screenCarY - 26f)
                )
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
                    color = Color.Black,
                    style = Stroke(width = 3.8f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = rightMustache,
                    color = Color(0xFF0F172A),
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                )
                val leftMustache = Path().apply {
                    moveTo(screenCarX - 2f, screenCarY - 24.5f)
                    quadraticTo(screenCarX - 6f, screenCarY - 24.5f, screenCarX - 8f, screenCarY - 27f)
                }
                drawPath(
                    path = leftMustache,
                    color = Color.Black,
                    style = Stroke(width = 3.8f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = leftMustache,
                    color = Color(0xFF0F172A),
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                )

                // 6. Styled Black Sunglasses (Aviators)
                drawCircle(
                    color = Color.Black,
                    radius = 4f,
                    center = Offset(screenCarX - 1.5f, screenCarY - 28f)
                )
                drawCircle(
                    color = Color(0xFF000000),
                    radius = 2.5f,
                    center = Offset(screenCarX - 1.5f, screenCarY - 28f)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 4f,
                    center = Offset(screenCarX + 2.5f, screenCarY - 28f)
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
                    strokeWidth = 2.0f
                )

                // 7. Sikh Turban (Dastar) with beautiful black outlines
                drawOval(
                    color = Color.Black,
                    topLeft = Offset(screenCarX - 14.5f, screenCarY - 37.5f),
                    size = Size(25f, 14f)
                )
                drawOval(
                    color = turbanColor,
                    topLeft = Offset(screenCarX - 12f, screenCarY - 35f),
                    size = Size(20f, 9f)
                )

                drawOval(
                    color = Color.Black,
                    topLeft = Offset(screenCarX - 12.5f, screenCarY - 39.5f),
                    size = Size(23f, 14f)
                )
                drawOval(
                    color = shadowTurban,
                    topLeft = Offset(screenCarX - 10f, screenCarY - 37f),
                    size = Size(18f, 9f)
                )

                drawOval(
                    color = Color.Black,
                    topLeft = Offset(screenCarX - 9.5f, screenCarY - 42.5f),
                    size = Size(19f, 13f)
                )
                drawOval(
                    color = turbanColor,
                    topLeft = Offset(screenCarX - 7f, screenCarY - 40f),
                    size = Size(14f, 8f)
                )

                val peakOutlinePath = Path().apply {
                    moveTo(screenCarX - 5f, screenCarY - 39f)
                    lineTo(screenCarX - 1f, screenCarY - 45f)
                    lineTo(screenCarX + 4f, screenCarY - 39f)
                    close()
                }
                drawPath(peakOutlinePath, Color.Black)
                val peakPath = Path().apply {
                    moveTo(screenCarX - 3f, screenCarY - 40f)
                    lineTo(screenCarX - 1f, screenCarY - 43f)
                    lineTo(screenCarX + 2f, screenCarY - 40f)
                    close()
                }
                drawPath(peakPath, turbanColor)

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
            if (vehicle.id == "Buggy") {
                // Chunky 2D Jeep tire with tread notches and olive rim hubs
                // Outer tyre outline
                drawCircle(
                    color = Color.Black,
                    radius = rad,
                    center = Offset(screenRearX, screenRearY)
                )
                // Charcoal tire meat
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = rad - 1.5f,
                    center = Offset(screenRearX, screenRearY)
                )
                // Rugged tread blocks around the perimeter matching 2D sketch artwork
                for (i in 0..11) {
                    val tireSectAngle = (i * PI / 6f).toFloat()
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenRearX + (rad - 4f) * cos(tireSectAngle), screenRearY + (rad - 4f) * sin(tireSectAngle)),
                        end = Offset(screenRearX + rad * cos(tireSectAngle), screenRearY + rad * sin(tireSectAngle)),
                        strokeWidth = 3f
                    )
                }
                // Olive Green rugged steel wheel rim
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.70f,
                    center = Offset(screenRearX, screenRearY)
                )
                drawCircle(
                    color = Color(0xFF5E6B4E), // Olive green match jeep body
                    radius = rad * 0.64f,
                    center = Offset(screenRearX, screenRearY)
                )
                // Concentric inner line detail
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.45f,
                    center = Offset(screenRearX, screenRearY),
                    style = Stroke(width = 1.8f)
                )
                // Hub center
                drawCircle(
                    color = Color(0xFF323B2A), // Shadow green inner plate
                    radius = rad * 0.35f,
                    center = Offset(screenRearX, screenRearY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.35f,
                    center = Offset(screenRearX, screenRearY),
                    style = Stroke(width = 1.2f)
                )
                // 5 circular hub bolt caps
                for (b in 0..4) {
                    val boltAngle = (b * 2 * PI / 5f).toFloat()
                    drawCircle(
                        color = Color.Black,
                        radius = 1.2f,
                        center = Offset(screenRearX + (rad * 0.22f) * cos(boltAngle), screenRearY + (rad * 0.22f) * sin(boltAngle))
                    )
                    drawCircle(
                        color = Color(0xFFD1D5DB), // tiny metal bolt
                        radius = 0.6f,
                        center = Offset(screenRearX + (rad * 0.22f) * cos(boltAngle), screenRearY + (rad * 0.22f) * sin(boltAngle))
                    )
                }
                // Center dust cap axle
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.14f,
                    center = Offset(screenRearX, screenRearY)
                )
            } else {
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
        }

        // Front tire (matching realistic styling)
        rotate(degrees = Math.toDegrees(gameState.frontWheelAngle.toDouble()).toFloat(), pivot = Offset(screenFrontX, screenFrontY)) {
            if (vehicle.id == "Buggy") {
                // Chunky 2D Jeep tire with tread notches and olive rim hubs
                // Outer tyre outline
                drawCircle(
                    color = Color.Black,
                    radius = rad,
                    center = Offset(screenFrontX, screenFrontY)
                )
                // Charcoal tire meat
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = rad - 1.5f,
                    center = Offset(screenFrontX, screenFrontY)
                )
                // Rugged tread blocks around the perimeter matching 2D sketch artwork
                for (i in 0..11) {
                    val tireSectAngle = (i * PI / 6f).toFloat()
                    drawLine(
                        color = Color.Black,
                        start = Offset(screenFrontX + (rad - 4f) * cos(tireSectAngle), screenFrontY + (rad - 4f) * sin(tireSectAngle)),
                        end = Offset(screenFrontX + rad * cos(tireSectAngle), screenFrontY + rad * sin(tireSectAngle)),
                        strokeWidth = 3f
                    )
                }
                // Olive Green rugged steel wheel rim
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.70f,
                    center = Offset(screenFrontX, screenFrontY)
                )
                drawCircle(
                    color = Color(0xFF5E6B4E), // Olive green match jeep body
                    radius = rad * 0.64f,
                    center = Offset(screenFrontX, screenFrontY)
                )
                // Concentric inner line detail
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.45f,
                    center = Offset(screenFrontX, screenFrontY),
                    style = Stroke(width = 1.8f)
                )
                // Hub center
                drawCircle(
                    color = Color(0xFF323B2A), // Shadow green inner plate
                    radius = rad * 0.35f,
                    center = Offset(screenFrontX, screenFrontY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.35f,
                    center = Offset(screenFrontX, screenFrontY),
                    style = Stroke(width = 1.2f)
                )
                // 5 circular hub bolt caps
                for (b in 0..4) {
                    val boltAngle = (b * 2 * PI / 5f).toFloat()
                    drawCircle(
                        color = Color.Black,
                        radius = 1.2f,
                        center = Offset(screenFrontX + (rad * 0.22f) * cos(boltAngle), screenFrontY + (rad * 0.22f) * sin(boltAngle))
                    )
                    drawCircle(
                        color = Color(0xFFD1D5DB), // tiny metal bolt
                        radius = 0.6f,
                        center = Offset(screenFrontX + (rad * 0.22f) * cos(boltAngle), screenFrontY + (rad * 0.22f) * sin(boltAngle))
                    )
                }
                // Center dust cap axle
                drawCircle(
                    color = Color.Black,
                    radius = rad * 0.14f,
                    center = Offset(screenFrontX, screenFrontY)
                )
            } else {
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
        colors = CardDefaults.cardColors(containerColor = Color(0xEE0E0D12)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.7f), RoundedCornerShape(8.dp)) // Royal golden accent
            .shadow(4.dp, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Power settings toggle
            IconButton(
                onClick = onRadioToggle,
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        if (isRadioOn) Color(0xFFDC2626) else Color(0xFF4B5563),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }

            // LCD Tuner Display - compact card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .width(130.dp)
                    .height(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (isRadioOn) Color(0xFFFDE047) else Color(0xFF64748B),
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            text = if (isRadioOn) {
                                when (activeTrack) {
                                    MusicTrack.THE_LAST_RIDE -> "SIDHU FM 94.4"
                                    MusicTrack.OLD_SKOOL -> "PUNJABI 101"
                                    MusicTrack.SIDHU_MOOSEWALA -> "LEGEND LIVE 5"
                                    MusicTrack.LEGEND -> "LEGEND LIVE 9"
                                    MusicTrack.MUSTANG -> "MUSTANG FM 10"
                                }
                            } else {
                                "STANDBY"
                            },
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRadioOn) Color(0xFFFDE047) else Color(0xFF64748B),
                            letterSpacing = 0.2.sp
                        )
                    }

                    Text(
                        text = if (isRadioOn) "ON" else "OFF",
                        fontSize = 6.sp,
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
                    .size(20.dp)
                    .background(
                        if (isRadioOn) Color(0xFF16A34A) else Color(0xFF374151),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Station",
                    tint = if (isRadioOn) Color.White else Color(0xFF9CA3AF),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
