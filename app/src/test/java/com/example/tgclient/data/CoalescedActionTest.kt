package com.example.tgclient.data

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescedActionTest {
    @Test
    fun combinesAReconnectBurstIntoOneUiSnapshot() = runTest {
        var publications = 0
        val action = CoalescedAction(backgroundScope, delayMillis = 16) { publications++ }

        repeat(250) { action.request() }
        runCurrent()
        assertEquals(0, publications)

        advanceTimeBy(16)
        runCurrent()
        assertEquals(1, publications)
    }

    @Test
    fun requestDuringPublicationSchedulesAnotherSnapshot() = runTest {
        var publications = 0
        lateinit var action: CoalescedAction
        action = CoalescedAction(backgroundScope, delayMillis = 16) {
            publications++
            if (publications == 1) action.request()
        }

        action.request()
        advanceTimeBy(16)
        runCurrent()
        assertEquals(1, publications)

        advanceTimeBy(16)
        runCurrent()
        assertEquals(2, publications)
    }
}
