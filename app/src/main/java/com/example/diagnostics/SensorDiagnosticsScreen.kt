package com.example.diagnostics

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.sensor.AltitudeSource
import com.example.ui.theme.FrostedAmbientBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDiagnosticsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    manageLifecycle: Boolean = false,
    viewModel: SensorDiagnosticsViewModel = viewModel()
) {
    val state by viewModel.engineState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    if (onNavigateBack != null) {
        BackHandler(onBack = onNavigateBack)
    }

    if (manageLifecycle) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onStartObserving()
                    Lifecycle.Event.ON_STOP -> viewModel.onStopObserving()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.onStopObserving()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onLocationPermissionUpdated()
    }

    val filteredSensors = remember(state.sensorSpecs, selectedFilter) {
        when (selectedFilter) {
            SensorListFilter.ALL -> state.sensorSpecs
            SensorListFilter.AVAILABLE -> state.sensorSpecs.filter { it.isAvailable }
            SensorListFilter.UNAVAILABLE -> state.sensorSpecs.filter { !it.isAvailable }
        }
    }

    val availableCoreCount = remember(state.sensorSpecs) {
        state.sensorSpecs.count { it.isAvailable }
    }

    Box(modifier = modifier.fillMaxSize()) {
        FrostedAmbientBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("diagnostics_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.diagnostics_title),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.diagnostics_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        ObservationPill(isObserving = state.isObserving)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { viewModel.rescanHardware() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("rescan_sensors_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.action_rescan)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("diagnostics_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero_device_summary") {
                    DeviceSummaryCard(
                        state = state,
                        availableCoreCount = availableCoreCount
                    )
                }

                item(key = "live_telemetry_strip") {
                    LiveTelemetrySection(
                        telemetry = state.telemetry,
                        locationState = state.locationState,
                        hasMagnetometer = state.capabilities.magneticFieldMonitor.source,
                        hasBarometer = state.capabilities.altitude.source == AltitudeSource.BAROMETRIC_ESTIMATE,
                        onRequestLocationPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        },
                        onOpenLocationSettings = {
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                item(key = "capabilities_header") {
                    SectionHeader(
                        title = stringResource(R.string.section_capabilities),
                        subtitle = "Hardware-verified feature readiness"
                    )
                }

                item(key = "capabilities_matrix") {
                    CapabilityMatrixSection(
                        capabilities = state.capabilities,
                        locationState = state.locationState
                    )
                }

                item(key = "sensors_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.section_core_sensors),
                            subtitle = "$availableCoreCount available • ${state.sensorSpecs.size - availableCoreCount} unavailable"
                        )
                        SensorFilterRow(
                            selectedFilter = selectedFilter,
                            allCount = state.sensorSpecs.size,
                            availableCount = availableCoreCount,
                            unavailableCount = state.sensorSpecs.size - availableCoreCount,
                            onSelectFilter = viewModel::setFilter
                        )
                    }
                }

                if (filteredSensors.isEmpty()) {
                    item(key = "empty_sensor_filter") {
                        EmptySensorFilterCard(filter = selectedFilter)
                    }
                } else {
                    items(
                        items = filteredSensors,
                        key = { it.sensorType.name }
                    ) { spec ->
                        SensorSpecificationCard(
                            spec = spec,
                            telemetry = state.telemetry
                        )
                    }
                }

                item(key = "permissions_lifecycle_footer") {
                    PermissionsAndLifecycleCard(
                        locationState = state.locationState,
                        isObserving = state.isObserving,
                        onRequestLocationPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                    )
                }
            }
        }
    }
    }
}
