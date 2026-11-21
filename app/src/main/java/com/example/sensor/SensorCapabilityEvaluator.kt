package com.example.sensor

/**
 * Pure, deterministic evaluator that derives feature capabilities from detected hardware sensors
 * and location permission/provider states without ever fabricating unsupported capabilities.
 */
class SensorCapabilityEvaluator {

    fun evaluate(
        specs: List<SensorSpec>,
        locationState: LocationDiagnosticSnapshot,
        isObserving: Boolean = false
    ): CapabilityReport {
        val byType = specs.associateBy { it.sensorType }

        return CapabilityReport(
            compass = evaluateCompass(byType, isObserving),
            level = evaluateLevel(byType, isObserving),
            magneticFieldMonitor = evaluateMagneticMonitor(byType, isObserving),
            gyroscopeDiagnostics = evaluateGyroscopeDiagnostics(byType, isObserving),
            location = evaluateLocation(locationState),
            altitude = evaluateAltitude(byType, locationState, isObserving)
        )
    }

    private fun evaluateCompass(
        byType: Map<RequiredSensorType, SensorSpec>,
        isObserving: Boolean
    ): FeatureCapability<CompassSource> {
        val rotationVector = byType[RequiredSensorType.ROTATION_VECTOR]
        val accelerometer = byType[RequiredSensorType.ACCELEROMETER]
        val magnetometer = byType[RequiredSensorType.MAGNETIC_FIELD]

        if (rotationVector?.isAvailable == true) {
            val status = combineSensorStatus(listOf(rotationVector), isObserving)
            return FeatureCapability(
                status = status,
                source = CompassSource.ROTATION_VECTOR,
                summary = "Rotation Vector active",
                detail = when (status) {
                    DiagnosticStatus.ACCURACY_UNRELIABLE ->
                        "Rotation Vector available, but accuracy is unreliable. Figure-8 calibration recommended."
                    DiagnosticStatus.INITIALIZING ->
                        "Rotation Vector detected; awaiting initial fused orientation sample."
                    else ->
                        "Using hardware/fused Rotation Vector sensor. Tilt-compensated heading ready for Milestone 3."
                }
            )
        }

        if (accelerometer?.isAvailable == true && magnetometer?.isAvailable == true) {
            val status = combineSensorStatus(listOf(accelerometer, magnetometer), isObserving)
            return FeatureCapability(
                status = status,
                source = CompassSource.ACCELEROMETER_AND_MAGNETOMETER,
                summary = "Accel + Magnetometer fallback",
                detail = when (status) {
                    DiagnosticStatus.ACCURACY_UNRELIABLE ->
                        "Accelerometer + Magnetometer available, but magnetometer accuracy is unreliable."
                    DiagnosticStatus.INITIALIZING ->
                        "Using Accelerometer + Magnetometer fallback; awaiting initial sensor readings."
                    else ->
                        "Rotation Vector absent; falling back to Accelerometer + Magnetometer matrix."
                }
            )
        }

        val missingReason = buildString {
            append("Compass heading unsupported: ")
            val missing = mutableListOf<String>()
            if (rotationVector?.isAvailable != true) missing += "Rotation Vector"
            if (magnetometer?.isAvailable != true) missing += "Magnetometer"
            if (accelerometer?.isAvailable != true) missing += "Accelerometer"
            append("missing ${missing.joinToString(", ")}. ")
            append("GPS bearing and gyroscope alone cannot determine absolute magnetic north.")
        }

        return FeatureCapability(
            status = DiagnosticStatus.UNAVAILABLE,
            source = CompassSource.UNSUPPORTED,
            summary = "Unsupported on this hardware",
            detail = missingReason
        )
    }

    private fun evaluateLevel(
        byType: Map<RequiredSensorType, SensorSpec>,
        isObserving: Boolean
    ): FeatureCapability<LevelSource> {
        val gravity = byType[RequiredSensorType.GRAVITY]
        val accelerometer = byType[RequiredSensorType.ACCELEROMETER]
        val rotationVector = byType[RequiredSensorType.ROTATION_VECTOR]

        return when {
            gravity?.isAvailable == true -> {
                val status = combineSensorStatus(listOf(gravity), isObserving)
                FeatureCapability(
                    status = status,
                    source = LevelSource.GRAVITY,
                    summary = "Gravity sensor",
                    detail = "Independent of magnetometer; suitable for horizontal and vertical surface leveling."
                )
            }
            accelerometer?.isAvailable == true -> {
                val status = combineSensorStatus(listOf(accelerometer), isObserving)
                FeatureCapability(
                    status = status,
                    source = LevelSource.ACCELEROMETER,
                    summary = "Accelerometer fallback",
                    detail = "Independent of magnetometer; using raw acceleration vector for surface tilt."
                )
            }
            rotationVector?.isAvailable == true -> {
                val status = combineSensorStatus(listOf(rotationVector), isObserving)
                FeatureCapability(
                    status = status,
                    source = LevelSource.ROTATION_VECTOR,
                    summary = "Rotation Vector fallback",
                    detail = "Using Rotation Vector pitch/roll components for surface level."
                )
            }
            else -> {
                FeatureCapability(
                    status = DiagnosticStatus.UNAVAILABLE,
                    source = LevelSource.UNSUPPORTED,
                    summary = "Unsupported",
                    detail = "No Gravity, Accelerometer, or Rotation Vector sensor detected."
                )
            }
        }
    }

    private fun evaluateMagneticMonitor(
        byType: Map<RequiredSensorType, SensorSpec>,
        isObserving: Boolean
    ): FeatureCapability<Boolean> {
        val magnetometer = byType[RequiredSensorType.MAGNETIC_FIELD]
        return if (magnetometer?.isAvailable == true) {
            val status = combineSensorStatus(listOf(magnetometer), isObserving)
            FeatureCapability(
                status = status,
                source = true,
                summary = "Available (μT)",
                detail = if (status == DiagnosticStatus.ACCURACY_UNRELIABLE) {
                    "Magnetometer detected, but interference or uncalibrated state reported."
                } else {
                    "Measures ambient 3-axis geomagnetic flux density in microteslas (μT)."
                }
            )
        } else {
            FeatureCapability(
                status = DiagnosticStatus.UNAVAILABLE,
                source = false,
                summary = "Unavailable",
                detail = "No magnetometer present; magnetic field monitor and μT telemetry disabled."
            )
        }
    }

    private fun evaluateGyroscopeDiagnostics(
        byType: Map<RequiredSensorType, SensorSpec>,
        isObserving: Boolean
    ): FeatureCapability<Boolean> {
        val gyro = byType[RequiredSensorType.GYROSCOPE]
        return if (gyro?.isAvailable == true) {
            val status = combineSensorStatus(listOf(gyro), isObserving)
            FeatureCapability(
                status = status,
                source = true,
                summary = "Available (rad/s)",
                detail = "Operates independently from compass; never required for basic compass heading."
            )
        } else {
            FeatureCapability(
                status = DiagnosticStatus.UNAVAILABLE,
                source = false,
                summary = "Unavailable",
                detail = "No hardware gyroscope detected. Basic compass and level remain unaffected."
            )
        }
    }

    private fun evaluateLocation(
        locationState: LocationDiagnosticSnapshot
    ): FeatureCapability<LocationPermissionState> {
        val detail = when (locationState.status) {
            DiagnosticStatus.PERMISSION_REQUIRED ->
                "Location permission not granted. Compass and Level continue offline without location."
            DiagnosticStatus.UNAVAILABLE ->
                locationState.statusMessage
            DiagnosticStatus.INITIALIZING ->
                "Permission granted (${locationState.permissionState.label}); waiting for provider fix."
            DiagnosticStatus.AVAILABLE ->
                "Active via ${locationState.providerName ?: "Android Location"} (${locationState.permissionState.label})."
            DiagnosticStatus.ACCURACY_UNRELIABLE ->
                locationState.statusMessage
        }

        return FeatureCapability(
            status = locationState.status,
            source = locationState.permissionState,
            summary = locationState.status.label,
            detail = detail
        )
    }

    private fun evaluateAltitude(
        byType: Map<RequiredSensorType, SensorSpec>,
        locationState: LocationDiagnosticSnapshot,
        isObserving: Boolean
    ): FeatureCapability<AltitudeSource> {
        val pressure = byType[RequiredSensorType.PRESSURE]
        if (pressure?.isAvailable == true) {
            val status = combineSensorStatus(listOf(pressure), isObserving)
            return FeatureCapability(
                status = status,
                source = AltitudeSource.BAROMETRIC_ESTIMATE,
                summary = "Barometric estimate",
                detail = "Barometer detected. Altitude is an estimate requiring a local sea-level reference pressure (default 1013.25 hPa)."
            )
        }

        return when {
            locationState.altitudeMeters != null -> {
                val accuracyNote = locationState.verticalAccuracyMeters?.let {
                    " (±%.1f m vertical accuracy)".format(it)
                } ?: " (vertical accuracy unreported on this API/provider)"
                FeatureCapability(
                    status = DiagnosticStatus.AVAILABLE,
                    source = AltitudeSource.LOCATION_GNSS,
                    summary = "GNSS altitude",
                    detail = "No barometer present; using Android Location altitude$accuracyNote."
                )
            }
            locationState.permissionState == LocationPermissionState.DENIED -> {
                FeatureCapability(
                    status = DiagnosticStatus.PERMISSION_REQUIRED,
                    source = AltitudeSource.UNAVAILABLE,
                    summary = "Permission required",
                    detail = "No barometer detected. Grant location permission to read GNSS altitude."
                )
            }
            !locationState.isLocationServiceEnabled -> {
                FeatureCapability(
                    status = DiagnosticStatus.UNAVAILABLE,
                    source = AltitudeSource.UNAVAILABLE,
                    summary = "Unavailable",
                    detail = "No barometer detected and system Location Services are disabled."
                )
            }
            else -> {
                FeatureCapability(
                    status = DiagnosticStatus.INITIALIZING,
                    source = AltitudeSource.LOCATION_GNSS,
                    summary = "Awaiting GNSS fix",
                    detail = "No barometer detected; waiting for GNSS altitude from Location provider."
                )
            }
        }
    }

    private fun combineSensorStatus(
        sensors: List<SensorSpec>,
        isObserving: Boolean
    ): DiagnosticStatus {
        if (sensors.any {
                it.accuracy == SensorAccuracyLevel.UNRELIABLE ||
                    it.accuracy == SensorAccuracyLevel.NO_CONTACT
            }
        ) {
            return DiagnosticStatus.ACCURACY_UNRELIABLE
        }
        if (isObserving && sensors.any { it.accuracy == SensorAccuracyLevel.INITIALIZING }) {
            return DiagnosticStatus.INITIALIZING
        }
        return DiagnosticStatus.AVAILABLE
    }
}
