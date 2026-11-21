package com.example.arcpark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ArcCompassApplication
import com.example.data.ParkingSpotEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArcParkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ArcCompassApplication).parkingRepository

    val savedSpots: StateFlow<List<ParkingSpotEntity>> = repository.allSpots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    fun saveNewSpot(
        name: String,
        floorOrLevel: String,
        notes: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?
    ) {
        viewModelScope.launch {
            repository.saveSpot(
                ParkingSpotEntity(
                    name = name.trim().ifBlank { "Parked Vehicle" },
                    floorOrLevel = floorOrLevel.trim(),
                    notes = notes.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracyMeters,
                    savedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateSpot(spot: ParkingSpotEntity) {
        viewModelScope.launch {
            repository.updateSpot(spot)
        }
    }

    fun deleteSpot(id: Long) {
        viewModelScope.launch {
            repository.deleteSpot(id)
        }
    }
}
