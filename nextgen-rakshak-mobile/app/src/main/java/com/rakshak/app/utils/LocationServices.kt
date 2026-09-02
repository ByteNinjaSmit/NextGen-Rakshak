package com.rakshak.app.utils

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat

/**
 * Whether the device's Location toggle is on. Nearby Connections discovery needs
 * it (the BLE scan it uses is a location-class API on many OEMs), yet the runtime
 * permission being granted is not the same thing — a volunteer can grant location
 * and still have the system toggle off, which makes the mesh silently find no
 * peers. Surfaced in the mesh debug screen.
 */
object LocationServices {

    fun enabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return true // can't tell — don't cry wolf
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            LocationManagerCompat.isLocationEnabled(manager)
        }
    }
}
