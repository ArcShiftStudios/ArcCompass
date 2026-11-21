package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.TriggerEventListener
import android.location.LocationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ArcCompassDatabase
import com.example.data.SavedPlaceEntity
import com.example.data.SavedPlacesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LevelAndSavedPlacesTest {

    private class LevelTestProvider(
        private val context: Context,
        private val gravitySensor: Sensor
    ) : SensorManagerProvider {
        override fun getDefaultSensor(type: Int): Sensor? =
            if (type == Sensor.TYPE_GRAVITY) gravitySensor else null

        override fun getAllSensors(): List<Sensor> = listOf(gravitySensor)

        override fun registerListener(
            listener: SensorEventListener,
            sensor: Sensor,
            samplingPeriodUs: Int
        ): Boolean = true

        override fun unregisterListener(listener: SensorEventListener) = Unit
        override fun requestTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean = false
        override fun cancelTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean = false
        override fun getLocationManager(): LocationManager? =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        override fun isLocationEnabled(): Boolean = true
        override fun getDeviceInfo(totalDetectedSensors: Int): DeviceHardwareInfo =
            DeviceHardwareInfo("Test", "LevelDevice", "Test", "td", "14", 34, 1)
    }

    private fun createSensorEvent(sensor: Sensor, x: Float, y: Float, z: Float): SensorEvent {
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        ctor.isAccessible = true
        val event = ctor.newInstance(3)
        event.sensor = sensor
        event.values[0] = x
        event.values[1] = y
        event.values[2] = z
        return event
    }

    @Test
    fun `level controller captures custom reference angle and resets cleanly to true zero`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gravitySensor = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)
        val provider = LevelTestProvider(context, gravitySensor)
        val accuracyMonitor = SensorAccuracyMonitor()
        val controller = LevelSensorController(provider, accuracyMonitor) { _, _ -> }

        controller.configureAndStart(mapOf(RequiredSensorType.GRAVITY to gravitySensor))
        // Tilted device (~14 deg roll, ~14 deg pitch)
        controller.onSensorChanged(createSensorEvent(gravitySensor, -2.5f, 2.5f, 9.15f))

        val tilted = controller.levelState.value
        assertTrue(tilted.isSupported)
        assertNotNull(tilted.rollDegrees)
        assertTrue(abs(tilted.rollDegrees ?: 0f) > 5f)
        assertFalse(tilted.hasCustomReference)
        assertFalse(tilted.isFlatLevel)

        // Capture current tilted orientation as 0° custom reference
        controller.captureReferenceOffset()
        val zeroed = controller.levelState.value
        assertTrue(zeroed.hasCustomReference)
        assertEquals(0f, zeroed.rollDegrees ?: 99f, 0.01f)
        assertEquals(0f, zeroed.pitchDegrees ?: 99f, 0.01f)
        assertTrue(zeroed.isFlatLevel)

        // Reset back to true gravity horizontal
        controller.clearReferenceOffset()
        val restored = controller.levelState.value
        assertFalse(restored.hasCustomReference)
        assertTrue(abs(restored.rollDegrees ?: 0f) > 5f)
        assertFalse(restored.isFlatLevel)
    }

    @Test
    fun `saved places repository persists and deletes offline waypoints`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ArcCompassDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = SavedPlacesRepository(db.savedPlaceDao())
            val id = repo.savePlace(
                SavedPlaceEntity(
                    name = "Summit Ridge",
                    category = "Trailhead",
                    latitude = 37.8651,
                    longitude = -119.5383,
                    altitudeMeters = 2480.0
                )
            )
            var places = repo.allPlaces.first()
            assertEquals(1, places.size)
            assertEquals("Summit Ridge", places.first().name)
            assertEquals("Trailhead", places.first().category)

            repo.deletePlace(id)
            places = repo.allPlaces.first()
            assertTrue(places.isEmpty())
        } finally {
            db.close()
        }
    }
}
