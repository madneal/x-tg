package com.example.tgclient.data

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MessageTimelineTest {
    @Test fun historyCommitDoesNotOverwriteConcurrentIncomingMessagesOrOtherChats() {
        val timelines = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
        val historyRead = CountDownLatch(1)
        val incomingPublished = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val history = executor.submit {
                timelines.updateTimeline(1L) { current ->
                    historyRead.countDown()
                    check(incomingPublished.await(5, TimeUnit.SECONDS))
                    (current + listOf(1L, 2L)).distinct().sorted()
                }
            }
            check(historyRead.await(5, TimeUnit.SECONDS))
            timelines.updateTimeline(1L) { it + 3L }
            timelines.updateTimeline(2L) { it + 99L }
            incomingPublished.countDown()
            history.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1L, 2L, 3L), timelines.value[1L])
            assertEquals(listOf(99L), timelines.value[2L])
        } finally {
            incomingPublished.countDown()
            executor.shutdownNow()
        }
    }
}
