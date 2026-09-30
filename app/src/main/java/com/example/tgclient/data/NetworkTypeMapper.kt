package com.example.tgclient.data

/** Maps Android's active default network to the TDLib network type constructor. */
internal fun tdLibNetworkType(
    hasInternet: Boolean,
    hasWifiTransport: Boolean,
    hasCellularTransport: Boolean,
): String = when {
    !hasInternet -> "networkTypeNone"
    hasWifiTransport -> "networkTypeWiFi"
    hasCellularTransport -> "networkTypeMobile"
    else -> "networkTypeOther"
}
