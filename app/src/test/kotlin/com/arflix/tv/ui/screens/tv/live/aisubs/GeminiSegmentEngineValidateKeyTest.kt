package com.arflix.tv.ui.screens.tv.live.aisubs

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Test

class GeminiSegmentEngineValidateKeyTest {

    private val offlineClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { throw AssertionError("validateKey must not reach the network") })
        .build()

    @Test
    fun `a key with a zero-width space is rejected instead of throwing`() = runBlocking {
        val engine = GeminiSegmentEngine(apiKey = { "" }, client = offlineClient)

        val result = engine.validateKey("AIza​key")

        assertThat(result).isFalse()
    }
}
