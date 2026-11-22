package com.arflix.tv.data.repository

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce

internal class ExpiringStreamCache<K, V>(
    private val capacity: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val expiresAt: (V) -> Long
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean =
            size > capacity
    }

    @Synchronized
    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt >= now()) return entry.value
        entries.remove(key)
        return null
    }

    @Synchronized
    operator fun set(key: K, value: V) {
        val time = now()
        entries.values.removeAll { it.expiresAt < time }
        entries[key] = Entry(value, expiresAt(value))
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun remove(key: K) {
        entries.remove(key)
    }
}

@OptIn(FlowPreview::class)
internal suspend fun collectAddonHealthWrites(updates: Flow<Unit>, write: suspend () -> Unit) {
    updates.debounce(5_000L).collect { write() }
}
