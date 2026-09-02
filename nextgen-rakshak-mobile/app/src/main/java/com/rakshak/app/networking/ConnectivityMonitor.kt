package com.rakshak.app.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether this device currently has internet. The mesh uses it two ways:
 * a device that is online announces itself as a gateway in its HELLO so peers
 * route match reports toward it, and the online↔mesh bridge only runs while
 * there is a connection to bridge to.
 */
class ConnectivityMonitor(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = currentlyOnline()
        }

        override fun onLost(network: Network) {
            _online.value = currentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    fun start() {
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
            .onFailure { Log.w(TAG, "network callback registration failed", it) }
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }

    private fun currentlyOnline(): Boolean {
        val network = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "ConnectivityMonitor"
    }
}
