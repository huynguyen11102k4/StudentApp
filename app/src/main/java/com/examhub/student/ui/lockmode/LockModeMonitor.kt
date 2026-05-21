package com.examhub.student.ui.lockmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class LockModeMonitor(
    private val context: Context,
    private val onNetworkLost: () -> Unit,
    private val onNetworkAvailable: () -> Unit,
    private val onScreenOff: () -> Unit
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registeredNetwork = false
    private var registeredScreen = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            onNetworkLost()
        }

        override fun onAvailable(network: Network) {
            onNetworkAvailable()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
        }
    }

    fun start() {
        if (!registeredNetwork) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            registeredNetwork = true
        }
        if (!registeredScreen) {
            context.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registeredScreen = true
        }
    }

    fun stop() {
        if (registeredNetwork) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            registeredNetwork = false
        }
        if (registeredScreen) {
            runCatching { context.unregisterReceiver(screenReceiver) }
            registeredScreen = false
        }
    }
}
