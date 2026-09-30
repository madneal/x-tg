package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTypeMapperTest {
    @Test
    fun mapsInternetWifiToWifi() {
        assertEquals("networkTypeWiFi", tdLibNetworkType(true, true, false))
    }

    @Test
    fun mapsInternetCellularToMobile() {
        assertEquals("networkTypeMobile", tdLibNetworkType(true, false, true))
    }

    @Test
    fun mapsConnectedButUnusableNetworkToNone() {
        assertEquals("networkTypeNone", tdLibNetworkType(false, true, false))
    }

    @Test
    fun mapsEthernetVpnOrOtherInternetTransportToOther() {
        assertEquals("networkTypeOther", tdLibNetworkType(true, false, false))
    }
}
