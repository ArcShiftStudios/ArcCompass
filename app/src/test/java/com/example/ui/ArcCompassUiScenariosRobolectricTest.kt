package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.TriggerEventListener
import android.location.LocationManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ArcCompassAppRoot
import com.example.arcpark.ArcParkFindModeScreen
import com.example.compass.CompassScreen
import com.example.data.AccentPreset
import com.example.data.AppThemeMode
import com.example.data.ArcCompassDatabase
import com.example.data.ArcCompassSettings
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.ParkingRepository
import com.example.data.ParkingSpotEntity
import com.example.data.SavedPlaceEntity
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
import com.example.sensor.InteractiveCheckPhase
import com.example.sensor.LevelSource
import com.example.sensor.LevelTiltState
import com.example.sensor.LiveTelemetrySnapshot
import com.example.sensor.LocationDiagnosticSnapshot
import com.example.sensor.LocationPermissionState
import com.example.sensor.RequiredSensorType
import com.example.sensor.SensorAccuracyLevel
import com.example.sensor.SensorEngineState
import com.example.sensor.SensorManagerProvider
import com.example.sensor.SensorReportingMode
import com.example.sensor.SensorRepository
import com.example.sensor.SensorSpec
import com.example.ui.theme.ArcCompassTheme
import com.example.ui.theme.FredokaExpanded
import com.example.ui.theme.FredokaNormal
import com.example.ui.theme.FredokaSemiCondensed
import com.example.ui.theme.FredokaSemiExpanded
import com.example.ui.theme.RawTelemetryMonoFontFamily
import com.example.ui.theme.arcTextRoles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSensor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArcCompassUiScenariosRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSpecs(magAvailable: Boolean): List<SensorSpec> = listOf(
        SensorSpec(
            sensorType = RequiredSensorType.ACCELEROMETER,
            isAvailable = true,
            name = "BMI160 Accel",
            vendor = "Bosch",
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
            isAvailable = magAvailable,
            name = if (magAvailable) "AK09918 Mag" else "Missing",
            vendor = if (magAvailable) "AKM" else "None",
            version = 1,
            powerMa = 1.1f,
            resolution = 0.15f,
            maxRange = 4900f,
            minDelayUs = 10000,
            reportingMode = SensorReportingMode.CONTINUOUS,
            isWakeUpSensor = false,
            accuracy = if (magAvailable) SensorAccuracyLevel.HIGH else SensorAccuracyLevel.NOT_APPLICABLE,
            status = if (magAvailable) DiagnosticStatus.AVAILABLE else DiagnosticStatus.UNAVAILABLE
        )
    )

    private fun sampleEngineState(
        hasCompass: Boolean = true,
        hasMagnetometer: Boolean = true,
        gpsAccuracyMeters: Float = 8f
    ): SensorEngineState {
        return SensorEngineState(
            deviceInfo = DeviceHardwareInfo("Vivo", "Y21", "vivo", "y21", "13", 33, 6),
            sensorSpecs = sampleSpecs(magAvailable = hasMagnetometer),
            capabilities = CapabilityReport(
                compass = FeatureCapability(
                    status = if (hasCompass) DiagnosticStatus.AVAILABLE else DiagnosticStatus.UNAVAILABLE,
                    source = if (hasCompass) CompassSource.ROTATION_VECTOR else CompassSource.UNSUPPORTED,
                    summary = "Compass",
                    detail = "Test"
                ),
                level = FeatureCapability(
                    status = DiagnosticStatus.AVAILABLE,
                    source = LevelSource.GRAVITY,
                    summary = "Level",
                    detail = "Test"
                ),
                magneticFieldMonitor = FeatureCapability(
                    status = if (hasMagnetometer) DiagnosticStatus.AVAILABLE else DiagnosticStatus.UNAVAILABLE,
                    source = hasMagnetometer,
                    summary = "Mag",
                    detail = "Test"
                ),
                gyroscopeDiagnostics = FeatureCapability(
                    status = DiagnosticStatus.AVAILABLE,
                    source = true,
                    summary = "Gyro",
                    detail = "Test"
                ),
                location = FeatureCapability(
                    status = DiagnosticStatus.AVAILABLE,
                    source = LocationPermissionState.GRANTED_FINE,
                    summary = "Loc",
                    detail = "Test"
                ),
                altitude = FeatureCapability(
                    status = DiagnosticStatus.AVAILABLE,
                    source = AltitudeSource.LOCATION_GNSS,
                    summary = "Alt",
                    detail = "Test"
                )
            ),
            telemetry = LiveTelemetrySnapshot(
                magneticFieldMicroTesla = if (hasMagnetometer) 48.2f else null,
                accelerometerAxes = AxisReading(0.12f, -0.05f, 9.81f),
                sensorUpdateTimestamps = mapOf(RequiredSensorType.ACCELEROMETER to 1_700_000_000_000L)
            ),
            locationState = LocationDiagnosticSnapshot(
                permissionState = LocationPermissionState.GRANTED_FINE,
                isLocationServiceEnabled = true,
                availableProviders = listOf("gps"),
                latitude = 37.7749,
                longitude = -122.4194,
                horizontalAccuracyMeters = gpsAccuracyMeters,
                altitudeMeters = 18.0,
                bearingDegrees = 42f,
                speedMetersPerSecond = 1.4f,
                status = DiagnosticStatus.AVAILABLE,
                statusMessage = "GPS Ready"
            ),
            isObserving = true,
            lastScanMillis = 1L
        )
    }

    @Test
    fun `compass screen switches dials, expands frosted sheet detents, and handles unsupported hardware`() {
        var settings by mutableStateOf(ArcCompassSettings())
        val savedPlaces = mutableListOf(
            SavedPlaceEntity(
                id = 1L,
                name = "Base Camp",
                category = "Waypoint",
                latitude = 37.7790,
                longitude = -122.4180,
                altitudeMeters = 25.0
            )
        )
        var savedCount by mutableStateOf(1)

        composeRule.setContent {
            ArcCompassTheme(settings = settings) {
                CompassScreen(
                    engineState = sampleEngineState(hasCompass = false, hasMagnetometer = false),
                    headingState = CompassHeadingState(
                        isSupported = false,
                        source = CompassSource.UNSUPPORTED,
                        status = DiagnosticStatus.UNAVAILABLE
                    ),
                    settings = settings,
                    savedPlaces = savedPlaces.take(savedCount),
                    onToggleTrueNorth = { settings = settings.copy(trueNorthEnabled = it) },
                    onToggleDialStyle = { settings = settings.copy(dialStyle = it) },
                    onSelectExpressiveShape = { settings = settings.copy(expressiveDialShape = it) },
                    onSavePlace = { name, cat, lat, lon, alt ->
                        savedPlaces.add(SavedPlaceEntity(2L, name, cat, lat, lon, alt))
                        savedCount = savedPlaces.size
                    },
                    onOpenSettings = {},
                    onLocationPermissionResult = {}
                )
            }
        }

        composeRule.onNodeWithTag("compass_dial_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_settings_button").assertDoesNotExist()

        // Expressive shape chips appear in the top DialCustomizationGlassBar; select before scrolling down
        composeRule.onNodeWithTag("expressive_shape_chip_rounded_flower").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(ExpressiveDialShape.ROUNDED_FLOWER, settings.expressiveDialShape)

        // Switching to Precision dial hides Expressive shape chips so Precision remains unmodified
        composeRule.onNodeWithTag("dial_style_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DialStyle.PRECISION, settings.dialStyle)
        assertTrue(composeRule.onAllNodesWithTag("expressive_shape_chip_rounded_flower").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("unsupported_compass_banner").performScrollTo().assertIsDisplayed()

        // Expand frosted bottom sheet: Collapsed -> Half -> Full
        composeRule.onNodeWithTag("info_panel_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Half").assertIsDisplayed()
        composeRule.onNodeWithTag("info_panel_content").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Unavailable").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("saved_places_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Base Camp").assertIsDisplayed()

        composeRule.onNodeWithTag("info_panel_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Full").assertIsDisplayed()

        // Save Place frosted dialog works inside expanded sheet
        composeRule.onNodeWithTag("save_place_button").performScrollTo().performClick()
        composeRule.onNodeWithText("Pin Current Location").assertIsDisplayed()
        composeRule.onNodeWithText("Save Place").performClick()
        composeRule.waitForIdle()
        assertEquals(2, savedCount)
    }

    @Test
    fun `raw mode displays only available hardware sensors and diagnostic banner requests user confirmation`() {
        var settings by mutableStateOf(ArcCompassSettings(rawModeEnabled = true))
        var diagnosticsState by mutableStateOf(
            CompassDiagnosticsState(
                suspectedAnomaly = CompassAnomalyReason.FROZEN_DURING_MOTION
            )
        )

        composeRule.setContent {
            ArcCompassTheme(settings = settings) {
                CompassScreen(
                    engineState = sampleEngineState(hasCompass = true, hasMagnetometer = false),
                    headingState = CompassHeadingState(
                        isSupported = true,
                        source = CompassSource.ROTATION_VECTOR,
                        status = DiagnosticStatus.AVAILABLE,
                        accuracy = SensorAccuracyLevel.HIGH,
                        magneticAzimuthDegrees = 128f
                    ),
                    settings = settings,
                    compassDiagnosticsState = diagnosticsState,
                    onToggleTrueNorth = {},
                    onToggleDialStyle = {},
                    onToggleRawMode = { settings = settings.copy(rawModeEnabled = it) },
                    onRequestDiagnosticCheck = {
                        diagnosticsState = diagnosticsState.copy(checkPhase = InteractiveCheckPhase.AWAITING_CONFIRMATION)
                    },
                    onStartDiagnosticCheck = {
                        diagnosticsState = diagnosticsState.copy(checkPhase = InteractiveCheckPhase.RUNNING)
                    },
                    onCancelDiagnosticCheck = {
                        diagnosticsState = diagnosticsState.copy(checkPhase = InteractiveCheckPhase.IDLE)
                    },
                    onDismissAnomalyBanner = {
                        diagnosticsState = diagnosticsState.copy(bannerDismissed = true)
                    },
                    onLocationPermissionResult = {}
                )
            }
        }

        // RAW mode displays available Accelerometer card and omits unavailable Magnetometer card
        composeRule.onNodeWithTag("compass_raw_mode_deck").assertIsDisplayed()
        composeRule.onNodeWithTag("raw_sensor_card_accelerometer").assertIsDisplayed()
        composeRule.onNodeWithTag("raw_sensor_card_magnetic_field").assertDoesNotExist()

        // Pause / Resume toggle updates label cleanly
        composeRule.onNodeWithTag("raw_mode_pause_button").performClick()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()

        // Anomaly banner appears near top and triggers confirmation modal prior to running diagnostic check
        composeRule.onNodeWithTag("compass_anomaly_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_anomaly_run_check_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compass_interactive_check_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_check_confirm_start").assertIsDisplayed()
    }

    @Test
    fun `level screen supports tap-to-zero custom reference and display-only sliders stay non-interactive`() {
        var supported by mutableStateOf(true)
        var hasCustomReference by mutableStateOf(false)
        composeRule.setContent {
            ArcCompassTheme {
                LevelScreen(
                    levelState = if (supported) {
                        LevelTiltState(
                            isSupported = true,
                            source = LevelSource.GRAVITY,
                            status = DiagnosticStatus.AVAILABLE,
                            accuracy = SensorAccuracyLevel.HIGH,
                            rollDegrees = if (hasCustomReference) 0.0f else 4.5f,
                            pitchDegrees = if (hasCustomReference) 0.0f else -2.1f,
                            rawRollDegrees = 4.5f,
                            rawPitchDegrees = -2.1f,
                            totalTiltDegrees = if (hasCustomReference) 0.0f else 5.0f,
                            verticalPlumbDegrees = 2.4f,
                            isFlatLevel = hasCustomReference,
                            isVerticalLevel = false,
                            hasCustomReference = hasCustomReference
                        )
                    } else {
                        LevelTiltState(isSupported = false)
                    },
                    settings = ArcCompassSettings(),
                    onCaptureReference = { hasCustomReference = true },
                    onClearReference = { hasCustomReference = false }
                )
            }
        }

        composeRule.onNodeWithTag("level_sensor_status").assertIsDisplayed()
        composeRule.onNodeWithTag("level_bullseye_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("level_roll_slider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("level_plumb_slider").performScrollTo().assertIsDisplayed()

        // Attempted swipe across display-only slider does not alter sensor state (appears in slider & summary metric)
        composeRule.onNodeWithTag("level_roll_slider").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("+4.5°").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("level_set_reference_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(hasCustomReference)
        composeRule.onNodeWithTag("level_reset_true_zero_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("level_flat_badge").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("level_reset_true_zero_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(!hasCustomReference)

        supported = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("unsupported_level_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun `level screen adapts cleanly to wide landscape configuration`() {
        composeRule.setContent {
            ArcCompassTheme {
                LevelScreen(
                    levelState = LevelTiltState(
                        isSupported = true,
                        source = LevelSource.GRAVITY,
                        status = DiagnosticStatus.AVAILABLE,
                        accuracy = SensorAccuracyLevel.HIGH,
                        rollDegrees = 0.1f,
                        pitchDegrees = 0.0f,
                        totalTiltDegrees = 0.1f,
                        verticalPlumbDegrees = 0.2f,
                        isFlatLevel = true
                    ),
                    settings = ArcCompassSettings()
                )
            }
        }

        composeRule.onNodeWithTag("level_bullseye_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("level_set_reference_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Horizontal Inclination (Roll)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vertical Inclination (Plumb)").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    fun `floating rounded-square nav navigates all tabs via tap and drag and expands settings sections`() {
        composeRule.setContent {
            ArcCompassAppRoot()
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("morphing_floating_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_nav_active_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_dial_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_settings_button").assertDoesNotExist()
        composeRule.onNodeWithTag("info_panel_toggle").assertIsDisplayed()

        // Horizontal drag across bottom bar moves off Compass
        composeRule.onNodeWithTag("morphing_floating_nav").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_level").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("level_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_arcpark").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("arcpark_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_sensor_diagnostics_item").assertIsDisplayed()

        // Swipe left on dock moves away from Settings
        composeRule.onNodeWithTag("morphing_floating_nav").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()

        // Scroll to LazyColumn item keys before clicking
        composeRule.onNodeWithTag("settings_list").performScrollToKey("section_instruments")
        composeRule.onNodeWithTag("settings_section_instruments").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Imperial units").performScrollTo().assertIsDisplayed()

        // Open Sensor Diagnostics from first settings item and return cleanly
        composeRule.onNodeWithTag("settings_list").performScrollToKey("section_hardware")
        composeRule.onNodeWithTag("settings_sensor_diagnostics_item").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_back_button").assertIsDisplayed()

        composeRule.onNodeWithTag("diagnostics_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun `scenario 7 and 9 arcpark find mode disables phone arrow on unreliable orientation and warns on poor GPS`() {
        val spot = ParkingSpotEntity(
            id = 1L,
            name = "North Garage",
            floorOrLevel = "B2",
            notes = "Near elevator",
            latitude = 37.7755,
            longitude = -122.4194,
            accuracyMeters = 10f,
            savedAtMillis = 1_700_000_000_000L
        )
        var reliableOrientation by mutableStateOf(false)

        composeRule.setContent {
            ArcCompassTheme {
                ArcParkFindModeScreen(
                    spot = spot,
                    locationState = sampleEngineState(gpsAccuracyMeters = 48f).locationState,
                    headingState = if (reliableOrientation) {
                        CompassHeadingState(
                            isSupported = true,
                            source = CompassSource.ROTATION_VECTOR,
                            status = DiagnosticStatus.AVAILABLE,
                            accuracy = SensorAccuracyLevel.HIGH,
                            magneticAzimuthDegrees = 15f
                        )
                    } else {
                        CompassHeadingState(
                            isSupported = true,
                            source = CompassSource.ROTATION_VECTOR,
                            status = DiagnosticStatus.ACCURACY_UNRELIABLE,
                            accuracy = SensorAccuracyLevel.UNRELIABLE,
                            magneticAzimuthDegrees = 15f
                        )
                    },
                    settings = ArcCompassSettings(reducedMotionEnabled = true),
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag("arcpark_unreliable_orientation_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("arcpark_directional_arrow").assertDoesNotExist()
        composeRule.onNodeWithText("GPS travel direction: NE (42°)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("arcpark_find_accuracy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Location accuracy: ±48 m (Low precision — GPS only)").assertIsDisplayed()

        reliableOrientation = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("arcpark_directional_arrow").assertIsDisplayed()
        composeRule.onNodeWithTag("arcpark_unreliable_orientation_banner").assertDoesNotExist()
    }

    @Test
    fun `scenario 10 fredoka typography roles and light dark night high contrast themes apply cleanly with Room CRUD`() = runBlocking {
        var themeMode by mutableStateOf(AppThemeMode.DARK)
        var nightFilter by mutableStateOf(false)
        var highContrast by mutableStateOf(false)
        var measuredBgLuminance = 0f
        var measuredInkLuminance = 0f
        var measuredPrimaryRed = 0f
        var displayFamilyMatches = false
        var headingFamilyMatches = false
        var settingsFamilyMatches = false
        var technicalFamilyMatches = false
        var regularRolesAvoidMono = false

        composeRule.setContent {
            ArcCompassTheme(
                settings = ArcCompassSettings(
                    themeMode = themeMode,
                    redScreenFilterEnabled = nightFilter,
                    highContrastEnabled = highContrast,
                    frostedBlurEnabled = false,
                    expressiveAccentPreset = AccentPreset.EMERALD
                )
            ) {
                val roles = MaterialTheme.arcTextRoles
                measuredBgLuminance = MaterialTheme.colorScheme.background.luminance()
                measuredInkLuminance = MaterialTheme.colorScheme.onSurface.luminance()
                measuredPrimaryRed = MaterialTheme.colorScheme.primary.red
                displayFamilyMatches = roles.display.fontFamily == FredokaExpanded
                headingFamilyMatches = roles.heading.fontFamily == FredokaSemiExpanded
                settingsFamilyMatches = roles.settings.fontFamily == FredokaNormal
                technicalFamilyMatches = roles.technical.fontFamily == FredokaSemiCondensed
                regularRolesAvoidMono = listOf(
                    roles.display, roles.heading, roles.measurement,
                    roles.compassDirection, roles.navigation, roles.settings,
                    roles.technical, roles.description, roles.button
                ).none { it.fontFamily == RawTelemetryMonoFontFamily }
            }
        }

        composeRule.waitForIdle()
        assertTrue(displayFamilyMatches)
        assertTrue(headingFamilyMatches)
        assertTrue(settingsFamilyMatches)
        assertTrue(technicalFamilyMatches)
        assertTrue(regularRolesAvoidMono)
        assertTrue(measuredBgLuminance < 0.1f)
        assertTrue("Dark theme text ink must be bright", measuredInkLuminance > 0.7f)

        themeMode = AppThemeMode.LIGHT
        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance > 0.7f)
        assertTrue("Light theme text ink must be dark", measuredInkLuminance < 0.15f)
        assertNotEquals(0f, measuredBgLuminance)

        highContrast = true
        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance > 0.95f)

        highContrast = false
        nightFilter = true
        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance < 0.05f)
        assertTrue(measuredPrimaryRed > 0.8f)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ArcCompassDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ParkingRepository(db.parkingSpotDao())
            val id = repo.saveSpot(
                ParkingSpotEntity(
                    name = "Airport Lot C",
                    floorOrLevel = "Level 3",
                    notes = "Row J",
                    latitude = 37.6213,
                    longitude = -122.3790,
                    accuracyMeters = 6f
                )
            )
            var list = repo.allSpots.first()
            assertEquals(1, list.size)
            assertEquals("Airport Lot C", list.first().name)

            repo.updateSpot(list.first().copy(notes = "Row K"))
            list = repo.allSpots.first()
            assertEquals("Row K", list.first().notes)

            repo.deleteSpot(id)
            list = repo.allSpots.first()
            assertTrue(list.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    fun `sensor check results dialog dismisses on Done, restarts cleanly on Retest, and restores default on Use Default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensors = mapOf(
            Sensor.TYPE_ACCELEROMETER to ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER),
            Sensor.TYPE_MAGNETIC_FIELD to ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD),
            Sensor.TYPE_GYROSCOPE to ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE),
            Sensor.TYPE_ROTATION_VECTOR to ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR),
            Sensor.TYPE_GAME_ROTATION_VECTOR to ShadowSensor.newInstance(Sensor.TYPE_GAME_ROTATION_VECTOR)
        )
        val fakeProvider = object : SensorManagerProvider {
            val activeRegistrations = mutableListOf<Pair<SensorEventListener, Int>>()

            override fun getDefaultSensor(type: Int): Sensor? = sensors[type]
            override fun getAllSensors(): List<Sensor> = sensors.values.toList()
            override fun registerListener(
                listener: SensorEventListener,
                sensor: Sensor,
                samplingPeriodUs: Int
            ): Boolean {
                activeRegistrations.add(listener to sensor.type)
                return true
            }
            override fun unregisterListener(listener: SensorEventListener) {
                activeRegistrations.removeAll { it.first === listener }
            }
            override fun requestTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean = false
            override fun cancelTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean = false
            override fun getLocationManager(): LocationManager? = null
            override fun isLocationEnabled(): Boolean = false
            override fun getDeviceInfo(totalDetectedSensors: Int): DeviceHardwareInfo =
                DeviceHardwareInfo("ArcTest", "Precision", "Test", "arc", "15", 35, totalDetectedSensors)

            fun emit(type: Int, vararg values: Float) {
                val sensor = sensors[type] ?: return
                val event = createSensorEvent(sensor, values)
                activeRegistrations
                    .filter { it.second == type }
                    .map { it.first }
                    .distinct()
                    .forEach { it.onSensorChanged(event) }
            }
        }

        val repository = SensorRepository(context, provider = fakeProvider)
        repository.startObserving()
        var settings by mutableStateOf(ArcCompassSettings(reducedMotionEnabled = true, frostedBlurEnabled = false))

        composeRule.setContent {
            val engineState by repository.engineState.collectAsStateWithLifecycle()
            val headingState by repository.headingState.collectAsStateWithLifecycle()
            val diagnosticsState by repository.compassDiagnosticsState.collectAsStateWithLifecycle()

            ArcCompassTheme(settings = settings) {
                CompassScreen(
                    engineState = engineState,
                    headingState = headingState,
                    settings = settings,
                    compassDiagnosticsState = diagnosticsState,
                    onToggleTrueNorth = { settings = settings.copy(trueNorthEnabled = it) },
                    onToggleDialStyle = { settings = settings.copy(dialStyle = it) },
                    onLocationPermissionResult = {},
                    onRequestDiagnosticCheck = repository::requestCompassDiagnosticCheck,
                    onStartDiagnosticCheck = repository::startCompassDiagnosticCheck,
                    onCancelDiagnosticCheck = repository::cancelCompassDiagnosticConfirmation,
                    onDismissAnomalyBanner = repository::dismissCompassAnomalyBanner,
                    onResetCompassRecovery = repository::resetCompassRecovery
                )
            }
        }

        // Initial default compass heading is active on ROTATION_VECTOR
        fakeProvider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.2f, 0.98f)
        composeRule.waitForIdle()
        assertEquals(CompassSource.ROTATION_VECTOR, repository.headingState.value.source)
        assertNotNull(repository.headingState.value.magneticAzimuthDegrees)

        // 1. Trigger diagnostics via bottom sheet "Sensor Check" (Collapsed -> Half -> Full)
        composeRule.onNodeWithTag("info_panel_toggle").performClick()
        composeRule.onNodeWithTag("info_panel_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet_sensor_check_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compass_interactive_check_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_check_confirm_start").performClick()
        composeRule.waitForIdle()

        // 2. Run guided 4-second check with frozen RV + active Accel & Mag -> switches to Accel+Mag fallback
        repeat(6) { idx ->
            ShadowLooper.idleMainLooper(750L, TimeUnit.MILLISECONDS)
            fakeProvider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.2f, 0.98f)
            fakeProvider.emit(Sensor.TYPE_ACCELEROMETER, idx * 0.8f, -idx * 0.4f, 9.7f)
            fakeProvider.emit(Sensor.TYPE_MAGNETIC_FIELD, 18f + idx * 3f, -12f, 40f)
            fakeProvider.emit(Sensor.TYPE_GYROSCOPE, 0.35f, 0.12f, 0.25f)
        }
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()

        // 3. Results dialog displays fallback & Done / Retest / Use Default buttons
        assertEquals(InteractiveCheckPhase.COMPLETED, repository.compassDiagnosticsState.value.checkPhase)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, repository.headingState.value.source)
        composeRule.onNodeWithText("Sensor Check Results").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_check_use_default").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("compass_check_retest").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("compass_check_done").performScrollTo().assertIsDisplayed()

        // 4. Tap Done -> dismisses dialog immediately, preserves fallback, and does not block screen interaction
        composeRule.onNodeWithTag("compass_check_done").performClick()
        composeRule.waitForIdle()
        assertEquals(InteractiveCheckPhase.IDLE, repository.compassDiagnosticsState.value.checkPhase)
        composeRule.onAllNodesWithTag("compass_interactive_check_dialog").assertCountEquals(0)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, repository.headingState.value.source)

        // Confirm fallback compass updates heading after dialog dismissal and underlying UI is clickable
        fakeProvider.emit(Sensor.TYPE_ACCELEROMETER, 0f, 0f, 9.81f)
        fakeProvider.emit(Sensor.TYPE_MAGNETIC_FIELD, 20f, -10f, 40f)
        composeRule.waitForIdle()
        assertNotNull(repository.headingState.value.magneticAzimuthDegrees)
        composeRule.onNodeWithTag("info_panel_toggle").performClick() // Full -> Collapsed
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dial_style_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DialStyle.PRECISION, settings.dialStyle)

        // 5. Open diagnostics again -> run check -> test Retest (no duplicate dialogs or listeners)
        composeRule.onNodeWithTag("info_panel_toggle").performClick() // Collapsed -> Half
        composeRule.onNodeWithTag("info_panel_toggle").performClick() // Half -> Full
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet_sensor_check_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compass_check_confirm_start").performClick()
        composeRule.waitForIdle()
        repeat(6) { idx ->
            ShadowLooper.idleMainLooper(750L, TimeUnit.MILLISECONDS)
            fakeProvider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.2f, 0.98f)
            fakeProvider.emit(Sensor.TYPE_ACCELEROMETER, idx * 0.9f, -idx * 0.4f, 9.6f)
            fakeProvider.emit(Sensor.TYPE_MAGNETIC_FIELD, 16f + idx * 3f, -11f, 39f)
            fakeProvider.emit(Sensor.TYPE_GYROSCOPE, 0.4f, 0.1f, 0.2f)
        }
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
        assertEquals(InteractiveCheckPhase.COMPLETED, repository.compassDiagnosticsState.value.checkPhase)

        // Tap Retest -> returns to RUNNING inside a single dialog without duplicating listeners
        composeRule.onNodeWithTag("compass_check_retest").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(InteractiveCheckPhase.RUNNING, repository.compassDiagnosticsState.value.checkPhase)
        composeRule.onAllNodesWithTag("compass_interactive_check_dialog").assertCountEquals(1)
        assertEquals(
            "No duplicate (listener, sensorType) registrations allowed",
            fakeProvider.activeRegistrations.distinct().size,
            fakeProvider.activeRegistrations.size
        )

        repeat(6) { idx ->
            ShadowLooper.idleMainLooper(750L, TimeUnit.MILLISECONDS)
            fakeProvider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.2f, 0.98f)
            fakeProvider.emit(Sensor.TYPE_ACCELEROMETER, idx * 0.9f, -idx * 0.4f, 9.6f)
            fakeProvider.emit(Sensor.TYPE_MAGNETIC_FIELD, 16f + idx * 3f, -11f, 39f)
            fakeProvider.emit(Sensor.TYPE_GYROSCOPE, 0.4f, 0.1f, 0.2f)
        }
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
        assertEquals(InteractiveCheckPhase.COMPLETED, repository.compassDiagnosticsState.value.checkPhase)
        composeRule.onAllNodesWithTag("compass_interactive_check_dialog").assertCountEquals(1)

        // 6. Tap Use Default -> dismisses dialog and restores default ROTATION_VECTOR sensor configuration
        composeRule.onNodeWithTag("compass_check_use_default").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(InteractiveCheckPhase.IDLE, repository.compassDiagnosticsState.value.checkPhase)
        assertNull(repository.compassDiagnosticsState.value.activeRecoverySource)
        composeRule.onAllNodesWithTag("compass_interactive_check_dialog").assertCountEquals(0)
        assertEquals(CompassSource.ROTATION_VECTOR, repository.headingState.value.source)
        assertEquals(
            fakeProvider.activeRegistrations.distinct().size,
            fakeProvider.activeRegistrations.size
        )

        // 7. Confirm compass still processes headings cleanly after restoring default
        fakeProvider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.5f, 0.866f)
        composeRule.waitForIdle()
        assertNotNull(repository.headingState.value.magneticAzimuthDegrees)
        repository.stopObserving()
    }

    private fun createSensorEvent(sensor: Sensor, values: FloatArray): SensorEvent {
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        ctor.isAccessible = true
        val event = ctor.newInstance(values.size)
        val sensorField = SensorEvent::class.java.getField("sensor")
        sensorField.isAccessible = true
        sensorField.set(event, sensor)
        System.arraycopy(values, 0, event.values, 0, values.size)
        event.timestamp = System.nanoTime()
        return event
    }
}
