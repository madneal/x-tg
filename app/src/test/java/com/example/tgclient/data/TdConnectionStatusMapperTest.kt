package com.example.tgclient.data

import com.example.tgclient.model.TdConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TdConnectionStatusMapperTest {
    @Test
    fun mapsEveryKnownTdlibConnectionState() {
        assertEquals(TdConnectionStatus.WAITING_FOR_NETWORK, tdConnectionStatus("connectionStateWaitingForNetwork"))
        assertEquals(TdConnectionStatus.CONNECTING_TO_PROXY, tdConnectionStatus("connectionStateConnectingToProxy"))
        assertEquals(TdConnectionStatus.CONNECTING, tdConnectionStatus("connectionStateConnecting"))
        assertEquals(TdConnectionStatus.UPDATING, tdConnectionStatus("connectionStateUpdating"))
        assertEquals(TdConnectionStatus.READY, tdConnectionStatus("connectionStateReady"))
    }

    @Test
    fun unknownFutureStatesDoNotMasqueradeAsReady() {
        assertEquals(TdConnectionStatus.UNKNOWN, tdConnectionStatus("connectionStateFuture"))
    }
}
