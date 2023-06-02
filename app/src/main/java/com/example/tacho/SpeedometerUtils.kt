package com.example.tacho

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class SpeedometerUtils {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val ONE_MINUTE_IN_MILLIS = 60000L
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var speedTextView: TextView
    private lateinit var maxSpeedView: TextView

    private var maxSpeed: Float = 0f
    private val speedList = mutableListOf<Float>()
    private val handler = Handler()
    private var isTimerRunning = false

    fun initializeSpeedometer(
        activity: AppCompatActivity,
        speedTextView: TextView,
        maxSpeedView: TextView
    ) {
        this.speedTextView = speedTextView
        this.maxSpeedView = maxSpeedView
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation
                val speedMps = lastLocation?.speed ?: 0f
                val speedKmph = speedMps * 3.6
                speedTextView.text = String.format("%.1f", speedKmph)
                if (isTimerRunning) {
                    speedList.add(speedMps)
                    if (speedMps > maxSpeed) {
                        maxSpeed = speedMps
                    }
                }
            }
        }

        requestLocationUpdates(activity)
        startTimer()
    }

    private fun requestLocationUpdates(activity: AppCompatActivity) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 0
            fastestInterval = 0
        }

        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(
                activity,
                locationPermission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(locationPermission),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startTimer() {
        isTimerRunning = true
        handler.postDelayed({
            isTimerRunning = false
            calculateMaxSpeed()
        }, ONE_MINUTE_IN_MILLIS)
    }

    private fun calculateMaxSpeed() {
        val maxSpeedKmph = maxSpeed * 3.6
        maxSpeedView.text = String.format("%.1f", maxSpeedKmph)
        resetValues()
    }

    private fun resetValues() {
        maxSpeed = 0f
        speedList.clear()
        startTimer()
    }

    fun stopLocationUpdates(activity: AppCompatActivity) {
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(
                activity,
                locationPermission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

