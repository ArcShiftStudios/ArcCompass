package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.example.MainTab
import com.example.arcpark.ArcParkFindModeScreen
import com.example.arcpark.ArcParkScreen
import com.example.compass.CalibrationGuideDialog
import com.example.compass.CompassScreen
import com.example.data.AppThemeMode
import com.example.data.ArcCompassSettings
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.ParkingSpotEntity
import com.example.data.SavedPlaceEntity
import com.example.diagnostics.SensorDiagnosticsScreen
import com.example.level.LevelScreen
import com.example.sensor.AltitudeSource
import com.example.sensor.AxisReading
import com.example.sensor.CapabilityReport
import com.example.sensor.CompassAnomalyReason
import com.example.sensor.CompassDiagnosticsState
import com.example.sensor.CompassHeadingState
import com.example.sensor.CompassSource
import com.example.sensor.DeviceHardwareInfo
import com.example.sensor.DiagnosticStatus
import com.example.sensor.FeatureCapability
import com.example.sensor.LevelSource
import com.example.sensor.LevelTiltState
import com.example.sensor.LiveTelemetrySnapshot
import com.example.sensor.LocationDiagnosticSnapshot
import com.example.sensor.LocationPermissionState
import com.example.sensor.RequiredSensorType
import com.example.sensor.SensorAccuracyLevel
import com.example.sensor.SensorEngineState
import com.example.sensor.SensorReportingMode
import com.example.sensor.SensorSpec
import com.example.settings.SettingsScreen
import com.example.ui.components.MorphingFloatingNavBar
import com.example.ui.theme.ArcCompassTheme
import com.example.ui.theme.FrostedAmbientBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class ArcCompassScreenshotVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun outputDir(): File = File("build/verification_screenshots").apply { mkdirs() }

    private fun saveCapture(name: String, includeDialogOverlay: Boolean = false) {
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val windowViews = WindowInspector.getGlobalWindowViews()
            assertTrue("Expected at least one window view for $name", windowViews.isNotEmpty())
            val baseView: View = windowViews.first()
            val w = baseView.width.coerceAtLeast(1)
            val h = baseView.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            baseView.draw(canvas)

            if (includeDialogOverlay && windowViews.size > 1) {
                val dialogView = windowViews.last()
                canvas.drawColor(AndroidColor.argb(128, 0, 0, 0))
                val dx = ((w - dialogView.width) / 2f).coerceAtLeast(0f)
                val dy = ((h - dialogView.height) / 2f).coerceAtLeast(0f)
                canvas.save()
                canvas.translate(dx, dy)
                dialogView.draw(canvas)
                canvas.restore()
            }

            // Ensure image actually contains drawn non-uniform pixels
            val centerPixel = bitmap.getPixel(w / 2, h / 2)
            val cornerPixel = bitmap.getPixel(4, 4)
            assertTrue(
                "Captured bitmap $name must have valid dimensions",
                bitmap.width >= 400 && bitmap.height >= 800 && (centerPixel != 0 || cornerPixel != 0)
            )
            val outFile = File(outputDir(), "$name.png")
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }

    private fun richEngineState(): SensorEngineState = SensorEngineState(
        deviceInfo = DeviceHardwareInfo("Google", "Pixel 9", "google", "tokay", "16", 36, 12),
        sensorSpecs = listOf(
            SensorSpec(
                sensorType = RequiredSensorType.ACCELEROMETER,
                isAvailable = true,
                name = "LSM6DSR Accel",
                vendor = "STMicro",
                version = 1,
                powerMa = 0.25f,
                resolution = 0.002f,
                maxRange = 78.4f,
                minDelayUs = 5000,
                reportingMode = SensorReportingMode.CONTINUOUS,
                isWakeUpSensor = false,
                accuracy = SensorAccuracyLevel.HIGH,
                status = DiagnosticStatus.AVAILABLE
            ),
            SensorSpec(
                sensorType = RequiredSensorType.MAGNETIC_FIELD,
                isAvailable = true,
                name = "MMC5633NJL Mag",
                vendor = "MEMSIC",
                version = 1,
                powerMa = 1.1f,
                resolution = 0.1f,
                maxRange = 3000f,
                minDelayUs = 10000,
                reportingMode = SensorReportingMode.CONTINUOUS,
                isWakeUpSensor = false,
                accuracy = SensorAccuracyLevel.MEDIUM,
                status = DiagnosticStatus.AVAILABLE
            ),
            SensorSpec(
                sensorType = RequiredSensorType.PRESSURE,
                isAvailable = false,
                name = "Not Present",
                vendor = "None",
                version = 0,
                powerMa = 0f,
                resolution = 0f,
                maxRange = 0f,
                minDelayUs = 0,
                reportingMode = SensorReportingMode.CONTINUOUS,
                isWakeUpSensor = false,
                accuracy = SensorAccuracyLevel.NOT_APPLICABLE,
                status = DiagnosticStatus.UNAVAILABLE
            )
        ),
        capabilities = CapabilityReport(
            compass = FeatureCapability(
                status = DiagnosticStatus.AVAILABLE,
                source = CompassSource.ROTATION_VECTOR,
                summary = "Rotation Vector",
                detail = "Hardware fused compass operational."
            ),
            level = FeatureCapability(
                status = DiagnosticStatus.AVAILABLE,
                source = LevelSource.GRAVITY,
                summary = "Gravity Vector",
                detail = "3-axis gravity vector available."
            ),
            magneticFieldMonitor = FeatureCapability(
                status = DiagnosticStatus.AVAILABLE,
                source = true,
                summary = "47.2 μT normal range",
                detail = "Earth geomagnetic baseline detected."
            ),
            gyroscopeDiagnostics = FeatureCapability(
                status = DiagnosticStatus.AVAILABLE,
                source = true,
                summary = "Gyroscope Active",
                detail = "Zero-bias drift nominal."
            ),
            location = FeatureCapability(
                status = DiagnosticStatus.AVAILABLE,
                source = LocationPermissionState.GRANTED_FINE,
                summary = "GPS Ready",
                detail = "Fine location granted."
            ),
            altitude = FeatureCapability(
                status = DiagnosticStatus.AVAILABLE,
                source = AltitudeSource.LOCATION_GNSS,
                summary = "GNSS Altitude",
                detail = "WGS84 elevation active."
            )
        ),
        telemetry = LiveTelemetrySnapshot(
            magneticFieldMicroTesla = 47.2f,
            magneticAxes = AxisReading(18.4f, -12.1f, 41.7f),
            accelerometerAxes = AxisReading(0.14f, -0.09f, 9.80f),
            gyroscopeAxes = AxisReading(0.002f, -0.001f, 0.000f),
            rotationVectorComponents = floatArrayOf(0.02f, -0.01f, 0.38f, 0.92f),
            sensorUpdateTimestamps = mapOf(
                RequiredSensorType.ACCELEROMETER to 1_700_000_000_000L,
                RequiredSensorType.MAGNETIC_FIELD to 1_700_000_000_000L
            )
        ),
        locationState = LocationDiagnosticSnapshot(
            permissionState = LocationPermissionState.GRANTED_FINE,
            isLocationServiceEnabled = true,
            availableProviders = listOf("gps", "fused"),
            providerName = "gps",
            latitude = 37.7749,
            longitude = -122.4194,
            horizontalAccuracyMeters = 6f,
            altitudeMeters = 24.5,
            verticalAccuracyMeters = 3.2f,
            bearingDegrees = 45f,
            speedMetersPerSecond = 1.2f,
            status = DiagnosticStatus.AVAILABLE,
            statusMessage = "GPS Fix Ready"
        ),
        isObserving = true,
        lastScanMillis = 1_700_000_000_000L
    )

    private fun sampleHeading(azimuth: Float = 232f): CompassHeadingState = CompassHeadingState(
        isSupported = true,
        source = CompassSource.ROTATION_VECTOR,
        status = DiagnosticStatus.AVAILABLE,
        accuracy = SensorAccuracyLevel.HIGH,
        magneticAzimuthDegrees = azimuth,
        trueAzimuthDegrees = (azimuth + 13.1f) % 360f,
        declinationDegrees = 13.1f
    )

    @Composable
    private fun ThemedShell(
        themeMode: AppThemeMode,
        tab: MainTab? = null,
        content: @Composable () -> Unit
    ) {
        ArcCompassTheme(
            settings = ArcCompassSettings(
                themeMode = themeMode,
                frostedBlurEnabled = false,
                reducedMotionEnabled = true
            )
        ) {
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FrostedAmbientBackdrop()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        content()
                    }
                    if (tab != null) {
                        MorphingFloatingNavBar(
                            currentTab = tab,
                            onSelectTab = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `render all screens in dark and light mode for visual verification`() {
        var themeMode by mutableStateOf(AppThemeMode.DARK)
        var screenKey by mutableStateOf("compass_expressive")

        val engine = richEngineState()
        val places = listOf(
            SavedPlaceEntity(
                id = 1L,
                name = "Trailhead North",
                category = "Waypoint",
                latitude = 37.7810,
                longitude = -122.4110,
                altitudeMeters = 48.0
            )
        )
        val spots = listOf(
            ParkingSpotEntity(
                id = 1L,
                name = "West Civic Garage",
                floorOrLevel = "Level B2",
                notes = "Near elevator bank C",
                latitude = 37.7759,
                longitude = -122.4184,
                accuracyMeters = 6f,
                savedAtMillis = 1_700_000_000_000L
            )
        )

        composeRule.setContent {
            when (screenKey) {
                "compass_expressive" -> ThemedShell(themeMode, MainTab.COMPASS) {
                    val settings = ArcCompassSettings(
                        themeMode = themeMode,
                        dialStyle = DialStyle.EXPRESSIVE,
                        expressiveDialShape = ExpressiveDialShape.SOFT_SCALLOPED,
                        frostedBlurEnabled = false,
                        reducedMotionEnabled = true
                    )
                    CompassScreen(
                        engineState = engine,
                        headingState = sampleHeading(232f),
                        settings = settings,
                        savedPlaces = places,
                        onToggleTrueNorth = {},
                        onToggleDialStyle = {},
                        onOpenSettings = {},
                        onLocationPermissionResult = {},
                        bottomNavClearance = 96.dp
                    )
                }

                "compass_precision_anomaly" -> ThemedShell(themeMode, MainTab.COMPASS) {
                    val settings = ArcCompassSettings(
                        themeMode = themeMode,
                        dialStyle = DialStyle.PRECISION,
                        frostedBlurEnabled = false,
                        reducedMotionEnabled = true
                    )
                    CompassScreen(
                        engineState = engine.copy(
                            telemetry = engine.telemetry.copy(magneticFieldMicroTesla = 112f)
                        ),
                        headingState = sampleHeading(45f).copy(
                            accuracy = SensorAccuracyLevel.LOW
                        ),
                        settings = settings,
                        savedPlaces = places,
                        compassDiagnosticsState = CompassDiagnosticsState(
                            suspectedAnomaly = CompassAnomalyReason.FROZEN_DURING_MOTION
                        ),
                        onToggleTrueNorth = {},
                        onToggleDialStyle = {},
                        onOpenSettings = {},
                        onLocationPermissionResult = {},
                        bottomNavClearance = 96.dp
                    )
                }

                "compass_raw" -> ThemedShell(themeMode, MainTab.COMPASS) {
                    val settings = ArcCompassSettings(
                        themeMode = themeMode,
                        rawModeEnabled = true,
                        frostedBlurEnabled = false,
                        reducedMotionEnabled = true
                    )
                    CompassScreen(
                        engineState = engine,
                        headingState = sampleHeading(128f),
                        settings = settings,
                        savedPlaces = places,
                        onToggleTrueNorth = {},
                        onToggleDialStyle = {},
                        onOpenSettings = {},
                        onLocationPermissionResult = {},
                        bottomNavClearance = 96.dp
                    )
                }

                "level" -> ThemedShell(themeMode, MainTab.LEVEL) {
                    LevelScreen(
                        levelState = LevelTiltState(
                            isSupported = true,
                            source = LevelSource.GRAVITY,
                            status = DiagnosticStatus.AVAILABLE,
                            accuracy = SensorAccuracyLevel.HIGH,
                            rollDegrees = 0.0f,
                            pitchDegrees = 0.1f,
                            totalTiltDegrees = 0.1f,
                            verticalPlumbDegrees = 0.2f,
                            isFlatLevel = true,
                            isVerticalLevel = true
                        ),
                        settings = ArcCompassSettings(
                            themeMode = themeMode,
                            frostedBlurEnabled = false,
                            reducedMotionEnabled = true
                        ),
                        bottomNavClearance = 96.dp
                    )
                }

                "arcpark_find" -> ArcCompassTheme(
                    settings = ArcCompassSettings(
                        themeMode = themeMode,
                        frostedBlurEnabled = false,
                        reducedMotionEnabled = true
                    )
                ) {
                    ArcParkFindModeScreen(
                        spot = spots.first(),
                        locationState = engine.locationState,
                        headingState = sampleHeading(18f),
                        settings = ArcCompassSettings(
                            themeMode = themeMode,
                            frostedBlurEnabled = false,
                            reducedMotionEnabled = true
                        ),
                        onClose = {}
                    )
                }

                "diagnostics" -> ArcCompassTheme(
                    settings = ArcCompassSettings(
                        themeMode = themeMode,
                        frostedBlurEnabled = false,
                        reducedMotionEnabled = true
                    )
                ) {
                    SensorDiagnosticsScreen(onNavigateBack = {})
                }

                "calibration_dialog" -> ThemedShell(themeMode, MainTab.COMPASS) {
                    CalibrationGuideDialog(
                        accuracy = SensorAccuracyLevel.LOW,
                        microTesla = 112.4f,
                        hasMagnetometer = true,
                        onDismiss = {}
                    )
                }
            }
        }

        val standaloneStates = listOf(
            "compass_expressive",
            "compass_precision_anomaly",
            "compass_raw",
            "level",
            "arcpark_find",
            "diagnostics"
        )

        for (mode in listOf(AppThemeMode.DARK, AppThemeMode.LIGHT)) {
            val suffix = if (mode == AppThemeMode.DARK) "dark" else "light"
            themeMode = mode
            for (key in standaloneStates) {
                screenKey = key
                saveCapture("${key}_$suffix")
            }

            // Capture expanded bottom sheet on compass
            screenKey = "compass_expressive"
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("info_panel_toggle").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("info_panel_content").assertExists()
            saveCapture("compass_sheet_$suffix")

            // Capture calibration dialog overlaid on main screen
            screenKey = "calibration_dialog"
            saveCapture("calibration_dialog_$suffix", includeDialogOverlay = true)
        }
    }

    @Test
    fun `render arcpark populated list and save dialog in dark and light mode`() {
        var themeMode by mutableStateOf(AppThemeMode.DARK)
        var seeded by mutableStateOf(false)

        composeRule.setContent {
            val settings = ArcCompassSettings(
                themeMode = themeMode,
                frostedBlurEnabled = false,
                reducedMotionEnabled = true
            )
            val parkVm: com.example.arcpark.ArcParkViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (!seeded) {
                    seeded = true
                    parkVm.saveNewSpot(
                        name = "West Civic Garage",
                        floorOrLevel = "Level B2",
                        notes = "Near elevator bank C",
                        latitude = 37.7759,
                        longitude = -122.4184,
                        accuracyMeters = 6f
                    )
                }
            }
            ArcCompassTheme(settings = settings) {
                Surface(
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FrostedAmbientBackdrop()
                        ArcParkScreen(
                            locationState = richEngineState().locationState,
                            headingState = sampleHeading(),
                            settings = settings,
                            onLocationPermissionResult = {},
                            onFindModeChanged = {},
                            bottomNavClearance = 96.dp,
                            parkViewModel = parkVm
                        )
                        MorphingFloatingNavBar(
                            currentTab = MainTab.ARCPARK,
                            onSelectTab = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        fun pumpFramesAndCapture(name: String, includeDialogOverlay: Boolean = false) {
            repeat(6) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.runOnUiThread {
                    ShadowLooper.idleMainLooper()
                }
            }
            composeRule.runOnUiThread {
                val windowViews = WindowInspector.getGlobalWindowViews()
                assertTrue("Expected at least one window view for $name", windowViews.isNotEmpty())
                val baseView: View = windowViews.first()
                val w = baseView.width.coerceAtLeast(1)
                val h = baseView.height.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                baseView.draw(canvas)
                if (includeDialogOverlay && windowViews.size > 1) {
                    val dialogView = windowViews.last()
                    canvas.drawColor(AndroidColor.argb(128, 0, 0, 0))
                    val dx = ((w - dialogView.width) / 2f).coerceAtLeast(0f)
                    val dy = ((h - dialogView.height) / 2f).coerceAtLeast(0f)
                    canvas.save()
                    canvas.translate(dx, dy)
                    dialogView.draw(canvas)
                    canvas.restore()
                }
                val outFile = File(outputDir(), "$name.png")
                FileOutputStream(outFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        }

        Thread.sleep(250)
        composeRule.waitForIdle()
        saveCapture("arcpark_dark")

        themeMode = AppThemeMode.LIGHT
        saveCapture("arcpark_light")

        // Open Save Parking dialog, capture with ShadowLooper idle in Light & Dark modes
        composeRule.onNodeWithTag("arcpark_save_fab").performClick()
        composeRule.mainClock.autoAdvance = false
        pumpFramesAndCapture("arcpark_dialog_light", includeDialogOverlay = true)

        themeMode = AppThemeMode.DARK
        pumpFramesAndCapture("arcpark_dialog_dark", includeDialogOverlay = true)

        composeRule.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `render settings screen and choice dialog in dark and light mode`() {
        var themeMode by mutableStateOf(AppThemeMode.DARK)

        composeRule.setContent {
            val settings = ArcCompassSettings(
                themeMode = themeMode,
                frostedBlurEnabled = false,
                reducedMotionEnabled = true
            )
            ArcCompassTheme(settings = settings) {
                Surface(
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FrostedAmbientBackdrop()
                        SettingsScreen(
                            settings = settings,
                            engineState = richEngineState(),
                            headingState = sampleHeading(),
                            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                            onOpenSensorDiagnostics = {},
                            bottomNavClearance = 96.dp
                        )
                        MorphingFloatingNavBar(
                            currentTab = MainTab.SETTINGS,
                            onSelectTab = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        for (mode in listOf(AppThemeMode.DARK, AppThemeMode.LIGHT)) {
            val suffix = if (mode == AppThemeMode.DARK) "dark" else "light"
            themeMode = mode
            saveCapture("settings_$suffix")

            composeRule.onNodeWithTag("setting_dial_style").performScrollTo().performClick()
            saveCapture("settings_dialog_$suffix", includeDialogOverlay = true)
            composeRule.onNodeWithText("Close").performClick()
            composeRule.waitForIdle()
        }
    }
}
