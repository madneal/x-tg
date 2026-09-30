package com.example.tgclient.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationRequestFieldsTest {
    @Test
    fun phoneLoginUsesInternationalNumberAndNullOptionalSettings() {
        val fields = phoneAuthenticationRequest("+9996612345")

        assertEquals("+9996612345", fields.phoneNumber)
        assertEquals(null, fields.settings)
    }
}
