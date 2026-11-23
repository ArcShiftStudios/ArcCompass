package com.example

import android.app.Application
import com.example.data.ArcCompassDatabase
import com.example.data.ParkingRepository
import com.example.data.SavedPlacesRepository
import com.example.data.SettingsRepository
import com.example.sensor.SensorRepository

/**
 * Application container providing single shared instances of the Sensor Detection Engine,
 * DataStore SettingsRepository, and Room Parking & SavedPlaces repositories across all UI destinations.
 */
class ArcCompassApplication : Application() {

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    val sensorRepository: SensorRepository by lazy {
        SensorRepository(
            context = applicationContext,
            settingsRepository = settingsRepository
        )
    }

    private val database: ArcCompassDatabase by lazy {
        ArcCompassDatabase.getInstance(applicationContext)
    }

    val parkingRepository: ParkingRepository by lazy {
        ParkingRepository(database.parkingSpotDao())
    }

    val savedPlacesRepository: SavedPlacesRepository by lazy {
        SavedPlacesRepository(database.savedPlaceDao())
    }
}
