package com.example.tgclient.data

import com.example.tgclient.model.AuthState
import org.json.JSONObject

object AuthStateMapper {
    fun fromJson(state: JSONObject?): AuthState = fromType(state?.optString("@type"))

    fun fromType(type: String?): AuthState = when (type) {
        "authorizationStateWaitPhoneNumber" -> AuthState.WaitPhoneNumber
        "authorizationStateWaitEmailAddress" -> AuthState.WaitEmailAddress
        "authorizationStateWaitEmailCode" -> AuthState.WaitEmailCode
        "authorizationStateWaitCode" -> AuthState.WaitCode
        "authorizationStateWaitPassword" -> AuthState.WaitPassword
        "authorizationStateWaitRegistration" -> AuthState.WaitRegistration
        "authorizationStateReady" -> AuthState.Ready
        "authorizationStateLoggingOut" -> AuthState.LoggingOut
        "authorizationStateClosing", "authorizationStateClosed" -> AuthState.Loading
        else -> AuthState.Loading
    }
}
