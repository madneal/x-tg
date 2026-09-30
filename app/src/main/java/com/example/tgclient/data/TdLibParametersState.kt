package com.example.tgclient.data

/** Tracks whether this in-process TDLib client accepted its parameters. */
internal class TdLibParametersState {
    @Volatile
    var isConfigured: Boolean = false
        private set

    suspend fun configureIfNeeded(configure: suspend () -> Unit) {
        if (isConfigured) return
        configure()
        isConfigured = true
    }
}
