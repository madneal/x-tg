package com.example.tgclient.data

import org.json.JSONObject

data class ScheduledMessage(val id: Long, val text: String, val sendAt: Long, val repeatPeriod: Int = 0)

internal fun scheduledSendOptions(sendAt: Long, now: Long = System.currentTimeMillis() / 1000, repeatPeriod: Int = 0): JSONObject {
    require(repeatPeriod == 0 || repeatPeriod == 86400) { "Unsupported repeat interval" }
    require(sendAt > now + 10) { "Choose a time at least 10 seconds in the future" }
    require(sendAt <= now + 366L * 86400 && sendAt <= Int.MAX_VALUE) { "Choose a date within the next year" }
    return JSONObject().put("@type", "messageSendOptions")
        .put("scheduling_state", JSONObject().put("@type", "messageSchedulingStateSendAtDate").put("send_date", sendAt).put("repeat_period", repeatPeriod))
}
