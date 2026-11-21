package com.example.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorDiagnosticsRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app launches on compass and navigates through settings to full sensor diagnostics`() {
        composeRule.waitForIdle()

        // Compass must be the launch destination
        composeRule.onNodeWithTag("compass_screen").assertIsDisplayed()

        // Navigate to Settings tab
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // First item under Hardware & Advanced opens Sensor Diagnostics
        composeRule.onNodeWithTag("settings_sensor_diagnostics_item").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_sensor_diagnostics_item").performClick()
        composeRule.waitForIdle()

        // Verify preserved Milestone 1 Sensor Diagnostics screen
        composeRule.onNodeWithTag("device_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("rescan_sensors_button").assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_magnetic_tile").assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_barometer_tile").assertIsDisplayed()

        // Scroll to the sensors_header item inside LazyColumn
        composeRule.onNodeWithTag("diagnostics_list")
            .performScrollToKey("sensors_header")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("filter_chip_all").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_chip_available").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_chip_unavailable").assertIsDisplayed()

        composeRule.onNodeWithTag("filter_chip_unavailable").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("filter_chip_all").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rescan_sensors_button").performClick()
        composeRule.waitForIdle()

        // Navigate back to Settings
        composeRule.onNodeWithTag("diagnostics_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
}
