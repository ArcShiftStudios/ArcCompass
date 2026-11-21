package com.example.arcpark

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ArcCompassSettings
import com.example.data.GeoNavigationMath
import com.example.data.ParkingSpotEntity
import com.example.sensor.CompassHeadingState
import com.example.sensor.LocationDiagnosticSnapshot
import com.example.sensor.LocationPermissionState
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassDialog
import com.example.ui.theme.GlassPill
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassTier
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusAvailableGreen
import com.example.ui.theme.StatusWarningAmber
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArcParkScreen(
    locationState: LocationDiagnosticSnapshot,
    headingState: CompassHeadingState,
    settings: ArcCompassSettings,
    onLocationPermissionResult: () -> Unit,
    onFindModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bottomNavClearance: Dp = 16.dp,
    parkViewModel: ArcParkViewModel = viewModel()
) {
    val spots by parkViewModel.savedSpots.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var editingSpot by remember { mutableStateOf<ParkingSpotEntity?>(null) }
    var activeFindSpotId by remember { mutableStateOf<Long?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onLocationPermissionResult()
    }

    val activeFindSpot = remember(spots, activeFindSpotId) {
        spots.firstOrNull { it.id == activeFindSpotId }
    }

    if (activeFindSpot != null) {
        ArcParkFindModeScreen(
            spot = activeFindSpot,
            locationState = locationState,
            headingState = headingState,
            settings = settings,
            onClose = {
                activeFindSpotId = null
                onFindModeChanged(false)
            }
        )
        return
    }

    val hasValidLocation = locationState.latitude != null && locationState.longitude != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("arcpark_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 96.dp + bottomNavClearance
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "arcpark_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ArcPark",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Offline parking saver & retrieval",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassPill(tintColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "${spots.size} saved",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            item(key = "arcpark_gps_status") {
                LocationReadinessBanner(
                    locationState = locationState,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            if (spots.isEmpty()) {
                item(key = "arcpark_empty") {
                    EmptyParkingCard(
                        canSave = hasValidLocation,
                        onParkHere = { showSaveDialog = true }
                    )
                }
            } else {
                items(items = spots, key = { it.id }) { spot ->
                    SavedParkingSpotCard(
                        spot = spot,
                        locationState = locationState,
                        useImperial = settings.useImperialUnits,
                        onFind = {
                            activeFindSpotId = spot.id
                            onFindModeChanged(true)
                        },
                        onEdit = { editingSpot = spot },
                        onDelete = { parkViewModel.deleteSpot(spot.id) }
                    )
                }
            }
        }

        // Frosted floating Save FAB positioned cleanly above morphing navigation bar
        GlassSurface(
            tier = GlassTier.FLOATING_CHROME,
            shape = CircleShape,
            tintColor = MaterialTheme.colorScheme.primaryContainer,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            elevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomNavClearance + 14.dp)
                .size(58.dp)
                .clip(CircleShape)
                .clickable { showSaveDialog = true }
                .testTag("arcpark_save_fab")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Save parking location",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    if (showSaveDialog) {
        SaveOrEditParkingDialog(
            existingSpot = null,
            locationState = locationState,
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            },
            onSave = { name, floor, notes ->
                val lat = locationState.latitude
                val lon = locationState.longitude
                if (lat != null && lon != null) {
                    parkViewModel.saveNewSpot(
                        name = name,
                        floorOrLevel = floor,
                        notes = notes,
                        latitude = lat,
                        longitude = lon,
                        accuracyMeters = locationState.horizontalAccuracyMeters
                    )
                    showSaveDialog = false
                }
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    editingSpot?.let { spot ->
        SaveOrEditParkingDialog(
            existingSpot = spot,
            locationState = locationState,
            onRequestPermission = {},
            onSave = { name, floor, notes ->
                parkViewModel.updateSpot(
                    spot.copy(
                        name = name.trim().ifBlank { "Parked Vehicle" },
                        floorOrLevel = floor.trim(),
                        notes = notes.trim()
                    )
                )
                editingSpot = null
            },
            onDismiss = { editingSpot = null }
        )
    }
}

@Composable
private fun LocationReadinessBanner(
    locationState: LocationDiagnosticSnapshot,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val acc = locationState.horizontalAccuracyMeters
    val isPoorAccuracy = acc != null && acc > 25f

    GlassCard(
        tier = GlassTier.CARD,
        borderColor = if (isPoorAccuracy) StatusWarningAmber.copy(alpha = 0.5f) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = if (locationState.latitude != null) MaterialTheme.colorScheme.primary else StatusWarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = when {
                        locationState.permissionState == LocationPermissionState.DENIED ->
                            "Location permission required for ArcPark"
                        !locationState.isLocationServiceEnabled ->
                            "Location services are disabled"
                        locationState.latitude != null ->
                            String.format(
                                Locale.US,
                                "GPS Ready • Accuracy %s",
                                acc?.let { "±%.0f m".format(it) } ?: "unknown"
                            )
                        else -> "Acquiring GPS location fix…"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (isPoorAccuracy) {
                Text(
                    text = "Poor GPS precision (±${acc!!.toInt()} m). Move outdoors or add floor/notes before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarningAmber
                )
            }

            if (locationState.permissionState == LocationPermissionState.DENIED) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("arcpark_grant_location_button")
                ) {
                    Text("Grant Location")
                }
            } else if (!locationState.isLocationServiceEnabled) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Open Location Settings")
                }
            }
        }
    }
}

@Composable
private fun EmptyParkingCard(
    canSave: Boolean,
    onParkHere: () -> Unit
) {
    GlassCard(
        tier = GlassTier.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("arcpark_empty_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No Saved Parking Spots",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Save your vehicle's GPS coordinates, floor level, and notes locally. Retrieve it anytime offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onParkHere,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("arcpark_empty_save_button")
            ) {
                Text(if (canSave) "Park Here" else "Save Parking Spot")
            }
        }
    }
}

@Composable
private fun SavedParkingSpotCard(
    spot: ParkingSpotEntity,
    locationState: LocationDiagnosticSnapshot,
    useImperial: Boolean,
    onFind: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val distanceMeters = if (locationState.latitude != null && locationState.longitude != null) {
        GeoNavigationMath.haversineDistanceMeters(
            locationState.latitude,
            locationState.longitude,
            spot.latitude,
            spot.longitude
        )
    } else null

    GlassCard(
        tier = GlassTier.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("arcpark_spot_card_${spot.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spot.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateFormatter.format(Date(spot.savedAtMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (spot.floorOrLevel.isNotBlank()) {
                    GlassPill(tintColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = spot.floorOrLevel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (spot.notes.isNotBlank()) {
                Text(
                    text = spot.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = GeoNavigationMath.formatDmsCoordinates(spot.latitude, spot.longitude),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = distanceMeters?.let {
                        "Distance: ${GeoNavigationMath.formatDistance(it, useImperial)}"
                    } ?: "Distance: awaiting GPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = spot.accuracyMeters?.let { "Saved: ±%.0f m".format(it) } ?: "Saved: ±? m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onFind,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("arcpark_find_button_${spot.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Find")
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("arcpark_edit_button_${spot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit parking spot"
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("arcpark_delete_button_${spot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete parking spot"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveOrEditParkingDialog(
    existingSpot: ParkingSpotEntity?,
    locationState: LocationDiagnosticSnapshot,
    onRequestPermission: () -> Unit,
    onSave: (name: String, floor: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(existingSpot) { mutableStateOf(existingSpot?.name ?: "My Vehicle") }
    var floor by remember(existingSpot) { mutableStateOf(existingSpot?.floorOrLevel ?: "") }
    var notes by remember(existingSpot) { mutableStateOf(existingSpot?.notes ?: "") }

    val isEditing = existingSpot != null
    val hasGpsFix = locationState.latitude != null && locationState.longitude != null
    val accuracy = locationState.horizontalAccuracyMeters
    val isPoorAccuracy = accuracy == null || accuracy > 25f

    GlassDialog(onDismissRequest = onDismiss) {
        Text(
            text = if (isEditing) "Edit Parking Spot" else "Save Parking Spot",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isEditing) {
                GlassCard(
                    tier = GlassTier.CARD,
                    tintColor = if (!hasGpsFix || isPoorAccuracy) {
                        StatusWarningAmber.copy(alpha = 0.14f)
                    } else {
                        StatusAvailableGreen.copy(alpha = 0.14f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (!hasGpsFix || isPoorAccuracy) {
                                Icons.Outlined.WarningAmber
                            } else {
                                Icons.Outlined.LocationOn
                            },
                            contentDescription = null,
                            tint = if (!hasGpsFix || isPoorAccuracy) StatusWarningAmber else StatusAvailableGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                locationState.permissionState == LocationPermissionState.DENIED ->
                                    "Location permission required before saving."
                                !hasGpsFix ->
                                    "Waiting for a valid GPS location fix…"
                                isPoorAccuracy ->
                                    "Low accuracy (±${accuracy?.toInt() ?: "?"} m). Consider waiting or adding floor notes."
                                else ->
                                    String.format(Locale.US, "GPS accuracy: ±%.0f m", accuracy!!)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (locationState.permissionState == LocationPermissionState.DENIED) {
                    OutlinedButton(onClick = onRequestPermission) {
                        Text("Grant Location")
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Parking name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arcpark_name_input")
            )

            OutlinedTextField(
                value = floor,
                onValueChange = { floor = it },
                label = { Text("Floor / Level (optional)") },
                placeholder = { Text("e.g., B2, Level 4") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arcpark_floor_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("e.g., Near north elevator") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arcpark_notes_input")
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSave(name, floor, notes) },
                enabled = isEditing || hasGpsFix,
                modifier = Modifier.testTag("arcpark_confirm_save_button")
            ) {
                Text(if (isEditing) "Update" else "Save")
            }
        }
    }
}
