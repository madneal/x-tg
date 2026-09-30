package com.example.tgclient.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** History responses and live updates may publish concurrently; never overwrite a snapshot. */
internal fun <T> MutableStateFlow<Map<Long, List<T>>>.updateTimeline(
    chatId: Long,
    transform: (List<T>) -> List<T>,
) {
    update { timelines -> timelines + (chatId to transform(timelines[chatId].orEmpty())) }
}
