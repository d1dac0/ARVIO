package com.arflix.tv.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StreamCacheSupportTest {
    @Test
    fun retainsOnlyTheMostRecentEntries() {
        val cache = ExpiringStreamCache<Int, Long>(24, now = { 0L }) { it }
        repeat(24) { cache[it] = 100L }
        assertThat(cache[0]).isEqualTo(100L)
        for (key in 24..29) cache[key] = 100L

        assertThat((0..29).filter { cache[it] != null }).containsExactly(0, *(7..29).toList().toTypedArray())
    }

    @Test
    fun prunesExpiredEntriesBeforeEvictingLiveOnes() {
        var now = 0L
        val cache = ExpiringStreamCache<String, Long>(2, now = { now }) { it }
        cache["live"] = 100L
        cache["expired"] = 10L
        now = 20L
        cache["new"] = 100L

        assertThat(cache["live"]).isEqualTo(100L)
        assertThat(cache["expired"]).isNull()
        assertThat(cache["new"]).isEqualTo(100L)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun writesOnceAfterEightHealthUpdates() = runTest {
        val updates = Channel<Unit>(Channel.CONFLATED)
        var writes = 0
        backgroundScope.launch { collectAddonHealthWrites(updates.receiveAsFlow()) { writes++ } }

        repeat(8) {
            updates.trySend(Unit)
            advanceTimeBy(100L)
        }
        assertThat(writes).isEqualTo(0)

        advanceTimeBy(5_000L)
        runCurrent()
        assertThat(writes).isEqualTo(1)
    }
}
