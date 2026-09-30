package com.example.tgclient.data

import com.example.tgclient.model.TdConnectionStatus

internal fun tdConnectionStatus(type: String): TdConnectionStatus = when (type) {
    "connectionStateWaitingForNetwork" -> TdConnectionStatus.WAITING_FOR_NETWORK
    "connectionStateConnectingToProxy" -> TdConnectionStatus.CONNECTING_TO_PROXY
    "connectionStateConnecting" -> TdConnectionStatus.CONNECTING
    "connectionStateUpdating" -> TdConnectionStatus.UPDATING
    "connectionStateReady" -> TdConnectionStatus.READY
    else -> TdConnectionStatus.UNKNOWN
}
