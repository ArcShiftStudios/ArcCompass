package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEventListener
import android.location.LocationManager
import android.os.Build

/**
 * Abstraction over Android [SensorManager] and [LocationManager] to isolate system services
 * and enable deterministic testing across varied hardware profiles.
 */
interface SensorManagerProvider {
    fun getDefaultSensor(type: Int): Sensor?
    fun getAllSensors(): List<Sensor>
    fun registerListener(
        listener: SensorEventListener,
        sensor: Sensor,
        samplingPeriodUs: Int
    ): Boolean

    fun unregisterListener(listener: SensorEventListener)
    fun requestTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean
    fun cancelTriggerSensor(listener: TriggerEventListener, sensor: Sensor): Boolean

    fun getLocationManager(): LocationManager?
    fun isLocationEnabled(): Boolean
    fun getDeviceInfo(totalDetectedSensors: Int): DeviceHardwareInfo
}

class AndroidSensorManagerProvider(
    context: Context
) : SensorManagerProvider {

    private val appContext = context.applicationContext
    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun getDefaultSensor(type: Int): Sensor? =
        sensorManager?.getDefaultSensor(type)

    override fun getAllSensors(): List<Sensor> =
        sensorManager?.getSensorList(Sensor.TYPE_ALL).orEmpty()

    override fun registerListener(
        listener: SensorEventListener,
        sensor: Sensor,
        samplingPeriodUs: Int
    ): Boolean {
        val manager = sensorManager ?: return false
        return manager.registerListener(listener, sensor, samplingPeriodUs)
    }

    override fun unregisterListener(listener: SensorEventListener) {
        sensorManager?.unregisterListener(listener)
    }

    override fun requestTriggerSensor(
        listener: TriggerEventListener,
        sensor: Sensor
    ): Boolean {
        val manager = sensorManager ?: return false
        return manager.requestTriggerSensor(listener, sensor)
    }

    override fun cancelTriggerSensor(
        listener: TriggerEventListener,
        sensor: Sensor
    ): Boolean {
        val manager = sensorManager ?: return false
        return manager.cancelTriggerSensor(listener, sensor)
    }

    override fun getLocationManager(): LocationManager? = locationManager

    override fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    override fun getDeviceInfo(totalDetectedSensors: Int): DeviceHardwareInfo {
        return DeviceHardwareInfo(
            manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Unknown" },
            model = Build.MODEL.orEmpty().ifBlank { "Unknown" },
            brand = Build.BRAND.orEmpty().ifBlank { "Unknown" },
            device = Build.DEVICE.orEmpty().ifBlank { "Unknown" },
            androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" },
            sdkInt = Build.VERSION.SDK_INT,
            totalDetectedSensors = totalDetectedSensors
        )
    }
}
