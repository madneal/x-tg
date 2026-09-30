package com.example.tgclient.data

/** Phone-first authorization uses TDLib defaults for optional native-app-only settings. */
internal data class PhoneAuthenticationRequest(val phoneNumber: String) {
    val settings: Nothing? get() = null
}

internal fun phoneAuthenticationRequest(phoneNumber: String) =
    PhoneAuthenticationRequest(phoneNumber)
