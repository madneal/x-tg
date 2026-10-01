package com.example.tgclient.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledMessageTest {
    @Test fun encodesServerSideScheduledSendTime() {
        val options = scheduledSendOptions(2000003600L, 2000000000L)
        assertEquals("messageSendOptions", options.getString("@type"))
        val state = options.getJSONObject("scheduling_state")
        assertEquals("messageSchedulingStateSendAtDate", state.getString("@type"))
        assertEquals(2000003600L, state.getLong("send_date"))
    }
    @Test fun encodesDailyRepeatAndKeepsOneTimeDefault() {
        assertEquals(86400, scheduledSendOptions(2000003600L, 2000000000L, 86400).getJSONObject("scheduling_state").getInt("repeat_period"))
        assertEquals(0, scheduledSendOptions(2000003600L, 2000000000L).getJSONObject("scheduling_state").getInt("repeat_period"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedRepeat() { scheduledSendOptions(2000003600L, 2000000000L, 12) }
    @Test(expected = IllegalArgumentException::class) fun rejectsPastTime() { scheduledSendOptions(100, 200) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDatesBeyondServerLimit() { scheduledSendOptions(200 + 368L * 86400, 200) }
}
