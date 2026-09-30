package com.example.tgclient.data

/** Counts unique persisted or local messages across overlapping TDLib history pages. */
internal class HistoryMessageAccumulator<T>(private val idOf: (T) -> Long) {
    private val seenIds = HashSet<Long>()
    private val values = ArrayList<T>()

    val size: Int get() = values.size

    fun addAll(messages: Iterable<T>) {
        messages.forEach { message ->
            val id = idOf(message)
            if (id != 0L && seenIds.add(id)) values += message
        }
    }

    fun toList(): List<T> = values.toList()
}
