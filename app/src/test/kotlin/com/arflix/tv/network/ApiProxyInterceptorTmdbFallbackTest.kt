package com.arflix.tv.network

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException

class ApiProxyInterceptorTmdbFallbackTest {

    private val tmdbRequest = Request.Builder()
        .url("https://api.themoviedb.org/3/tv/1399/season/1?api_key=k&language=en-US")
        .build()

    private class FakeChain(
        private val request: Request,
        private val direct: (Request) -> Response,
    ) {
        val sent = mutableListOf<Request>()
        val chain: Interceptor.Chain = mockk(relaxed = true) {
            every { request() } returns request
            every { call() } returns mockk<Call> { every { isCanceled() } returns false }
            every { proceed(any()) } answers {
                val req = firstArg<Request>()
                sent += req
                if (req.url.host == "api.themoviedb.org") direct(req) else response(req, 200)
            }
        }
    }

    private fun interceptor(edgeProxy: Boolean = false) =
        ApiProxyInterceptor(tmdbEdgeProxy = edgeProxy, proxyConfigured = { true })

    @Test
    fun `a working direct call stays direct`() {
        val fake = FakeChain(tmdbRequest) { response(it, 200) }
        val result = interceptor().intercept(fake.chain)

        assertThat(result.code).isEqualTo(200)
        assertThat(fake.sent.map { it.url.host }).containsExactly("api.themoviedb.org")
    }

    @Test
    fun `a rejected key falls back to the proxy and stays there`() {
        val fake = FakeChain(tmdbRequest) { response(it, 401) }
        val interceptor = interceptor()

        val first = interceptor.intercept(fake.chain)
        assertThat(first.code).isEqualTo(200)
        assertThat(fake.sent).hasSize(2)
        val proxied = fake.sent.last()
        assertThat(proxied.url.host).isNotEqualTo("api.themoviedb.org")
        assertThat(proxied.url.queryParameter("path")).isEqualTo("/tv/1399/season/1")
        assertThat(proxied.url.queryParameter("api_key")).isNull()

        fake.sent.clear()
        interceptor.intercept(fake.chain)
        assertThat(fake.sent.map { it.url.host }).doesNotContain("api.themoviedb.org")
    }

    @Test
    fun `an unreachable TMDB host falls back to the proxy`() {
        val fake = FakeChain(tmdbRequest) { throw IOException("blocked") }
        val result = interceptor().intercept(fake.chain)

        assertThat(result.code).isEqualTo(200)
        assertThat(fake.sent.last().url.host).isNotEqualTo("api.themoviedb.org")
    }

    @Test
    fun `other errors are returned as they are`() {
        val fake = FakeChain(tmdbRequest) { response(it, 404) }
        val result = interceptor().intercept(fake.chain)

        assertThat(result.code).isEqualTo(404)
        assertThat(fake.sent).hasSize(1)
    }

    @Test
    fun `edge proxy builds never call TMDB directly`() {
        val fake = FakeChain(tmdbRequest) { error("direct call not expected") }
        interceptor(edgeProxy = true).intercept(fake.chain)

        assertThat(fake.sent.map { it.url.host }).doesNotContain("api.themoviedb.org")
    }

    private companion object {
        fun response(request: Request, code: Int) = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .body("{}".toResponseBody())
            .build()
    }
}
