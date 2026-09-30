package com.example.tgclient.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Coalesces bursts of replaceable UI snapshots while preserving every underlying update. */
internal class CoalescedAction(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val action: () -> Unit,
) {
    private val scheduled = AtomicBoolean(false)

    fun request() {
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(delayMillis)
            // Clear before invoking the action. A request that races with publication
            // schedules a follow-up snapshot instead of getting lost.
            scheduled.set(false)
            action()
        }
    }
}
