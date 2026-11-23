package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.TriggerEventListener
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ArcCompassViewModel
import com.example.compass.CompassScreen
import com.example.data.ArcCompassSettings
import com.example.data.CompassSensorMode
import com.example.data.SettingsRepository
import com.example.settings.SettingsScreen
import com.example.ui.theme.ArcCompassTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSensor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-port")
class SensorModePersistenceAndWarningsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            val repo = SettingsRepository(context)
            repo.setCompassSensorMode(CompassSensorMode.AUTO_DEFAULT)
            repo.setHasSeenCalibrationGuidance(false)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            val repo = SettingsRepository(context)
            repo.setCompassSensorMode(CompassSensorMode.AUTO_DEFAULT)
            repo.setHasSeenCalibrationGuidance(false)
        }
    }

    private class FakeSensorProvider(
        private val sensors: Map<Int, Sensor>
    ) : SensorManagerProvider {
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
            DeviceHardwareInfo("ArcTest", "Model", "Test", "arc", "15", 35, totalDetectedSensors)

        fun emit(type: Int, vararg values: Float) {
            val sensor = sensors[type] ?: return
            val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            val event = ctor.newInstance(values.size)
            val sensorField = SensorEvent::class.java.getField("sensor")
            sensorField.isAccessible = true
            sensorField.set(event, sensor)
            System.arraycopy(values, 0, event.values, 0, values.size)
            event.timestamp = System.nanoTime()

            activeRegistrations
                .filter { it.second == type }
                .map { it.first }
                .distinct()
                .forEach { it.onSensorChanged(event) }
        }
    }

    private fun fullSensors(): Map<Int, Sensor> = mapOf(
        Sensor.TYPE_ACCELEROMETER to ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER),
        Sensor.TYPE_MAGNETIC_FIELD to ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD),
        Sensor.TYPE_GYROSCOPE to ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE),
        Sensor.TYPE_GRAVITY to ShadowSensor.newInstance(Sensor.TYPE_GRAVITY),
        Sensor.TYPE_ROTATION_VECTOR to ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR),
        Sensor.TYPE_GAME_ROTATION_VECTOR to ShadowSensor.newInstance(Sensor.TYPE_GAME_ROTATION_VECTOR)
    )

    @Test
    fun `scenarios 2, 3, 4, 5, 12 fusion recovery persists across relaunch, clears frozen warning, auto-dismisses 5s banner, and avoids duplicate listeners`() = runBlocking {
        val provider = FakeSensorProvider(fullSensors())
        val settingsRepo1 = SettingsRepository(context)
        val repo1 = SensorRepository(context, provider = provider, settingsRepository = settingsRepo1)
        repo1.startObserving()
        ShadowLooper.idleMainLooper()

        // Scenario 1: Default Rotation Vector starts healthy with no spurious warnings
        provider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.1f, 0.99f)
        provider.emit(Sensor.TYPE_MAGNETIC_FIELD, 18f, -12f, 40f)
        assertEquals(CompassSource.ROTATION_VECTOR, repo1.headingState.value.source)
        assertNull(repo1.compassDiagnosticsState.value.suspectedAnomaly)
        assertFalse(repo1.compassDiagnosticsState.value.magneticInterferenceActive)
        assertFalse(repo1.compassDiagnosticsState.value.verticalOrientationWarningActive)
        assertEquals(provider.activeRegistrations.distinct().size, provider.activeRegistrations.size)

        // Scenario 2 & 4: Frozen RV -> interactive check switches to Accel+Mag Fusion, clears frozen warning
        repo1.requestCompassDiagnosticCheck()
        repo1.startCompassDiagnosticCheck()
        repeat(6) { idx ->
            ShadowLooper.idleMainLooper(750L, TimeUnit.MILLISECONDS)
            provider.emit(Sensor.TYPE_ROTATION_VECTOR, 0f, 0f, 0.1f, 0.99f)
            provider.emit(Sensor.TYPE_ACCELEROMETER, idx * 0.9f, -idx * 0.4f, 9.7f)
            provider.emit(Sensor.TYPE_MAGNETIC_FIELD, 18f + idx * 3f, -12f, 40f)
        }
        ShadowLooper.idleMainLooper(600L, TimeUnit.MILLISECONDS)

        assertEquals(InteractiveCheckPhase.COMPLETED, repo1.compassDiagnosticsState.value.checkPhase)
        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, repo1.headingState.value.source)
        assertNull(repo1.compassDiagnosticsState.value.suspectedAnomaly)
        assertEquals(CompassSensorMode.FUSION_ACCEL_MAG, repo1.compassDiagnosticsState.value.activeSensorMode)

        // Scenario 5: Info banner appears on recovery and auto-dismisses after 5 seconds
        assertNotNull(repo1.compassDiagnosticsState.value.infoBanner)
        ShadowLooper.idleMainLooper(5_200L, TimeUnit.MILLISECONDS)
        assertNull("Informational banner must auto-dismiss after 5 seconds", repo1.compassDiagnosticsState.value.infoBanner)

        // Fusion compass continues delivering live headings cleanly without frozen warnings
        provider.emit(Sensor.TYPE_ACCELEROMETER, 0f, 0f, 9.81f)
        provider.emit(Sensor.TYPE_MAGNETIC_FIELD, 20f, -10f, 41f)
        assertNotNull(repo1.headingState.value.magneticAzimuthDegrees)
        assertNull(repo1.compassDiagnosticsState.value.suspectedAnomaly)

        // Verify persisted mode in DataStore
        assertEquals(CompassSensorMode.FUSION_ACCEL_MAG, settingsRepo1.settingsFlow.first().compassSensorMode)

        // Stop session 1 -> clean listener unregistration
        repo1.stopObserving()
        assertTrue(provider.activeRegistrations.isEmpty())

        // Scenario 3: Force-close & reopen simulation (fresh repositories on same DataStore)
        val settingsRepo2 = SettingsRepository(context)
        val repo2 = SensorRepository(context, provider = provider, settingsRepository = settingsRepo2)
        repo2.startObserving()
        ShadowLooper.idleMainLooper(150L, TimeUnit.MILLISECONDS)

        assertEquals(CompassSource.ACCELEROMETER_AND_MAGNETOMETER, repo2.headingState.value.source)
        assertEquals(CompassSensorMode.FUSION_ACCEL_MAG, repo2.compassDiagnosticsState.value.activeSensorMode)
        assertEquals(InteractiveCheckPhase.IDLE, repo2.compassDiagnosticsState.value.checkPhase)
        assertNull("Restarting into saved mode must not show unsolicited info banner", repo2.compassDiagnosticsState.value.infoBanner)
        assertEquals(
            "No duplicate (listener, sensorType) registrations after relaunch",
            provider.activeRegistrations.distinct().size,
            provider.activeRegistrations.size
        )

        // Scenario 12: Manual mode switch back to Default -> no duplicate listeners -> clean stop
        repo2.selectCompassSensorMode(CompassSensorMode.ROTATION_VECTOR)
        ShadowLooper.idleMainLooper(100L, TimeUnit.MILLISECONDS)
        assertEquals(CompassSource.ROTATION_VECTOR, repo2.headingState.value.source)
        assertEquals(provider.activeRegistrations.distinct().size, provider.activeRegistrations.size)

        repo2.stopObserving()
        assertTrue(provider.activeRegistrations.isEmpty())
    }

    @Test
    fun `scenario 6 first-launch calibration guidance displays once and stays hidden after Got it dismissal`() = runBlocking {
        val settingsRepo = SettingsRepository(context)
        var settingsState by mutableStateOf(settingsRepo.settingsFlow.first())

        composeRule.setContent {
            ArcCompassTheme(settings = settingsState) {
                CompassScreen(
                    engineState = minimalEngineState(hasCompass = true, hasMagnetometer = true),
                    headingState = CompassHeadingState(
                        isSupported = true,
                        source = CompassSource.ROTATION_VECTOR,
                        status = DiagnosticStatus.AVAILABLE,
                        accuracy = SensorAccuracyLevel.HIGH,
                        magneticAzimuthDegrees = 180f
                    ),
                    settings = settingsState,
                    onToggleTrueNorth = {},
                    onToggleDialStyle = {},
                    onMarkGuidanceSeen = {
                        runBlocking {
                            settingsRepo.setHasSeenCalibrationGuidance(true)
                            settingsState = settingsRepo.settingsFlow.first()
                        }
                    },
                    onLocationPermissionResult = {}
                )
            }
        }

        // Shown on first launch once settings finish loading
        composeRule.onNodeWithTag("first_launch_calibration_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("first_launch_dismiss_button").performClick()
        composeRule.waitForIdle()

        // Persisted as seen and immediately hidden
        composeRule.onAllNodesWithTag("first_launch_calibration_banner").assertCountEquals(0)
        assertTrue(settingsRepo.settingsFlow.first().hasSeenCalibrationGuidance)
    }

    @Test
    fun `scenario 7 settings compass hosts sensor mode and unified sensor calibration dialog without main-screen duplicate button`() {
        val viewModel = ArcCompassViewModel(ApplicationProvider.getApplicationContext())
        var activeSettings by mutableStateOf(ArcCompassSettings(isLoaded = true, hasSeenCalibrationGuidance = true))

        composeRule.setContent {
            ArcCompassTheme(settings = activeSettings) {
                SettingsScreen(
                    settings = activeSettings,
                    engineState = minimalEngineState(hasCompass = true, hasMagnetometer = true),
                    headingState = CompassHeadingState(
                        isSupported = true,
                        source = CompassSource.ROTATION_VECTOR,
                        status = DiagnosticStatus.AVAILABLE,
                        accuracy = SensorAccuracyLevel.HIGH,
                        magneticAzimuthDegrees = 90f
                    ),
                    viewModel = viewModel,
                    onOpenSensorDiagnostics = {}
                )
            }
        }

        composeRule.onNodeWithTag("setting_compass_sensor_mode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setting_compass_sensor_calibration").performScrollTo().assertIsDisplayed()

        // No duplicate Calibration guide row in Section 5 Accessibility
        assertTrue(composeRule.onAllNodesWithText("Calibration guide").fetchSemanticsNodes().isEmpty())

        // Open unified Sensor Calibration dialog
        composeRule.onNodeWithTag("setting_compass_sensor_calibration").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calibration_guide_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("When calibration is recommended").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("calibration_run_check_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("calibration_done_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("calibration_guide_dialog").assertCountEquals(0)

        // Open Sensor mode picker
        composeRule.onNodeWithTag("setting_compass_sensor_mode").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sensor Fusion (Accel + Mag)").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `scenario 10 and 11 condition warnings prioritize without stacking and magnetometer-less device omits magnetic interference`() {
        var diagnosticsState by mutableStateOf(
            CompassDiagnosticsState(
                magneticInterferenceActive = true,
                magneticInterferenceMicroTesla = 112f,
                verticalOrientationWarningActive = true
            )
        )

        composeRule.setContent {
            ArcCompassTheme {
                CompassScreen(
                    engineState = minimalEngineState(hasCompass = true, hasMagnetometer = true),
                    headingState = CompassHeadingState(
                        isSupported = true,
                        source = CompassSource.ROTATION_VECTOR,
                        status = DiagnosticStatus.AVAILABLE,
                        accuracy = SensorAccuracyLevel.LOW,
                        magneticAzimuthDegrees = 45f
                    ),
                    settings = ArcCompassSettings(isLoaded = true, hasSeenCalibrationGuidance = true),
                    compassDiagnosticsState = diagnosticsState,
                    onToggleTrueNorth = {},
                    onToggleDialStyle = {},
                    onLocationPermissionResult = {}
                )
            }
        }

        // Scenario 10: Highest-priority warning only (Magnetic interference supersedes vertical orientation; never stacked)
        composeRule.onNodeWithTag("magnetic_interference_banner").assertIsDisplayed()
        composeRule.onAllNodesWithTag("vertical_orientation_banner").assertCountEquals(0)

        // When magnetic interference clears, vertical warning surfaces cleanly if still active
        diagnosticsState = diagnosticsState.copy(magneticInterferenceActive = false)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("magnetic_interference_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("vertical_orientation_banner").assertIsDisplayed()

        // When orientation normalizes, warnings clear completely
        diagnosticsState = diagnosticsState.copy(verticalOrientationWarningActive = false)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("vertical_orientation_banner").assertCountEquals(0)

        // Scenario 11: Device without magnetometer must never generate magnetic interference or fabricated heading
        val noMagSensors = mapOf(
            Sensor.TYPE_ACCELEROMETER to ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER),
            Sensor.TYPE_GRAVITY to ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)
        )
        val noMagProvider = FakeSensorProvider(noMagSensors)
        val noMagRepo = SensorRepository(context, provider = noMagProvider)
        noMagRepo.startObserving()
        repeat(6) {
            ShadowLooper.idleMainLooper(500L, TimeUnit.MILLISECONDS)
            noMagProvider.emit(Sensor.TYPE_ACCELEROMETER, 0.2f, 9.6f, 0.3f)
        }
        assertFalse(noMagRepo.headingState.value.isSupported)
        assertNull(noMagRepo.headingState.value.magneticAzimuthDegrees)
        assertTrue(noMagRepo.levelState.value.isSupported)
        assertFalse(noMagRepo.compassDiagnosticsState.value.magneticInterferenceActive)
        assertNull(noMagRepo.compassDiagnosticsState.value.magneticInterferenceMicroTesla)
        noMagRepo.stopObserving()
    }

    private fun minimalEngineState(
        hasCompass: Boolean,
        hasMagnetometer: Boolean
    ): SensorEngineState = SensorEngineState(
        deviceInfo = DeviceHardwareInfo("ArcTest", "Baseline", "Test", "arc", "15", 35, 6),
        sensorSpecs = listOf(
            SensorSpec(
                sensorType = RequiredSensorType.ACCELEROMETER,
                isAvailable = true,
                name = "Accel",
                vendor = "Test",
                version = 1,
                powerMa = 0.2f,
                resolution = 0.01f,
                maxRange = 78f,
                minDelayUs = 5000,
                reportingMode = SensorReportingMode.CONTINUOUS,
                isWakeUpSensor = false,
                accuracy = SensorAccuracyLevel.HIGH,
                status = DiagnosticStatus.AVAILABLE
            ),
            SensorSpec(
                sensorType = RequiredSensorType.MAGNETIC_FIELD,
                isAvailable = hasMagnetometer,
                name = "Mag",
                vendor = "Test",
                version = 1,
                powerMa = 1.0f,
                resolution = 0.1f,
                maxRange = 4000f,
                minDelayUs = 10000,
                reportingMode = SensorReportingMode.CONTINUOUS,
                isWakeUpSensor = false,
                accuracy = if (hasMagnetometer) SensorAccuracyLevel.HIGH else SensorAccuracyLevel.NOT_APPLICABLE,
                status = if (hasMagnetometer) DiagnosticStatus.AVAILABLE else DiagnosticStatus.UNAVAILABLE
            )
        ),
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
                status = DiagnosticStatus.UNAVAILABLE,
                source = false,
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
            magneticFieldMicroTesla = if (hasMagnetometer) 46.5f else null
        ),
        locationState = LocationDiagnosticSnapshot(
            permissionState = LocationPermissionState.GRANTED_FINE,
            isLocationServiceEnabled = true,
            availableProviders = listOf("gps"),
            latitude = 37.7749,
            longitude = -122.4194,
            status = DiagnosticStatus.AVAILABLE,
            statusMessage = "Ready"
        ),
        isObserving = true,
        lastScanMillis = 1L
    )
}
