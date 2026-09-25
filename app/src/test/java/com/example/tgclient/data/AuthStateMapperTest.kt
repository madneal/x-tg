package com.example.tgclient.data

import com.example.tgclient.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthStateMapperTest {
    @Test
    fun mapsPhoneAuthorizationState() {
        assertEquals(
            AuthState.WaitPhoneNumber,
            AuthStateMapper.fromType("authorizationStateWaitPhoneNumber"),
        )
    }

    @Test
    fun mapsReadyAuthorizationState() {
        assertEquals(AuthState.Ready, AuthStateMapper.fromType("authorizationStateReady"))
    }

    @Test
    fun unknownStateDoesNotGrantAccess() {
        assertEquals(AuthState.Loading, AuthStateMapper.fromType("futureAuthorizationState"))
    }
}
