package com.examhub.student.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkStatusProvider {
    fun currentNetwork(context: Context): Map<String, String> {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return mapOf("type" to "none", "connected" to "false")
        val network = connectivityManager.activeNetwork
            ?: return mapOf("type" to "none", "connected" to "false")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return mapOf("type" to "none", "connected" to "false")

        return mapOf(
            "type" to capabilities.networkType(),
            "connected" to "true",
            "validated" to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString(),
            "metered" to (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).toString(),
            "downstream_kbps" to capabilities.linkDownstreamBandwidthKbps.toString(),
            "upstream_kbps" to capabilities.linkUpstreamBandwidthKbps.toString()
        )
    }

    private fun NetworkCapabilities.networkType(): String = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
        else -> "other"
    }
}
