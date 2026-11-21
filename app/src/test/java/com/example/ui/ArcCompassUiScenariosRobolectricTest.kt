package com.example.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.sensor.CapabilityReport
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
import com.example.sensor.SensorAccuracyLevel
import com.example.sensor.SensorEngineState
import com.example.ui.theme.ArcCompassTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArcCompassUiScenariosRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleEngineState(
        hasCompass: Boolean = true,
        hasMagnetometer: Boolean = true,
        gpsAccuracyMeters: Float = 8f
    ): SensorEngineState {
        return SensorEngineState(
            deviceInfo = DeviceHardwareInfo("Vivo", "Y21", "vivo", "y21", "13", 33, 6),
            sensorSpecs = emptyList(),
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
            telemetry = LiveTelemetrySnapshot(magneticFieldMicroTesla = if (hasMagnetometer) 48.2f else null),
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
        // Milestone 4: Extra top-right circular settings control must not exist on CompassScreen
        composeRule.onNodeWithTag("compass_settings_button").assertDoesNotExist()
        composeRule.onNodeWithTag("unsupported_compass_banner").performScrollTo().assertIsDisplayed()

        // Expressive shape chips appear on Expressive dial and update selection
        composeRule.onNodeWithTag("expressive_shape_chip_rounded_flower").performClick()
        assertEquals(ExpressiveDialShape.ROUNDED_FLOWER, settings.expressiveDialShape)

        // Switching to Precision dial hides Expressive shape chips so Precision remains unmodified
        composeRule.onNodeWithTag("dial_style_toggle").performClick()
        assertEquals(DialStyle.PRECISION, settings.dialStyle)
        assertTrue(composeRule.onAllNodesWithTag("expressive_shape_chip_rounded_flower").fetchSemanticsNodes().isEmpty())

        // Expand frosted bottom sheet: Collapsed -> Half (already populated with telemetry + Saved Places) -> Full
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

        // Save Place frosted dialog works inside expanded sheet (no duplicate Settings button in Dock)
        composeRule.onNodeWithTag("save_place_button").performScrollTo().performClick()
        composeRule.onNodeWithText("Pin Current Location").assertIsDisplayed()
        composeRule.onNodeWithText("Save Place").performClick()
        composeRule.waitForIdle()
        assertEquals(2, savedCount)
    }

    @Test
    fun `level screen supports tap-to-zero custom reference and fallback card when unsupported`() {
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
        composeRule.onNodeWithTag("level_set_reference_button").performClick()
        composeRule.waitForIdle()
        assertTrue(hasCustomReference)
        composeRule.onNodeWithTag("level_reset_true_zero_button").assertIsDisplayed()
        composeRule.onNodeWithTag("level_flat_badge").assertIsDisplayed()

        composeRule.onNodeWithTag("level_reset_true_zero_button").performClick()
        composeRule.waitForIdle()
        assertTrue(!hasCustomReference)

        supported = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("unsupported_level_card").assertIsDisplayed()
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
    fun `morphing floating nav navigates all tabs at phone dimensions and expands settings sections`() {
        composeRule.setContent {
            ArcCompassAppRoot()
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("morphing_floating_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_dial_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("compass_settings_button").assertDoesNotExist()
        composeRule.onNodeWithTag("info_panel_toggle").assertIsDisplayed()

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

        // Expand collapsible Instruments & Units section in redesigned Settings
        composeRule.onNodeWithTag("settings_section_instruments").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Imperial units").performScrollTo().assertIsDisplayed()

        // Open Sensor Diagnostics from first settings item and return cleanly
        composeRule.onNodeWithTag("settings_sensor_diagnostics_item").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostics_back_button").assertIsDisplayed()

        composeRule.onNodeWithTag("diagnostics_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    // Scenario 7 & 9: Poor GPS accuracy warning + ArcPark Find Mode with unreliable vs reliable orientation
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

        // Unreliable orientation: banner shown, arrow hidden, separate GPS travel direction shown, poor GPS warning shown
        composeRule.onNodeWithTag("arcpark_unreliable_orientation_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("arcpark_directional_arrow").assertDoesNotExist()
        composeRule.onNodeWithText("GPS travel direction: NE (42°)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("arcpark_find_accuracy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Location accuracy: ±48 m (Low precision — GPS only)").assertIsDisplayed()

        // Reliable orientation: phone-relative directional arrow appears
        reliableOrientation = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("arcpark_directional_arrow").assertIsDisplayed()
        composeRule.onNodeWithTag("arcpark_unreliable_orientation_banner").assertDoesNotExist()
    }

    // Scenario 10: Light/Dark/Night/HighContrast themes + Room persistence CRUD
    @Test
    fun `scenario 10 light dark night high contrast themes apply cleanly and Room parking CRUD persists`() = runBlocking {
        var themeMode by mutableStateOf(AppThemeMode.DARK)
        var nightFilter by mutableStateOf(false)
        var highContrast by mutableStateOf(false)
        var measuredBgLuminance = 0f
        var measuredPrimaryRed = 0f

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
                measuredBgLuminance = MaterialTheme.colorScheme.background.luminance()
                measuredPrimaryRed = MaterialTheme.colorScheme.primary.red
            }
        }

        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance < 0.1f)

        themeMode = AppThemeMode.LIGHT
        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance > 0.7f)

        highContrast = true
        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance > 0.95f)

        highContrast = false
        nightFilter = true
        composeRule.waitForIdle()
        assertTrue(measuredBgLuminance < 0.05f)
        assertTrue(measuredPrimaryRed > 0.8f)

        // Room local CRUD verification
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
}
