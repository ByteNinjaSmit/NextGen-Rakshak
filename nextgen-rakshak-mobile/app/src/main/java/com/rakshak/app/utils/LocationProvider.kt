package com.rakshak.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/** Thin wrapper over FusedLocationProvider to fetch the current GPS fix. */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Current location, or null if unavailable / permission missing. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Location? = runCatching {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
    }.getOrNull()
}
