package com.example.tgclient.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdLibParametersStateTest {
    @Test
    fun retriesAuthorizationLookupWithoutResendingAcceptedParameters() = runBlocking {
        val state = TdLibParametersState()
        var parameterRequests = 0

        state.configureIfNeeded { parameterRequests++ }
        assertTrue(state.isConfigured)

        // Model getAuthorizationState failing after setTdlibParameters succeeded.
        state.configureIfNeeded { parameterRequests++ }

        assertEquals(1, parameterRequests)
    }

    @Test
    fun retriesParameterConfigurationWhenTdlibRejectedTheFirstAttempt() = runBlocking {
        val state = TdLibParametersState()
        var parameterRequests = 0

        runCatching {
            state.configureIfNeeded {
                parameterRequests++
                error("temporary setup failure")
            }
        }
        assertFalse(state.isConfigured)

        state.configureIfNeeded { parameterRequests++ }

        assertEquals(2, parameterRequests)
        assertTrue(state.isConfigured)
    }
}
