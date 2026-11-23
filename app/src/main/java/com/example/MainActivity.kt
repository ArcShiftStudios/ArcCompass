package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.arcpark.ArcParkScreen
import com.example.compass.CompassScreen
import com.example.data.SavedPlaceEntity
import com.example.diagnostics.SensorDiagnosticsScreen
import com.example.level.LevelScreen
import com.example.settings.SettingsScreen
import com.example.ui.components.MorphingFloatingNavBar
import com.example.ui.components.MorphingFloatingNavRail
import com.example.ui.theme.ArcCompassTheme
import com.example.ui.theme.FrostedAmbientBackdrop

enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    COMPASS(R.string.nav_compass, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_compass"),
    LEVEL(R.string.nav_level, Icons.Filled.Straighten, Icons.Outlined.Straighten, "nav_level"),
    ARCPARK(R.string.nav_arcpark, Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar, "nav_arcpark"),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcCompassAppRoot()
        }
    }
}

@Composable
fun ArcCompassAppRoot(
    viewModel: ArcCompassViewModel = viewModel()
) {
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val headingState by viewModel.headingState.collectAsStateWithLifecycle()
    val levelState by viewModel.levelState.collectAsStateWithLifecycle()
    val compassDiagnosticsState by viewModel.compassDiagnosticsState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startSensors()
                Lifecycle.Event.ON_STOP -> viewModel.stopSensors()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSensors()
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.COMPASS) }
    var showSensorDiagnostics by rememberSaveable { mutableStateOf(false) }
    var openCalibrationInSettings by rememberSaveable { mutableStateOf(false) }
    var inFindMode by remember { mutableStateOf(false) }

    ArcCompassTheme(settings = settings) {
        androidx.compose.material3.Surface(
            color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize()
        ) {
            if (showSensorDiagnostics) {
                SensorDiagnosticsScreen(
                    onNavigateBack = { showSensorDiagnostics = false },
                    manageLifecycle = false
                )
                return@Surface
            }

            if (!inFindMode && currentTab != MainTab.COMPASS) {
                BackHandler {
                    currentTab = MainTab.COMPASS
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
            FrostedAmbientBackdrop()

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp
                val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomNavClearance = if (!useRail && !inFindMode) {
                    navBarsBottom + 96.dp
                } else {
                    navBarsBottom + 16.dp
                }

                if (useRail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (!inFindMode) {
                            MorphingFloatingNavRail(
                                currentTab = currentTab,
                                onSelectTab = { currentTab = it },
                                modifier = Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Vertical + WindowInsetsSides.Start
                                    )
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (inFindMode) {
                                        Modifier
                                    } else {
                                        Modifier.windowInsetsPadding(
                                            WindowInsets.safeDrawing.only(
                                                WindowInsetsSides.Top + WindowInsetsSides.End
                                            )
                                        )
                                    }
                                )
                        ) {
                            TabDestinationContent(
                                currentTab = currentTab,
                                engineState = engineState,
                                headingState = headingState,
                                levelState = levelState,
                                compassDiagnosticsState = compassDiagnosticsState,
                                settings = settings,
                                savedPlaces = savedPlaces,
                                bottomNavClearance = bottomNavClearance,
                                viewModel = viewModel,
                                openCalibrationInSettings = openCalibrationInSettings,
                                onCalibrationInSettingsConsumed = { openCalibrationInSettings = false },
                                onNavigateToSettings = { currentTab = MainTab.SETTINGS },
                                onOpenCalibrationSettings = {
                                    openCalibrationInSettings = true
                                    currentTab = MainTab.SETTINGS
                                },
                                onOpenSensorDiagnostics = { showSensorDiagnostics = true },
                                onFindModeChanged = { inFindMode = it }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (inFindMode) {
                                        Modifier
                                    } else {
                                        Modifier.windowInsetsPadding(
                                            WindowInsets.safeDrawing.only(
                                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                            )
                                        )
                                    }
                                )
                        ) {
                            TabDestinationContent(
                                currentTab = currentTab,
                                engineState = engineState,
                                headingState = headingState,
                                levelState = levelState,
                                compassDiagnosticsState = compassDiagnosticsState,
                                settings = settings,
                                savedPlaces = savedPlaces,
                                bottomNavClearance = bottomNavClearance,
                                viewModel = viewModel,
                                openCalibrationInSettings = openCalibrationInSettings,
                                onCalibrationInSettingsConsumed = { openCalibrationInSettings = false },
                                onNavigateToSettings = { currentTab = MainTab.SETTINGS },
                                onOpenCalibrationSettings = {
                                    openCalibrationInSettings = true
                                    currentTab = MainTab.SETTINGS
                                },
                                onOpenSensorDiagnostics = { showSensorDiagnostics = true },
                                onFindModeChanged = { inFindMode = it }
                            )
                        }

                        if (!inFindMode) {
                            MorphingFloatingNavBar(
                                currentTab = currentTab,
                                onSelectTab = { currentTab = it },
                                modifier = Modifier.align(Alignment.BottomCenter)
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
private fun TabDestinationContent(
    currentTab: MainTab,
    engineState: com.example.sensor.SensorEngineState,
    headingState: com.example.sensor.CompassHeadingState,
    levelState: com.example.sensor.LevelTiltState,
    compassDiagnosticsState: com.example.sensor.CompassDiagnosticsState,
    settings: com.example.data.ArcCompassSettings,
    savedPlaces: List<SavedPlaceEntity>,
    bottomNavClearance: Dp,
    viewModel: ArcCompassViewModel,
    openCalibrationInSettings: Boolean,
    onCalibrationInSettingsConsumed: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenCalibrationSettings: () -> Unit,
    onOpenSensorDiagnostics: () -> Unit,
    onFindModeChanged: (Boolean) -> Unit
) {
    when (currentTab) {
        MainTab.COMPASS -> {
            CompassScreen(
                engineState = engineState,
                headingState = headingState,
                settings = settings,
                onToggleTrueNorth = viewModel::setTrueNorth,
                onToggleDialStyle = viewModel::setDialStyle,
                onOpenSettings = onNavigateToSettings,
                onLocationPermissionResult = viewModel::onLocationPermissionUpdated,
                savedPlaces = savedPlaces,
                onSelectExpressiveShape = viewModel::setExpressiveDialShape,
                onToggleImperial = viewModel::setImperialUnits,
                onSelectHeadingFormat = viewModel::setHeadingFormat,
                onToggleDecimals = viewModel::setShowHeadingDecimals,
                onRescanHardware = viewModel::rescanHardware,
                onSavePlace = viewModel::saveCurrentPlace,
                onDeletePlace = viewModel::deleteSavedPlace,
                compassDiagnosticsState = compassDiagnosticsState,
                onToggleRawMode = viewModel::setRawMode,
                onRequestDiagnosticCheck = viewModel::requestCompassDiagnosticCheck,
                onStartDiagnosticCheck = viewModel::startCompassDiagnosticCheck,
                onCancelDiagnosticCheck = viewModel::cancelCompassDiagnosticConfirmation,
                onDismissAnomalyBanner = viewModel::dismissCompassAnomalyBanner,
                onDismissInfoBanner = viewModel::dismissInfoBanner,
                onDismissMagneticBanner = viewModel::dismissMagneticInterferenceBanner,
                onDismissOrientationBanner = viewModel::dismissVerticalOrientationBanner,
                onMarkGuidanceSeen = viewModel::markCalibrationGuidanceSeen,
                onOpenCalibrationSettings = onOpenCalibrationSettings,
                onResetCompassRecovery = viewModel::resetCompassRecovery,
                bottomNavClearance = bottomNavClearance
            )
        }
        MainTab.LEVEL -> {
            LevelScreen(
                levelState = levelState,
                settings = settings,
                onCaptureReference = viewModel::captureLevelReference,
                onClearReference = viewModel::clearLevelReference,
                bottomNavClearance = bottomNavClearance
            )
        }
        MainTab.ARCPARK -> {
            ArcParkScreen(
                locationState = engineState.locationState,
                headingState = headingState,
                settings = settings,
                onLocationPermissionResult = viewModel::onLocationPermissionUpdated,
                onFindModeChanged = onFindModeChanged,
                bottomNavClearance = bottomNavClearance
            )
        }
        MainTab.SETTINGS -> {
            SettingsScreen(
                settings = settings,
                engineState = engineState,
                headingState = headingState,
                viewModel = viewModel,
                onOpenSensorDiagnostics = onOpenSensorDiagnostics,
                compassDiagnosticsState = compassDiagnosticsState,
                openCalibrationOnLaunch = openCalibrationInSettings,
                onCalibrationLaunchConsumed = onCalibrationInSettingsConsumed,
                bottomNavClearance = bottomNavClearance
            )
        }
    }
}

